package ru.rutcampustrack.schedule.event;

import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.rutcampustrack.shared.outbox.OutboxCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxMetrics;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
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
 * <p>Внимание: {@link Storage} не создаёт LockProvider — он уже есть в
 * {@code SchedulingConfig}. Если {@code SchedulingConfig} guarded
 * {@code @Profile("!test")}, то в тестах LockProvider-а нет, но Publisher
 * тоже в тестах не стартует — @SchedulerLock не проверяется.
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
    }
}
