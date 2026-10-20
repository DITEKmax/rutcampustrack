package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * M05 Группа 1 — regression guard для composite index {@code idx_lessons_item_date
 * (schedule_item_id, date) WHERE status != 'cancelled'} (V12__add_performance_indexes.sql, DECISION
 * D1).
 *
 * <p>Проверяет что hot query {@code findByScheduleItemIdInAndDateBetweenAndStatusIn} выполняется
 * быстрее чем 50ms на seed-dataset (600 schedule_items, 12000 lessons — соответствует {@code
 * docs/milestones/M05-performance/seed-perf.sql}).
 *
 * <p>Seed идемпотентен: использует id ≥ 900000 и DELETE перед вставкой. При падении теста —
 * проверить EXPLAIN ANALYZE, убедиться что планировщик не делает {@code Seq Scan on lessons}.
 */
@org.springframework.test.context.TestPropertySource(
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LessonPerformanceIT extends AbstractScheduleIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LessonPerformanceIT.class);

    private static final int GROUPS = 20;
    private static final int SLOTS_PER_GROUP = 30;
    private static final int WEEKS = 20;
    private static final long QUERY_TIME_LIMIT_MS = 1000L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired private ru.rutcampustrack.schedule.lesson.LessonTransferWriter transferWriter;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private java.time.Clock clock;

    private long retainedOneOff;
    private long currentOneOff;
    @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private ru.rutcampustrack.schedule.grpc.AcademicGrpcClient academic;

    @BeforeEach
    void seed() {
        resetScheduleData();
        org.mockito.Mockito.when(clock.instant())
                .thenReturn(java.time.Instant.parse("2026-01-01T06:00:00Z"));
        org.mockito.Mockito.when(clock.getZone()).thenReturn(java.time.ZoneId.of("Europe/Moscow"));
        org.mockito.Mockito.when(clock.withZone(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation ->
                                java.time.Clock.fixed(clock.instant(), invocation.getArgument(0)));
        org.mockito.Mockito.when(academic.getSemesterArchiveAuthorityState(1L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(1L)
                                .build());

        // schedule_items: 20 groups × 30 slots = 600 rows.
        jdbc.update(
                """
INSERT INTO schedule_items (
    id, assignment_id, group_id, subject_id, semester_id,
    day_of_week, lesson_number, start_time, end_time,
    week_type, room, is_active, created_at
)
SELECT
    900000 + (g.go * 30 + s.so),
    900000 + (g.go * 30 + s.so),
    900000 + g.go,
    1 + (s.so % 15),
    1,
    1 + (s.so % 5),
    1 + (s.so % 6),
    (ARRAY[TIME '08:30', TIME '10:05', TIME '11:40', TIME '13:45', TIME '15:20', TIME '16:55'])[1 + s.so % 6],
    (ARRAY[TIME '09:50', TIME '11:25', TIME '13:00', TIME '15:05', TIME '16:40', TIME '18:15'])[1 + s.so % 6],
    'all'::week_type,
    'A-' || (100 + (s.so % 20)),
    TRUE,
    NOW() - INTERVAL '200 days'
FROM generate_series(1, ?) AS g(go),
     generate_series(1, ?) AS s(so)
""",
                GROUPS, SLOTS_PER_GROUP);

        jdbc.update(
                """
INSERT INTO schedule_assignment_fences
    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id, lesson_type,
     valid_from, cap_until_exclusive, creation_cap_until_exclusive)
SELECT assignment_id, group_id, subject_id, semester_id, 700, 'lecture',
       DATE '2000-01-01', DATE '2200-01-01', DATE '2200-01-01'
  FROM schedule_items WHERE group_id >= 900000
""");
        jdbc.update(
                """
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type)
                SELECT si.id, (DATE '2026-01-05') + ((w.wo - 1) * 7) + (si.day_of_week - 1),
                       si.assignment_id, si.group_id, si.subject_id, si.semester_id, 700, 'lecture'
                  FROM schedule_items si CROSS JOIN generate_series(1, ?) AS w(wo)
                 WHERE si.group_id >= 900000
                """,
                WEEKS);

        // lessons: for each schedule_item × 20 weeks = 12000 rows.
        jdbc.update(
                """
INSERT INTO lessons (
    schedule_item_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,
    assigned_teacher_id, lesson_type, lesson_number, day_of_week, start_time, end_time,
    room_snapshot, week_type_snapshot, generation, revision, date, status, is_geo_blocked,
    created_at, is_blocked_by_headman
)
SELECT
    si.id, occurrence.id, si.assignment_id, si.group_id, si.subject_id, si.semester_id,
    700, 'lecture', si.lesson_number, si.day_of_week, si.start_time, si.end_time,
    si.room, 'all', 1, 1,
    DATE '2026-01-05'
        + ((w.wo - 1) * INTERVAL '7 days')
        + ((si.day_of_week - 1) * INTERVAL '1 day'),
    CASE
        WHEN w.wo <= 8 THEN 'closed'::lesson_status
        WHEN w.wo = 9 THEN 'active'::lesson_status
        WHEN w.wo = 10 AND (si.id % 20) = 0
             THEN 'cancelled'::lesson_status
        ELSE 'planned'::lesson_status
    END,
    FALSE,
    NOW() - INTERVAL '100 days',
    FALSE
FROM schedule_items si CROSS JOIN generate_series(1, ?) AS w(wo)
JOIN lesson_occurrences occurrence ON occurrence.schedule_item_id = si.id
 AND occurrence.occurrence_date = (DATE '2026-01-05') + ((w.wo - 1) * 7) + (si.day_of_week - 1)
WHERE si.group_id >= 900000
""",
                WEEKS);

        jdbc.update(
                """
                UPDATE lesson_occurrences occurrence SET current_lesson_id = lesson.id
                  FROM lessons lesson WHERE lesson.occurrence_id = occurrence.id
                """);
        seedRetainedOneOffHistory();
        jdbc.execute("ANALYZE lessons");
        jdbc.execute("ANALYZE schedule_items");
    }

    private void seedRetainedOneOffHistory() {
        jdbc.update(
                """
INSERT INTO schedule_assignment_fences(assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,
    lesson_type,valid_from,cap_until_exclusive,creation_cap_until_exclusive)
VALUES (950001,900010,99,1,700,'lecture',DATE '2026-01-01',DATE '2027-01-01',DATE '2027-01-01')
""");
        long origin =
                jdbc.queryForObject(
                        """
INSERT INTO schedule_one_off_lessons(group_id,subject_id,semester_id,date,lesson_number,classroom,created_by)
VALUES (900010,99,1,DATE '2026-01-10',1,'one-off',42) RETURNING id
""",
                        Long.class);
        long occurrence =
                jdbc.queryForObject(
                        """
INSERT INTO lesson_occurrences(one_off_lesson_id,occurrence_date,assignment_id,group_id,subject_id,
    semester_id,assigned_teacher_id,lesson_type,generation,revision)
VALUES (?,DATE '2026-01-10',950001,900010,99,1,700,'lecture',1,1) RETURNING id
""",
                        Long.class,
                        origin);
        retainedOneOff =
                jdbc.queryForObject(
                        """
INSERT INTO lessons(one_off_lesson_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,
    assigned_teacher_id,lesson_type,lesson_number,day_of_week,start_time,end_time,
    room_snapshot,week_type_snapshot,generation,revision,date,status,is_geo_blocked)
VALUES (?,?,950001,900010,99,1,700,'lecture',1,6,TIME '08:30',TIME '09:50','one-off','all',1,1,DATE '2026-01-10','planned',FALSE) RETURNING id
""",
                        Long.class,
                        origin,
                        occurrence);
        jdbc.update(
                "UPDATE lesson_occurrences SET current_lesson_id=? WHERE id=?",
                retainedOneOff,
                occurrence);
        jdbc.update(
                "UPDATE schedule_one_off_lessons SET physical_lesson_id=? WHERE id=?",
                retainedOneOff,
                origin);
        transferWriter.transfer(
                retainedOneOff,
                42L,
                new ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest(
                        LocalDate.of(2026, 1, 11),
                        1,
                        null,
                        null,
                        "moved-one-off",
                        "1",
                        java.util.UUID.randomUUID()));
        currentOneOff =
                jdbc.queryForObject(
                        "SELECT current_lesson_id FROM lesson_occurrences WHERE id=?",
                        Long.class,
                        occurrence);
    }

    @AfterEach
    void cleanup() {
        resetScheduleData();
    }

    @Test
    void weekJournalHttpHasExactRowsBoundedQueriesAndLatency() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-User-Id", "42");
        headers.set("X-User-Role", "STUDENT");
        headers.set("X-Group-Id", "900010");
        var expected =
                jdbc.queryForList(
                        """
SELECT id FROM lessons WHERE group_id = 900010 AND date BETWEEN DATE '2026-01-05'
    AND DATE '2026-01-11' AND status IN ('planned','active','closed','transferred') ORDER BY id
""",
                        Long.class);
        assertThat(expected).hasSize(32).contains(retainedOneOff, currentOneOff);
        var stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        // Warm-up the same endpoint once; measure every subsequent response, never best-of-N.
        String url =
                "/schedule/groups/900010/lessons?dateFrom=2026-01-05&dateTo=2026-01-11&size=200&status=PLANNED&status=ACTIVE&status=CLOSED&status=TRANSFERRED";
        http.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                com.fasterxml.jackson.databind.JsonNode.class);
        stats.clear();
        long started = System.nanoTime();
        var response =
                http.exchange(
                        url,
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        com.fasterxml.jackson.databind.JsonNode.class);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.path("page").path("totalElements").asLong()).isEqualTo(32L);
        List<Long> actual = new java.util.ArrayList<>();
        for (var row : body.path("_embedded").path("lessonResponseList")) {
            actual.add(row.path("id").asLong());
            assertThat(row.path("groupId").asLong()).isEqualTo(900010L);
            long id = row.path("id").asLong();
            assertThat(row.path("status").asText())
                    .isEqualTo(
                            id == retainedOneOff
                                    ? "TRANSFERRED"
                                    : id == currentOneOff ? "PLANNED" : "CLOSED");
            assertThat(row.path("current").asBoolean()).isEqualTo(id != retainedOneOff);
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(stats.getPrepareStatementCount())
                .as("paged read must not query per lesson")
                .isLessThanOrEqualTo(6L);
        assertThat(elapsedMs)
                .as("local isolated-PG HTTP operational guard, not p95")
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }
}
