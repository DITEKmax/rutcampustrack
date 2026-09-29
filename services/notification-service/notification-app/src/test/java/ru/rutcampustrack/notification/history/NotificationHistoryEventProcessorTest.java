package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryEventProcessorTest {

    @Mock private NotificationHistoryRepository repository;
    @Mock private NotificationHistoryService historyService;
    @Mock private IdempotencyGuard idempotencyGuard;
    @Mock private AcademicGroupMemberClient academicGroupMemberClient;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationHistoryEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationHistoryEventProcessor(
                repository, historyService, idempotencyGuard, academicGroupMemberClient, messagingTemplate);
    }

    @Test
    void lessonClosedHistoryUsesCurrentHeadmanOnlyAndDropsDuplicateEvent() {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "group_id", 42,
                "lesson_id", 101,
                "subject_id", 8,
                "private_detail", "must not be copied");
        Map<String, Object> envelope = Map.of(
                "event_type", "lesson.closed",
                "event_id", eventId,
                "occurred_at", "2026-09-29T12:00:00Z",
                "trace_id", "trace-closed-1",
                "payload", payload);
        List<NotificationHistoryDocument> savedDocuments = new ArrayList<>();
        when(idempotencyGuard.tryClaim(eq(NotificationHistoryConsumer.CONSUMER_ID), eq(envelope)))
                .thenReturn(true, false);
        when(academicGroupMemberClient.getCurrentHeadmanUserIds(42L)).thenReturn(List.of(11L));
        when(repository.findByEventIdAndUserIdIn(eventId, List.of(11L))).thenReturn(List.of());
        doAnswer(invocation -> {
            Iterable<NotificationHistoryDocument> documents = invocation.getArgument(0);
            documents.forEach(savedDocuments::add);
            return savedDocuments;
        }).when(repository).saveAll(any());

        processor.persist(envelope);
        processor.persist(envelope);

        assertThat(savedDocuments).singleElement().satisfies(document -> {
            assertThat(document.getUserId()).isEqualTo(11L);
            assertThat(document.getEventId()).isEqualTo(eventId);
            assertThat(document.getType()).isEqualTo(NotificationType.LESSON_CLOSED);
            assertThat(document.getTraceId()).isEqualTo("trace-closed-1");
            assertThat(document.getPayload())
                    .containsEntry("lesson_id", 101L)
                    .containsEntry("group_id", 42L)
                    .containsEntry("subject_id", 8L)
                    .doesNotContainKey("private_detail");
        });
        verify(academicGroupMemberClient, times(1)).getCurrentHeadmanUserIds(42L);
        verify(academicGroupMemberClient, never()).getMemberUserIds(eq(42L), any());
        verify(historyService).invalidateUnreadCount(11L);
        verify(messagingTemplate).convertAndSend(
                "/topic/user/11", Map.of("type", "notification.history.changed"));
    }
}
