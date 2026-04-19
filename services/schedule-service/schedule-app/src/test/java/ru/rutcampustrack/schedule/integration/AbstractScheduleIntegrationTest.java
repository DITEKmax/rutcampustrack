package ru.rutcampustrack.schedule.integration;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.util.List;

/**
 * Abstract base class for Schedule Service integration tests.
 *
 * Provides:
 * - Static PostgreSQL 16 Testcontainer with schedule_db (shared across all tests in JVM)
 * - Flyway auto-applies V1__baseline.sql creating schedule_items + lessons tables
 * - @MockitoBean RabbitTemplate prevents DomainEventListener from failing without RabbitMQ
 * - RabbitAutoConfiguration excluded via spring.autoconfigure.exclude
 * - @ActiveProfiles("test") activates application-test.yml and excludes @Profile("!test") beans
 *
 * No Redis exclusion needed — schedule-service does not use Redis.
 * gRPC server port set to -1 in application-test.yml — no Netty port binding in tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractScheduleIntegrationTest {

    @MockitoBean
    protected RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    protected OutboxStorage outboxStorage;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    /**
     * M02: удаляет все pending outbox-записи (markSent в tx). Вызывать
     * из @AfterEach в тестах которые пишут в outbox, чтобы следующий тест
     * стартовал с чистым findPending.
     */
    protected void drainOutbox() {
        if (outboxStorage == null || transactionManager == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<OutboxRecord> pending = outboxStorage.findPending(1000);
            pending.forEach(r -> outboxStorage.markSent(r.id()));
        });
    }

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("schedule_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }
}
