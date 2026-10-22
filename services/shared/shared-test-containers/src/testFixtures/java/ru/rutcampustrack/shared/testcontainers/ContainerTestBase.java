package ru.rutcampustrack.shared.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real service dependencies owned by one test JVM. Containers are private (never reused), start
 * only when Spring resolves their connection properties, and are stopped on JVM shutdown. A fresh
 * namespace prevents another test JVM from sharing destructive cleanup state.
 */
@Testcontainers
public abstract class ContainerTestBase {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16-alpine");
    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:7");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final DockerImageName RABBITMQ_IMAGE =
            DockerImageName.parse("rabbitmq:3.13-management-alpine");

    private static final String NAMESPACE =
            "test_" + java.util.UUID.randomUUID().toString().replace("-", "");

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName(NAMESPACE)
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(false);

    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(MONGO_IMAGE).withReuse(false);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379).withReuse(false);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(RABBITMQ_IMAGE).withReuse(false);

    static {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    stopOwned(RABBITMQ);
                                    stopOwned(REDIS);
                                    stopOwned(MONGO);
                                    stopOwned(POSTGRES);
                                },
                                "owned-test-containers-cleanup"));
    }

    private static synchronized <T extends org.testcontainers.containers.GenericContainer<?>>
            T started(T container) {
        if (!container.isRunning()) container.start();
        return container;
    }

    private static void stopOwned(org.testcontainers.containers.GenericContainer<?> container) {
        try {
            if (container.isRunning()) container.stop();
        } catch (RuntimeException ignored) {
            // Ryuk also owns these non-reused containers if the JVM is already shutting down.
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", () -> started(POSTGRES).getJdbcUrl());
        registry.add("spring.datasource.username", () -> started(POSTGRES).getUsername());
        registry.add("spring.datasource.password", () -> started(POSTGRES).getPassword());
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // MongoDB
        registry.add("spring.data.mongodb.uri", () -> started(MONGO).getReplicaSetUrl(NAMESPACE));

        // Redis
        registry.add("spring.data.redis.host", () -> started(REDIS).getHost());
        registry.add("spring.data.redis.port", () -> started(REDIS).getMappedPort(6379));

        // RabbitMQ
        registry.add("spring.rabbitmq.host", () -> started(RABBITMQ).getHost());
        registry.add("spring.rabbitmq.port", () -> started(RABBITMQ).getAmqpPort());
        registry.add("spring.rabbitmq.username", () -> started(RABBITMQ).getAdminUsername());
        registry.add("spring.rabbitmq.password", () -> started(RABBITMQ).getAdminPassword());
    }
}
