package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тест {@link NotificationHistoryConsumer} (M10 G3).
 * Покрывает persist + skip + error isolation — всё через mock repo.
 */
@ExtendWith(MockitoExtension.class)
class NotificationHistoryConsumerTest {

    @Mock
    private NotificationHistoryRepository repository;

    private NotificationHistoryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationHistoryConsumer(repository);
    }

    @Test
    void persistsExcuseRequested() {
        Map<String, Object> payload = Map.of("user_id", 42, "group_id", 7, "excuse_type", "illness");
        Map<String, Object> envelope = Map.of(
                "event_type", "excuse.requested",
                "payload", payload,
                "trace_id", "trace-xyz"
        );

        consumer.onEvent(envelope);

        ArgumentCaptor<NotificationHistoryDocument> captor =
                ArgumentCaptor.forClass(NotificationHistoryDocument.class);
        verify(repository).save(captor.capture());
        NotificationHistoryDocument saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getType()).isEqualTo(NotificationType.EXCUSE_REQUESTED);
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getTraceId()).isEqualTo("trace-xyz");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    void skipsBroadcastLessonStarted() {
        Map<String, Object> envelope = Map.of(
                "event_type", "lesson.started",
                "payload", Map.of("group_id", 7)
        );

        consumer.onEvent(envelope);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenMissingEventType() {
        consumer.onEvent(Map.of("payload", Map.of("user_id", 1)));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenMissingUserId() {
        Map<String, Object> envelope = Map.of(
                "event_type", "attendance.marked",
                "payload", Map.of("lesson_id", 100)
        );

        consumer.onEvent(envelope);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void errorInRepositoryDoesNotRethrow() {
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("mongo down"));
        Map<String, Object> envelope = Map.of(
                "event_type", "excuse.approved",
                "payload", Map.of("user_id", 1)
        );

        // Не должно бросить — error-isolation guarantee.
        consumer.onEvent(envelope);

        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }
}
