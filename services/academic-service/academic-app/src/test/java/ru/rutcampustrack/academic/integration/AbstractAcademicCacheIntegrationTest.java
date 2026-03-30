package ru.rutcampustrack.academic.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for cache integration tests.
 * Starts Testcontainers PostgreSQL + Redis. Redis autoconfiguration is NOT excluded
 * so that Spring Cache is wired to a real Redis instance.
 *
 * Key difference from AbstractAcademicIntegrationTest:
 * - Adds GenericContainer for Redis alongside PostgreSQLContainer
 * - Does NOT exclude RedisAutoConfiguration / RedisRepositoriesAutoConfiguration
 * - Only excludes RabbitMQ autoconfiguration
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractAcademicCacheIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Only exclude RabbitMQ -- Redis is NEEDED for cache tests
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }
}
