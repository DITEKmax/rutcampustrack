package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * M02 Группа 6 acceptance — integration-тест retention (7 дней, NEW-7).
 *
 * <p>Пишем 3 записи в academic_outbox через реальный OutboxStorage. Чтобы «перемотать время» —
 * подменяем {@link Clock} через test-config на fixed-часы в «недавнем прошлом». После markSent()
 * cleanup удаляет только SENT rows старше retention; PENDING — не трогает.
 *
 * <p>Spring-специфично: перезаписываем {@code @Bean OutboxCleanupJob} через {@code @Import
 * CleanupTestConfig} с {@code @Primary} — наш {@code OutboxConfig.Publisher} guarded
 * {@code @Profile("!test")}, но сам {@code OutboxCleanupJob} в нём же → в тестах его вообще нет.
 * Поэтому создаём тестовый bean с подменённым Clock'ом.
 */
@Import(OutboxCleanupIT.CleanupTestConfig.class)
class OutboxCleanupIT extends AbstractAcademicIntegrationTest {

    /** Retention — 7 дней (такой же дефолт как в прод-конфиге). */
    private static final int RETENTION_DAYS = 7;

    /** "Сейчас" в тестовом Clock'е. */
    static final Instant TEST_NOW = Instant.parse("2026-04-19T12:00:00Z");

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired private OutboxStorage outboxStorage;

    @Autowired private OutboxCleanupJob cleanupJob;

    @Autowired private PlatformTransactionManager txManager;

    @Test
    void cleanup_deletesSentOlderThanRetention_keepsRecent() {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        String prefix = "retention." + java.util.UUID.randomUUID() + ".";
        List<String> kinds = List.of("old", "recent", "pending", "cutoff", "beforeCutoff");
        tt.executeWithoutResult(
                tx ->
                        kinds.forEach(
                                kind ->
                                        outboxStorage.save(
                                                prefix + kind, "{\"kind\":\"" + kind + "\"}")));
        var owned =
                outboxStorage.findPending(1000).stream()
                        .filter(row -> row.eventType().startsWith(prefix))
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        row -> row.eventType().substring(prefix.length()),
                                        row -> row));
        assertThat(owned.keySet()).containsExactlyInAnyOrderElementsOf(kinds);
        Instant cutoff = TEST_NOW.minus(Duration.ofDays(RETENTION_DAYS));
        var sentTimes =
                java.util.Map.of(
                        "old", TEST_NOW.minus(Duration.ofDays(10)),
                        "recent", TEST_NOW.minus(Duration.ofDays(3)),
                        "cutoff", cutoff,
                        "beforeCutoff", cutoff.minusNanos(1000));
        sentTimes.forEach(
                (kind, at) -> {
                    tt.executeWithoutResult(tx -> outboxStorage.markSent(owned.get(kind).id()));
                    updateSentAt(tt, owned.get(kind).id(), at);
                });
        var before =
                jdbc.queryForList(
                        "SELECT id,event_type,payload,status,sent_at FROM academic_outbox WHERE"
                            + " event_type LIKE ? ORDER BY event_type",
                        prefix + "%");
        long deleted = tt.execute(tx -> cleanupJob.runCleanup());
        assertThat(deleted).isEqualTo(2L);
        var expected =
                before.stream()
                        .filter(
                                row ->
                                        !List.of(prefix + "old", prefix + "beforeCutoff")
                                                .contains(row.get("event_type")))
                        .toList();
        assertThat(
                        jdbc.queryForList(
                                "SELECT id,event_type,payload,status,sent_at FROM academic_outbox"
                                    + " WHERE event_type LIKE ? ORDER BY event_type",
                                prefix + "%"))
                .containsExactlyElementsOf(expected);
        assertThat(
                        outboxStorage.findPending(1000).stream()
                                .filter(row -> row.eventType().startsWith(prefix)))
                .containsExactly(owned.get("pending"));
        long secondDeleted = tt.execute(tx -> cleanupJob.runCleanup());
        assertThat(secondDeleted).isEqualTo(0L);
    }

    private void updateSentAt(TransactionTemplate tt, Object id, Instant sentAt) {
        tt.executeWithoutResult(
                s ->
                        entityManager
                                .createNativeQuery(
                                        "UPDATE academic_outbox SET sent_at = :ts WHERE id = :id")
                                .setParameter("ts", sentAt)
                                .setParameter("id", id)
                                .executeUpdate());
    }

    @Autowired private jakarta.persistence.EntityManager entityManager;

    /**
     * Test-config: инжектит {@link OutboxCleanupJob} с {@link #TEST_NOW} Clock'ом. В prod-профиле
     * job создаётся в {@code OutboxConfig.Publisher @Profile("!test")} — значит в test его нет, мы
     * создаём сами. Primary — на случай если что-то другое попробует создать.
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
