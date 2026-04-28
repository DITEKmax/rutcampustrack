package ru.rutcampustrack.schedule.events;

import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonReminderJob;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Contract test для {@code lesson.reminder} (NOTIF unification).
 *
 * <p>Сценарий: ACTIVE lesson, время — за серединой пары, marker
 * {@code reminder_midpoint_sent_at} ещё null. Job публикует event,
 * payload должен соответствовать {@code event-schemas/lesson.reminder.json}.
 */
class LessonReminderContractIT extends AbstractScheduleIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    LessonReminderJob job;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @MockitoBean
    Clock clock;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        drainOutbox();
    }

    @Test
    void publishedLessonReminderPayload_matchesSchema() throws Exception {
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.ACTIVE);

        // 09:20 — после midpoint (09:17:30), до end (10:05).
        Instant fixed = ZonedDateTime.of(2026, 4, 3, 9, 20, 0, 0, MOSCOW).toInstant();
        when(clock.instant()).thenReturn(fixed);
        when(clock.getZone()).thenReturn(MOSCOW);

        job.runReminders();

        Optional<OutboxRecord> reminder = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.reminder".equals(r.eventType()))
                .findFirst();
        assertThat(reminder)
                .as("lesson.reminder должен быть записан в outbox")
                .isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "lesson.reminder.json", reminder.get().payload());
        assertThat(errors)
                .as("lesson.reminder payload должен соответствовать event-schemas/lesson.reminder.json")
                .isEmpty();
    }

    @Test
    void notPublishedTwiceForSameLesson() {
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.ACTIVE);

        Instant fixed = ZonedDateTime.of(2026, 4, 3, 9, 20, 0, 0, MOSCOW).toInstant();
        when(clock.instant()).thenReturn(fixed);
        when(clock.getZone()).thenReturn(MOSCOW);

        job.runReminders();
        long firstCount = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.reminder".equals(r.eventType())).count();
        assertThat(firstCount).isEqualTo(1L);

        // Второй tick — marker reminder_midpoint_sent_at уже выставлен,
        // повторно не публикуется.
        job.runReminders();
        long secondCount = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.reminder".equals(r.eventType())).count();
        assertThat(secondCount).isEqualTo(1L);
    }

    @Test
    void notPublishedBeforeMidpoint() {
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.ACTIVE);

        // 09:00 — до midpoint (09:17:30).
        Instant fixed = ZonedDateTime.of(2026, 4, 3, 9, 0, 0, 0, MOSCOW).toInstant();
        when(clock.instant()).thenReturn(fixed);
        when(clock.getZone()).thenReturn(MOSCOW);

        job.runReminders();

        boolean any = outboxStorage.findPending(100).stream()
                .anyMatch(r -> "lesson.reminder".equals(r.eventType()));
        assertThat(any).isFalse();
    }

    private ScheduleItem createScheduleItem(LocalTime startTime, LocalTime endTime) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(1L);
        item.setSubjectId(100L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 5);
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
