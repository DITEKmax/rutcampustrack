package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.excuse.ExcuseService;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinService;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private SemesterCacheService semesterCacheService;

    @Mock
    private LateCheckinService lateCheckinService;

    @Mock
    private ExcuseService excuseService;

    @Mock
    private IdempotencyGuard idempotencyGuard;

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
    }

    private Map<String, Object> envelope(String eventType, Map<String, Object> payload) {
        return Map.of(
                "event_type", eventType,
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", Instant.now().toString(),
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
        eventConsumer.onEvent(envelope("lesson.closed", Map.of(
                "lesson_id", 1, "group_id", 10
        )));
        verify(lessonEventService).processLessonClosed(1L, 10L);
        verifyNoInteractions(semesterCacheService);
    }

    @Test
    void lessonCancelled_delegatesToLessonEventService() {
        eventConsumer.onEvent(envelope("lesson.cancelled", Map.of(
                "lesson_id", 1
        )));
        verify(lessonEventService).processLessonCancelled(1L);
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
}
