package ru.rutcampustrack.schedule.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

/**
 * Phase 61 D-04: Integration tests for {@link ScheduleGrpcServiceImpl#resolveLesson}.
 *
 * <p>Covers: - happy path: lesson exists on date with matching groupId/lessonNumber → returns
 * LessonResponse - NOT_FOUND: wrong groupId → ResourceNotFoundException (маппится на
 * Status.NOT_FOUND через @GrpcAdvice) - NOT_FOUND: lesson cancelled → исключается из резолва
 * (status filter planned/active/closed)
 *
 * <p>Pattern mirrors {@link ScheduleGrpcServiceImplIT}: прямой вызов + mock StreamObserver (без
 * in-process gRPC канала, per D-06).
 */
@org.springframework.context.annotation.Import(TestGrpcTransport.Config.class)
@org.springframework.test.context.TestPropertySource(
        properties = {
            "grpc.server.port=0",
            "grpc.server.address=127.0.0.1",
            "grpc.auth.secret=synthetic-test-grpc"
        })
class ScheduleGrpcResolveLessonIT extends AbstractScheduleIntegrationTest {

    @Autowired ScheduleGrpcServiceImpl grpcService;
    @Autowired TestGrpcTransport.Port grpcPort;

    @Autowired LessonRepository lessonRepository;

    @Autowired ScheduleItemRepository scheduleItemRepository;

    /** Prevents outbound gRPC connection to academic-service on Spring context startup. */
    @MockitoBean AcademicGrpcClient academicGrpcClient;

    @BeforeEach
    void resetFixtures() {
        resetScheduleData();
    }

    @AfterEach
    void cleanup() {
        resetScheduleData();
    }

    @Test
    void resolveLesson_returnsLesson_whenExists() throws Exception {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.PLANNED);

        ResolveLessonRequest request =
                ResolveLessonRequest.newBuilder()
                        .setGroupId(10L)
                        .setDate("2026-05-01")
                        .setLessonNumber(3)
                        .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);
        grpcService.resolveLesson(request, observer);

        ArgumentCaptor<LessonResponse> captor = ArgumentCaptor.forClass(LessonResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        LessonResponse response = captor.getValue();
        assertThat(response.getId()).isEqualTo(lesson.getId());
        assertThat(response.getGroupId()).isEqualTo(10L);
        assertThat(response.getSubjectId()).isEqualTo(42L);
        assertThat(response.getLessonNumber()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("planned");
        assertThat(response.getDate()).isEqualTo("2026-05-01");

        try (var transport = new TestGrpcTransport(grpcPort)) {
            assertThat(transport.stub.resolveLesson(request)).isEqualTo(response);
        }
        for (String secret : new String[] {null, "wrong-synthetic-secret"}) {
            try (var transport = new TestGrpcTransport(grpcPort, secret)) {
                var failure =
                        org.junit.jupiter.api.Assertions.assertThrows(
                                io.grpc.StatusRuntimeException.class,
                                () -> transport.stub.resolveLesson(request));
                assertThat(failure.getStatus().getCode())
                        .isEqualTo(io.grpc.Status.Code.UNAUTHENTICATED);
            }
        }
    }

    @Test
    void resolveLesson_throwsNotFound_whenGroupMismatch() throws Exception {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.PLANNED);

        ResolveLessonRequest request =
                ResolveLessonRequest.newBuilder()
                        .setGroupId(99L) // несуществующая группа
                        .setDate("2026-05-01")
                        .setLessonNumber(3)
                        .build();

        try (var transport = new TestGrpcTransport(grpcPort)) {
            var failure =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            io.grpc.StatusRuntimeException.class,
                            () -> transport.stub.resolveLesson(request));
            assertThat(failure.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
        }
    }

    @Test
    void resolveLesson_throwsNotFound_whenLessonCancelled() throws Exception {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.CANCELLED);

        ResolveLessonRequest request =
                ResolveLessonRequest.newBuilder()
                        .setGroupId(10L)
                        .setDate("2026-05-01")
                        .setLessonNumber(3)
                        .build();

        try (var transport = new TestGrpcTransport(grpcPort)) {
            var failure =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            io.grpc.StatusRuntimeException.class,
                            () -> transport.stub.resolveLesson(request));
            assertThat(failure.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
        }
    }

    @Test
    void resolveLesson_throwsNotFound_whenDateHasNoLesson() throws Exception {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.PLANNED);

        ResolveLessonRequest request =
                ResolveLessonRequest.newBuilder()
                        .setGroupId(10L)
                        .setDate("2030-12-31") // дата без пар
                        .setLessonNumber(3)
                        .build();

        try (var transport = new TestGrpcTransport(grpcPort)) {
            var failure =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            io.grpc.StatusRuntimeException.class,
                            () -> transport.stub.resolveLesson(request));
            assertThat(failure.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ScheduleItem createScheduleItem(
            Long groupId, Long subjectId, Short lessonNumber, String room) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(groupId);
        item.setSubjectId(subjectId);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 1);
        item.setLessonNumber(lessonNumber);
        item.setStartTime(LocalTime.of(8, 30));
        item.setEndTime(LocalTime.of(10, 0));
        item.setWeekType(WeekType.ALL);
        item.setRoom(room);
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return saveFixtureScheduleItem(item);
    }

    private Lesson createLesson(Long scheduleItemId, LocalDate date, LessonStatus status) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(status);
        lesson.setGeoBlocked(false);
        lesson.setCreatedAt(OffsetDateTime.now());
        return saveFixtureLesson(lesson);
    }
}
