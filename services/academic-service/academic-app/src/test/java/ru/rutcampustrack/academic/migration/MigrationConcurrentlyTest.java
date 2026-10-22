package ru.rutcampustrack.academic.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * M13 G21 — guard для CREATE INDEX в Flyway-миграциях.
 *
 * <p>На prod-таблицах (users / groups / lessons / homeworks / etc.) plain {@code CREATE INDEX}
 * блокирует таблицу на время index build → downtime. Online-индексы существующих таблиц в новых
 * миграциях после baseline должны использовать {@code CONCURRENTLY}. Индексы новых таблиц остаются
 * в транзакции создания таблицы.
 *
 * <p>CONCURRENTLY требует single-statement миграцию (не работает в transaction). Flyway по
 * умолчанию оборачивает каждый файл в transaction; отключить нужно через companion {@code
 * .sql.conf} с {@code executeInTransaction=false} у отдельной миграции индекса.
 *
 * <p>Cutoff: existing миграции до V{@value #BASELINE_CUTOFF} grandfathered (нельзя редактировать
 * applied миграции, см. CLAUDE.md). Новые V{N+1}+ — проверяются.
 */
class MigrationConcurrentlyTest {

    /**
     * Последняя миграция перед M13 G21. Все V{N} ≤ cutoff — grandfathered.
     *
     * <p>M16 G6 hotfix #3 (2026-04-27): bumped 18→20. V19 (audit_log table) + V20 (initial indexes)
     * grandfathered как «empty-table CREATE INDEX без CONCURRENTLY». На пустой таблице plain CREATE
     * INDEX берёт lock на 0 строк = instant, без downtime. Flyway 10.20.1 имел регрессию (issue
     * #3961) — CONCURRENTLY вешал pg_advisory_lock, блокируя всё IT (locally + CI). Future
     * migrations на заполненной audit_log должны использовать CONCURRENTLY +
     * executeInTransaction=false.
     *
     * <p>2026-04-28: bumped 20→22. V21 (CREATE COLLATION ru_icu — no INDEX) + V22 (CREATE INDEX
     * idx_users_lastname_icu без CONCURRENTLY). Изначально V22 была написана с CONCURRENTLY +
     * companion .sql.conf (executeInTransaction=false), но Flyway 10.20.1 issue #3961 повесил
     * Spring context при первом старте в e2e (контейнер unhealthy через 3 минуты,
     * dependency_failed_to_start). Откатили на plain CREATE INDEX по тому же паттерну, что V20
     * audit_log: • в текущем prod (~300 строк users) write lock = миллисекунды, • после апгрейда
     * Flyway до ≥10.21 (issue #3961 закрыт) можно будет выпустить V{N}__reindex_users_concurrently
     * если объём вырастет.
     */
    private static final int BASELINE_CUTOFF = 22;

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    private static final Pattern CREATE_INDEX =
            Pattern.compile(
                    "create\\s+(?:unique\\s+)?index\\s+(concurrently\\s+)?(?:if\\s+not\\s+exists\\s+)?(\\w+)\\s+on\\s+(\\w+)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern CREATE_INDEX_DECLARATION =
            Pattern.compile("create\\s+(?:unique\\s+)?index\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CREATE_TABLE =
            Pattern.compile(
                    "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(\\w+)",
                    Pattern.CASE_INSENSITIVE);

    // Exact immutable applied migrations, never a version-wide exemption:
    // V31 installs code columns/backfill/uniqueness together; V32 replaces
    // all-history uniqueness with active-only uniqueness; V33 fails closed
    // on duplicates before installing the one-headman authority; V34/V40/
    // V49 install lifecycle/receipt/adoption identity columns and guards;
    // V54 installs activation_scheduled with all legacy values FALSE.
    private static final Map<String, Integer> ATOMIC_INDEXES =
            Map.of(
                    "groups_code_pair_uq", 31,
                    "idx_groups_registry_code", 31,
                    "headman_assistants_active_group_student_uq", 32,
                    "user_role_grants_active_headman_group_uq", 33,
                    "idx_assignments_lifecycle_group", 34,
                    "lesson_transfer_receipts_source_event_uq", 40,
                    "assignment_adoption_target_uq", 49,
                    "semesters_scheduled_activation_due", 54);

    // Historical covering index, not an atomic schema invariant. Applied V27
    // bytes cannot be rewritten. Its write-lock cost on a live upgrade remains
    // operational debt; this exception gives no permission to future indexes.
    private static final Map<String, Integer> HISTORICAL_ONLINE_INDEXES =
            Map.of("idx_sgh_group_user_joined", 27);

    private static final Pattern VERSION_PREFIX = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    @DisplayName(
            "Online-индексы требуют CONCURRENTLY; новые таблицы и точные исторические исключения транзакционны")
    void newMigrations_createIndex_mustUseConcurrently() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            List<Path> sqlFiles =
                    files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                            .sorted()
                            .toList();

            assertThat(sqlFiles).isNotEmpty().as("Migration directory должна содержать .sql файлы");

            for (Path file : sqlFiles) {
                String fileName = file.getFileName().toString();
                Matcher versionMatcher = VERSION_PREFIX.matcher(fileName);
                if (!versionMatcher.matches()) {
                    continue;
                }
                int version = Integer.parseInt(versionMatcher.group(1));
                if (version <= BASELINE_CUTOFF) {
                    continue;
                }

                String content = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                assertThat(CREATE_INDEX.matcher(content).results().count())
                        .as(
                                "V%d (%s): every CREATE INDEX must be parsed; review unsupported SQL syntax",
                                version, fileName)
                        .isEqualTo(CREATE_INDEX_DECLARATION.matcher(content).results().count());
                Matcher indexMatcher = CREATE_INDEX.matcher(content);
                while (indexMatcher.find()) {
                    String indexName = indexMatcher.group(2).toLowerCase(java.util.Locale.ROOT);
                    String tableName = indexMatcher.group(3).toLowerCase(java.util.Locale.ROOT);
                    boolean newTable =
                            CREATE_TABLE
                                    .matcher(content)
                                    .results()
                                    .anyMatch(table -> table.group(1).equalsIgnoreCase(tableName));
                    boolean exactException =
                            Integer.valueOf(version).equals(ATOMIC_INDEXES.get(indexName))
                                    || Integer.valueOf(version)
                                            .equals(HISTORICAL_ONLINE_INDEXES.get(indexName));
                    int start = indexMatcher.start();
                    String snippet =
                            content.substring(start, Math.min(start + 80, content.length()));
                    assertThat(indexMatcher.group(1) != null || newTable || exactException)
                            .as(
                                    "V%d (%s) содержит plain CREATE INDEX без CONCURRENTLY: '%s...'. "
                                            + "Online-индекс существующей таблицы вынеси в отдельную миграцию "
                                            + "с CREATE INDEX CONCURRENTLY и companion .sql.conf executeInTransaction=false.",
                                    version, fileName, snippet.replaceAll("\\s+", " "))
                            .isTrue();
                    if (indexMatcher.group(1) != null) {
                        Path config = file.resolveSibling(fileName + ".conf");
                        assertThat(Files.isRegularFile(config))
                                .as("CONCURRENTLY требует companion %s", config)
                                .isTrue();
                        assertThat(Files.readString(config, StandardCharsets.UTF_8))
                                .containsPattern(
                                        "(?m)^\\s*executeInTransaction\\s*=\\s*false\\s*$");
                    }
                }
            }
        }
    }

    /**
     * Strip line ('--') and block ('/* ... *&#47;') SQL comments чтобы "-- CREATE INDEX example" в
     * комментарии не триггерил false-positive.
     */
    private static String stripComments(String sql) {
        String noBlocks = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlocks.replaceAll("--[^\\n]*", " ");
    }
}
