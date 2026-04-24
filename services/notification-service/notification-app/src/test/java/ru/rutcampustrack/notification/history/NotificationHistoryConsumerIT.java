package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

/**
 * Integration-тест {@link NotificationHistoryConsumer} (M10 G3).
 *
 * <p>Публикует event в fanout exchange {@code rut-uit.events} через
 * {@link RabbitTemplate} → consumer на {@code notification-web.history}
 * persist'ит document в real Mongo (Testcontainers).
 */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "notification.history.ttl-days=30"
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
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(),
                NotificationHistoryDocument.class);
    }

    @Test
    void excuseRequested_isPersisted() {
        Map<String, Object> payload = Map.of(
                "user_id", 42,
                "group_id", 7,
                "excuse_type", "illness"
        );
        Map<String, Object> envelope = Map.of(
                "event_type", "excuse.requested",
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", "2026-04-24T12:00:00Z",
                "event_version", 1,
                "trace_id", "trace-it-123",
                "source", "attendance-service",
                "payload", payload
        );

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<NotificationHistoryDocument> all = repository.findAll();
            assertThat(all).hasSize(1);
            NotificationHistoryDocument doc = all.get(0);
            assertThat(doc.getUserId()).isEqualTo(42L);
            assertThat(doc.getType()).isEqualTo(NotificationType.EXCUSE_REQUESTED);
            assertThat(doc.getTraceId()).isEqualTo("trace-it-123");
            assertThat(doc.getSentAt()).isNotNull();
            assertThat(doc.getReadAt()).isNull();
        });
    }

    @Test
    void broadcastLessonStarted_isSkipped() {
        Map<String, Object> payload = Map.of("group_id", 7, "lesson_id", 100);
        Map<String, Object> envelope = Map.of(
                "event_type", "lesson.started",
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", "2026-04-24T12:00:00Z",
                "event_version", 1,
                "trace_id", "trace-skip",
                "source", "schedule-service",
                "payload", payload
        );

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);

        // Даём consumer'у шанс обработать, затем проверяем что persist не произошёл.
        // Использовать await'a с otherwise — тут подождём фиксированно, убедимся что пусто.
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(repository.findAll()).isEmpty();
    }
}
