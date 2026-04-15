package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 61-02 / D-01, D-02, D-15: подтверждает, что после миграции V13:
 * <ul>
 *   <li>{@code homeworks.lesson_date DATE NOT NULL} и {@code homeworks.lesson_number INT NOT NULL} существуют;</li>
 *   <li>колонка {@code homeworks.lesson_id} удалена;</li>
 *   <li>индекс {@code idx_hw_lesson} удалён, индекс {@code idx_homeworks_group_date} создан.</li>
 * </ul>
 *
 * Чистая schema-only проверка через {@code information_schema}/{@code pg_indexes} — не создаёт записей
 * и не зависит от остальных тестов.
 */
class HomeworkMigrationIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void lessonDateColumnExistsAndIsNotNull() {
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns " +
                        "WHERE table_name = 'homeworks' AND column_name = 'lesson_date'",
                String.class);
        assertThat(isNullable).isEqualTo("NO");
    }

    @Test
    void lessonNumberColumnExistsAndIsNotNull() {
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns " +
                        "WHERE table_name = 'homeworks' AND column_name = 'lesson_number'",
                String.class);
        assertThat(isNullable).isEqualTo("NO");
    }

    @Test
    void lessonIdColumnIsRemoved() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name = 'homeworks' AND column_name = 'lesson_id'",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void indexIdxHwLessonIsRemoved() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                        "WHERE tablename = 'homeworks' AND indexname = 'idx_hw_lesson'",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void indexIdxHomeworksGroupDateExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                        "WHERE tablename = 'homeworks' AND indexname = 'idx_homeworks_group_date'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
