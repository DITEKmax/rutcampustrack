package ru.rutcampustrack.attendance.event;

import com.mongodb.client.MongoDatabase;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.rutcampustrack.shared.events.IdempotencyStore;
import ru.rutcampustrack.shared.outbox.IdempotencyCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxMetrics;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;

import java.time.Clock;

/**
 * Собирает outbox-инфраструктуру в attendance-service (M02 Группа 5).
 *
 * <p>Разделено на Storage (всегда) + Publisher (prod only), как в academic.
 * Mongo-специфично: LockProvider через {@code shedlock-provider-mongo}
 * (не jdbc — у attendance MongoDB).
 */
@Configuration
public class OutboxConfig {

    public static final String OUTBOX_COLLECTION = "attendance_outbox";
    public static final String SHEDLOCK_COLLECTION = "shedLock";

    @Configuration
    public static class Storage {
        @Bean
        public LockProvider outboxLockProvider(MongoTemplate mongoTemplate) {
            MongoDatabase db = mongoTemplate.getDb();
            return new MongoLockProvider(db);
        }

        @Bean
        public MongoOutboxStorage outboxStorage(MongoTemplate mongoTemplate) {
            MongoOutboxStorage storage = new MongoOutboxStorage(mongoTemplate, OUTBOX_COLLECTION);
            storage.ensureIndexes();
            return storage;
        }

        @Bean
        public OutboxMetrics outboxMetrics(OutboxStorage storage, MeterRegistry meterRegistry) {
            return new OutboxMetrics(storage, meterRegistry);
        }
    }

    @Configuration
    @Profile("!test")
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
    public static class Publisher {
        @Bean
        public OutboxPublisherJob outboxPublisherJob(OutboxStorage storage,
                                                     OutboxEventSender sender,
                                                     MeterRegistry meterRegistry) {
            return new OutboxPublisherJob(storage, sender, meterRegistry);
        }

        @Bean
        public OutboxCleanupJob outboxCleanupJob(
                OutboxStorage storage,
                @Value("${rutcampustrack.outbox.retention-days:7}") int retentionDays) {
            return new OutboxCleanupJob(storage, Clock.systemUTC(), retentionDays);
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
