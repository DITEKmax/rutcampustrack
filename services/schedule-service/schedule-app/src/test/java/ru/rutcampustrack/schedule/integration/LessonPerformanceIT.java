package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LessonPerformanceIT extends AbstractScheduleIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LessonPerformanceIT.class);

    private static final int GROUPS = 20;
    private static final int SLOTS_PER_GROUP = 30;
    private static final int WEEKS = 20;
    private static final long QUERY_TIME_LIMIT_MS = 50L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LessonRepository lessonRepository;

    @BeforeEach
    void seed() {
        resetScheduleData();

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
                SELECT si.id, (CURRENT_DATE - 70) + ((w.wo - 1) * 7) + (si.day_of_week - 1),
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
                    (CURRENT_DATE - INTERVAL '70 days')
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
                 AND occurrence.occurrence_date = (CURRENT_DATE - 70) + ((w.wo - 1) * 7) + (si.day_of_week - 1)
                WHERE si.group_id >= 900000
                """,
                WEEKS);

        jdbc.update(
                """
                UPDATE lesson_occurrences occurrence SET current_lesson_id = lesson.id
                  FROM lessons lesson WHERE lesson.occurrence_id = occurrence.id
                """);
        jdbc.execute("ANALYZE lessons");
        jdbc.execute("ANALYZE schedule_items");
    }

    @AfterEach
    void cleanup() {
        resetScheduleData();
    }

    @Test
    void weekJournalQueryStaysUnder50ms() {
        // Emulates LessonService.getLessonsForGroup(groupId, from, to).
        Long targetGroupId = 900000L + 10;
        List<Long> scheduleItemIds =
                jdbc.queryForList(
                        """
                SELECT id FROM schedule_items
                 WHERE group_id = ? AND semester_id = 1 AND is_active = TRUE
                """,
                        Long.class,
                        targetGroupId);

        assertThat(scheduleItemIds).as("schedule_items for target group").hasSize(SLOTS_PER_GROUP);

        LocalDate from = LocalDate.now().minusDays(70);
        LocalDate to = from.plusDays(7);
        List<String> statuses = List.of("planned", "active", "closed");

        // Warm-up (buffer cache, JIT).
        lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn(
                scheduleItemIds, from, to, statuses);

        // Measure across 5 runs → use best time (regression-guard semantics).
        long[] timings =
                IntStream.range(0, 5)
                        .mapToLong(
                                i -> {
                                    long start = System.nanoTime();
                                    List<Lesson> result =
                                            lessonRepository
                                                    .findByScheduleItemIdInAndDateBetweenAndStatusIn(
                                                            scheduleItemIds, from, to, statuses);
                                    long elapsed = (System.nanoTime() - start) / 1_000_000L;
                                    assertThat(result).as("week lessons").isNotEmpty();
                                    return elapsed;
                                })
                        .toArray();

        long bestMs = LongStream.of(timings).min().orElseThrow();
        log.info(
                "Q1 week-journal timings(ms): {} — best={}ms, limit={}ms",
                timings,
                bestMs,
                QUERY_TIME_LIMIT_MS);

        assertThat(bestMs)
                .as(
                        "week-journal query should complete under %dms "
                                + "(regression guard for idx_lessons_item_date)",
                        QUERY_TIME_LIMIT_MS)
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }
}
