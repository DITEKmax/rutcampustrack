package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for EventConsumer routing logic.
 * <p>
 * D-09 (semester.archived → SemesterCacheService.refresh()) is proven here rather than
 * via integration test: the prior IT was flaky under cached-Spring-context reuse where
 * EventConsumer held a stale mock reference — see git history for details.
 *
 * <p>M13 G24-fix-7 follow-up: добавлен @Mock IdempotencyGuard + setup
 * stub'а tryClaim → true. До этого fix'а EventConsumer.idempotencyGuard
 * (added в M13 G8) был null в тесте, а NPE случался только начиная с
 * fail-closed G24-fix-6 (раньше null guard'а не вызывался — старый
 * условный flow). Теперь явный mock покрывает M13 G8 + G24-fix-6.
 */
@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private LessonEventService lessonEventService;

    @Mock
    private LessonTransferParticipantService lessonTransferParticipantService;

    @Mock
    private SemesterCacheService semesterCacheService;

    @Mock
    private StudentRequestService studentRequestService;

    @Mock
    private SemesterArchiveParticipantService semesterArchiveParticipantService;

    @Mock
    private SemesterArchiveEffectService semesterArchiveEffectService;

    @Mock
    private IdempotencyGuard idempotencyGuard;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private EventConsumer eventConsumer;

    @BeforeEach
    void setUp() {
        // Default: первый delivery — claim успешен, handler выполняется.
        // lenient — тесты missingEventType / lessonStarted делают early
        // return до tryClaim, для них stub не используется. STRICT_STUBS
        // считал бы это test failure — lenient допускает unused stubbing.
        org.mockito.Mockito.lenient()
                .when(idempotencyGuard.tryClaim(eq(EventConsumer.CONSUMER_ID), any()))
                .thenReturn(true);
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(semesterArchiveEffectService).apply(any(), any());
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(semesterArchiveEffectService).apply(any(), any(), any());
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> transaction = invocation.getArgument(0);
            transaction.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Map<String, Object> envelope(String eventType, Map<String, Object> payload) {
        return Map.of(
                "event_type", eventType,
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", Instant.now().toString(),
                "event_version", 1,
                "source", "notification-bot",
                "payload", payload
        );
    }

    private Map<String, Object> scheduleEnvelope(String eventType, Map<String, Object> payload) {
        return Map.of(
                "event_type", eventType,
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", Instant.now().toString(),
                "event_version", 1,
                "source", "schedule-service",
                "trace_id", "consumer-test-correlation",
                "payload", payload
        );
    }

    @Test
    void semesterArchived_triggersRefresh() {
        eventConsumer.onEvent(envelope("semester.archived", Map.of("semester_id", 1)));
        verify(semesterCacheService).refresh();
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void lessonClosed_delegatesToLessonEventService() {
        LessonEventService.LessonClosedSnapshot snapshot = new LessonEventService.LessonClosedSnapshot(
                1L, 10L, 3L, null, null, null);
        when(lessonEventService.prepareLessonClosed(1L, 10L, 3L)).thenReturn(snapshot);
        eventConsumer.onEvent(scheduleEnvelope("lesson.closed", Map.of(
                "lesson_id", 1, "group_id", 10, "semester_id", 3
        )));
        verify(lessonEventService).prepareLessonClosed(1L, 10L, 3L);
        verify(lessonEventService).applyLessonClosed(snapshot);
        verifyNoInteractions(semesterCacheService);
    }

    @Test
    void lessonClosed_duplicateReceiptIsAcknowledgedWithoutExternalPreparation() {
        Map<String, Object> envelope = scheduleEnvelope("lesson.closed", Map.of(
                "lesson_id", 1, "group_id", 10, "semester_id", 3
        ));
        when(semesterArchiveEffectService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveEffectService).apply(eq(envelope), any());
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void duplicateTrackedScheduleEffect_replaysStoredReceiptBeforeClaim() {
        Map<String, Object> envelope = scheduleEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 1, "semester_id", 3));
        when(semesterArchiveEffectService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveEffectService).apply(eq(envelope), any());
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void duplicateTrackedScheduleEffectWithoutReceiptIsNotAcknowledged() {
        Map<String, Object> envelope = scheduleEnvelope("lesson.deleted", Map.of(
                "lesson_ids", java.util.List.of(1L), "semester_id", 3));
        when(idempotencyGuard.tryClaim(EventConsumer.CONSUMER_ID, envelope)).thenReturn(false);
        when(semesterArchiveEffectService.hasReceipt(envelope)).thenReturn(false);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveEffectService).hasReceipt(envelope);
        org.mockito.Mockito.verify(semesterArchiveEffectService, never()).apply(any(), any());
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void duplicateTransferWithStoredReceiptIsSentThroughReceiptReplay() {
        Map<String, Object> envelope = scheduleEnvelope("lesson.transfer.requested", Map.of());
        when(lessonTransferParticipantService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(lessonTransferParticipantService).apply(envelope);
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
    }

    @Test
    void duplicateParticipantCommandWithStoredReceiptIsSentThroughReceiptReplay() {
        Map<String, Object> envelope = new java.util.HashMap<>(scheduleEnvelope(
                "semester.archive.participant.command", Map.of()));
        envelope.put("source", "academic-service");
        when(semesterArchiveParticipantService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveParticipantService).apply(envelope);
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
    }

    @Test
    void lessonCancelled_delegatesToLessonEventService() {
        eventConsumer.onEvent(scheduleEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 1, "semester_id", 3
        )));
        verify(lessonEventService).processLessonCancelled(1L, 3L);
        verifyNoInteractions(semesterCacheService);
    }

    @Test
    void lessonStarted_isNoOp() {
        eventConsumer.onEvent(envelope("lesson.started", Map.of("lesson_id", 1)));
        verifyNoInteractions(lessonEventService, semesterCacheService);
    }

    @Test
    void unknownEventType_isIgnored() {
        eventConsumer.onEvent(envelope("something.random", Map.of("x", 1)));
        verifyNoInteractions(lessonEventService, semesterCacheService);
    }

    @Test
    void missingEventType_isIgnored() {
        eventConsumer.onEvent(Map.of("event_id", "x", "payload", Map.of()));
        verifyNoInteractions(lessonEventService, semesterCacheService);
    }

    @Test
    void lateCheckinDecision_passesPositiveInternalActorToTheService() {
        String requestId = "0123456789abcdef01234567";
        eventConsumer.onEvent(envelope("late_checkin.decision", Map.of(
                "request_id", requestId, "decision_by", 42L, "approved", true
        )));

        verify(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);
    }

    @Test
    void lateCheckinDecision_missingOrInvalidActorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> eventConsumer.onEvent(envelope(
                "late_checkin.decision", Map.of(
                        "request_id", "0123456789abcdef01234567", "approved", true))));
        assertThrows(IllegalArgumentException.class, () -> eventConsumer.onEvent(envelope(
                "late_checkin.decision", Map.of(
                        "request_id", "0123456789abcdef01234567", "decision_by", 0L, "approved", true))));

        verifyNoInteractions(studentRequestService);
    }

    @Test
    void decisionWithUntrustedSourceIsRejectedBeforeDomain() {
        Map<String, Object> event = new java.util.HashMap<>(envelope("late_checkin.decision", Map.of(
                "request_id", "0123456789abcdef01234567", "decision_by", 42L, "approved", true)));
        event.put("source", "web-client");

        assertThrows(IllegalArgumentException.class, () -> eventConsumer.onEvent(event));
        verifyNoInteractions(studentRequestService);
    }

    @Test
    void authenticatedStaleLateDecisionIsAcknowledgedWithoutMutation() {
        String requestId = "0123456789abcdef01234567";
        doThrow(new ConflictException("Решение по запросу уже принято"))
                .when(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);

        assertDoesNotThrow(() -> eventConsumer.onEvent(envelope("late_checkin.decision", Map.of(
                "request_id", requestId, "decision_by", 42L, "approved", true))));
        verify(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);
    }
}
