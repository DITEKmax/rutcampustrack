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
 * Cache integration uses the shared split PostgreSQL/Auth fixture and a separate disposable
 * Academic cache Redis. Auth tickets and limits use the fixture's distinct critical Redis process.
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

    @org.springframework.beans.factory.annotation.Autowired
    protected AcademicIdentityIntegrationFixture identityFixture;

    protected org.springframework.test.web.servlet.request.RequestPostProcessor signedAdmin() {
        return AcademicTestAdminRequest.signed(identityFixture);
    }

    @MockitoBean RabbitTemplate rabbitTemplate;

    @MockitoBean OutboxEventSender outboxEventSender;

    static final AcademicIdentityIntegrationFixture IDENTITY_FIXTURE =
            AcademicIdentityIntegrationFixture.start();
    static final PostgreSQLContainer<?> POSTGRES = IDENTITY_FIXTURE.postgres();
    static final GenericContainer<?> REDIS;

    static {
        REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(false);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        IDENTITY_FIXTURE.properties(registry, "cache");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Academic's cache is separate from the helper's Auth-critical Redis process.
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
