package ru.rutcampustrack.academic.event;

import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
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

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Собирает outbox-инфраструктуру в academic-service (M02 Группа 5).
 *
 * <p>Разделено на два {@code @Configuration}-подкласса:
 * <ul>
 *   <li>{@link Storage} — активен всегда (в том числе {@code @ActiveProfiles("test")}).
 *       Поставляет {@link OutboxStorage} + {@link LockProvider} — нужны
 *       {@code DomainEventListener} и тестам, пишущим в outbox.</li>
 *   <li>{@link Publisher} — guarded {@code @Profile("!test")}. Запускает
 *       {@code @Scheduled} tick публикатора. В тестах не стартует, чтобы
 *       не тикать автоматически — тесты сами дёргают
 *       {@code OutboxPublisherJob.publishBatch()}.</li>
 * </ul>
 */
@Configuration
public class OutboxConfig {

    @Configuration
    public static class Storage {
        @Bean
        public LockProvider outboxLockProvider(DataSource dataSource) {
            return new JdbcTemplateLockProvider(
                    JdbcTemplateLockProvider.Configuration.builder()
                            .withJdbcTemplate(new JdbcTemplate(dataSource))
                            .usingDbTime()
                            .build());
        }

        @Bean
        public OutboxStorage outboxStorage() {
            return new JpaOutboxStorage<>(AcademicOutboxEntity.class);
        }

        @Bean
        public OutboxMetrics outboxMetrics(OutboxStorage storage, MeterRegistry meterRegistry) {
            return new OutboxMetrics(storage, meterRegistry);
        }

        @Bean
        public IdempotencyStore idempotencyStore() {
            return new JpaIdempotencyStore<>(AcademicEventConsumerProcessedEntity.class);
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
