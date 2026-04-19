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
import ru.rutcampustrack.schedule.lesson.LessonStatusTransitionJob;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * M02 Группа 8 — contract test: реальный service call → выпущенный event
 * в schedule_outbox валидируется против event-schemas/lesson.started.json
 * + lesson.closed.json.
 *
 * <p>Flow: startup LessonStatusTransitionJob в transition-тестовом окне
 * → records попадают в outbox → OutboxStorage.findPending → каждая строка
 * валидируется networknt json-schema-validator'ом через {@link EventSchemaValidator}.
 * $ref в schema резолвится относительно event-schemas/ directory (sibling
 * _common.json).
 */
class LessonStartedContractIT extends AbstractScheduleIntegrationTest {

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

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        drainOutbox();
    }

    @Test
    void publishedLessonStartedPayload_matchesSchema() throws Exception {
        // Arrange: planned lesson ready to transition to ACTIVE
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.PLANNED);

        Instant fixed = ZonedDateTime.of(2026, 4, 3, 10, 0, 0, 0, MOSCOW).toInstant();
        when(clock.instant()).thenReturn(fixed);
        when(clock.getZone()).thenReturn(MOSCOW);

        // Act
        job.runTransitions();

        // Assert: find lesson.started entry in outbox and validate against schema
        Optional<OutboxRecord> started = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.started".equals(r.eventType()))
                .findFirst();
        assertThat(started)
                .as("lesson.started должен быть записан в outbox")
                .isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "lesson.started.json", started.get().payload());
        assertThat(errors)
                .as("lesson.started payload должен соответствовать event-schemas/lesson.started.json")
                .isEmpty();
    }

    @Test
    void publishedLessonClosedPayload_matchesSchema() throws Exception {
        ScheduleItem item = createScheduleItem(LocalTime.of(8, 30), LocalTime.of(10, 5));
        createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.ACTIVE);

        // 10:11 — past end+5min closure deadline
        Instant fixed = ZonedDateTime.of(2026, 4, 3, 10, 11, 0, 0, MOSCOW).toInstant();
        when(clock.instant()).thenReturn(fixed);
        when(clock.getZone()).thenReturn(MOSCOW);

        job.runTransitions();

        Optional<OutboxRecord> closed = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.closed".equals(r.eventType()))
                .findFirst();
        assertThat(closed).as("lesson.closed должен быть записан в outbox").isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "lesson.closed.json", closed.get().payload());
        assertThat(errors)
                .as("lesson.closed payload должен соответствовать event-schemas/lesson.closed.json")
                .isEmpty();
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
