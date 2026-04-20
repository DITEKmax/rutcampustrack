package ru.rutcampustrack.notification.push;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * M05 G7 (NEW-148) — удаляет push-подписки, не доставлявшие push'и
 * дольше {@code retention-days} (по умолчанию 90 дней).
 *
 * <p>Причины, по которым подписка может «протухнуть» без 410 Gone:
 * <ul>
 *   <li>Устройство долго оффлайн / пользователь удалил приложение без
 *       отписки;</li>
 *   <li>Группа не получает ни одного pushable-события за окно retention;</li>
 *   <li>Push endpoint вернул 4xx/5xx, отличный от 410.</li>
 * </ul>
 *
 * <p>Cron {@code 0 0 3 * * SUN} — раз в неделю, воскресенье 03:00 UTC.
 * {@link SchedulerLock} защищает от параллельного cleanup'а при
 * scale-out notification-web (D10 micro-ADR M05).
 *
 * <p>Bootstrap-миграция ({@link #backfillMissingLastSeen()}) выполняется
 * однократно на старте — проставляет {@code last_seen = created_at} для
 * документов, созданных до M05 G7 (поле отсутствует). Без bootstrap
 * первый cleanup удалил бы все pre-M05 подписки из-за запроса
 * {@code last_seen < cutoff} (null &lt; anything = false → запись не
 * попадает; тем не менее нормализуем схему для наблюдаемости).
 */
@Component
@Slf4j
public class PushSubscriptionCleanupJob {

    private final PushSubscriptionRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final long retentionDays;

    public PushSubscriptionCleanupJob(
            PushSubscriptionRepository repository,
            MongoTemplate mongoTemplate,
            Clock clock,
            @Value("${rutcampustrack.push.cleanup.retention-days:90}") long retentionDays) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /**
     * Раз в неделю: удалить подписки с {@code last_seen < now - 90d}.
     *
     * <p>Индекс {@code idx_last_seen} (см. PushMongoConfig) обеспечивает
     * IXSCAN — без полного коллекционного scan'а.
     */
    @Scheduled(cron = "${rutcampustrack.push.cleanup.cron:0 0 3 * * SUN}")
    @SchedulerLock(name = "cleanupStalePushSubs", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanupStalePushSubs() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
        long deleted = repository.deleteByLastSeenBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleanup push-subs: deleted {} stale subscriptions (last_seen < {})",
                    deleted, cutoff);
        } else {
            log.debug("Cleanup push-subs: nothing to delete (cutoff={})", cutoff);
        }
    }

    /**
     * Проставляет {@code last_seen = created_at} для документов, созданных
     * до M05 G7 (поле {@code last_seen} отсутствует / null).
     *
     * <p>Публичный метод для переиспользования в интеграционных тестах.
     */
    public long backfillMissingLastSeen() {
        Query query = new Query(Criteria.where("last_seen").exists(false));
        // Mongo $set может копировать значения через агрегационный pipeline;
        // используем прямой update с expression fieldpath.
        Update update = new Update()
                .set("last_seen", Instant.now(clock));
        long modified = mongoTemplate.updateMulti(query, update, PushSubscriptionDocument.class)
                .getModifiedCount();
        if (modified > 0) {
            log.info("Backfill last_seen: {} pre-M05 subscriptions normalized", modified);
        }
        return modified;
    }
}
