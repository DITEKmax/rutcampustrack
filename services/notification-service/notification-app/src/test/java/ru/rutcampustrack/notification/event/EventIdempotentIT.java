package ru.rutcampustrack.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.notification.history.NotificationHistoryConsumer;
import ru.rutcampustrack.notification.history.NotificationHistoryDocument;
import ru.rutcampustrack.notification.history.NotificationHistoryRepository;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        mongoTemplate.remove(new Query(), COLLECTION);
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
        });
    }
}
