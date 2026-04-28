package ru.rutcampustrack.academic.migration;

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
 * M13 G21 — guard для CREATE INDEX в Flyway-миграциях.
 *
 * <p>На prod-таблицах (users / groups / lessons / homeworks / etc.) plain
 * {@code CREATE INDEX} блокирует таблицу на время index build → downtime.
 * Правило: все CREATE INDEX **в новых миграциях** (после baseline cutoff)
 * должны использовать {@code CONCURRENTLY}.
 *
 * <p>CONCURRENTLY требует single-statement миграцию (не работает в
 * transaction). Flyway по умолчанию оборачивает каждый файл в transaction;
 * отключить можно через `-- ##` в начале либо вынести индексы в отдельную
 * миграцию.
 *
 * <p>Cutoff: existing миграции до V{@value #BASELINE_CUTOFF} grandfathered
 * (нельзя редактировать applied миграции, см. CLAUDE.md). Новые V{N+1}+ —
 * проверяются.
 */
class MigrationConcurrentlyTest {

    /**
     * Последняя миграция перед M13 G21. Все V{N} ≤ cutoff — grandfathered.
     * Бамп при добавлении новой миграции с CONCURRENTLY (signal что guard
     * caught её — increment cutoff чтобы не проверяли повторно).
     *
     * <p>M16 G6 hotfix #3 (2026-04-27): bumped 18→20. V19 (audit_log table)
     * + V20 (initial indexes) grandfathered как «empty-table CREATE INDEX
     * без CONCURRENTLY». На пустой таблице plain CREATE INDEX берёт
     * lock на 0 строк = instant, без downtime. Flyway 10.20.1 имел
     * регрессию (issue #3961) — CONCURRENTLY вешал pg_advisory_lock,
     * блокируя всё IT (locally + CI). Future migrations на заполненной
     * audit_log должны использовать CONCURRENTLY + executeInTransaction=false.
     *
     * <p>2026-04-28: bumped 20→22. V21 (CREATE COLLATION ru_icu — no INDEX) +
     * V22 (CREATE INDEX CONCURRENTLY idx_users_lastname_icu) — both compliant,
     * grandfathered after passing the guard.
     */
    private static final int BASELINE_CUTOFF = 22;

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
                                    + "Замени на CREATE INDEX CONCURRENTLY IF NOT EXISTS + добавь -- ##  "
                                    + "в начало файла (или вынеси в отдельную миграцию). "
                                    + "См. CLAUDE.md раздел «База данных».",
                                    version, fileName, snippet.replaceAll("\\s+", " "))
                            .isTrue();
                }
            }
        }
    }

    /**
     * Strip line ('--') and block ('/* ... *&#47;') SQL comments чтобы
     * "-- CREATE INDEX example" в комментарии не триггерил false-positive.
     */
    private static String stripComments(String sql) {
        String noBlocks = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlocks.replaceAll("--[^\\n]*", " ");
    }
}
