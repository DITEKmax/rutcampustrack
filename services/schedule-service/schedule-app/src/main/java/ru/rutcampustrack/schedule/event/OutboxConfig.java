package ru.rutcampustrack.schedule.event;

import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.rutcampustrack.shared.events.IdempotencyGuard;
import ru.rutcampustrack.shared.events.IdempotencyStore;
import ru.rutcampustrack.shared.outbox.IdempotencyCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxMetrics;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.jpa.JpaIdempotencyStore;
import ru.rutcampustrack.shared.outbox.jpa.JpaOutboxStorage;

import java.time.Clock;

/**
 * Собирает outbox-инфраструктуру в schedule-service (M02 Группа 5).
 *
 * <p>M02 Группа 1: существующий {@code SchedulingConfig} уже создаёт
 * LockProvider + {@code @EnableSchedulerLock} для
 * LessonStatusTransitionJob. Здесь создаётся Storage+Publisher, причём
 * Storage активен всегда (нужен DomainEventListener даже в тестах),
 * а Publisher guarded {@code @Profile("!test")}.
 *
 * <p>M13 G24-fix-5 — про LockProvider:
 * <ul>
 *   <li><b>В prod</b>: {@code SchedulingConfig} активен (Profile != "test"),
 *       создаёт LockProvider. {@code IdempotencyCleanupJob} +
 *       {@code OutboxPublisherJob} + {@code OutboxCleanupJob} в Publisher
 *       блоке используют {@code @SchedulerLock} — работает корректно.</li>
 *   <li><b>В test</b>: оба ({@code SchedulingConfig} + {@code Publisher})
 *       guarded {@code @Profile("!test")} — не активны, LockProvider не
 *       создаётся, но {@code @SchedulerLock} тоже не вызывается (no
 *       jobs scheduled).</li>
 * </ul>
 * <p><b>Invariant</b>: schedule-app зависит от SchedulingConfig для
 * LockProvider'а (в отличие от academic, где LockProvider в
 * OutboxConfig.Storage). Если кто-то удалит/перенесёт SchedulingConfig —
 * prod scheduler упадёт «no LockProvider bean defined». Регрессия
 * покрыта {@code OutboxLockProviderIT} (M13 G24-fix-5).
 */
@Configuration
public class OutboxConfig {

    @Configuration
    public static class Storage {
        @Bean
        public OutboxStorage outboxStorage() {
            return new JpaOutboxStorage<>(ScheduleOutboxEntity.class);
        }

        @Bean
        public OutboxMetrics outboxMetrics(OutboxStorage storage, MeterRegistry meterRegistry) {
            return new OutboxMetrics(storage, meterRegistry);
        }

        @Bean
        public IdempotencyStore idempotencyStore() {
            return new JpaIdempotencyStore<>(ScheduleEventConsumerProcessedEntity.class);
        }

        @Bean
        public IdempotencyGuard idempotencyGuard(IdempotencyStore store) {
            return new IdempotencyGuard(store);
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
