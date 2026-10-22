package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.rutcampustrack.schedule.config.SchedulingConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

/**
 * M02 Группа 1 — smoke-тест ShedLock на реальной схеме schedule_db.
 *
 * <p>Моделирует 2 инстанса schedule-service через 2 независимых JdbcTemplateLockProvider поверх
 * одного PostgreSQL + Flyway-миграцию V10__shedlock_table.sql: 1) Первый провайдер берёт lock
 * "lesson-status-transition". 2) Второй провайдер пытается взять тот же lock одновременно →
 * Optional.empty(). 3) После unlock первого — второй может взять lock.
 *
 * <p>Тест не поднимает Spring context (нельзя — `@ActiveProfiles("test")` деактивирует
 * SchedulingConfig). Проверяем сырой lock-механизм: достаточно для acceptance criteria «2 инстанса
 * → событие один раз».
 */
@Testcontainers
class ShedLockSmokeIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("schedule_db")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");

    private static DataSource dataSource;

    @BeforeAll
    static void setup() {
        POSTGRES.start();
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void twoInstances_onlyOneHoldsLock() {
        try (var context1 = newContext();
                var context2 = newContext()) {
            LockProvider provider1 = context1.getBean(LockProvider.class);
            LockProvider provider2 = context2.getBean(LockProvider.class);

            LockConfiguration config =
                    new LockConfiguration(
                            Instant.now(),
                            "lesson-status-transition",
                            Duration.ofMinutes(2),
                            Duration.ofSeconds(10));

            Optional<SimpleLock> lock1 = provider1.lock(config);
            assertThat(lock1).as("first instance acquires lock").isPresent();

            Optional<SimpleLock> lock2 = provider2.lock(config);
            assertThat(lock2)
                    .as("second instance must be blocked while first holds lock")
                    .isEmpty();

            lock1.get().unlock();

            // lockAtLeastFor = 10s — после unlock lock_until всё ещё in-future,
            // поэтому второй инстанс не должен мгновенно перехватить. Это ожидаемо
            // и документирует семантику защиты от двойной публикации.
            Optional<SimpleLock> lock3 = provider2.lock(config);
            assertThat(lock3).as("lockAtLeastFor still applies after unlock").isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext newContext() {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("production");
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(SchedulingConfig.class);
        context.refresh();
        return context;
    }
}
