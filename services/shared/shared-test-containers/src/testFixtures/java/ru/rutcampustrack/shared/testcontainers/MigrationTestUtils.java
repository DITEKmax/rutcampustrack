package ru.rutcampustrack.shared.testcontainers;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import javax.sql.DataSource;

/**
 * Утилита для поэтапного прогона Flyway-миграций (P2-8/3): позволяет
 * data-preservation тестам зафиксировать промежуточное состояние схемы,
 * залить туда данные и затем накатить оставшиеся миграции — проверяя,
 * что пользовательские данные не теряются.
 *
 * <p>Используется в M07 и в тестах миграций.
 */
public final class MigrationTestUtils {

    private MigrationTestUtils() {
    }

    /**
     * Накатывает миграции до указанной версии включительно.
     *
     * @param dataSource целевой DataSource (обычно от {@code PostgreSQLContainer.getJdbcUrl}).
     * @param version    версия в формате {@code "N"} или {@code "N.M"} (Flyway semantics).
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
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
    }
}
