package ru.rutcampustrack.schedule.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
     * grandfathered. Бамп при добавлении новой миграции с CONCURRENTLY.
     */
    private static final int BASELINE_CUTOFF = 14;

    private static final Path MIGRATION_DIR =
            Path.of("src/main/resources/db/migration");

    private static final Pattern CREATE_INDEX = Pattern.compile(
            "create\\s+(?:unique\\s+)?index\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONCURRENTLY = Pattern.compile(
            "create\\s+(?:unique\\s+)?index\\s+concurrently\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VERSION_PREFIX = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    @DisplayName("Новые миграции (после baseline cutoff) с CREATE INDEX должны использовать CONCURRENTLY")
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
                    int start = indexMatcher.start();
                    String snippet = content.substring(start, Math.min(start + 80, content.length()));
                    assertThat(CONCURRENTLY.matcher(snippet).find())
                            .as("V%d (%s) содержит plain CREATE INDEX без CONCURRENTLY: '%s...'. "
                                    + "Замени на CREATE INDEX CONCURRENTLY IF NOT EXISTS + добавь -- ## "
                                    + "в начало файла (или вынеси в отдельную миграцию). "
                                    + "См. CLAUDE.md раздел «База данных».",
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
