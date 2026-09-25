package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Focused history processing tests; transaction and retry semantics are covered by IT. */
@ExtendWith(MockitoExtension.class)
class NotificationHistoryConsumerTest {

    @Mock private NotificationHistoryRepository repository;
    @Mock private NotificationHistoryService historyService;
    @Mock private IdempotencyGuard idempotencyGuard;
    @Mock private AcademicGroupMemberClient academicGroupMemberClient;
    @Mock private MongoTemplate mongoTemplate;

    @Captor private ArgumentCaptor<Iterable<NotificationHistoryDocument>> savedDocuments;

    private NotificationHistoryConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyGuard.tryClaim(anyString(), any())).thenReturn(true);
        lenient().when(mongoTemplate.exists(any(Query.class), anyString())).thenReturn(false);
        lenient().when(repository.findByEventIdAndUserIdIn(anyString(), anyList())).thenReturn(List.of());
        NotificationHistoryEventProcessor processor = new NotificationHistoryEventProcessor(
                repository, historyService, idempotencyGuard, academicGroupMemberClient);
        consumer = new NotificationHistoryConsumer(mongoTemplate, processor);
    }

    @Test
    void persistsPersonalEventWithItsEventIdAndInvalidatesUnread() {
        Map<String, Object> payload = Map.of("user_id", 42, "group_id", 7, "excuse_type", "illness");
        Map<String, Object> envelope = event("excuse.requested", payload);
        envelope.put("trace_id", "trace-xyz");

        consumer.onEvent(envelope);

        verify(repository).saveAll(savedDocuments.capture());
        NotificationHistoryDocument saved = only(savedDocuments.getValue());
        assertThat(saved.getEventId()).isEqualTo(envelope.get("event_id"));
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getType()).isEqualTo(NotificationType.EXCUSE_REQUESTED);
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getTraceId()).isEqualTo("trace-xyz");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getReadAt()).isNull();
        verify(historyService).invalidateUnreadCount(42L);
    }

    @Test
    void persistsGroupBroadcastForDatedAcademicMembers() {
        Map<String, Object> envelope = event("lesson.started", Map.of(
                "group_id", 7, "lesson_id", 100, "room", "Room 101"));
        LocalDate moscowEventDate = LocalDate.of(2026, 4, 25);
        when(academicGroupMemberClient.getMemberUserIds(7L, moscowEventDate))
                .thenReturn(List.of(42L, 43L));

        consumer.onEvent(envelope);

        verify(academicGroupMemberClient).getMemberUserIds(7L, moscowEventDate);
        verify(repository).saveAll(savedDocuments.capture());
        List<NotificationHistoryDocument> saved = StreamSupport.stream(
                savedDocuments.getValue().spliterator(), false).toList();
        assertThat(saved).hasSize(2)
                .extracting(NotificationHistoryDocument::getUserId)
                .containsExactly(42L, 43L);
        assertThat(saved).allSatisfy(document -> {
            assertThat(document.getEventId()).isEqualTo(envelope.get("event_id"));
            assertThat(document.getType()).isEqualTo(NotificationType.LESSON_STARTED);
            assertThat(document.getPayload()).containsEntry("room", "Room 101");
        });
        verify(historyService).invalidateUnreadCount(42L);
        verify(historyService).invalidateUnreadCount(43L);
    }

    @Test
    void groupHistorySnapshotExcludesPrivateAndUnapprovedPayloadFields() {
        Map<String, Object> cancelledPayload = Map.ofEntries(
                Map.entry("lesson_id", 100),
                Map.entry("group_id", 7),
                Map.entry("subject_id", 9),
                Map.entry("date", "2026-04-24"),
                Map.entry("start_time", "12:00"),
                Map.entry("end_time", "13:30"),
                Map.entry("lesson_number", 3),
                Map.entry("cancel_reason", "student medical detail"),
                Map.entry("cancelled_by", 5),
                Map.entry("cancelled_at", "2026-04-24T10:00:00Z"),
                Map.entry("private_extension", Map.of("student_id", 99)));
        Map<String, Object> homeworkPayload = Map.of(
                "homework_id", 300,
                "group_id", 7,
                "subject_id", 9,
                "lesson_date", "2026-04-25",
                "lesson_number", 4,
                "title", "Read chapter 5",
                "description", "student personal details",
                "link", "https://files.example/with-private-token",
                "has_link", true,
                "attachments", List.of("private-file-id"));
        when(academicGroupMemberClient.getMemberUserIds(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(42L));

        consumer.onEvent(event("lesson.cancelled", cancelledPayload));
        consumer.onEvent(event("homework.published", homeworkPayload));

        verify(repository, org.mockito.Mockito.times(2)).saveAll(savedDocuments.capture());
        List<Map<String, Object>> storedPayloads = new java.util.ArrayList<>();
        savedDocuments.getAllValues().forEach(documents ->
                documents.forEach(document -> storedPayloads.add(document.getPayload())));
        Map<String, Object> cancelledStored = storedPayloads.get(0);
        assertThat(cancelledStored)
                .containsEntry("group_id", 7L)
                .containsEntry("lesson_id", 100L)
                .containsEntry("date", "2026-04-24")
                .containsEntry("cancelled_at", "2026-04-24T10:00:00Z")
                .doesNotContainKeys("cancel_reason", "cancelled_by", "private_extension");

        Map<String, Object> homeworkStored = storedPayloads.get(1);
        assertThat(homeworkStored)
                .containsEntry("homework_id", 300L)
                .containsEntry("title", "Read chapter 5")
                .doesNotContainKeys("description", "link", "has_link", "attachments");
    }

    @Test
    void invalidEnvelopeFailsInsteadOfBeingAcknowledged() {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("payload", Map.of("user_id", 1));

        assertThatThrownBy(() -> consumer.onEvent(envelope))
                .isInstanceOf(IllegalArgumentException.class);
        verify(idempotencyGuard, never()).tryClaim(anyString(), any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void fractionalGroupIdFailsClosedBeforeMembershipLookup() {
        Map<String, Object> envelope = event("lesson.started", Map.of("group_id", 7.5));

        assertThatThrownBy(() -> consumer.onEvent(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group_id");

        verify(academicGroupMemberClient, never()).getMemberUserIds(anyLong(), any(LocalDate.class));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void skipsUnmappedPersonalActionWithoutUserId() {
        consumer.onEvent(event("attendance.marked", Map.of("lesson_id", 100)));

        verify(repository, never()).saveAll(any());
    }

    @Test
    void mongoFailurePropagatesSoTheTransactionAndClaimCanRollback() {
        when(repository.saveAll(any())).thenThrow(new RuntimeException("mongo down"));
        Map<String, Object> envelope = event("excuse.decided",
                Map.of("user_id", 1, "status", "approved"));

        assertThatThrownBy(() -> consumer.onEvent(envelope))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mongo down");

        verify(repository).saveAll(any());
        verify(historyService, never()).invalidateUnreadCount(any());
    }

    @Test
    void persistsExcuseDecidedRejectedAsRejected() {
        Map<String, Object> payload = Map.of("user_id", 99, "status", "rejected", "excuse_id", 42);
        consumer.onEvent(event("excuse.decided", payload));

        verify(repository).saveAll(savedDocuments.capture());
        assertThat(only(savedDocuments.getValue()).getType()).isEqualTo(NotificationType.EXCUSE_REJECTED);
    }

    private static Map<String, Object> event(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("event_type", eventType);
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("occurred_at", "2026-04-24T21:30:00Z");
        envelope.put("payload", payload);
        return envelope;
    }

    private static NotificationHistoryDocument only(Iterable<NotificationHistoryDocument> documents) {
        List<NotificationHistoryDocument> list = StreamSupport.stream(documents.spliterator(), false).toList();
        assertThat(list).hasSize(1);
        return list.get(0);
    }
}
