package ru.rutcampustrack.schedule.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 G21 — guard для CREATE INDEX в Flyway-миграциях schedule-service.
 *
 * <p>См. {@code MigrationConcurrentlyTest} в academic-service для полного
 * описания. Cutoff отдельный per service (схемы независимы):
 * {@value #BASELINE_CUTOFF} = последняя миграция schedule-app перед M13 G21.
 */
class MigrationConcurrentlyTest {

    /**
     * Последняя миграция schedule-app перед M13 G21. Все V{N} ≤ cutoff —
     * grandfathered. Cutoff фиксирован; новые online-индексы существующих
     * таблиц требуют CREATE INDEX CONCURRENTLY и companion .sql.conf с
     * executeInTransaction=false (SQL-комментарий не является Flyway directive).
     */
    private static final int BASELINE_CUTOFF = 14;

    private static final Path MIGRATION_DIR =
            Path.of("src/main/resources/db/migration");

    private static final Pattern CREATE_INDEX = Pattern.compile(
            "create\\s+(?:unique\\s+)?index\\s+(concurrently\\s+)?(?:if\\s+not\\s+exists\\s+)?(\\w+)\\s+on\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    // Accepted transactional identity migrations must install columns, guards
    // and their supporting indexes atomically. V17 refuses populated legacy
    // tables; V20/V21 install unique lifecycle authority; V24 adds placement
    // columns and validates all bindings in the same transaction. Never turn
    // these applied migrations into nontransactional online index builds.
    private static final Map<String, Integer> ATOMIC_INDEXES = Map.of(
            "uq_lessons_included_physical_slot", 17,
            "uq_lessons_included_occurrence", 17,
            "idx_lessons_occurrence_history", 17,
            "uq_lesson_lifecycle_restore_operation", 20,
            "uq_lesson_lifecycle_transfer_operation", 21,
            "homework_binding_semester_pending_idx", 24,
            "homework_binding_date_deadline_idx", 24);

    private static final Pattern VERSION_PREFIX = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    @DisplayName("Online indexes require CONCURRENTLY; new tables and exact atomic identity migrations stay transactional")
    void newMigrations_createIndex_mustUseConcurrently() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            List<Path> sqlFiles = files
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();

            assertThat(sqlFiles).isNotEmpty()
                    .as("Migration directory должна содержать .sql файлы");

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
                Matcher indexMatcher = CREATE_INDEX.matcher(content);
                while (indexMatcher.find()) {
                    String indexName = indexMatcher.group(2).toLowerCase(java.util.Locale.ROOT);
                    String tableName = indexMatcher.group(3).toLowerCase(java.util.Locale.ROOT);
                    boolean newTable = CREATE_TABLE.matcher(content).results()
                            .anyMatch(table -> table.group(1).equalsIgnoreCase(tableName));
                    boolean exactAtomicIndex = Integer.valueOf(version).equals(ATOMIC_INDEXES.get(indexName));
                    int start = indexMatcher.start();
                    String snippet = content.substring(start, Math.min(start + 80, content.length()));
                    assertThat(indexMatcher.group(1) != null || newTable || exactAtomicIndex)
                            .as("V%d (%s) содержит plain CREATE INDEX без CONCURRENTLY: '%s...'. "
                                    + "Для online-индекса существующей таблицы используй отдельную "
                                    + "нетранзакционную миграцию с CREATE INDEX CONCURRENTLY.",
                                    version, fileName, snippet.replaceAll("\\s+", " "))
                            .isTrue();
                }
            }
        }
    }

    private static String stripComments(String sql) {
        String noBlocks = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlocks.replaceAll("--[^\\n]*", " ");
    }
}
