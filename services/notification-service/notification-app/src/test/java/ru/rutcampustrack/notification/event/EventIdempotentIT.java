package ru.rutcampustrack.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.notification.history.NotificationHistoryConsumer;
import ru.rutcampustrack.notification.history.NotificationHistoryDocument;
import ru.rutcampustrack.notification.history.NotificationHistoryRepository;
import ru.rutcampustrack.notification.history.AcademicGroupMemberClient;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * M13 G8 — IT для consumer-side dedup в notification-app.
 *
 * <p>Публикует {@code excuse.requested} с одинаковым {@code event_id}
 * дважды; обе очереди ({@code notification-web.events} и
 * {@code notification-web.history}) получают сообщение, но guard'ы
 * пропускают только первое — history-document создаётся один раз,
 * claim-records по двум consumerId — по одному каждой.
 */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "notification.history.ttl-days=30"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventIdempotentIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    EventIdempotentIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String COLLECTION = "event_consumer_processed";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationHistoryRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired private RabbitAdmin rabbitAdmin;
    @MockitoBean private AcademicGroupMemberClient academicGroupMemberClient;
    @MockitoBean private WebPushDeliveryService webPushDeliveryService;

    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        mongoTemplate.remove(new Query(), COLLECTION);
        rabbitAdmin.purgeQueue("notification-web.events.dlq", false);
        when(academicGroupMemberClient.getCurrentHeadmanUserIds(7L)).thenReturn(List.of());
        when(webPushDeliveryService.shouldPush(anyString())).thenReturn(true);
        when(webPushDeliveryService.sendToGroup(anyLong(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(webPushDeliveryService.sendToGroup(anyLong(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private Map<String, Object> envelope(UUID eventId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "excuse.requested");
        envelope.put("event_id", eventId.toString());
        envelope.put("trace_id", "trace-" + eventId);
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload", Map.of(
                "user_id", 42,
                "group_id", 7,
                "ticket_id", "request-it-1",
                "excuse_type", "illness"));
        return envelope;
    }

    private void publish(Map<String, Object> env) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", env);
    }

    private long claimsForConsumer(String consumerId, UUID eventId) {
        return mongoTemplate.count(
                new Query(Criteria.where("consumer_id").is(consumerId)
                        .and("event_id").is(eventId.toString())),
                COLLECTION);
    }

    @Test
    void duplicateDelivery_historyPersistedOnce_claimsRecorded() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);

        publish(envelope);
        publish(envelope);

        // Один history-document даже после двух доставок.
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getUserId()).isEqualTo(42L);
        });

        // Claim для каждого из двух consumer'ов — по одной записи.
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isEqualTo(1L);
            assertThat(claimsForConsumer(NotificationHistoryConsumer.CONSUMER_ID, eventId))
                    .isEqualTo(1L);
        });

        // Третья публикация — count'ы остаются.
        publish(envelope);
        await().pollDelay(2, TimeUnit.SECONDS).atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(repository.findAll()).hasSize(1);
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isEqualTo(1L);
            assertThat(claimsForConsumer(NotificationHistoryConsumer.CONSUMER_ID, eventId)).isEqualTo(1L);
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isZero();
            verify(webPushDeliveryService, times(1)).sendToGroup(7L, "excuse.requested",
                    (Map<String, Object>) envelope.get("payload"), Set.of());
        });
    }

    @Test
    void headmanLookupFailureRollsBackDeliveryClaimThenRetryCommitsOnce() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);
        when(webPushDeliveryService.resolveCurrentAudience(7L, "excuse.requested"))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException()).thenReturn(Set.of(42L));

        publish(envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isEqualTo(1);
            assertThat(repository.findAll()).hasSize(1);
            verify(webPushDeliveryService, times(2)).resolveCurrentAudience(7L, "excuse.requested");
        });
        publish(envelope);
        await().during(ofSeconds(1)).atMost(ofSeconds(3)).untilAsserted(() -> {
                verify(webPushDeliveryService, times(2)).resolveCurrentAudience(7L, "excuse.requested");
                assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isZero();
        });
    }

    @Test
    void exhaustedHeadmanLookupRetriesLeaveNoCommittedDeliveryClaimAndReachDlq() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);
        when(webPushDeliveryService.resolveCurrentAudience(7L, "excuse.requested"))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException());

        publish(envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            verify(webPushDeliveryService, times(3)).resolveCurrentAudience(7L, "excuse.requested");
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isZero();
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isEqualTo(1);
        });
        Map<?, ?> retained = (Map<?, ?>) rabbitTemplate.receiveAndConvert("notification-web.events.dlq");
        assertThat(retained.get("event_id")).isEqualTo(eventId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lesson.started", "homework.published"})
    void groupLookupFailureRollsBackDeliveryClaimThenRetryCommitsOnce(String eventType) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);
        envelope.put("event_type", eventType);
        Map<String, Object> payload = Map.of("group_id", 7);
        envelope.put("payload", payload);
        when(academicGroupMemberClient.getMemberUserIds(anyLong(), any())).thenReturn(List.of(42L));
        when(webPushDeliveryService.resolveCurrentAudience(7L, eventType))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException()).thenReturn(Set.of(42L));

        publish(envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isEqualTo(1);
            assertThat(repository.findAll()).hasSize(1);
            verify(webPushDeliveryService, times(2)).resolveCurrentAudience(7L, eventType);
        });
        publish(envelope);
        await().during(ofSeconds(1)).atMost(ofSeconds(3)).untilAsserted(() -> {
            verify(webPushDeliveryService, times(2)).resolveCurrentAudience(7L, eventType);
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isZero();
        });
    }

    @Test
    void exhaustedGroupLookupRetriesLeaveNoCommittedDeliveryClaimAndReachDlq() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);
        envelope.put("event_type", "group.archived");
        Map<String, Object> payload = Map.of("group_id", 7);
        envelope.put("payload", payload);
        when(webPushDeliveryService.resolveCurrentAudience(7L, "group.archived"))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException());

        publish(envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            verify(webPushDeliveryService, times(3)).resolveCurrentAudience(7L, "group.archived");
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isZero();
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isEqualTo(1);
        });
        Map<?, ?> retained = (Map<?, ?>) rabbitTemplate.receiveAndConvert("notification-web.events.dlq");
        assertThat(retained.get("event_id")).isEqualTo(eventId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lesson.started", "excuse.requested"})
    void slowProviderDoesNotHoldDeliveryClaimAndReplayDoesNotScheduleAnotherSend(String eventType) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);
        envelope.put("event_type", eventType);
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        when(academicGroupMemberClient.getMemberUserIds(anyLong(), any())).thenReturn(List.of(42L));
        when(webPushDeliveryService.resolveCurrentAudience(7L, eventType)).thenReturn(Set.of(42L));
        var providerPending = new CompletableFuture<Void>();
        when(webPushDeliveryService.sendToGroup(7L, eventType, payload)).thenReturn(providerPending);
        when(webPushDeliveryService.sendToGroup(7L, eventType, payload, Set.of(42L))).thenReturn(providerPending);

        try {
            publish(envelope);
            await().atMost(ofSeconds(5)).untilAsserted(() -> {
                assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, eventId)).isEqualTo(1);
                assertThat(providerPending).isNotCompleted();
                verify(webPushDeliveryService).sendToGroup(7L, eventType, payload, Set.of(42L));
            });
            publish(envelope);
            await().during(ofSeconds(1)).atMost(ofSeconds(3)).untilAsserted(() -> {
                verify(webPushDeliveryService).sendToGroup(7L, eventType, payload, Set.of(42L));
                assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isZero();
            });
        } finally {
            providerPending.complete(null);
        }
    }
}
