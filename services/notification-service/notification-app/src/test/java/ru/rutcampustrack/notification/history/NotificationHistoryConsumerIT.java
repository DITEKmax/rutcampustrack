package ru.rutcampustrack.notification.history;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void excuseRequestedIsPersistedWithEventIdentity() {
        Map<String, Object> payload = Map.of(
                "user_id", 42,
                "group_id", 7,
                "excuse_type", "illness"
        );
        Map<String, Object> envelope = event("excuse.requested", UUID.randomUUID().toString(), payload,
                "2026-04-24T12:00:00Z");
        envelope.put("trace_id", "trace-it-123");

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findAll();
            assertThat(all).hasSize(1);
            NotificationHistoryDocument doc = all.get(0);
            assertThat(doc.getUserId()).isEqualTo(42L);
            assertThat(doc.getEventId()).isEqualTo(envelope.get("event_id"));
            assertThat(doc.getType()).isEqualTo(NotificationType.EXCUSE_REQUESTED);
            assertThat(doc.getTraceId()).isEqualTo("trace-it-123");
            assertThat(doc.getSentAt()).isNotNull();
            assertThat(doc.getReadAt()).isNull();
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
