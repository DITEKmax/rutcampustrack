package ru.rutcampustrack.schedule.grpc;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Phase 61 D-04: Integration tests for {@link ScheduleGrpcServiceImpl#resolveLesson}.
 *
 * Covers:
 *  - happy path: lesson exists on date with matching groupId/lessonNumber → returns LessonResponse
 *  - NOT_FOUND: wrong groupId → ResourceNotFoundException (маппится на Status.NOT_FOUND через @GrpcAdvice)
 *  - NOT_FOUND: lesson cancelled → исключается из резолва (status filter planned/active/closed)
 *
 * Pattern mirrors {@link ScheduleGrpcServiceImplTest}: прямой вызов + mock StreamObserver
 * (без in-process gRPC канала, per D-06).
 */
class ScheduleGrpcResolveLessonIT extends AbstractScheduleIntegrationTest {

    @Autowired
    ScheduleGrpcServiceImpl grpcService;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    /**
     * Prevents outbound gRPC connection to academic-service on Spring context startup.
     */
    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
    }

    @Test
    void resolveLesson_returnsLesson_whenExists() {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.PLANNED);

        ResolveLessonRequest request = ResolveLessonRequest.newBuilder()
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
    }

    @Test
    void resolveLesson_throwsNotFound_whenGroupMismatch() {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.PLANNED);

        ResolveLessonRequest request = ResolveLessonRequest.newBuilder()
                .setGroupId(99L)   // несуществующая группа
                .setDate("2026-05-01")
                .setLessonNumber(3)
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> grpcService.resolveLesson(request, observer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveLesson_throwsNotFound_whenLessonCancelled() {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.CANCELLED);

        ResolveLessonRequest request = ResolveLessonRequest.newBuilder()
                .setGroupId(10L)
                .setDate("2026-05-01")
                .setLessonNumber(3)
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> grpcService.resolveLesson(request, observer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveLesson_throwsNotFound_whenDateHasNoLesson() {
        ScheduleItem item = createScheduleItem(10L, 42L, (short) 3, "A101");
        createLesson(item.getId(), LocalDate.of(2026, 5, 1), LessonStatus.PLANNED);

        ResolveLessonRequest request = ResolveLessonRequest.newBuilder()
                .setGroupId(10L)
                .setDate("2030-12-31")  // дата без пар
                .setLessonNumber(3)
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> grpcService.resolveLesson(request, observer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ScheduleItem createScheduleItem(Long groupId, Long subjectId, Short lessonNumber, String room) {
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
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long scheduleItemId, LocalDate date, LessonStatus status) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(status);
        lesson.setGeoBlocked(false);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
