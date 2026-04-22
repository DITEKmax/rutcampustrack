package ru.rutcampustrack.schedule.lesson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for LessonStatusTransitionJob cron transitions.
 *
 * Tests cover:
 * - CRON-01 / EVNT-01: planned->active when start_time is reached
 * - CRON-02 / EVNT-02: active->closed after end_time + 5 min grace period
 * - CRON-03: catch-up for all past-due lessons on restart (not just today's)
 * - Negative cases: no premature transitions, no change to cancelled lessons
 *
 * IMPORTANT: Tests are NOT @Transactional. Using @Transactional would cause rollback
 * instead of commit, so the @TransactionalEventListener(AFTER_COMMIT) in DomainEventListener
 * would never fire. Manual @AfterEach cleanup is used instead.
 */
class LessonStatusTransitionJobIT extends AbstractScheduleIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    LessonStatusTransitionJob job;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @MockitoBean
    Clock clock;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @BeforeEach
    void configureClock() {
        // Default: Moscow 2026-04-03 10:00 (after a typical 08:30 start, before 10:05+5 end)
        Instant fixedInstant = ZonedDateTime.of(2026, 4, 3, 10, 0, 0, 0, MOSCOW).toInstant();
        when(clock.getZone()).thenReturn(MOSCOW);
        when(clock.instant()).thenReturn(fixedInstant);
    }

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        drainOutbox();
    }

    // =========================================================================
    // CRON-01 / EVNT-01: planned -> active when start_time reached
    // =========================================================================

    @Test
    void transitionsPlannedToActive() {
        // ScheduleItem: startTime=08:30, endTime=10:05 (not yet past end_time + 5min at 10:00)
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.PLANNED);

        // Clock at 10:00 — after 08:30 start, before 10:10 closure deadline
        setClockTo(2026, 4, 3, 10, 0);

        job.runTransitions();

        Lesson after = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(LessonStatus.ACTIVE);

        // Verify lesson.started был записан в outbox (via DomainEventListener BEFORE_COMMIT)
        assertThat(outboxStorage.findPending(10))
                .anyMatch(r -> "lesson.started".equals(r.eventType()));
    }

    // =========================================================================
    // CRON-02 / EVNT-02: active -> closed after end_time + 5min grace
    // =========================================================================

    @Test
    void transitionsActiveToClosedAfterGrace() {
        // ScheduleItem: startTime=08:30, endTime=10:05 → closure at 10:10
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.ACTIVE);

        // Clock at 10:11 — 1 minute past the 10:10 closure deadline
        setClockTo(2026, 4, 3, 10, 11);

        job.runTransitions();

        Lesson after = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(LessonStatus.CLOSED);
        assertThat(after.getClosedAt()).isNotNull();

        // Verify lesson.closed был записан в outbox
        assertThat(outboxStorage.findPending(10))
                .anyMatch(r -> "lesson.closed".equals(r.eventType()));
    }

    // =========================================================================
    // Negative: ACTIVE lesson stays ACTIVE before grace period ends
    // =========================================================================

    @Test
    void doesNotTransitionActiveBeforeGracePeriod() {
        // ScheduleItem: endTime=10:05 → closure deadline is 10:10
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.ACTIVE);

        // Clock at 10:09 — before 10:10 closure deadline
        setClockTo(2026, 4, 3, 10, 9);

        job.runTransitions();

        Lesson after = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(LessonStatus.ACTIVE);
    }

    // =========================================================================
    // CRON-03: catch-up after restart — ALL past-due lessons transition in one tick
    // =========================================================================

    @Test
    void catchesUpMissedTransitionsOnRestart() {
        // ScheduleItem: startTime=08:30, endTime=10:05 (both past-due at 12:00)
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));

        // Three lessons from previous days — simulating server was offline
        Lesson l1 = createLesson(item.getId(), LocalDate.of(2026, 4, 1), LessonStatus.PLANNED);
        Lesson l2 = createLesson(item.getId(), LocalDate.of(2026, 4, 2), LessonStatus.PLANNED);
        Lesson l3 = createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.PLANNED);

        // Clock at 12:00 on 2026-04-03 — all three lessons are past start_time AND end_time+5min
        setClockTo(2026, 4, 3, 12, 0);

        // Single tick — must catch up all 3
        job.runTransitions();

        // Phase 1 activates all 3 (queries for PLANNED with start_time <= now)
        // Phase 2 immediately closes all 3 (queries for ACTIVE with end_time+5min <= now)
        // (The phase 2 query finds the lessons just saved as ACTIVE)
        List<Lesson> all = lessonRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).allMatch(l -> l.getStatus() == LessonStatus.CLOSED);
        assertThat(all).allMatch(l -> l.getClosedAt() != null);

        // 3 lesson.started + 3 lesson.closed в outbox
        List<OutboxRecord> pending = outboxStorage.findPending(100);
        assertThat(pending.stream().filter(r -> "lesson.started".equals(r.eventType())).count())
                .isGreaterThanOrEqualTo(3);
        assertThat(pending.stream().filter(r -> "lesson.closed".equals(r.eventType())).count())
                .isGreaterThanOrEqualTo(3);
    }

    // =========================================================================
    // Negative: future PLANNED lesson stays PLANNED
    // =========================================================================

    @Test
    void doesNotTransitionFutureLesson() {
        // ScheduleItem: startTime=14:00, endTime=15:35 (future lesson)
        ScheduleItem item = createScheduleItem(LocalTime.of(14, 0), LocalTime.of(15, 35));
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 4), LessonStatus.PLANNED);

        // Clock at 10:00 on 2026-04-03 — lesson is tomorrow
        setClockTo(2026, 4, 3, 10, 0);

        job.runTransitions();

        Lesson after = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(LessonStatus.PLANNED);

        // No outbox entries should be created
        assertThat(outboxStorage.findPending(10)).isEmpty();
    }

    // =========================================================================
    // Negative: CANCELLED lesson is never touched by cron
    // =========================================================================

    @Test
    void doesNotTransitionCancelledLesson() {
        // ScheduleItem: startTime=08:30 (past)
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.CANCELLED);

        // Clock at 10:00 — past start_time but lesson is CANCELLED
        setClockTo(2026, 4, 3, 10, 0);

        job.runTransitions();

        Lesson after = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(LessonStatus.CANCELLED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setClockTo(int year, int month, int day, int hour, int minute) {
        Instant instant = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, MOSCOW).toInstant();
        when(clock.instant()).thenReturn(instant);
        when(clock.getZone()).thenReturn(MOSCOW);
    }

    private ScheduleItem createScheduleItem(LocalTime startTime, LocalTime endTime) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(1L);
        item.setSubjectId(100L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 5);  // Friday (2026-04-03 is a Friday)
        item.setLessonNumber((short) 1);
        item.setStartTime(startTime);
        item.setEndTime(endTime);
        item.setWeekType(WeekType.ALL);
        item.setRoom("101");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long scheduleItemId, LocalDate date, LessonStatus status) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(status);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
