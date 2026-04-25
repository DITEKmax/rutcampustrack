package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M13 G12 — проверяет что {@code /actuator/health} реально отражает
 * состояние downstream'ов (Rabbit), а не silent UP при их падении.
 *
 * <p>Не extends {@link AbstractAcademicIntegrationTest} (тот mock'ает
 * RabbitTemplate + excluds RabbitAutoConfiguration) и не
 * {@link AbstractAcademicEventIntegrationTest} (тот reuse=true делит
 * RabbitMQContainer с другими IT — stop его сломает соседей).
 *
 * <p>Здесь — own dedicated PostgreSQLContainer + RabbitMQContainer
 * без {@code reuse}. Тест останавливает Rabbit и проверяет что
 * {@code components.rabbit.status: DOWN} в health response.
 *
 * <p>{@code @Order} нужен — health-up должен быть первым (до stop'а),
 * health-down — после.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthDegradationIT {

    @Autowired
    TestRestTemplate restTemplate;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("academic_db")
            .withUsername("rct_user")
            .withPassword("rct_dev_pass")
            .withReuse(true);

    /**
     * Dedicated Rabbit без reuse — тест свободно его останавливает,
     * не ломая параллельные IT.
     */
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                      "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");

        // application-test.yml выключает management.health.rabbit.enabled
        // (чтобы остальные IT без Rabbit container'а не падали). Здесь — у нас
        // dedicated RabbitMQContainer, indicator должен быть активен — это
        // то, что мы тестируем.
        registry.add("management.health.rabbit.enabled", () -> "true");

        registry.add("grpc.server.port", () -> -1);
    }

    @Test
    @Order(1)
    void healthUp_when_allDownstreams_alive() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("health response должен показывать status=UP пока Rabbit жив")
                .contains("\"status\":\"UP\"")
                .as("rabbit health indicator должен присутствовать (не отключён)")
                .contains("\"rabbit\"");
    }

    @Test
    @Order(2)
    void healthDown_when_rabbitStopped() {
        // Симулируем «kill rabbitmq» — контейнер останавливается.
        RABBITMQ.stop();

        // Spring Boot RabbitHealthIndicator пингует через short timeout.
        // Ждём пока health indicator успеет обновиться (TCP timeout для
        // closed port'а на localhost ~ instant, но даём 10с margin).
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

            // SERVICE_UNAVAILABLE (503) когда хотя бы один indicator DOWN.
            assertThat(response.getStatusCode())
                    .as("health endpoint должен возвращать 503 если Rabbit упал")
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

            assertThat(response.getBody())
                    .as("composite status должен быть DOWN")
                    .contains("\"status\":\"DOWN\"")
                    .as("rabbit indicator должен сообщать о своей деградации")
                    .contains("\"rabbit\"")
                    .containsPattern("\"rabbit\"\\s*:\\s*\\{[^}]*\"status\"\\s*:\\s*\"DOWN\"");
        });
    }
}
