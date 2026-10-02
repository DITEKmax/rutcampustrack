package ru.rutcampustrack.notification.history;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.IdempotencyGuard;
import ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Rabbit/Mongo proof of historical audience persistence and atomic event replay. */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "notification.history.ttl-days=30",
        "grpc.auth.secret=test-grpc-secret"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationHistoryConsumerIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    NotificationHistoryConsumerIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private NotificationHistoryRepository repository;

    @MockitoSpyBean private MongoTemplate mongoTemplate;
    @MockitoBean private nl.martijndwars.webpush.PushService pushService;
    @MockitoBean private AcademicGroupMemberClient academicGroupMemberClient;
    @MockitoSpyBean private IdempotencyGuard idempotencyGuard;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), NotificationHistoryDocument.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"excuse.requested", "late_checkin.requested"})
    void requestedHistoryRetriesLookupAndKeepsOwnerAndCurrentHeadmanRowsOnCommittedReplay(String eventType) {
        String idField = "excuse.requested".equals(eventType) ? "ticket_id" : "request_id";
        Map<String, Object> payload = Map.of(
                "user_id", 42,
                "group_id", 7,
                idField, "request-it-1",
                "excuse_type", "illness"
        );
        Map<String, Object> envelope = event(eventType, UUID.randomUUID().toString(), payload,
                "2026-04-24T12:00:00Z");
        envelope.put("trace_id", "trace-it-123");
        doThrow(new StatusRuntimeException(Status.UNAVAILABLE)).doReturn(List.of(11L))
                .when(academicGroupMemberClient).getCurrentHeadmanUserIds(7L);

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findAll();
            assertThat(all).hasSize(2).extracting(NotificationHistoryDocument::getUserId)
                    .containsExactlyInAnyOrder(42L, 11L);
            assertThat(all).allSatisfy(doc -> {
                assertThat(doc.getEventId()).isEqualTo(envelope.get("event_id"));
                assertThat(doc.getType()).isEqualTo("excuse.requested".equals(eventType)
                        ? NotificationType.EXCUSE_REQUESTED : NotificationType.LATE_CHECKIN_REQUESTED);
                assertThat(doc.getTraceId()).isEqualTo("trace-it-123");
                assertThat(doc.getSentAt()).isNotNull();
                assertThat(doc.getReadAt()).isNull();
                assertThat(doc.getPayload()).isEqualTo(doc.getUserId() == 42L ? payload
                        : Map.of("group_id", 7L, idField, "request-it-1"));
            });
        });
        verify(academicGroupMemberClient, times(2)).getCurrentHeadmanUserIds(7L);
        // Reassignment after commit cannot backfill a replay into another person's history.
        doReturn(List.of(12L)).when(academicGroupMemberClient).getCurrentHeadmanUserIds(7L);
        clearInvocations(academicGroupMemberClient);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
        await().during(ofSeconds(1)).atMost(ofSeconds(3)).untilAsserted(() -> {
            assertThat(repository.findAll()).hasSize(2).extracting(NotificationHistoryDocument::getUserId)
                    .containsExactlyInAnyOrder(42L, 11L);
            verify(academicGroupMemberClient, never()).getCurrentHeadmanUserIds(7L);
        });
    }

    @Test
    void groupFailureRollsBackClaimAndCommittedReplayKeepsReadState() {
        String eventId = UUID.randomUUID().toString();
        LocalDate eventDate = LocalDate.of(2026, 4, 25);
        Map<String, Object> envelope = event("lesson.started", eventId,
                Map.of("group_id", 7, "lesson_id", 100), "2026-04-24T21:30:00Z");

        doThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .when(academicGroupMemberClient).getMemberUserIds(7L, eventDate);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() ->
                verify(academicGroupMemberClient, times(3)).getMemberUserIds(7L, eventDate));
        await().atMost(ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbitAdmin.getQueueInfo("notification-web.history.dlq").getMessageCount())
                        .isEqualTo(1));
        assertThat(repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L))).isEmpty();
        Query claimQuery = Query.query(Criteria.where("consumer_id")
                .is(NotificationHistoryConsumer.CONSUMER_ID).and("event_id").is(eventId));
        assertThat(mongoTemplate.count(claimQuery, MongoIdempotencyStore.DEFAULT_COLLECTION)).isZero();
        assertThat(rabbitTemplate.receive("notification-web.history.dlq")).isNotNull();

        doReturn(List.of(42L, 43L)).when(academicGroupMemberClient).getMemberUserIds(7L, eventDate);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L)))
                        .extracting(NotificationHistoryDocument::getUserId)
                        .containsExactlyInAnyOrder(42L, 43L));
        NotificationHistoryDocument recipient42 = repository.findByEventIdAndUserIdIn(
                        eventId, List.of(42L, 43L)).stream()
                .filter(document -> document.getUserId() == 42L)
                .findFirst()
                .orElseThrow();
        Instant readAt = Instant.parse("2026-04-25T01:00:00Z");
        assertThat(repository.markRead(recipient42.getId(), 42L, readAt)).isEqualTo(1L);

        clearInvocations(mongoTemplate, idempotencyGuard, academicGroupMemberClient);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().pollDelay(ofSeconds(2)).atMost(ofSeconds(5)).untilAsserted(() -> {
            verify(mongoTemplate).exists(ArgumentMatchers.argThat(query -> {
                org.bson.Document queryDocument = query.getQueryObject();
                return eventId.equals(queryDocument.getString("event_id"))
                        && NotificationHistoryConsumer.CONSUMER_ID
                                .equals(queryDocument.getString("consumer_id"));
            }), eq(MongoIdempotencyStore.DEFAULT_COLLECTION));
            assertThat(repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L))).hasSize(2);
            assertThat(repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L)).stream()
                        .filter(document -> document.getUserId() == 42L)
                        .findFirst().orElseThrow().getReadAt()).isEqualTo(readAt);
            assertThat(rabbitAdmin.getQueueInfo("notification-web.history.dlq").getMessageCount()).isZero();
            assertThat(mongoTemplate.count(claimQuery, MongoIdempotencyStore.DEFAULT_COLLECTION)).isEqualTo(1L);
        });
        verify(idempotencyGuard, never()).tryClaim(eq(NotificationHistoryConsumer.CONSUMER_ID), any());
        verify(academicGroupMemberClient, never()).getMemberUserIds(anyLong(), any(LocalDate.class));
    }

    @Test
    void homeworkUpdatedUsesDatedAudienceSanitizedSnapshotAndIdempotentReplay() {
        String eventId = UUID.randomUUID().toString();
        LocalDate eventDate = LocalDate.of(2026, 4, 24);
        Map<String, Object> envelope = event("homework.updated", eventId,
                Map.ofEntries(
                        Map.entry("homework_id", 300),
                        Map.entry("group_id", 7),
                        Map.entry("subject_id", 9),
                        Map.entry("lesson_date", "2026-04-25"),
                        Map.entry("lesson_number", 4),
                        Map.entry("title", "Read chapter 5"),
                        Map.entry("description", "private assignment details"),
                        Map.entry("link", "https://files.example/private-token"),
                        Map.entry("attachments", List.of("private-file-id"))),
                "2026-04-24T12:00:00Z");
        doReturn(List.of(42L, 43L)).when(academicGroupMemberClient).getMemberUserIds(7L, eventDate);

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L));
            assertThat(all).hasSize(2)
                    .extracting(NotificationHistoryDocument::getUserId)
                    .containsExactlyInAnyOrder(42L, 43L);
            assertThat(all).allSatisfy(document -> {
                assertThat(document.getType()).isEqualTo(NotificationType.HOMEWORK_UPDATED);
                assertThat(document.getPayload())
                        .containsEntry("homework_id", 300L)
                        .containsEntry("group_id", 7L)
                        .containsEntry("subject_id", 9L)
                        .containsEntry("lesson_date", "2026-04-25")
                        .containsEntry("lesson_number", 4)
                        .containsEntry("title", "Read chapter 5")
                        .doesNotContainKeys("description", "link", "attachments");
            });
        });

        clearInvocations(mongoTemplate, idempotencyGuard, academicGroupMemberClient);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
        await().pollDelay(ofSeconds(2)).atMost(ofSeconds(5)).untilAsserted(() -> {
            verify(mongoTemplate).exists(ArgumentMatchers.argThat(query -> {
                org.bson.Document queryDocument = query.getQueryObject();
                return eventId.equals(queryDocument.getString("event_id"))
                        && NotificationHistoryConsumer.CONSUMER_ID
                                .equals(queryDocument.getString("consumer_id"));
            }), eq(MongoIdempotencyStore.DEFAULT_COLLECTION));
            assertThat(repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L))).hasSize(2);
            Query claimQuery = Query.query(Criteria.where("consumer_id")
                    .is(NotificationHistoryConsumer.CONSUMER_ID).and("event_id").is(eventId));
            assertThat(mongoTemplate.count(claimQuery, MongoIdempotencyStore.DEFAULT_COLLECTION)).isEqualTo(1L);
        });
        verify(idempotencyGuard, never()).tryClaim(eq(NotificationHistoryConsumer.CONSUMER_ID), any());
        verify(academicGroupMemberClient, never()).getMemberUserIds(anyLong(), any(LocalDate.class));
    }

    @Test
    void oneOffCancellationKeepsDatedRecipientHistoryAndReadStateOnReplay() {
        String eventId = UUID.randomUUID().toString();
        LocalDate eventDate = LocalDate.of(2026, 4, 25);
        Map<String, Object> envelope = event("lesson.one_off.cancelled", eventId,
                Map.of("group_id", 7, "subject_id", 9, "date", "2026-05-01",
                        "lesson_number", 4, "semester_id", 2, "private_detail", "not retained"),
                "2026-04-24T21:30:00Z");
        doReturn(List.of(42L, 43L)).when(academicGroupMemberClient).getMemberUserIds(7L, eventDate);
        // User 44 joins before the lesson date, but after this cancellation was published.
        doReturn(List.of(42L, 43L, 44L)).when(academicGroupMemberClient)
                .getMemberUserIds(7L, LocalDate.of(2026, 5, 1));

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findAll();
            assertThat(all).hasSize(2).extracting(NotificationHistoryDocument::getUserId)
                    .containsExactlyInAnyOrder(42L, 43L);
            assertThat(all).allSatisfy(document -> {
                assertThat(document.getEventId()).isEqualTo(eventId);
                assertThat(document.getType()).isEqualTo(NotificationType.LESSON_CANCELLED);
                assertThat(document.getPayload()).isEqualTo(Map.of(
                        "group_id", 7L, "subject_id", 9L, "date", "2026-05-01", "lesson_number", 4));
                assertThat(document.getReadAt()).isNull();
            });
        });
        verify(academicGroupMemberClient).getMemberUserIds(7L, eventDate);
        NotificationHistoryDocument recipient42 = repository.findByEventIdAndUserIdIn(eventId, List.of(42L))
                .getFirst();
        Instant readAt = Instant.parse("2026-04-25T01:00:00Z");
        assertThat(repository.markRead(recipient42.getId(), 42L, readAt)).isEqualTo(1L);

        // Later membership cannot add a recipient or reset an existing row during replay.
        doReturn(List.of(42L, 43L, 44L)).when(academicGroupMemberClient).getMemberUserIds(7L, eventDate);
        clearInvocations(mongoTemplate, idempotencyGuard, academicGroupMemberClient);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
        await().pollDelay(ofSeconds(2)).atMost(ofSeconds(5)).untilAsserted(() -> {
            verify(mongoTemplate).exists(ArgumentMatchers.argThat(query -> {
                org.bson.Document queryDocument = query.getQueryObject();
                return eventId.equals(queryDocument.getString("event_id"))
                        && NotificationHistoryConsumer.CONSUMER_ID
                                .equals(queryDocument.getString("consumer_id"));
            }), eq(MongoIdempotencyStore.DEFAULT_COLLECTION));
            assertThat(repository.findAll()).hasSize(2).extracting(NotificationHistoryDocument::getUserId)
                    .containsExactlyInAnyOrder(42L, 43L);
            NotificationHistoryDocument retained = repository.findByEventIdAndUserIdIn(eventId, List.of(42L))
                    .getFirst();
            assertThat(retained.getId()).isEqualTo(recipient42.getId());
            assertThat(retained.getReadAt()).isEqualTo(readAt);
            assertThat(retained.getPayload()).isEqualTo(recipient42.getPayload());
            Query claimQuery = Query.query(Criteria.where("consumer_id")
                    .is(NotificationHistoryConsumer.CONSUMER_ID).and("event_id").is(eventId));
            assertThat(mongoTemplate.count(claimQuery, MongoIdempotencyStore.DEFAULT_COLLECTION)).isEqualTo(1L);
        });
        verify(idempotencyGuard, never()).tryClaim(eq(NotificationHistoryConsumer.CONSUMER_ID), any());
        verify(academicGroupMemberClient, never()).getMemberUserIds(anyLong(), any(LocalDate.class));
    }

    @Test
    void groupBroadcastIsPersistedForAcademicMembers() {
        String eventId = UUID.randomUUID().toString();
        LocalDate eventDate = LocalDate.of(2026, 4, 24);
        Map<String, Object> envelope = event("lesson.started", eventId,
                Map.of("group_id", 7, "lesson_id", 100), "2026-04-24T12:00:00Z");
        doReturn(List.of(42L)).when(academicGroupMemberClient).getMemberUserIds(7L, eventDate);

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findByEventIdAndUserIdIn(eventId, List.of(42L));
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getType()).isEqualTo(NotificationType.LESSON_STARTED);
            assertThat(all.get(0).getUserId()).isEqualTo(42L);
        });
        verify(academicGroupMemberClient, timeout(10_000)).getMemberUserIds(7L, eventDate);
    }

    private static Map<String, Object> event(String eventType,
                                             String eventId,
                                             Map<String, Object> payload,
                                             String occurredAt) {
        return new java.util.HashMap<>(Map.of(
                "event_type", eventType,
                "event_id", eventId,
                "occurred_at", occurredAt,
                "event_version", 1,
                "trace_id", "trace-it",
                "source", "schedule-service",
                "payload", payload
        ));
    }

}
