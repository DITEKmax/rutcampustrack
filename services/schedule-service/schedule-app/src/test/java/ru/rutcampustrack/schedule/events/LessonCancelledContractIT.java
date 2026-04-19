package ru.rutcampustrack.schedule.events;

import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonService;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * M02 Группа 8 — contract test: cancelLesson() → lesson.cancelled event
 * в outbox валидируется против event-schemas/lesson.cancelled.json.
 */
class LessonCancelledContractIT extends AbstractScheduleIntegrationTest {

    @Autowired
    LessonService lessonService;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    RequestContext requestContext;

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        drainOutbox();
    }

    @Test
    void cancelLesson_publishesValidLessonCancelledEvent() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId());

        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);
        when(requestContext.getUserId()).thenReturn(999L);
        when(requestContext.isHeadman()).thenReturn(false);

        lessonService.cancelLesson(lesson.getId(), new CancelLessonRequest("Preparation day"));

        Optional<OutboxRecord> cancelled = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.cancelled".equals(r.eventType()))
                .findFirst();
        assertThat(cancelled).isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "lesson.cancelled.json", cancelled.get().payload());
        assertThat(errors)
                .as("lesson.cancelled payload должен соответствовать schema")
                .isEmpty();
    }

    private ScheduleItem createScheduleItem() {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(10L);
        item.setSubjectId(200L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 5);
        item.setLessonNumber((short) 2);
        item.setStartTime(LocalTime.of(10, 10));
        item.setEndTime(LocalTime.of(11, 45));
        item.setWeekType(WeekType.ALL);
        item.setRoom("B-202");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long itemId) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(itemId);
        lesson.setDate(LocalDate.of(2026, 4, 3));
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
