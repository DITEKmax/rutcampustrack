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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

/**
 * Integration test verifying that cancelling a lesson publishes a LessonCancelledEvent
 * to the outbox (EVNT-03).
 *
 * M02 Группа 5: listener теперь пишет в schedule_outbox (BEFORE_COMMIT),
 * а не напрямую в Rabbit. Тест проверяет запись в outbox — это эквивалент
 * гарантии «событие не теряется, даже если Rabbit недоступен».
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
        drainOutbox();
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

        // Assert: LessonCancelledEvent записано в outbox (в той же tx что и
        // доменная операция). Publisher async, в тестах не тикает.
        List<OutboxRecord> pending = outboxStorage.findPending(10);
        assertThat(pending)
                .anyMatch(r -> "lesson.cancelled".equals(r.eventType()));
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
