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
 * Базовый класс для integration-тестов сервисов (NEW-158, P2-8/2).
 *
 * <p>Поднимает четыре контейнера с {@code reuse=true} — повторный прогон
 * в пределах одной сессии разработчика не перезапускает инфру. Для включения
 * reuse нужен файл {@code ~/.testcontainers.properties} со строкой
 * {@code testcontainers.reuse.enable=true}.
 *
 * <p>Контейнеры стартуют при первом обращении (static-инициализация) —
 * тест не получит контекст без живой инфры.
 *
 * <p>Пробрасывает в Spring-контекст:
 * <ul>
 *   <li>{@code spring.datasource.url/username/password} — PostgreSQL.</li>
 *   <li>{@code spring.data.mongodb.uri} — MongoDB.</li>
 *   <li>{@code spring.data.redis.host/port} — Redis.</li>
 *   <li>{@code spring.rabbitmq.host/port/username/password} — RabbitMQ.</li>
 * </ul>
 *
 * <p>Сервис-потребитель подключает модуль через
 * {@code testImplementation(testFixtures(project(":services:shared:shared-test-containers")))}
 * и расширяет этот класс.
 */
@Testcontainers
public abstract class ContainerTestBase {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16-alpine");
    private static final DockerImageName MONGO_IMAGE =
            DockerImageName.parse("mongo:7");
    private static final DockerImageName REDIS_IMAGE =
            DockerImageName.parse("redis:7-alpine");
    private static final DockerImageName RABBITMQ_IMAGE =
            DockerImageName.parse("rabbitmq:3.13-management-alpine");

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("test_db")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(MONGO_IMAGE)
                    .withReuse(true);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(REDIS_IMAGE)
                    .withExposedPorts(6379)
                    .withReuse(true);

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(RABBITMQ_IMAGE)
                    .withReuse(true);

    static {
        POSTGRES.start();
        MONGO.start();
        REDIS.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // MongoDB
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // RabbitMQ
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}
