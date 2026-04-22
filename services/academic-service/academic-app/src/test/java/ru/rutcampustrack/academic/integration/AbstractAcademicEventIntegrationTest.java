package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.util.List;

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
 *
 * M02 Группа 5: {@code DomainEventListener} пишет в outbox (а не напрямую в
 * Rabbit). Для event-тестов после сервисного вызова нужно явно дёрнуть
 * {@link #flushOutbox()} — эмулирует OutboxPublisherJob tick и публикует
 * pending события в Rabbit. OutboxPublisherJob прод-bean guarded
 * {@code @Profile("!test")}, тест-bean создаётся {@link OutboxTestConfig}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractAcademicEventIntegrationTest {

    @Autowired
    protected OutboxPublisherJob outboxPublisherJob;

    @Autowired(required = false)
    protected OutboxStorage outboxStorage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Эмулирует один tick OutboxPublisherJob: читает pending rows из
     * academic_outbox и публикует в Rabbit. Вызывать в тестах после
     * service-вызова, который публикует события.
     *
     * <p>{@code publishBatch()} содержит {@code UPDATE} (markSent/markFailed),
     * требующий активной tx. В проде это обеспечивает {@code @Transactional}
     * на tick()-методе; в тестах оборачиваем вручную в TransactionTemplate.
     */
    protected void flushOutbox() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outboxPublisherJob.publishBatch());
    }

    /**
     * M02: drain leftover pending outbox rows между тестами — academic_outbox
     * shared через reused Testcontainer. Без этого предыдущий тест может
     * оставить события, которые flushOutbox() подхватит и пошлёт в Rabbit
     * next testу.
     */
    @BeforeEach
    void drainOutboxBeforeEach() {
        if (outboxStorage == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<OutboxRecord> leftover = outboxStorage.findPending(1000);
            leftover.forEach(r -> outboxStorage.markSent(r.id()));
        });
    }

    static final PostgreSQLContainer<?> POSTGRES;
    static final RabbitMQContainer RABBITMQ;

    static {
        // M08 D5 — reuse=true; локально активируется через
        // ~/.testcontainers.properties (testcontainers.reuse.enable=true),
        // на CI игнорируется (fresh runner).
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass")
                .withReuse(true);
        POSTGRES.start();

        RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
                .withReuse(true);
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
