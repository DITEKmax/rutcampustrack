package ru.rutcampustrack.schedule.integration;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

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
