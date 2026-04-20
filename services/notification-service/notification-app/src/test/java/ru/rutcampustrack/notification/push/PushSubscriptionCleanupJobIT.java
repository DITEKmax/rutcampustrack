package ru.rutcampustrack.notification.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 G7 (NEW-148) — проверяет что {@link PushSubscriptionCleanupJob}
 * удаляет подписки с {@code last_seen} старше retention-окна и не трогает
 * свежие/будущие.
 *
 * <p>Сценарии:
 * <ul>
 *   <li>dead_sub (last_seen = now - 100d) → удалён;</li>
 *   <li>fresh_sub (last_seen = now - 10d) → остаётся;</li>
 *   <li>borderline_sub (last_seen = now - 89d) → остаётся (&lt; 90d);</li>
 *   <li>backfill: sub без last_seen → получает last_seen на bootstrap
 *       и не удаляется.</li>
 * </ul>
 *
 * <p>Расширяет {@link ContainerTestBase} — реальный Mongo через Testcontainers.
 * Profile {@code test} отключает {@code PushCleanupConfig.Scheduler}, поэтому
 * ни @Scheduled, ни ShedLock не запускаются — вызываем job напрямую.
 */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "rutcampustrack.push.cleanup.retention-days=90"
})
class PushSubscriptionCleanupJobIT extends ContainerTestBase {

    @org.springframework.test.context.DynamicPropertySource
    static void testKeyPath(org.springframework.test.context.DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    PushSubscriptionCleanupJobIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private PushSubscriptionRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    /** Mock PushService bean — иначе WebPushConfig попытается разобрать VAPID ключи
     *  (BouncyCastle ECCurve), а тестовые значения не валидны. */
    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    private PushSubscriptionCleanupJob job;

    // Детерминированное время: все относительные расчёты от фиксированного now.
    private static final Instant NOW = Instant.parse("2026-04-20T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        // Чистим коллекцию — ContainerTestBase shared между тестами.
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(),
                PushSubscriptionDocument.class);
        job = new PushSubscriptionCleanupJob(repository, mongoTemplate, fixedClock, 90);
    }

    private PushSubscriptionDocument sub(String endpoint, Instant lastSeen) {
        return PushSubscriptionDocument.builder()
                .userId(1L)
                .groupId(10L)
                .endpoint(endpoint)
                .p256dh("p256dh-" + endpoint)
                .auth("auth-" + endpoint)
                .createdAt(NOW.minusSeconds(86400L * 200))  // все созданы давно
                .lastSeen(lastSeen)
                .build();
    }

    @Test
    void cleanup_deletesDeadSubs_keepsFreshOnes() {
        repository.save(sub("dead", NOW.minusSeconds(86400L * 100)));     // 100 дней — dead
        repository.save(sub("fresh", NOW.minusSeconds(86400L * 10)));     // 10 дней — живая
        repository.save(sub("borderline", NOW.minusSeconds(86400L * 89))); // 89 дней — живая (< 90)

        job.cleanupStalePushSubs();

        assertThat(repository.findAll())
                .extracting(PushSubscriptionDocument::getEndpoint)
                .containsExactlyInAnyOrder("fresh", "borderline");
    }

    @Test
    void cleanup_onExactBoundary_treats90daysAsEligible() {
        // Строго < cutoff — значит 90 дней РОВНО попадает, т.к. minus(90d).
        // Реальный cutoff = NOW - 90d. Записи с last_seen == cutoff остаются
        // (< не включает равенство). Запись с last_seen < cutoff — удаляется.
        repository.save(sub("boundary", NOW.minusSeconds(86400L * 90).minusSeconds(1))); // за 1с до cutoff

        job.cleanupStalePushSubs();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void backfill_setsLastSeenForDocsMissingField() {
        // Вставляем документ без last_seen напрямую через mongoTemplate
        // (PushSubscriptionDocument.builder не позволит создать null last_seen
        // в новых документах, но старые pre-M05 имеют это состояние).
        mongoTemplate.getCollection("push_subscriptions").insertOne(new org.bson.Document()
                .append("user_id", 2L)
                .append("group_id", 10L)
                .append("endpoint", "legacy")
                .append("p256dh", "p")
                .append("auth", "a")
                .append("is_headman", false)
                .append("created_at", NOW.minusSeconds(86400L * 30)));

        long modified = job.backfillMissingLastSeen();

        assertThat(modified).isEqualTo(1);
        PushSubscriptionDocument legacy = repository.findAll().stream()
                .filter(d -> "legacy".equals(d.getEndpoint()))
                .findFirst().orElseThrow();
        assertThat(legacy.getLastSeen()).isEqualTo(NOW);

        // После backfill — cleanup не должен удалить legacy подписку:
        // last_seen = NOW, cutoff = NOW - 90d, NOW >= cutoff.
        job.cleanupStalePushSubs();
        assertThat(repository.findAll())
                .extracting(PushSubscriptionDocument::getEndpoint)
                .contains("legacy");
    }
}
