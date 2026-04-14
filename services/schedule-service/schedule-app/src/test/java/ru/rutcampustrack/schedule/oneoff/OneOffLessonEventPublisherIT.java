package ru.rutcampustrack.schedule.oneoff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.event.OneOffLessonCancelledEvent;
import ru.rutcampustrack.schedule.event.OneOffLessonCreatedEvent;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test verifying that creating / deleting a one-off lesson publishes
 * OneOffLessonCreatedEvent / OneOffLessonCancelledEvent to RabbitMQ (Phase 60-04).
 *
 * Uses @Autowired OneOffLessonService directly to avoid HTTP auth complexity.
 * NOT @Transactional — transaction must commit for @TransactionalEventListener(AFTER_COMMIT)
 * to fire and forward the event to rabbitTemplate.
 */
class OneOffLessonEventPublisherIT extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 77L;
    private static final Long SUBJECT_ID = 555L;
    private static final Long SEMESTER_ID = 11L;
    private static final Long USER_ID = 9001L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 4, 20); // Monday
    private static final short TARGET_LESSON = (short) 3;

    @Autowired
    OneOffLessonService oneOffLessonService;

    @Autowired
    OneOffLessonRepository oneOffLessonRepository;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    RequestContext requestContext;

    private static final SemesterResponse MOCK_SEMESTER = SemesterResponse.newBuilder()
            .setId(SEMESTER_ID)
            .setName("Spring 2026")
            .setDateFrom("2026-02-02")
            .setDateTo("2026-06-30")
            .setFirstWeekType("odd")
            .build();

    @BeforeEach
    void setUp() {
        oneOffLessonRepository.deleteAll();

        when(academicGrpcClient.getActiveSemester()).thenReturn(MOCK_SEMESTER);
        when(academicGrpcClient.parseSemesterFirstWeekType(MOCK_SEMESTER))
                .thenReturn(WeekType.ODD);
        when(academicGrpcClient.validateGroup(GROUP_ID))
                .thenReturn(GroupResponse.newBuilder()
                        .setId(GROUP_ID).setName("G").setIsActive(true).build());

        // ADMIN role bypasses headman gRPC round-trip.
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);
        when(requestContext.getUserId()).thenReturn(USER_ID);
        when(requestContext.isHeadman()).thenReturn(false);
    }

    @AfterEach
    void cleanup() {
        oneOffLessonRepository.deleteAll();
    }

    @Test
    void publishesCreatedEventOnCreate() {
        oneOffLessonService.createOneOffLesson(new CreateOneOffLessonRequest(
                GROUP_ID, SUBJECT_ID, TARGET_DATE, TARGET_LESSON, "D-404"));

        verify(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                (Object) argThat(e -> e instanceof OneOffLessonCreatedEvent
                        && ((OneOffLessonCreatedEvent) e).getEventType().equals("lesson.one_off.created")));
    }

    @Test
    void publishesCancelledEventOnDelete() {
        // Arrange: insert a one-off directly so delete() has something to remove.
        OneOffLesson existing = new OneOffLesson();
        existing.setGroupId(GROUP_ID);
        existing.setSubjectId(SUBJECT_ID);
        existing.setSemesterId(SEMESTER_ID);
        existing.setDate(TARGET_DATE);
        existing.setLessonNumber(TARGET_LESSON);
        existing.setClassroom("D-404");
        existing.setCreatedBy(USER_ID);
        OneOffLesson saved = oneOffLessonRepository.save(existing);

        // Act
        oneOffLessonService.deleteOneOffLesson(saved.getId());

        // Assert: OneOffLessonCancelledEvent forwarded to RabbitMQ after commit
        verify(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                (Object) argThat(e -> e instanceof OneOffLessonCancelledEvent
                        && ((OneOffLessonCancelledEvent) e).getEventType().equals("lesson.one_off.cancelled")));
    }
}
