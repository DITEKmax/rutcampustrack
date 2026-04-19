package ru.rutcampustrack.schedule.event;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.jpa.JpaOutboxStorage;

import javax.sql.DataSource;

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
    }

    @Configuration
    @Profile("!test")
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
    public static class Publisher {
        @Bean
        public OutboxPublisherJob outboxPublisherJob(OutboxStorage storage,
                                                     OutboxEventSender sender) {
            return new OutboxPublisherJob(storage, sender);
        }
    }
}
