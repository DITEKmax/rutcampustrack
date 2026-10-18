package ru.rutcampustrack.academic.integration;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.shared.outbox.OutboxEventSender;

/**
 * Base class for cache integration tests. Starts Testcontainers PostgreSQL + Redis. Redis
 * autoconfiguration is NOT excluded for the default critical factory/template. The private cache
 * endpoint is explicitly bound to this fixture's owned Redis too; one CacheIT case adds a second
 * owned Redis to prove actual process isolation.
 *
 * <p>Key difference from AbstractAcademicIntegrationTest: - Adds GenericContainer for Redis
 * alongside PostgreSQLContainer - Does NOT exclude RedisAutoConfiguration /
 * RedisRepositoriesAutoConfiguration - Only excludes RabbitMQ autoconfiguration (mock
 * RabbitTemplate satisfies DomainEventListener)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InternalJwtTestConfig.class)
@TestExecutionListeners(
        listeners = AcademicFixtureResetListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractAcademicCacheIntegrationTest {

    @MockitoBean RabbitTemplate rabbitTemplate;

    @MockitoBean OutboxEventSender outboxEventSender;

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        // Private resources only: fixture reset must never clear a reused cache.
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName(
                                "academic_ci_"
                                        + java.util.UUID.randomUUID().toString().replace("-", ""))
                        .withUsername("rct_user")
                        .withPassword("rct_dev_pass")
                        .withReuse(false);
        POSTGRES.start();
        ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.prepare(POSTGRES);

        REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(false);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Both are owned by this fixture; production uses separate Redis processes.
        registry.add("rct.cache.redis.host", REDIS::getHost);
        registry.add("rct.cache.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("rct.cache.redis.username", () -> "");
        registry.add("rct.cache.redis.password", () -> "");
        // Only exclude RabbitMQ -- Redis is NEEDED for cache tests
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }

    protected static String cachePrefix(CacheManager manager, String name) {
        return ((RedisCacheManager) manager)
                .getCacheConfigurations()
                .get(name)
                .getKeyPrefixFor(name);
    }
}
