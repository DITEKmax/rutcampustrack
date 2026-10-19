package ru.rutcampustrack.attendance.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

/**
 * Unit tests for EventConsumer routing logic.
 *
 * <p>D-09 (semester.archived → SemesterCacheService.refresh()) is proven here rather than via
 * integration test: the prior IT was flaky under cached-Spring-context reuse where EventConsumer
 * held a stale mock reference — see git history for details.
 *
 * <p>M13 G24-fix-7 follow-up: добавлен @Mock IdempotencyGuard + setup stub'а tryClaim → true. До
 * этого fix'а EventConsumer.idempotencyGuard (added в M13 G8) был null в тесте, а NPE случался
 * только начиная с fail-closed G24-fix-6 (раньше null guard'а не вызывался — старый условный flow).
 * Теперь явный mock покрывает M13 G8 + G24-fix-6.
 */
@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock private LessonEventService lessonEventService;

    @Mock private LessonTransferParticipantService lessonTransferParticipantService;

    @Mock private SemesterCacheService semesterCacheService;

    @Mock private StudentRequestService studentRequestService;

    @Mock private SemesterArchiveParticipantService semesterArchiveParticipantService;

    @Mock private SemesterDeletionParticipantService semesterDeletionParticipantService;

    @Mock private SemesterArchiveEffectService semesterArchiveEffectService;

    @Mock private IdempotencyGuard idempotencyGuard;

    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private EventConsumer eventConsumer;

    @BeforeEach
    void setUp() {
        // Default: первый delivery — claim успешен, handler выполняется.
        // lenient — тесты missingEventType / lessonStarted делают early
        // return до tryClaim, для них stub не используется. STRICT_STUBS
        // считал бы это test failure — lenient допускает unused stubbing.
        org.mockito.Mockito.lenient()
                .when(idempotencyGuard.tryClaim(eq(EventConsumer.CONSUMER_ID), any()))
                .thenReturn(true);
        org.mockito.Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            ((Runnable) invocation.getArgument(1)).run();
                            return null;
                        })
                .when(semesterArchiveEffectService)
                .apply(any(), any());
        org.mockito.Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            ((Runnable) invocation.getArgument(1)).run();
                            ((Runnable) invocation.getArgument(2)).run();
                            return null;
                        })
                .when(semesterArchiveEffectService)
                .apply(any(), any(), any());
        org.mockito.Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            Consumer<TransactionStatus> transaction = invocation.getArgument(0);
                            transaction.accept(null);
                            return null;
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
    }

    private Map<String, Object> envelope(String eventType, Map<String, Object> payload) {
        return Map.of(
                "event_type",
                eventType,
                "event_id",
                UUID.randomUUID().toString(),
                "occurred_at",
                Instant.now().toString(),
                "event_version",
                1,
                "source",
                "notification-bot",
                "payload",
                payload);
    }

    private Map<String, Object> scheduleEnvelope(String eventType, Map<String, Object> payload) {
        return Map.of(
                "event_type",
                eventType,
                "event_id",
                UUID.randomUUID().toString(),
                "occurred_at",
                Instant.now().toString(),
                "event_version",
                1,
                "source",
                "schedule-service",
                "trace_id",
                "consumer-test-correlation",
                "payload",
                payload);
    }

    @Test
    void initialMembershipFailureKeepsPrivateCorrelationAndOnlyClosedDiagnosticFacts() {
        var initial = org.mockito.Mockito.mock(InitialMembershipAttendanceBackfillService.class);
        eventConsumer.setInitialMembershipBackfills(initial);
        String privatePayload = "PRIVATE_INITIAL_MEMBERSHIP_PAYLOAD_MARKER";
        String privateError = "PRIVATE_INITIAL_MEMBERSHIP_ERROR_MARKER";
        String eventId = UUID.randomUUID().toString();
        var envelope =
                Map.<String, Object>of(
                        "event_type",
                        InitialMembershipAttendanceBackfillService.TYPE,
                        "event_id",
                        eventId,
                        "trace_id",
                        "0123456789abcdef0123456789abcdef",
                        "request_id",
                        "33333333-3333-4333-8333-333333333333",
                        "source",
                        "academic-service",
                        "payload",
                        Map.of("private", privatePayload));
        var logger = (Logger) LoggerFactory.getLogger(EventConsumer.class);
        Level previousLevel = logger.getLevel();
        var previousMdc = MDC.getCopyOfContextMap();
        var appender =
                new ListAppender<ILoggingEvent>() {
                    @Override
                    protected void append(ILoggingEvent event) {
                        event.prepareForDeferredProcessing();
                        super.append(event);
                    }
                };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            MDC.put("eventId", "previous-event");
            MDC.put("requestId", "previous-request");
            MDC.put("originTraceId", "previous-origin");
            MDC.put("traceId", "framework-trace");
            var failures = new java.util.LinkedHashMap<RuntimeException, String>();
            failures.put(
                    new ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException(
                            privateError),
                    "ACADEMIC_UNAVAILABLE");
            failures.put(new IllegalStateException(privateError), "OTHER");
            failures.put(new IllegalArgumentException(privateError), "INVALID_ARGUMENT");
            failures.put(
                    new IllegalStateException("Academic Service unavailable: UNAVAILABLE"),
                    "OTHER");
            failures.put(
                    new ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException(
                            null),
                    "ACADEMIC_UNAVAILABLE");
            for (var status : io.grpc.Status.Code.values()) {
                String category =
                        switch (status) {
                            case UNAVAILABLE -> "ACADEMIC_RPC_UNAVAILABLE";
                            case DEADLINE_EXCEEDED -> "ACADEMIC_RPC_DEADLINE";
                            case UNAUTHENTICATED -> "ACADEMIC_RPC_AUTHENTICATION";
                            case PERMISSION_DENIED -> "ACADEMIC_RPC_PERMISSION";
                            default -> "ACADEMIC_RPC_OTHER";
                        };
                failures.put(
                        new ru.rutcampustrack.attendance.exception
                                .AcademicServiceUnavailableException(
                                "Academic Service unavailable: " + status, status),
                        category);
            }
            var guards =
                    Map.of(
                            "Attendance semester authority has no directed service identity",
                            "ACADEMIC_DIRECTED_IDENTITY",
                            "Mismatched semester state",
                            "ACADEMIC_SEMESTER_ECHO",
                            "Initial membership semester is unavailable for admitted effects",
                            "ACADEMIC_SEMESTER_ADMISSION",
                            "Academic returned a missing or mismatched historical roster echo",
                            "ACADEMIC_ROSTER_ECHO",
                            "Academic returned duplicate or invalid historical student identity",
                            "ACADEMIC_ROSTER_IDENTITY");
            for (var guard : guards.entrySet()) {
                failures.put(
                        new ru.rutcampustrack.attendance.exception
                                .AcademicServiceUnavailableException(guard.getKey()),
                        guard.getValue());
                failures.put(
                        new ru.rutcampustrack.attendance.exception
                                .AcademicServiceUnavailableException(guard.getKey() + privateError),
                        "ACADEMIC_UNAVAILABLE");
            }
            for (String message :
                    java.util.List.of(
                            "Academic Service unavailable: UNAVAILABLE " + privateError,
                            privateError + "Academic Service unavailable: UNAVAILABLE",
                            "Academic Service unavailable: UNAVAILABLE\n" + privateError,
                            "Academic Service unavailable: UNAVAILABLE ",
                            "academic Service unavailable: UNAVAILABLE",
                            "Academic Service unavailable:\tUNAVAILABLE")) {
                failures.put(
                        new ru.rutcampustrack.attendance.exception
                                .AcademicServiceUnavailableException(
                                message, io.grpc.Status.Code.UNAVAILABLE),
                        "ACADEMIC_UNAVAILABLE");
            }
            for (var entry : failures.entrySet()) {
                int previousRecords = appender.list.size();
                RuntimeException failure = entry.getKey();
                doThrow(failure).when(initial).handle(envelope);
                assertSame(
                        failure,
                        assertThrows(
                                RuntimeException.class, () -> eventConsumer.onEvent(envelope)));
                assertEquals(previousRecords + 1, appender.list.size());
                var record = appender.list.getLast();
                assertEquals(
                        "event consumption failure family=INITIAL_MEMBERSHIP category="
                                + entry.getValue(),
                        record.getFormattedMessage());
                assertEquals(Level.WARN, record.getLevel());
                assertNull(record.getThrowableProxy());
                assertEquals(1, record.getArgumentArray().length);
                assertTrue(record.getArgumentArray()[0] instanceof Enum<?>);
                assertFalse(record.getFormattedMessage().contains(privatePayload));
                assertFalse(record.getFormattedMessage().contains(privateError));
                assertEquals(eventId, record.getMDCPropertyMap().get("eventId"));
                assertEquals(
                        "33333333-3333-4333-8333-333333333333",
                        record.getMDCPropertyMap().get("requestId"));
                assertEquals(
                        "0123456789abcdef0123456789abcdef",
                        record.getMDCPropertyMap().get("originTraceId"));
                assertEquals("framework-trace", record.getMDCPropertyMap().get("traceId"));
                assertEquals("previous-event", MDC.get("eventId"));
                assertEquals("previous-request", MDC.get("requestId"));
                assertEquals("previous-origin", MDC.get("originTraceId"));
                assertEquals("framework-trace", MDC.get("traceId"));
            }
            assertEquals(failures.size(), appender.list.size());
            org.mockito.Mockito.doNothing().when(initial).handle(envelope);
            assertDoesNotThrow(() -> eventConsumer.onEvent(envelope));
            assertEquals(failures.size(), appender.list.size());
            assertEquals("previous-event", MDC.get("eventId"));
            assertEquals("previous-request", MDC.get("requestId"));
            assertEquals("previous-origin", MDC.get("originTraceId"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            MDC.clear();
            if (previousMdc != null) MDC.setContextMap(previousMdc);
        }
    }

    @Test
    void lessonClosedFailureReportsTypedRpcAndLocalCauseWithoutPrivateData() {
        String privateError = "PRIVATE_LESSON_ERROR";
        var event =
                scheduleEnvelope(
                        "lesson.closed", Map.of("lesson_id", 1, "group_id", 10, "semester_id", 3));
        var logger = (Logger) LoggerFactory.getLogger(EventConsumer.class);
        var previousLevel = logger.getLevel();
        var previousMdc = MDC.getCopyOfContextMap();
        var appender =
                new ListAppender<ILoggingEvent>() {
                    @Override
                    protected void append(ILoggingEvent entry) {
                        entry.prepareForDeferredProcessing();
                        super.append(entry);
                    }
                };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            MDC.put("eventId", "previous-event");
            var failures = new java.util.LinkedHashMap<RuntimeException, String>();
            for (var code : io.grpc.Status.Code.values()) {
                var academic =
                        new ru.rutcampustrack.attendance.exception
                                .AcademicServiceUnavailableException(privateError, code);
                var schedule =
                        new ru.rutcampustrack.attendance.exception
                                .ScheduleServiceUnavailableException(privateError, code);
                assertSame(code, academic.getStatusCode());
                assertSame(code, schedule.getStatusCode());
                boolean retryable =
                        code == io.grpc.Status.Code.UNAVAILABLE
                                || code == io.grpc.Status.Code.DEADLINE_EXCEEDED;
                assertEquals(retryable, academic.isRetryable());
                assertEquals(retryable, schedule.isRetryable());
                failures.put(
                        academic,
                        "ACADEMIC_UNAVAILABLE rpc=ACADEMIC_GROUP_MEMBERS status="
                                + code
                                + " cause=NONE");
                failures.put(
                        schedule,
                        "SCHEDULE_UNAVAILABLE rpc=SCHEDULE_LESSON_BY_ID status="
                                + code
                                + " cause=NONE");
            }
            failures.put(
                    new ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException(
                            privateError),
                    "ACADEMIC_UNAVAILABLE rpc=NONE status=NONE cause=SEMANTIC_REJECTION");
            failures.put(
                    new ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException(
                            privateError),
                    "SCHEDULE_UNAVAILABLE rpc=NONE status=NONE cause=SEMANTIC_REJECTION");
            var unknownCommit = new com.mongodb.MongoException(112, privateError);
            unknownCommit.addLabel("UnknownTransactionCommitResult");
            failures.put(unknownCommit, "OTHER rpc=NONE status=NONE cause=MONGO_UNKNOWN_COMMIT");
            var transientTransaction = new com.mongodb.MongoException(1, privateError);
            transientTransaction.addLabel("TransientTransactionError");
            failures.put(
                    new org.springframework.dao.DataAccessResourceFailureException(
                            privateError, transientTransaction),
                    "OTHER rpc=NONE status=NONE cause=MONGO_TRANSIENT_TRANSACTION");
            failures.put(
                    new com.mongodb.MongoException(112, privateError),
                    "OTHER rpc=NONE status=NONE cause=MONGO_WRITE_CONFLICT");
            failures.put(
                    new com.mongodb.MongoException(11000, privateError),
                    "OTHER rpc=NONE status=NONE cause=MONGO_DUPLICATE_KEY");
            failures.put(
                    new com.mongodb.MongoException(123, privateError),
                    "OTHER rpc=NONE status=NONE cause=MONGO_OTHER");
            failures.put(
                    new org.springframework.transaction.UnexpectedRollbackException(privateError),
                    "TRANSACTION_ROLLBACK rpc=NONE status=NONE cause=TRANSACTION_ROLLBACK");
            failures.put(
                    new IllegalStateException(privateError),
                    "OTHER rpc=NONE status=NONE cause=OTHER");
            for (var entry : failures.entrySet()) {
                doThrow(entry.getKey()).when(lessonEventService).prepareLessonClosed(1L, 10L, 3L);
                int before = appender.list.size();
                assertSame(
                        entry.getKey(),
                        assertThrows(RuntimeException.class, () -> eventConsumer.onEvent(event)));
                assertEquals(before + 1, appender.list.size());
                var record = appender.list.getLast();
                assertEquals(
                        "lesson.closed failure stage=PREPARE category=" + entry.getValue(),
                        record.getFormattedMessage());
                assertEquals(5, record.getArgumentArray().length);
                for (var argument : record.getArgumentArray())
                    assertTrue(argument instanceof Enum<?>);
                assertNull(record.getThrowableProxy());
                assertFalse(record.getFormattedMessage().contains(privateError));
                assertFalse(record.getFormattedMessage().contains((String) event.get("event_id")));
                assertEquals(event.get("event_id"), record.getMDCPropertyMap().get("eventId"));
                assertEquals("previous-event", MDC.get("eventId"));
            }
            // Typed dependency errors outside the owned PREPARE caller never claim an RPC method.
            var snapshot =
                    new LessonEventService.LessonClosedSnapshot(1L, 10L, 3L, null, null, null);
            org.mockito.Mockito.doReturn(snapshot)
                    .when(lessonEventService)
                    .prepareLessonClosed(1L, 10L, 3L);
            var failure =
                    new ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException(
                            privateError, io.grpc.Status.Code.INTERNAL);
            doThrow(failure).when(lessonEventService).applyLessonClosed(snapshot);
            assertSame(
                    failure,
                    assertThrows(RuntimeException.class, () -> eventConsumer.onEvent(event)));
            assertEquals(
                    "lesson.closed failure stage=TRANSACTION category=ACADEMIC_UNAVAILABLE rpc=NONE"
                            + " status=INTERNAL cause=NONE",
                    appender.list.getLast().getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            MDC.clear();
            if (previousMdc != null) MDC.setContextMap(previousMdc);
        }
    }

    @Test
    void semesterArchived_triggersRefresh() {
        eventConsumer.onEvent(envelope("semester.archived", Map.of("semester_id", 1)));
        verify(semesterCacheService).refresh();
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void lessonClosed_delegatesToLessonEventService() {
        LessonEventService.LessonClosedSnapshot snapshot =
                new LessonEventService.LessonClosedSnapshot(1L, 10L, 3L, null, null, null);
        when(lessonEventService.prepareLessonClosed(1L, 10L, 3L)).thenReturn(snapshot);
        eventConsumer.onEvent(
                scheduleEnvelope(
                        "lesson.closed", Map.of("lesson_id", 1, "group_id", 10, "semester_id", 3)));
        verify(lessonEventService).prepareLessonClosed(1L, 10L, 3L);
        verify(lessonEventService).applyLessonClosed(snapshot);
        verifyNoInteractions(semesterCacheService);
    }

    @Test
    void lessonClosed_duplicateReceiptIsAcknowledgedWithoutExternalPreparation() {
        Map<String, Object> envelope =
                scheduleEnvelope(
                        "lesson.closed", Map.of("lesson_id", 1, "group_id", 10, "semester_id", 3));
        when(semesterArchiveEffectService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveEffectService).apply(eq(envelope), any());
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void duplicateTrackedScheduleEffect_replaysStoredReceiptBeforeClaim() {
        Map<String, Object> envelope =
                scheduleEnvelope("lesson.cancelled", Map.of("lesson_id", 1, "semester_id", 3));
        when(semesterArchiveEffectService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveEffectService).apply(eq(envelope), any());
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
        verifyNoInteractions(lessonEventService);
    }

    @Test
    void duplicateTrackedScheduleEffectWithoutReceiptIsNotAcknowledged() {
        Map<String, Object> envelope =
                scheduleEnvelope(
                        "lesson.deleted",
                        Map.of("lesson_ids", java.util.List.of(1L), "semester_id", 3));
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
        Map<String, Object> envelope =
                new java.util.HashMap<>(
                        scheduleEnvelope("semester.archive.participant.command", Map.of()));
        envelope.put("source", "academic-service");
        when(semesterArchiveParticipantService.hasReceipt(envelope)).thenReturn(true);

        eventConsumer.onEvent(envelope);

        verify(semesterArchiveParticipantService).apply(envelope);
        verify(idempotencyGuard, never()).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
    }

    @Test
    void deletionParticipantCommandRoutesToDeleteHandlerOnFirstDeliveryAndReplay() {
        Map<String, Object> envelope =
                new java.util.HashMap<>(
                        scheduleEnvelope(
                                "semester.archive.participant.command",
                                Map.of("command", "COMMIT_DELETE")));
        envelope.put("source", "academic-service");
        when(semesterDeletionParticipantService.hasReceipt(envelope)).thenReturn(false, true);

        eventConsumer.onEvent(envelope);
        eventConsumer.onEvent(envelope);

        verify(semesterDeletionParticipantService, org.mockito.Mockito.times(2))
                .hasReceipt(envelope);
        verify(semesterDeletionParticipantService, org.mockito.Mockito.times(2)).apply(envelope);
        verify(semesterArchiveParticipantService, never()).hasReceipt(envelope);
        verify(semesterArchiveParticipantService, never()).apply(envelope);
        verify(idempotencyGuard).tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope));
    }

    @Test
    void lessonCancelled_delegatesToLessonEventService() {
        eventConsumer.onEvent(
                scheduleEnvelope("lesson.cancelled", Map.of("lesson_id", 1, "semester_id", 3)));
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
    void missingEventType_isRejectedForDlq() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventConsumer.onEvent(Map.of("event_id", "x", "payload", Map.of())));
        verifyNoInteractions(lessonEventService, semesterCacheService);
    }

    @Test
    void lateCheckinDecision_passesPositiveInternalActorToTheService() {
        String requestId = "0123456789abcdef01234567";
        eventConsumer.onEvent(
                envelope(
                        "late_checkin.decision",
                        Map.of("request_id", requestId, "decision_by", 42L, "approved", true)));

        verify(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);
    }

    @Test
    void lateCheckinDecision_missingOrInvalidActorIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        eventConsumer.onEvent(
                                envelope(
                                        "late_checkin.decision",
                                        Map.of(
                                                "request_id",
                                                "0123456789abcdef01234567",
                                                "approved",
                                                true))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        eventConsumer.onEvent(
                                envelope(
                                        "late_checkin.decision",
                                        Map.of(
                                                "request_id",
                                                "0123456789abcdef01234567",
                                                "decision_by",
                                                0L,
                                                "approved",
                                                true))));

        verifyNoInteractions(studentRequestService);
    }

    @Test
    void decisionWithUntrustedSourceIsRejectedBeforeDomain() {
        Map<String, Object> event =
                new java.util.HashMap<>(
                        envelope(
                                "late_checkin.decision",
                                Map.of(
                                        "request_id",
                                        "0123456789abcdef01234567",
                                        "decision_by",
                                        42L,
                                        "approved",
                                        true)));
        event.put("source", "web-client");

        assertThrows(IllegalArgumentException.class, () -> eventConsumer.onEvent(event));
        verifyNoInteractions(studentRequestService);
    }

    @Test
    void authenticatedStaleLateDecisionIsAcknowledgedWithoutMutation() {
        String requestId = "0123456789abcdef01234567";
        doThrow(new ConflictException("Решение по запросу уже принято"))
                .when(studentRequestService)
                .decideLateCheckinFromBot(requestId, 42L, true);

        assertDoesNotThrow(
                () ->
                        eventConsumer.onEvent(
                                envelope(
                                        "late_checkin.decision",
                                        Map.of(
                                                "request_id",
                                                requestId,
                                                "decision_by",
                                                42L,
                                                "approved",
                                                true))));
        verify(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);
    }
}
