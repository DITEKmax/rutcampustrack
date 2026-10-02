package ru.rutcampustrack.schedule.oneoff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test verifying that creating / deleting a one-off lesson publishes
 * OneOffLessonCreatedEvent / exact LessonCancelledEvent to the durable outbox.
 *
 * Uses @Autowired OneOffLessonService directly to avoid HTTP auth complexity.
 * NOT @Transactional — the writer commits the BEFORE_COMMIT outbox entry.
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
        resetScheduleData();

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
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenReturn(SemesterStateResponse.newBuilder().setId(SEMESTER_ID).setActive(true).build());
        when(academicGrpcClient.getAssignmentsByIds(List.of(501L))).thenReturn(List.of(AssignmentInfo.newBuilder()
                .setId(501L).setTeacherId(700L).setGroupId(GROUP_ID).setSubjectId(SUBJECT_ID).setSemesterId(SEMESTER_ID)
                .setLessonType("lecture").setValidFrom("2026-02-02").setValidUntilExclusive("2026-07-01").build()));
    }

    @AfterEach
    void cleanup() {
        drainOutbox();
    }

    @Test
    void publishesCreatedEventOnCreate() {
        oneOffLessonService.createOneOffLesson(new CreateOneOffLessonRequest(
                GROUP_ID, SUBJECT_ID, 501L, TARGET_DATE, TARGET_LESSON, LocalTime.of(8,30), LocalTime.of(10,0), "D-404"), UUID.randomUUID());

        assertThat(outboxStorage.findPending(10))
                .anyMatch(r -> "lesson.one_off.created".equals(r.eventType()));
    }

    @Test
    void publishesCancelledEventOnDelete() {
        OneOffLesson saved = oneOffLessonService.createOneOffLesson(new CreateOneOffLessonRequest(
                GROUP_ID, SUBJECT_ID, 501L, TARGET_DATE, TARGET_LESSON, LocalTime.of(8,30), LocalTime.of(10,0), "D-404"), UUID.randomUUID());

        // Act
        oneOffLessonService.deleteOneOffLesson(saved.getId());

        // Exact physical cancellation, without the old destructive natural-key event.
        assertThat(outboxStorage.findPending(10))
                .anyMatch(r -> "lesson.cancelled".equals(r.eventType()));
        assertThat(outboxStorage.findPending(10)).noneMatch(r -> "lesson.one_off.cancelled".equals(r.eventType()));
    }
}
