package ru.rutcampustrack.auth.integration;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.auth.support.AuthDatabaseFixture;

import java.nio.file.Path;
import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        // Auth migrations and bootstrap fixtures use an owner connection; Boot runtime
        // uses a distinct principal connected only to auth_db. No Academic schema exists.
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("auth_db")
                        .withUsername("rct_user")
                        .withPassword("rct_dev_pass")
                        .withReuse(false);
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .load()
                .migrate();
        var owner =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        AuthDatabaseFixture.runtimePrincipal(owner);
        seedIdentity(owner, "admin", "admin", null);
        seedIdentity(owner, "teacher", "teacher", null);
        seedIdentity(owner, "student", "student", 1L);

        REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(false);
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "rct_auth_runtime");
        registry.add("spring.datasource.password", () -> AuthDatabaseFixture.RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:" + migrationDirectory());
        registry.add("tma.bot-token", () -> "test_bot_token_12345");
        registry.add("tma.auth-date-max-age-seconds", () -> "86400");
    }

    /** DDL fault injection belongs to this fresh synthetic fixture's owner, never runtime. */
    protected static JdbcTemplate authFixtureOwnerJdbc() {
        return new JdbcTemplate(
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static Path migrationDirectory() {
        return AuthDatabaseFixture.migrations();
    }

    private static void seedIdentity(JdbcTemplate jdbc, String login, String role, Long groupId) {
        long id =
                AuthDatabaseFixture.identity(
                        jdbc,
                        login,
                        Instant.now(),
                        "$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi",
                        "password");
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                        + " (?,?,'active',?)",
                id,
                role,
                groupId);
        if ("student".equals(role))
            jdbc.update(
                    "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                            + " (?,'headman','active',?)",
                    id,
                    groupId);
        if ("admin".equals(role))
            jdbc.update(
                    "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)"
                            + " VALUES (?,TRUE,'FULL','{}',1)",
                    id);
    }
}
