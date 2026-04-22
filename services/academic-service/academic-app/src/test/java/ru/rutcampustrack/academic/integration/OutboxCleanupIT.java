package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.shared.outbox.OutboxCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M02 Группа 6 acceptance — integration-тест retention (7 дней, NEW-7).
 *
 * <p>Пишем 3 записи в academic_outbox через реальный OutboxStorage.
 * Чтобы «перемотать время» — подменяем {@link Clock} через test-config
 * на fixed-часы в «недавнем прошлом». После markSent() cleanup удаляет
 * только SENT rows старше retention; PENDING — не трогает.
 *
 * <p>Spring-специфично: перезаписываем {@code @Bean OutboxCleanupJob} через
 * {@code @Import CleanupTestConfig} с {@code @Primary} — наш
 * {@code OutboxConfig.Publisher} guarded {@code @Profile("!test")}, но сам
 * {@code OutboxCleanupJob} в нём же → в тестах его вообще нет. Поэтому
 * создаём тестовый bean с подменённым Clock'ом.
 */
@Import(OutboxCleanupIT.CleanupTestConfig.class)
class OutboxCleanupIT extends AbstractAcademicIntegrationTest {

    /** Retention — 7 дней (такой же дефолт как в прод-конфиге). */
    private static final int RETENTION_DAYS = 7;

    /** "Сейчас" в тестовом Clock'е. */
    static final Instant TEST_NOW = Instant.parse("2026-04-19T12:00:00Z");

    @Autowired
    private OutboxStorage outboxStorage;

    @Autowired
    private OutboxCleanupJob cleanupJob;

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void cleanup_deletesSentOlderThanRetention_keepsRecent() {
        TransactionTemplate tt = new TransactionTemplate(txManager);

        // Record A: SENT 10 дней назад → должна удалиться
        // Record B: SENT 3 дня назад → остаётся
        // Record C: PENDING → остаётся (cleanup трогает только SENT)
        tt.executeWithoutResult(s -> {
            outboxStorage.save("test.old", "{\"age\":10}");
            outboxStorage.save("test.recent", "{\"age\":3}");
            outboxStorage.save("test.pending", "{\"age\":0}");
        });

        List<OutboxRecord> beforeCleanup = outboxStorage.findPending(100);
        assertThat(beforeCleanup).hasSize(3);

        // Помечаем первые две SENT с разными sent_at.
        // Реальных manipulate sent_at API нет, поэтому markSent и полагаемся
        // на Clock-подмену. Cleanup использует `TEST_NOW - retention` как
        // cutoff; чтобы rows оказались ПО РАЗНЫЕ стороны cutoff'а, нужно
        // менять sent_at напрямую через native SQL. Используем JdbcTemplate.
        OutboxRecord oldRec = beforeCleanup.get(0);
        OutboxRecord recentRec = beforeCleanup.get(1);

        tt.executeWithoutResult(s -> {
            outboxStorage.markSent(oldRec.id());
            outboxStorage.markSent(recentRec.id());
        });

        // Подмешиваем sent_at через JPA native query. В production sent_at
        // устанавливается в markSent = now(); для теста нам нужны явные
        // значения относительно TEST_NOW.
        Instant tenDaysAgo = TEST_NOW.minus(Duration.ofDays(10));
        Instant threeDaysAgo = TEST_NOW.minus(Duration.ofDays(3));
        updateSentAt(tt, oldRec.id(), tenDaysAgo);
        updateSentAt(tt, recentRec.id(), threeDaysAgo);

        // Запускаем cleanup — Clock подменён на TEST_NOW, retention 7d.
        // Cutoff = TEST_NOW - 7d = 2026-04-12 12:00Z.
        // oldRec.sent_at = TEST_NOW - 10d < cutoff → DELETE
        // recentRec.sent_at = TEST_NOW - 3d > cutoff → KEEP
        // pending → не SENT, не trогается
        long deleted = tt.execute(s -> cleanupJob.runCleanup());

        assertThat(deleted).isEqualTo(1L);

        // Проверяем: pending всё ещё там, recentRec нет в pending (он sent)
        List<OutboxRecord> stillPending = outboxStorage.findPending(100);
        assertThat(stillPending).hasSize(1);
        assertThat(stillPending.get(0).eventType()).isEqualTo("test.pending");
    }

    private void updateSentAt(TransactionTemplate tt, Object id, Instant sentAt) {
        tt.executeWithoutResult(s ->
                entityManager.createNativeQuery(
                                "UPDATE academic_outbox SET sent_at = :ts WHERE id = :id")
                        .setParameter("ts", sentAt)
                        .setParameter("id", id)
                        .executeUpdate());
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Test-config: инжектит {@link OutboxCleanupJob} с {@link #TEST_NOW} Clock'ом.
     * В prod-профиле job создаётся в {@code OutboxConfig.Publisher @Profile("!test")} —
     * значит в test его нет, мы создаём сами. Primary — на случай если что-то
     * другое попробует создать.
     */
    @TestConfiguration
    @Profile("test")
    static class CleanupTestConfig {
        @Bean
        @Primary
        OutboxCleanupJob testCleanupJob(OutboxStorage storage) {
            Clock fixedClock = Clock.fixed(TEST_NOW, ZoneId.of("UTC"));
            return new OutboxCleanupJob(storage, fixedClock, RETENTION_DAYS);
        }
    }
}
