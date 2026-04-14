package ru.rutcampustrack.schedule.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.event.LessonCancelledEvent;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonService;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test verifying that cancelling a lesson publishes a LessonCancelledEvent
 * to RabbitMQ (EVNT-03).
 *
 * Uses @Autowired LessonService directly to avoid HTTP auth complexity.
 * NOT @Transactional — transaction must commit for @TransactionalEventListener(AFTER_COMMIT)
 * to fire and forward the event to rabbitTemplate.
 */
class LessonCancelEventTest extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 200L;
    private static final Long USER_ID = 999L;

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
    }

    // =========================================================================
    // EVNT-03: LessonCancelledEvent published on lesson cancellation
    // =========================================================================

    @Test
    void publishesCancelledEventOnCancel() {
        // Arrange: create schedule item and a planned lesson
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 3), LessonStatus.PLANNED);

        // Configure ADMIN role so headman check is bypassed
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);
        when(requestContext.getUserId()).thenReturn(USER_ID);
        when(requestContext.isHeadman()).thenReturn(false);

        // Act: cancel the lesson
        lessonService.cancelLesson(lesson.getId(), new CancelLessonRequest("Teacher sick"));

        // Assert: LessonCancelledEvent was forwarded to RabbitMQ after commit
        verify(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                (Object) argThat(e -> e instanceof LessonCancelledEvent
                        && ((LessonCancelledEvent) e).getEventType().equals("lesson.cancelled")));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ScheduleItem createScheduleItem() {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(GROUP_ID);
        item.setSubjectId(SUBJECT_ID);
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

    private Lesson createLesson(Long scheduleItemId, LocalDate date, LessonStatus status) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(status);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
