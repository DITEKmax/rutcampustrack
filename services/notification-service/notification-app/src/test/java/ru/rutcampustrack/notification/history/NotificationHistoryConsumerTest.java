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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private NotificationHistoryService historyService;

    @Mock
    private ru.rutcampustrack.shared.events.IdempotencyGuard idempotencyGuard;

    private NotificationHistoryConsumer consumer;

    @BeforeEach
    void setUp() {
        // M13 G24-fix-6: real IdempotencyGuard теперь fail-closed на missing
        // event_id. Mock'аем guard.tryClaim → true чтобы тесты остались о
        // routing'е. lenient — некоторые тесты skip'аются до tryClaim.
        lenient().when(idempotencyGuard.tryClaim(any(), any())).thenReturn(true);
        consumer = new NotificationHistoryConsumer(repository, historyService, idempotencyGuard);
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
        verify(historyService).invalidateUnreadCount(42L);
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
                "event_type", "excuse.decided",
                "payload", Map.of("user_id", 1, "status", "approved")
        );

        // Не должно бросить — error-isolation guarantee.
        consumer.onEvent(envelope);

        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistsExcuseDecidedRejectedAsRejected() {
        // G9 H1 regression guard: payload.status="rejected" должен
        // сохраняться как EXCUSE_REJECTED, не EXCUSE_APPROVED.
        Map<String, Object> payload = Map.of("user_id", 99, "status", "rejected", "excuse_id", 42);
        Map<String, Object> envelope = Map.of(
                "event_type", "excuse.decided",
                "payload", payload,
                "trace_id", "trace-rejected"
        );

        consumer.onEvent(envelope);

        ArgumentCaptor<NotificationHistoryDocument> captor =
                ArgumentCaptor.forClass(NotificationHistoryDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EXCUSE_REJECTED);
    }
}
