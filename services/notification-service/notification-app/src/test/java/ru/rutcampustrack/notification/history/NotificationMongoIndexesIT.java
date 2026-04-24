package ru.rutcampustrack.notification.history;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 G6 — IT проверяет что при старте notification-web на чистой Mongo
 * {@link NotificationHistoryMongoConfig#initIndexes()} создаёт все 3
 * ожидаемых индекса + TTL {@code expireAfterSeconds}, а fail-fast
 * verification не срабатывает (нет
 * {@link IllegalStateException} на context load).
 */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "notification.history.ttl-days=30"
})
class NotificationMongoIndexesIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    NotificationMongoIndexesIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    @Test
    void notificationHistory_afterStartup_hasExpectedIndexesAndTtl() {
        // Collection создана + 3 кастомных индекса + _id_ = 4 total.
        List<Document> indexes = new ArrayList<>();
        mongoTemplate.getCollection("notification_history").listIndexes().forEach(indexes::add);

        Set<String> names = indexes.stream()
                .map(d -> d.getString("name"))
                .collect(Collectors.toSet());

        assertThat(names)
                .as("все 3 custom-индекса + _id_ должны присутствовать")
                .contains(
                        NotificationHistoryMongoConfig.IDX_USER_SENT_DESC,
                        NotificationHistoryMongoConfig.IDX_USER_READ,
                        NotificationHistoryMongoConfig.IDX_TTL_SENT_AT,
                        "_id_"
                );

        // TTL: expireAfterSeconds на ttl_sent_at = 30 дней = 2_592_000 sec.
        Document ttlIdx = indexes.stream()
                .filter(d -> NotificationHistoryMongoConfig.IDX_TTL_SENT_AT.equals(d.getString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ttl_sent_at index missing"));

        Number ttl = ttlIdx.get("expireAfterSeconds", Number.class);
        assertThat(ttl)
                .as("ttl_sent_at expireAfterSeconds должно быть 30 дней")
                .isNotNull();
        assertThat(ttl.longValue()).isEqualTo(30L * 24 * 60 * 60);

        // Compound (user_id, sent_at) = checklist требование «compound (user_id, created_at)».
        // В notification_history поле называется sent_at (M10 schema — см.
        // NotificationHistoryDocument.@Field).
        Document userSentIdx = indexes.stream()
                .filter(d -> NotificationHistoryMongoConfig.IDX_USER_SENT_DESC.equals(d.getString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("idx_user_sent_desc missing"));

        Document key = userSentIdx.get("key", Document.class);
        assertThat(key).containsKeys("user_id", "sent_at");
        assertThat(key.getInteger("user_id")).isEqualTo(1);
        assertThat(key.getInteger("sent_at")).isEqualTo(-1);
    }
}
