package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 Группа 1 — regression guard для composite indexes (V17, DECISION D2):
 *   idx_tsg_group_semester ON teacher_subject_groups (group_id, semester_id)
 *   idx_hw_group_semester  ON homeworks (group_id, semester_id)
 *
 * <p>Проверяет что hot queries {@code findByGroupIdAndSemesterId} на обеих
 * таблицах выполняются быстрее 50ms на seed-dataset (1800 rows каждая).
 *
 * <p>Seed идемпотентен: id ≥ 900000 и DELETE перед вставкой.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcademicPerformanceIT extends AbstractAcademicIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AcademicPerformanceIT.class);

    private static final int GROUPS = 20;
    private static final int SEMESTERS = 3;
    private static final int SUBJECTS_PER_GROUP = 15;
    private static final int TSG_SLOTS = 30;
    private static final long QUERY_TIME_LIMIT_MS = 50L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TeacherSubjectGroupRepository tsgRepository;
    @Autowired private HomeworkRepository homeworkRepository;

    @BeforeEach
    void seed() {
        // Cleanup в правильном FK-порядке.
        jdbc.update("DELETE FROM homework_completions WHERE homework_id IN "
                + "(SELECT id FROM homeworks WHERE group_id >= 900000)");
        jdbc.update("DELETE FROM homeworks WHERE group_id >= 900000");
        jdbc.update("DELETE FROM teacher_subject_groups WHERE group_id >= 900000");
        jdbc.update("DELETE FROM subjects WHERE group_id >= 900000");
        jdbc.update("DELETE FROM student_group_history WHERE user_id >= 900000");
        jdbc.update("DELETE FROM attendance_thresholds WHERE group_id >= 900000");
        jdbc.update("DELETE FROM headman_assistants WHERE group_id >= 900000");
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id >= 900000");
        jdbc.update("DELETE FROM users WHERE id >= 900000");
        jdbc.update("DELETE FROM groups WHERE id >= 900000");
        jdbc.update("DELETE FROM semesters WHERE id >= 900000");

        // 3 семестра, id 900001..900003 (даты вне возможных prod-семестров).
        jdbc.update("""
                INSERT INTO semesters (id, name, date_from, date_to, is_active, created_at)
                VALUES
                  (900001, 'M05 perf 2024/25 fall', DATE '2024-09-01', DATE '2025-01-31', FALSE, NOW()),
                  (900002, 'M05 perf 2025 spring',   DATE '2025-02-01', DATE '2025-06-30', FALSE, NOW()),
                  (900003, 'M05 perf 2025/26 fall',  DATE '2025-09-01', DATE '2026-01-31', FALSE, NOW())
                ON CONFLICT (id) DO NOTHING
                """);

        // 20 групп.
        jdbc.update("""
                INSERT INTO groups (id, name, is_active, created_at)
                SELECT 900000 + g.go, 'М05-G' || g.go, TRUE, NOW() - INTERVAL '200 days'
                  FROM generate_series(1, ?) AS g(go)
                """, GROUPS);

        // 20 teachers.
        jdbc.update("""
                INSERT INTO users (id, login, last_name, first_name, role, status, created_at, updated_at)
                SELECT
                    900100 + t.to_,
                    'teacher' || (900100 + t.to_),
                    'M05Teacher' || t.to_, 'Имя' || t.to_,
                    'teacher'::user_role, 'active'::account_status,
                    NOW() - INTERVAL '200 days', NOW() - INTERVAL '200 days'
                  FROM generate_series(1, 20) AS t(to_)
                """);

        // subjects: group-scoped, GROUPS × SUBJECTS_PER_GROUP = 300.
        jdbc.update("""
                INSERT INTO subjects (id, name, type, group_id, created_at)
                SELECT
                    900000 + (g.go * 100 + s.so),
                    'M05 subj g' || g.go || ' #' || s.so,
                    'lecture'::subject_type,
                    900000 + g.go,
                    NOW()
                  FROM generate_series(1, ?) AS g(go),
                       generate_series(1, ?) AS s(so)
                """, GROUPS, SUBJECTS_PER_GROUP);

        // TSG: GROUPS × SEMESTERS × TSG_SLOTS = 1800 rows.
        jdbc.update("""
                INSERT INTO teacher_subject_groups (
                    id, teacher_id, subject_id, group_id, semester_id, assigned_at
                )
                SELECT
                    900000 + (g.go * 120 + sem.so * 30 + slot.so),
                    900100 + (1 + (slot.so % 20)),
                    900000 + (g.go * 100 + (1 + (slot.so % 15))),
                    900000 + g.go,
                    900000 + sem.so,
                    NOW() - INTERVAL '200 days'
                  FROM generate_series(1, ?) AS g(go),
                       generate_series(1, ?) AS sem(so),
                       generate_series(1, ?) AS slot(so)
                """, GROUPS, SEMESTERS, TSG_SLOTS);

        // Homeworks: GROUPS × SEMESTERS × SUBJECTS_PER_GROUP × 2 = 1800.
        jdbc.update("""
                INSERT INTO homeworks (
                    id, group_id, subject_id, semester_id,
                    lesson_date, lesson_number,
                    title, description, published_by, created_at, updated_at
                )
                SELECT
                    900000 + (g.go * 120 + (sem.so * 30 + subj.so * 2 + hw.ho)),
                    900000 + g.go,
                    900000 + (g.go * 100 + subj.so),
                    900000 + sem.so,
                    (CURRENT_DATE - INTERVAL '60 days'
                       + ((hw.ho * 30 + subj.so) * INTERVAL '1 day'))::date,
                    1 + (subj.so % 6),
                    'M05 HW g' || g.go || ' s' || sem.so || ' #' || subj.so || '-' || hw.ho,
                    'desc',
                    900100 + (1 + (subj.so % 20)),
                    NOW() - INTERVAL '180 days',
                    NOW() - INTERVAL '180 days'
                  FROM generate_series(1, ?) AS g(go),
                       generate_series(1, ?) AS sem(so),
                       generate_series(1, ?) AS subj(so),
                       generate_series(1, 2) AS hw(ho)
                """, GROUPS, SEMESTERS, SUBJECTS_PER_GROUP);

        jdbc.execute("ANALYZE teacher_subject_groups");
        jdbc.execute("ANALYZE homeworks");
    }

    @Test
    void tsgFindByGroupAndSemesterStaysUnder50ms() {
        Long groupId = 900010L;
        Long semesterId = 900002L;

        tsgRepository.findByGroupIdAndSemesterId(groupId, semesterId); // warm-up

        long[] timings = IntStream.range(0, 5).mapToLong(i -> {
            long start = System.nanoTime();
            var result = tsgRepository.findByGroupIdAndSemesterId(groupId, semesterId);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            assertThat(result).isNotEmpty();
            return elapsed;
        }).toArray();

        long best = LongStream.of(timings).min().orElseThrow();
        log.info("Q2 tsg findBy(group,sem) timings(ms): {} — best={}ms", timings, best);
        assertThat(best)
                .as("tsg findByGroupIdAndSemesterId — regression guard for idx_tsg_group_semester")
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }

    @Test
    void homeworksFindByGroupAndSemesterStaysUnder50ms() {
        Long groupId = 900010L;
        Long semesterId = 900002L;

        homeworkRepository.findByGroupIdAndSemesterId(groupId, semesterId); // warm-up

        long[] timings = IntStream.range(0, 5).mapToLong(i -> {
            long start = System.nanoTime();
            List<Homework> result = homeworkRepository
                    .findByGroupIdAndSemesterId(groupId, semesterId);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            assertThat(result).isNotEmpty();
            return elapsed;
        }).toArray();

        long best = LongStream.of(timings).min().orElseThrow();
        log.info("Q3 hw findBy(group,sem) timings(ms): {} — best={}ms", timings, best);
        assertThat(best)
                .as("hw findByGroupIdAndSemesterId — regression guard for idx_hw_group_semester")
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }
}
