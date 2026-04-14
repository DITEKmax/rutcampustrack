package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for EventConsumer routing logic.
 * <p>
 * D-09 (semester.archived → SemesterCacheService.refresh()) is proven here rather than
 * via integration test: the prior IT was flaky under cached-Spring-context reuse where
 * EventConsumer held a stale mock reference — see git history for details.
 */
@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private LessonEventService lessonEventService;

    @Mock
    private SemesterCacheService semesterCacheService;

    @InjectMocks
    private EventConsumer eventConsumer;

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
