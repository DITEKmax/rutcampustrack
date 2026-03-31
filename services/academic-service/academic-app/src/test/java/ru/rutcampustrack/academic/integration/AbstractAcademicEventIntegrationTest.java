package ru.rutcampustrack.academic.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for event integration tests.
 * Starts Testcontainers PostgreSQL + RabbitMQ. RabbitMQ autoconfiguration is NOT excluded
 * so that the full AMQP stack (RabbitTemplate, DomainEventListener) is wired.
 *
 * Key differences from AbstractAcademicIntegrationTest:
 * - Adds RabbitMQContainer alongside PostgreSQLContainer
 * - Does NOT exclude RabbitAutoConfiguration -- AMQP stack is needed for event tests (Pitfall 2)
 * - Excludes Redis autoconfiguration (no Redis container needed for event tests per D-12)
 * - Sets grpc.server.port=-1 to disable Netty port binding (matches Phase 07 convention)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractAcademicEventIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final RabbitMQContainer RABBITMQ;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();

        RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // RabbitMQ -- point at Testcontainers broker (credentials: guest/guest by default)
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        // Exclude Redis -- not needed for event tests (per D-12)
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                      "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");

        // Disable gRPC server in event tests
        registry.add("grpc.server.port", () -> -1);
    }
}
