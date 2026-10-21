package ru.rutcampustrack.notification.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static java.time.Duration.ofSeconds;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rabbit/Mongo proof of historical audience persistence and atomic event replay. */
@SpringBootTest(
        properties = {
            "spring.data.redis.password=",
            "notification.bot-preferences.token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
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
            Path keyFile =
                    Path.of(
                            NotificationHistoryConsumerIT.class
                                    .getResource("/test-public.pem")
                                    .toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private NotificationHistoryRepository repository;

    @Autowired
    private org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
            listenerRegistry;

    @MockitoSpyBean private MongoTemplate mongoTemplate;
    @MockitoBean private nl.martijndwars.webpush.PushService pushService;
    @MockitoBean private NotificationAudienceClient audienceClient;
    @MockitoSpyBean private IdempotencyGuard idempotencyGuard;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), NotificationHistoryDocument.class);
        for (String collection :
                List.of(
                        MongoIdempotencyStore.DEFAULT_COLLECTION,
                        NotificationAudienceService.COLLECTION,
                        ru.rutcampustrack.notification.push.NotificationPushIntentStore
                                .COLLECTION)) {
            mongoTemplate.remove(new Query(), collection);
        }
        rabbitAdmin.purgeQueue("notification-web.events.dlq", false);
        rabbitAdmin.purgeQueue("notification-web.history.dlq", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"excuse.requested", "late_checkin.requested"})
    void requestedHistoryRetriesLookupAndKeepsOwnerAndCurrentHeadmanRowsOnCommittedReplay(
            String eventType) {
        String idField = "excuse.requested".equals(eventType) ? "ticket_id" : "request_id";
        Map<String, Object> payload =
                Map.of(
                        "user_id",
                        42,
                        "group_id",
                        7,
                        idField,
                        "request-it-1",
                        "excuse_type",
                        "illness");
        Map<String, Object> envelope =
                event(eventType, UUID.randomUUID().toString(), payload, "2026-04-24T12:00:00Z");
        envelope.put("trace_id", "trace-it-123");
        doThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .doReturn(Map.of(42L, Set.of("STUDENT"), 11L, Set.of("HEADMAN")))
                .when(audienceClient)
                .resolve(eq(envelope));

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            List<NotificationHistoryDocument> all = repository.findAll();
                            assertThat(all)
                                    .hasSize(2)
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(42L, 11L);
                            assertThat(all)
                                    .allSatisfy(
                                            doc -> {
                                                assertThat(doc.getEventId())
                                                        .isEqualTo(envelope.get("event_id"));
                                                assertThat(doc.getType())
                                                        .isEqualTo(
                                                                "excuse.requested".equals(eventType)
                                                                        ? NotificationType
                                                                                .EXCUSE_REQUESTED
                                                                        : NotificationType
                                                                                .LATE_CHECKIN_REQUESTED);
                                                assertThat(doc.getTraceId())
                                                        .isEqualTo("trace-it-123");
                                                assertThat(doc.getSentAt()).isNotNull();
                                                assertThat(doc.getReadAt()).isNull();
                                                assertThat(doc.getPayload())
                                                        .isEqualTo(
                                                                Map.of(
                                                                        "group_id",
                                                                        7L,
                                                                        idField,
                                                                        "request-it-1"))
                                                        .doesNotContainKeys(
                                                                "user_id", "excuse_type");
                                            });
                        });
        verify(audienceClient, atLeast(2)).resolve(eq(envelope));
        // Reassignment after commit cannot backfill a replay into another person's history.
        doReturn(Map.of(42L, Set.of("STUDENT"), 12L, Set.of("HEADMAN")))
                .when(audienceClient)
                .resolve(eq(envelope));
        clearInvocations(audienceClient);
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
        await().during(ofSeconds(1))
                .atMost(ofSeconds(3))
                .untilAsserted(
                        () -> {
                            assertThat(repository.findAll())
                                    .hasSize(2)
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(42L, 11L);
                            verify(audienceClient, never()).resolve(any());
                        });
    }

    @Test
    void groupFailureRollsBackClaimAndCommittedReplayKeepsReadState() {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope =
                event(
                        "lesson.started",
                        eventId,
                        Map.of("group_id", 7, "lesson_id", 100),
                        "2026-04-24T21:30:00Z");

        doThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .when(audienceClient)
                .resolve(eq(envelope));
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10))
                .untilAsserted(() -> verify(audienceClient, atLeast(3)).resolve(eq(envelope)));
        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                rabbitAdmin
                                                        .getQueueInfo(
                                                                "notification-web.history.dlq")
                                                        .getMessageCount())
                                        .isEqualTo(1));
        assertThat(repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L))).isEmpty();
        Query claimQuery =
                Query.query(
                        Criteria.where("consumer_id")
                                .is(NotificationHistoryConsumer.CONSUMER_ID)
                                .and("event_id")
                                .is(eventId));
        assertThat(mongoTemplate.count(claimQuery, MongoIdempotencyStore.DEFAULT_COLLECTION))
                .isZero();
        assertThat(rabbitTemplate.receive("notification-web.history.dlq")).isNotNull();

        doReturn(Map.of(42L, Set.of("STUDENT"), 43L, Set.of("STUDENT")))
                .when(audienceClient)
                .resolve(eq(envelope));
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate.count(
                                                    claimQuery,
                                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                                    .isEqualTo(1L);
                            assertThat(
                                            repository.findByEventIdAndUserIdIn(
                                                    eventId, List.of(42L, 43L)))
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(42L, 43L);
                        });
        NotificationHistoryDocument recipient42 =
                repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L)).stream()
                        .filter(document -> document.getUserId() == 42L)
                        .findFirst()
                        .orElseThrow();
        Instant readAt = Instant.parse("2026-04-25T01:00:00Z");
        assertThat(repository.markRead(recipient42.getId(), 42L, readAt)).isEqualTo(1L);

        clearInvocations(audienceClient);
        assertReplayAcknowledged(envelope);

        await().pollDelay(ofSeconds(2))
                .atMost(ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            repository.findByEventIdAndUserIdIn(
                                                    eventId, List.of(42L, 43L)))
                                    .hasSize(2);
                            assertThat(
                                            repository
                                                    .findByEventIdAndUserIdIn(
                                                            eventId, List.of(42L, 43L))
                                                    .stream()
                                                    .filter(document -> document.getUserId() == 42L)
                                                    .findFirst()
                                                    .orElseThrow()
                                                    .getReadAt())
                                    .isEqualTo(readAt);
                            assertThat(
                                            rabbitAdmin
                                                    .getQueueInfo("notification-web.history.dlq")
                                                    .getMessageCount())
                                    .isZero();
                            assertThat(
                                            mongoTemplate.count(
                                                    claimQuery,
                                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                                    .isEqualTo(1L);
                        });
    }

    @Test
    void homeworkUpdatedUsesDatedAudienceSanitizedSnapshotAndIdempotentReplay() {
        String eventId = UUID.randomUUID().toString();
        Query claimQuery =
                Query.query(
                        Criteria.where("consumer_id")
                                .is(NotificationHistoryConsumer.CONSUMER_ID)
                                .and("event_id")
                                .is(eventId));
        Map<String, Object> envelope =
                event(
                        "homework.updated",
                        eventId,
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
        doReturn(Map.of(42L, Set.of("STUDENT"), 43L, Set.of("STUDENT")))
                .when(audienceClient)
                .resolve(eq(envelope));

        rabbitTemplate.convertAndSend("", "notification-web.history", envelope);

        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate.count(
                                                    claimQuery,
                                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                                    .isEqualTo(1L);
                            List<NotificationHistoryDocument> all =
                                    repository.findByEventIdAndUserIdIn(eventId, List.of(42L, 43L));
                            assertThat(all)
                                    .hasSize(2)
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(42L, 43L);
                            assertThat(all)
                                    .allSatisfy(
                                            document -> {
                                                assertThat(document.getType())
                                                        .isEqualTo(
                                                                NotificationType.HOMEWORK_UPDATED);
                                                assertThat(document.getPayload())
                                                        .containsEntry("homework_id", 300L)
                                                        .containsEntry("group_id", 7L)
                                                        .containsEntry("subject_id", 9L)
                                                        .containsEntry("lesson_date", "2026-04-25")
                                                        .containsEntry("lesson_number", 4L)
                                                        .containsEntry("title", "Read chapter 5")
                                                        .doesNotContainKeys(
                                                                "description",
                                                                "link",
                                                                "attachments");
                                            });
                        });

        clearInvocations(audienceClient);
        assertReplayAcknowledged(envelope);
        await().pollDelay(ofSeconds(2))
                .atMost(ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            repository.findByEventIdAndUserIdIn(
                                                    eventId, List.of(42L, 43L)))
                                    .hasSize(2);
                            assertThat(
                                            mongoTemplate.count(
                                                    claimQuery,
                                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                                    .isEqualTo(1L);
                        });
    }

    @Test
    void oneOffCancellationKeepsDatedRecipientHistoryAndReadStateOnReplay() {
        String eventId = UUID.randomUUID().toString();
        Query claimQuery =
                Query.query(
                        Criteria.where("consumer_id")
                                .is(NotificationHistoryConsumer.CONSUMER_ID)
                                .and("event_id")
                                .is(eventId));
        Map<String, Object> envelope =
                event(
                        "lesson.one_off.cancelled",
                        eventId,
                        Map.of(
                                "group_id",
                                7,
                                "subject_id",
                                9,
                                "date",
                                "2026-05-01",
                                "lesson_number",
                                4,
                                "semester_id",
                                2,
                                "private_detail",
                                "not retained"),
                        "2026-04-24T21:30:00Z");
        doReturn(Map.of(42L, Set.of("STUDENT"), 43L, Set.of("STUDENT")))
                .when(audienceClient)
                .resolve(eq(envelope));

        rabbitTemplate.convertAndSend("", "notification-web.history", envelope);

        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate.count(
                                                    claimQuery,
                                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                                    .isEqualTo(1L);
                            List<NotificationHistoryDocument> all = repository.findAll();
                            assertThat(all)
                                    .hasSize(2)
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(42L, 43L);
                            assertThat(all)
                                    .allSatisfy(
                                            document -> {
                                                assertThat(document.getEventId())
                                                        .isEqualTo(eventId);
                                                assertThat(document.getType())
                                                        .isEqualTo(
                                                                NotificationType.LESSON_CANCELLED);
                                                assertThat(document.getPayload())
                                                        .isEqualTo(
                                                                Map.of(
                                                                        "group_id",
                                                                        7L,
                                                                        "subject_id",
                                                                        9L,
                                                                        "semester_id",
                                                                        2L,
                                                                        "date",
                                                                        "2026-05-01",
                                                                        "lesson_number",
                                                                        4L));
                                                assertThat(document.getReadAt()).isNull();
                                            });
                        });
        verify(audienceClient, atLeastOnce()).resolve(eq(envelope));
        NotificationHistoryDocument recipient42 =
                repository.findByEventIdAndUserIdIn(eventId, List.of(42L)).getFirst();
        Instant readAt = Instant.parse("2026-04-25T01:00:00Z");
        assertThat(repository.markRead(recipient42.getId(), 42L, readAt)).isEqualTo(1L);

        // Later membership cannot add a recipient or reset an existing row during replay.
        doReturn(Map.of(42L, Set.of("STUDENT"), 43L, Set.of("STUDENT"), 44L, Set.of("STUDENT")))
                .when(audienceClient)
                .resolve(eq(envelope));
        clearInvocations(audienceClient);
        assertReplayAcknowledged(envelope);
        await().pollDelay(ofSeconds(2))
                .atMost(ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(repository.findAll())
                                    .hasSize(2)
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(42L, 43L);
                            NotificationHistoryDocument retained =
                                    repository
                                            .findByEventIdAndUserIdIn(eventId, List.of(42L))
                                            .getFirst();
                            assertThat(retained.getId()).isEqualTo(recipient42.getId());
                            assertThat(retained.getReadAt()).isEqualTo(readAt);
                            assertThat(retained.getPayload()).isEqualTo(recipient42.getPayload());
                            assertThat(
                                            mongoTemplate.count(
                                                    claimQuery,
                                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                                    .isEqualTo(1L);
                        });
    }

    @Test
    void groupBroadcastIsPersistedForAcademicMembers() {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope =
                event(
                        "lesson.started",
                        eventId,
                        Map.of("group_id", 7, "lesson_id", 100),
                        "2026-04-24T12:00:00Z");
        doReturn(Map.of(42L, Set.of("STUDENT"))).when(audienceClient).resolve(eq(envelope));

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            List<NotificationHistoryDocument> all =
                                    repository.findByEventIdAndUserIdIn(eventId, List.of(42L));
                            assertThat(all).hasSize(1);
                            assertThat(all.get(0).getType())
                                    .isEqualTo(NotificationType.LESSON_STARTED);
                            assertThat(all.get(0).getUserId()).isEqualTo(42L);
                        });
        verify(audienceClient, atLeastOnce()).resolve(eq(envelope));
    }

    private static Map<String, Object> event(
            String eventType, String eventId, Map<String, Object> payload, String occurredAt) {
        return new java.util.HashMap<>(
                Map.of(
                        "event_type", eventType,
                        "event_id", eventId,
                        "occurred_at", occurredAt,
                        "event_version", 1,
                        "trace_id", "trace-it",
                        "source", "schedule-service",
                        "payload", payload));
    }

    /** Observe the owned delivery's successful production return and real AUTO ACK. */
    private void assertReplayAcknowledged(Map<String, Object> envelope) {
        var rowsBefore = repository.findAll();
        var claimsBefore =
                mongoTemplate.findAll(
                        org.bson.Document.class, MongoIdempotencyStore.DEFAULT_COLLECTION);
        var container =
                listenerRegistry.getListenerContainers().stream()
                        .filter(
                                org.springframework.amqp.rabbit.listener
                                                .SimpleMessageListenerContainer.class
                                        ::isInstance)
                        .map(
                                org.springframework.amqp.rabbit.listener
                                                .SimpleMessageListenerContainer.class
                                        ::cast)
                        .filter(
                                item ->
                                        java.util.Arrays.asList(item.getQueueNames())
                                                .contains("notification-web.history"))
                        .findFirst()
                        .orElseThrow();
        assertThat(container.getAcknowledgeMode())
                .isEqualTo(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        container.stop();
        var original =
                (org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener)
                        container.getMessageListener();
        var originalAck = container.getMessageAckListener();
        var targetTag = new java.util.concurrent.atomic.AtomicLong(-1);
        var returned = new java.util.concurrent.atomic.AtomicBoolean();
        var acknowledged = new java.util.concurrent.CountDownLatch(1);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        String eventId = envelope.get("event_id").toString();
        try {
            container.setMessageListener(
                    (org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener)
                            (message, channel) -> {
                                boolean target =
                                        eventId.equals(
                                                new com.fasterxml.jackson.databind.ObjectMapper()
                                                        .readTree(message.getBody())
                                                        .path("event_id")
                                                        .asText());
                                original.onMessage(message, channel);
                                if (target) {
                                    targetTag.set(message.getMessageProperties().getDeliveryTag());
                                    returned.set(true);
                                }
                            });
            container.setMessageAckListener(
                    (success, tag, cause) -> {
                        if (originalAck != null) originalAck.onComplete(success, tag, cause);
                        if (returned.get() && tag >= targetTag.get()) {
                            if (!success)
                                failure.set(
                                        cause == null
                                                ? new AssertionError("History replay ACK failed")
                                                : cause);
                            acknowledged.countDown();
                        }
                    });
            container.start();
            rabbitTemplate.convertAndSend("", "notification-web.history", envelope);
            assertThat(acknowledged.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    .as("owned replay returned and was AUTO ACKed")
                    .isTrue();
            assertThat(failure.get()).isNull();
            assertThat(returned.get()).isTrue();
            assertThat(repository.findAll())
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactlyInAnyOrderElementsOf(rowsBefore);
            assertThat(
                            mongoTemplate.findAll(
                                    org.bson.Document.class,
                                    MongoIdempotencyStore.DEFAULT_COLLECTION))
                    .containsExactlyInAnyOrderElementsOf(claimsBefore);
            assertThat(rabbitAdmin.getQueueInfo("notification-web.history.dlq").getMessageCount())
                    .isZero();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } finally {
            container.stop();
            container.setMessageListener(original);
            container.setMessageAckListener(originalAck);
            container.start();
        }
    }
}
