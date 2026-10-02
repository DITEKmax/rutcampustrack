package ru.rutcampustrack.auth.event;

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
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxMetrics;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.jpa.JpaOutboxStorage;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
public class OutboxConfig {
    @Bean
    public OutboxStorage authOutboxStorage() {
        return new JpaOutboxStorage<>(AuthOutboxEntity.class);
    }

    @Bean
    public OutboxMetrics authOutboxMetrics(OutboxStorage storage, MeterRegistry meterRegistry) {
        return new OutboxMetrics(storage, meterRegistry);
    }

    @Configuration
    @Profile("!test")
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT15M")
    public static class Publisher {
        @Bean
        public LockProvider authOutboxLockProvider(DataSource dataSource) {
            return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                    .withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
        }

        @Bean
        public AuthOutboxPublisherJob authOutboxPublisherJob(OutboxStorage storage,
                OutboxEventSender sender, MeterRegistry meterRegistry) {
            return new AuthOutboxPublisherJob(storage, sender, meterRegistry);
        }

        @Bean
        public AuthOutboxCleanupJob authOutboxCleanupJob(OutboxStorage storage,
                @Value("${rutcampustrack.auth-outbox.retention-days:7}") int retentionDays) {
            return new AuthOutboxCleanupJob(storage, Clock.systemUTC(), retentionDays);
        }
    }
}
