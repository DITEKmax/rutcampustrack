package ru.rutcampustrack.notification.push;

import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.rutcampustrack.shared.events.IdempotencyStore;
import ru.rutcampustrack.shared.outbox.IdempotencyCleanupJob;

import java.time.Clock;

/**
 * M05 G7 (NEW-148) — инфраструктура для {@link PushSubscriptionCleanupJob}:
 * scheduling + ShedLock-Mongo + системный Clock для тестируемости.
 *
 * <p>Разделено на {@code Infra} (всегда — Clock bean для тестов) и
 * {@code Scheduler} (prod only, с {@code @Profile("!test")} чтобы не
 * запускался из integration-тестов). Reference-pattern — attendance
 * {@code OutboxConfig} (M02 Группа 5).
 */
@Configuration
@Slf4j
public class PushCleanupConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    /**
     * Bootstrap: нормализует отсутствующее {@code last_seen} для документов,
     * созданных до M05 G7. Выполняется один раз на {@link ApplicationReadyEvent}
     * (поздний хук — MongoTemplate и PostConstruct PushMongoConfig уже
     * отработали). Идемпотентно: при повторном старте ничего не меняет,
     * потому что все подписки уже имеют {@code last_seen}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStart(ApplicationReadyEvent event) {
        PushSubscriptionCleanupJob job = event.getApplicationContext()
                .getBean(PushSubscriptionCleanupJob.class);
        try {
            long modified = job.backfillMissingLastSeen();
            if (modified > 0) {
                log.info("M05 G7 bootstrap: last_seen backfill on {} pre-existing push subscriptions",
                        modified);
            }
        } catch (Exception e) {
            log.warn("M05 G7 bootstrap backfill failed (will retry lazily): {}", e.getMessage());
        }
    }

    @Configuration
    @Profile("!test")
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
    public static class Scheduler {
        @Bean
        public LockProvider pushCleanupLockProvider(MongoTemplate mongoTemplate) {
            MongoDatabase db = mongoTemplate.getDb();
            return new MongoLockProvider(db);
        }

        /** M13 G8 — daily cleanup для event_consumer_processed (retention 7d). */
        @Bean
        public IdempotencyCleanupJob idempotencyCleanupJob(
                IdempotencyStore store,
                @Value("${rutcampustrack.idempotency.retention-days:7}") int retentionDays) {
            return new IdempotencyCleanupJob(store, Clock.systemUTC(), retentionDays);
        }
    }
}
