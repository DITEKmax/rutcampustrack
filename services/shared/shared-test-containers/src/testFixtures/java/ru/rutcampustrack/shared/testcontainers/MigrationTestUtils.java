package ru.rutcampustrack.shared.testcontainers;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import javax.sql.DataSource;

/**
 * Утилита для поэтапного прогона Flyway-миграций (P2-8/3): позволяет data-preservation тестам
 * зафиксировать промежуточное состояние схемы, залить туда данные и затем накатить оставшиеся
 * миграции — проверяя, что пользовательские данные не теряются.
 *
 * <p>Используется в M07 и в тестах миграций.
 */
public final class MigrationTestUtils {

    private MigrationTestUtils() {}

    /**
     * Constant material only for private, non-reused test containers; never application
     * configuration.
     */
    public static final String ACADEMIC_AUTH_FIXTURE_PASSWORD = "rct_owned_fixture_only";

    /**
     * Bootstrap the approved role contract before latest Academic migrations, using the owned
     * container administrator.
     */
    public static void prepareAcademicAuthPrincipals(
            org.testcontainers.containers.PostgreSQLContainer<?> owner) {
        if (!owner.isRunning() || owner.isShouldBeReused())
            throw new IllegalStateException(
                    "Auth role bootstrap requires a running non-reused owned PostgreSQL");
        String database = owner.getDatabaseName();
        if (!database.matches("[A-Za-z0-9_]+"))
            throw new IllegalStateException("Unexpected owned database name");
        try (var connection =
                        java.sql.DriverManager.getConnection(
                                owner.getJdbcUrl(), owner.getUsername(), owner.getPassword());
                var statement = connection.createStatement()) {
            try (var result =
                    statement.executeQuery("SELECT current_setting('is_superuser')='on'")) {
                if (!result.next() || !result.getBoolean(1))
                    throw new IllegalStateException(
                            "Owned role bootstrap requires the container administrator");
            }
            for (String role :
                    java.util.List.of(
                            "rct_migrator",
                            "rct_auth_runtime",
                            "rct_academic_runtime",
                            "rct_runtime",
                            "rct_auth_command_owner")) {
                boolean exists;
                try (var query =
                        connection.prepareStatement(
                                "SELECT EXISTS(SELECT FROM pg_roles WHERE rolname=?)")) {
                    query.setString(1, role);
                    try (var result = query.executeQuery()) {
                        result.next();
                        exists = result.getBoolean(1);
                    }
                }
                if (!exists)
                    statement.execute(
                            "CREATE ROLE "
                                    + role
                                    + (role.equals("rct_auth_command_owner")
                                            ? " NOLOGIN"
                                            : " LOGIN PASSWORD '"
                                                    + ACADEMIC_AUTH_FIXTURE_PASSWORD
                                                    + "'")
                                    + " NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION"
                                    + " NOBYPASSRLS");
            }
            statement.execute("GRANT rct_auth_command_owner TO rct_migrator");
            statement.execute(
                    "GRANT CONNECT,CREATE ON DATABASE \"" + database + "\" TO rct_migrator");
            statement.execute("GRANT USAGE,CREATE ON SCHEMA public TO rct_migrator");
        } catch (java.sql.SQLException failure) {
            // Do not attach JDBC connection/password/statement diagnostics to the published test
            // failure.
            throw new IllegalStateException("Owned Auth principal bootstrap unavailable");
        }
    }

    /**
     * Накатывает миграции до указанной версии включительно.
     *
     * @param dataSource целевой DataSource (обычно от {@code PostgreSQLContainer.getJdbcUrl}).
     * @param version версия в формате {@code "N"} или {@code "N.M"} (Flyway semantics).
     */
    public static void runMigrationsUpTo(DataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion(version))
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /** Накатывает все миграции. */
    public static void runAllMigrations(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /** Очищает схему (для data-preservation тестов между прогонами). */
    public static void clean(DataSource dataSource) {
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
    }
}
