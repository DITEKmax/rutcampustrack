package ru.rutcampustrack.academic.homework;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

/**
 * Phase 61-02 / D-01, D-02, D-15: подтверждает, что после миграции V13:
 *
 * <ul>
 *   <li>{@code homeworks.lesson_date DATE NOT NULL} и {@code homeworks.lesson_number INT NOT NULL}
 *       существуют;
 *   <li>колонка {@code homeworks.lesson_id} удалена;
 *   <li>индекс {@code idx_hw_lesson} удалён, индекс {@code idx_homeworks_group_date} создан.
 * </ul>
 *
 * Чистая schema-only проверка через {@code information_schema}/{@code pg_indexes} — не создаёт
 * записей и не зависит от остальных тестов.
 */
class HomeworkMigrationIT extends AbstractAcademicIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void lessonDateColumnExistsAndIsNotNull() {
        String isNullable =
                jdbcTemplate.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_name = 'homeworks' AND column_name = 'lesson_date'",
                        String.class);
        assertThat(isNullable).isEqualTo("NO");
    }

    @Test
    void lessonNumberColumnSupportsAcceptedDateOnlyMode() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        for (Object[] accepted : new Object[][] {{"DATE", null}, {"LESSON", 1}, {"LESSON", 8}}) {
            tx.executeWithoutResult(
                    status -> {
                        long id =
                                insertHomework(
                                        (String) accepted[0],
                                        (Integer) accepted[1],
                                        java.util.UUID.randomUUID());
                        var row =
                                jdbcTemplate.queryForMap(
                                        "SELECT binding_mode,lesson_number FROM homeworks WHERE"
                                            + " id=?",
                                        id);
                        assertThat(row.get("binding_mode")).isEqualTo(accepted[0]);
                        assertThat(row.get("lesson_number")).isEqualTo(accepted[1]);
                        status.setRollbackOnly();
                    });
        }
        for (Object[] rejected :
                new Object[][] {
                    {"DATE", 1}, {"LESSON", null}, {"LESSON", 0}, {"LESSON", 9}, {"OTHER", 1}
                }) {
            var key = java.util.UUID.randomUUID();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    tx.executeWithoutResult(
                                            status ->
                                                    insertHomework(
                                                            (String) rejected[0],
                                                            (Integer) rejected[1],
                                                            key)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                    .satisfies(
                            error -> {
                                var sql = (org.postgresql.util.PSQLException) error.getCause();
                                assertThat(sql.getSQLState()).isEqualTo("23514");
                                assertThat(sql.getServerErrorMessage().getConstraint())
                                        .isEqualTo("homeworks_binding_mode_chk");
                            });
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM homeworks WHERE request_key=?",
                                    Long.class,
                                    key))
                    .isZero();
        }
    }

    private long insertHomework(String mode, Integer number, java.util.UUID key) {
        var canonical = identityFixture.canonical();
        Long subject =
                jdbcTemplate.queryForObject(
                        "INSERT INTO subjects(name,type,group_id,semester_id) VALUES('binding"
                            + " constraint','lecture',?,?) RETURNING id",
                        Long.class,
                        canonical.groupId(),
                        canonical.semesterId());
        var day =
                jdbcTemplate.queryForObject(
                        "SELECT date_from FROM semesters WHERE id=?",
                        java.time.LocalDate.class,
                        canonical.semesterId());
        return jdbcTemplate.queryForObject(
                """
INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,
    lesson_number,binding_mode,binding_id,actor_id,request_key,payload_hash,publication_state)
VALUES(?,?,?,'binding constraint',?,?,?,?,?,?,?,?,'ACTIVE') RETURNING id
""",
                Long.class,
                canonical.groupId(),
                subject,
                canonical.semesterId(),
                canonical.teacherId(),
                day,
                number,
                mode,
                Math.abs(key.getMostSignificantBits() / 2) + 1,
                canonical.teacherId(),
                key,
                new byte[32]);
    }

    @Test
    void lessonIdColumnIsRemoved() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_name = 'homeworks' AND column_name = 'lesson_id'",
                        Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void indexIdxHwLessonIsRemoved() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_indexes "
                                + "WHERE tablename = 'homeworks' AND indexname = 'idx_hw_lesson'",
                        Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void indexIdxHomeworksGroupDateExists() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'homeworks' AND"
                            + " indexname = 'idx_homeworks_group_date'",
                        Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
