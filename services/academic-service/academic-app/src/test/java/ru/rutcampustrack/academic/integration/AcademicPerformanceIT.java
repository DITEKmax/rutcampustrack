package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Composite-index regression guards on the current Academic assignment and homework repositories.
 * The historical tsg-named test retains its group/semester query shape, 1800-row dataset and 50ms
 * budget on AssignmentRepository, used by AssignmentService.listAssignments since V25.
 *
 * <p>Owned fixture reset isolates the domain rows; Auth allocates the teacher identities.
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
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private HomeworkRepository homeworkRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        // Actual Auth creation must finish outside the Academic transaction and measured queries.
        var admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        Long[] teacherIds =
                IntStream.rangeClosed(1, 20)
                        .mapToObj(
                                i ->
                                        identityFixture
                                                .seedUser(
                                                        new CreateUserRequest(
                                                                "M05Teacher" + i,
                                                                "Имя" + i,
                                                                null,
                                                                UserRole.TEACHER,
                                                                null,
                                                                "M05-PERF-" + i,
                                                                null),
                                                        admin)
                                                .userId())
                        .toArray(Long[]::new);
        // Reset owns teardown; seed canonical types atomically with their parents.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {

                            // 3 семестра, id 900001..900003 (даты вне возможных prod-семестров).
                            jdbc.update(
                                    """
INSERT INTO semesters (id, name, date_from, date_to, is_active, created_at)
VALUES
  (900001, 'M05 perf 2024/25 fall', DATE '2024-09-01', DATE '2025-01-31', FALSE, NOW()),
  (900002, 'M05 perf 2025 spring',   DATE '2025-02-01', DATE '2025-06-30', FALSE, NOW()),
  (900003, 'M05 perf 2025/26 fall',  DATE '2025-09-01', DATE '2026-01-31', FALSE, NOW())
ON CONFLICT (id) DO NOTHING
""");

                            // 20 групп.
                            jdbc.update(
                                    """
INSERT INTO groups (id, name, is_active, created_at)
SELECT 900000 + g.go, 'М05-G' || g.go, TRUE, NOW() - INTERVAL '200 days'
  FROM generate_series(1, ?) AS g(go)
""",
                                    GROUPS);

                            // subjects: group-scoped, GROUPS × SUBJECTS_PER_GROUP = 300.
                            jdbc.update(
                                    """
                                    INSERT INTO subjects (id, name, type, group_id, created_at)
                                    SELECT
                                        900000 + (g.go * 100 + s.so),
                                        'M05 subj g' || g.go || ' #' || s.so,
                                        'lecture'::subject_type,
                                        900000 + g.go,
                                        NOW()
                                      FROM generate_series(1, ?) AS g(go),
                                           generate_series(1, ?) AS s(so)
                                    """,
                                    GROUPS,
                                    SUBJECTS_PER_GROUP);
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type) "
                                            + "SELECT id,type FROM subjects WHERE id >= 900000");

                            // Current assignments: GROUPS × SEMESTERS × TSG_SLOTS = 1800 rows.
                            jdbc.update(
                                    """
INSERT INTO assignments (
    id, teacher_id, subject_id, group_id, semester_id, lesson_type, valid_from, created_at
)
SELECT
    900000 + (g.go * 120 + sem.so * 30 + slot.so),
    teachers.ids[1 + (slot.so % 20)],
    900000 + (g.go * 100 + (1 + (slot.so % 15))),
    900000 + g.go,
    900000 + sem.so,
    'lecture'::subject_type,
    dates.date_from,
    NOW() - INTERVAL '200 days'
  FROM generate_series(1, ?) AS g(go),
       generate_series(1, ?) AS sem(so),
       generate_series(1, ?) AS slot(so)
  CROSS JOIN (SELECT ?::bigint[] AS ids) AS teachers
  CROSS JOIN semesters AS dates
 WHERE dates.id = 900000 + sem.so
""",
                                    GROUPS,
                                    SEMESTERS,
                                    TSG_SLOTS,
                                    new SqlArrayValue("bigint", (Object[]) teacherIds));

                            // Homeworks: GROUPS × SEMESTERS × SUBJECTS_PER_GROUP × 2 = 1800.
                            jdbc.update(
                                    """
INSERT INTO homeworks (
    id, group_id, subject_id, semester_id,
    lesson_date, lesson_number,
    title, description, published_by, created_at, updated_at,
    binding_id, actor_id, request_key, payload_hash, publication_state
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
    teachers.ids[1 + (subj.so % 20)],
    NOW() - INTERVAL '180 days',
    NOW() - INTERVAL '180 days',
    900000 + (g.go * 120 + (sem.so * 30 + subj.so * 2 + hw.ho)),
    teachers.ids[1 + (subj.so % 20)],
    gen_random_uuid(), decode(repeat('00',32),'hex'), 'ACTIVE'
  FROM generate_series(1, ?) AS g(go),
       generate_series(1, ?) AS sem(so),
       generate_series(1, ?) AS subj(so),
       generate_series(1, 2) AS hw(ho)
  CROSS JOIN (SELECT ?::bigint[] AS ids) AS teachers
""",
                                    GROUPS,
                                    SEMESTERS,
                                    SUBJECTS_PER_GROUP,
                                    new SqlArrayValue("bigint", (Object[]) teacherIds));

                            jdbc.execute("ANALYZE assignments");
                            jdbc.execute("ANALYZE homeworks");
                        });
    }

    @Test
    void tsgFindByGroupAndSemesterStaysUnder50ms() {
        Long groupId = 900010L;
        Long semesterId = 900002L;

        assignmentRepository.findByGroupIdAndSemesterId(groupId, semesterId); // warm-up

        long[] timings =
                IntStream.range(0, 5)
                        .mapToLong(
                                i -> {
                                    long start = System.nanoTime();
                                    var result =
                                            assignmentRepository.findByGroupIdAndSemesterId(
                                                    groupId, semesterId);
                                    long elapsed = (System.nanoTime() - start) / 1_000_000L;
                                    assertThat(result)
                                            .extracting(row -> row.getId())
                                            .containsExactlyInAnyOrderElementsOf(
                                                    expectedIds("assignments"));
                                    assertThat(result)
                                            .allMatch(
                                                    row ->
                                                            row.getGroupId().equals(groupId)
                                                                    && row.getSemesterId()
                                                                            .equals(semesterId));
                                    return elapsed;
                                })
                        .toArray();

        assertBoundedPlan("assignments");
        long best = LongStream.of(timings).min().orElseThrow();
        log.info("Q2 assignments findBy(group,sem) timings(ms): {} — best={}ms", timings, best);
        assertThat(best)
                .as(
                        "assignments findByGroupIdAndSemesterId — regression guard for"
                                + " idx_assignments_group_semester")
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }

    @Test
    void homeworksFindByGroupAndSemesterStaysUnder50ms() {
        Long groupId = 900010L;
        Long semesterId = 900002L;

        homeworkRepository.findByGroupIdAndSemesterId(groupId, semesterId); // warm-up

        long[] timings =
                IntStream.range(0, 5)
                        .mapToLong(
                                i -> {
                                    long start = System.nanoTime();
                                    List<Homework> result =
                                            homeworkRepository.findByGroupIdAndSemesterId(
                                                    groupId, semesterId);
                                    long elapsed = (System.nanoTime() - start) / 1_000_000L;
                                    assertThat(result)
                                            .extracting(Homework::getId)
                                            .containsExactlyInAnyOrderElementsOf(
                                                    expectedIds("homeworks"));
                                    assertThat(result)
                                            .allMatch(
                                                    row ->
                                                            row.getGroupId().equals(groupId)
                                                                    && row.getSemesterId()
                                                                            .equals(semesterId));
                                    return elapsed;
                                })
                        .toArray();

        assertBoundedPlan("homeworks");
        long best = LongStream.of(timings).min().orElseThrow();
        log.info("Q3 hw findBy(group,sem) timings(ms): {} — best={}ms", timings, best);
        assertThat(best)
                .as("hw findByGroupIdAndSemesterId — regression guard for idx_hw_group_semester")
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }

    private List<Long> expectedIds(String table) {
        var ids = new java.util.ArrayList<Long>();
        if (table.equals("assignments")) {
            for (long slot = 1; slot <= 30; slot++) ids.add(900000L + 10 * 120 + 2 * 30 + slot);
        } else {
            for (long subject = 1; subject <= 15; subject++)
                for (long homework = 1; homework <= 2; homework++)
                    ids.add(900000L + 10 * 120 + 2 * 30 + subject * 2 + homework);
        }
        return ids;
    }

    private void assertBoundedPlan(String table) {
        String plan =
                jdbc.queryForObject(
                        "EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) SELECT * FROM "
                                + table
                                + " WHERE group_id=? AND semester_id=?",
                        String.class,
                        900010L,
                        900002L);
        try {
            var node =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(plan)
                            .get(0)
                            .path("Plan");
            assertThat(node.path("Actual Rows").asLong()).isEqualTo(30);
            assertThat(work(node)).isLessThanOrEqualTo(120);
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }

    private long work(com.fasterxml.jackson.databind.JsonNode node) {
        long rows =
                node.path("Actual Rows").asLong() + node.path("Rows Removed by Filter").asLong();
        for (var child : node.path("Plans")) rows += work(child);
        return rows;
    }
}
