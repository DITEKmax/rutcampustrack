package ru.rutcampustrack.academic.migration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

/** Bootstrap only for an explicitly supplied, running disposable PostgreSQL test container. */
public final class OwnedAuthPrincipals {
    private static final String PASSWORD =
            ru.rutcampustrack.shared.testcontainers.MigrationTestUtils
                    .ACADEMIC_AUTH_FIXTURE_PASSWORD;
    private static final List<String> LOGINS =
            List.of("rct_migrator", "rct_auth_runtime", "rct_academic_runtime", "rct_runtime");

    private OwnedAuthPrincipals() {}

    public static void prepare(PostgreSQLContainer<?> owner) {
        ru.rutcampustrack.shared.testcontainers.MigrationTestUtils.prepareAcademicAuthPrincipals(
                owner);
    }

    public static DriverManagerDataSource source(PostgreSQLContainer<?> owner, String role) {
        if (!LOGINS.contains(role) || !owner.isRunning())
            throw new IllegalArgumentException("Owned fixture principal required");
        return new DriverManagerDataSource(owner.getJdbcUrl(), role, PASSWORD);
    }

    /**
     * Flyway clean's public-schema default does not include the separately owned command schema.
     */
    public static void cleanCommands(JdbcTemplate administrator) {
        administrator.execute("DROP SCHEMA IF EXISTS auth_api CASCADE");
    }
}
