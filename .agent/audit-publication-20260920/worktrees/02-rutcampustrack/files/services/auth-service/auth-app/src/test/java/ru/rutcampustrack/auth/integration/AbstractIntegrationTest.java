package ru.rutcampustrack.auth.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        // Each auth integration run uses a fresh database and cache so the
        // canonical V1..V24 authority schema cannot collide with older test data.
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass")
                .withReuse(false);
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withReuse(false);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:" + migrationDirectory());
        registry.add("tma.bot-token", () -> "test_bot_token_12345");
        registry.add("tma.auth-date-max-age-seconds", () -> "86400");
    }

    private static Path migrationDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "services/academic-service/academic-app/src/main/resources/db/migration");
            if (Files.isRegularFile(candidate.resolve("V24__auth_session_authority.sql"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("canonical academic V1..V24 migration directory is unavailable");
    }
}
