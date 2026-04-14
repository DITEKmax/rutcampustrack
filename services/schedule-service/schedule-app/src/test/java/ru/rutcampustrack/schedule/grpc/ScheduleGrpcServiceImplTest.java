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
 * Integration tests for ScheduleGrpcServiceImpl covering all 3 gRPC RPCs.
 *
 * Tests verify:
 * - GRPC-01 GetActiveLesson: happy path, not-found, multiple-overlap (D-02)
 * - GRPC-02 GetLessonById: happy path, not-found
 * - GRPC-03 GetLessonsByGroup: happy path, invalid date range (D-04), empty result
 *
 * Uses direct method invocation with mock StreamObserver (per D-06 — no in-process gRPC channel).
 * NOT @Transactional — uses @AfterEach cleanup to avoid preventing AFTER_COMMIT event listeners.
 */
class ScheduleGrpcServiceImplTest extends AbstractScheduleIntegrationTest {

    @Autowired
    ScheduleGrpcServiceImpl grpcService;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    /**
     * Prevents outbound gRPC connection to academic-service port 19091 on Spring context startup.
     * Without this, Spring context fails with StatusRuntimeException: UNAVAILABLE.
     */
    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
    }

    // =========================================================================
    // GRPC-01: GetActiveLesson
    // =========================================================================

    @Test
    void getActiveLesson_happyPath_returnsLessonResponse() {
        ScheduleItem item = createScheduleItem(100L, 1L, (short) 1, "A101");
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 4), LessonStatus.ACTIVE);

        ActiveLessonRequest request = ActiveLessonRequest.newBuilder()
                .setGroupId(100L)
                .setTimestamp("2026-04-04T09:00:00")
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);
        grpcService.getActiveLesson(request, observer);

        ArgumentCaptor<LessonResponse> captor = ArgumentCaptor.forClass(LessonResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        LessonResponse response = captor.getValue();
        assertThat(response.getId()).isEqualTo(lesson.getId());
        assertThat(response.getGroupId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo("active");
        assertThat(response.getLessonNumber()).isEqualTo(1);
        assertThat(response.getRoom()).isEqualTo("A101");
    }

    @Test
    void getActiveLesson_noActiveLessons_throwsResourceNotFoundException() {
        // No lessons in DB — expect not-found exception (D-01)
        ActiveLessonRequest request = ActiveLessonRequest.newBuilder()
                .setGroupId(999L)
                .setTimestamp("2026-04-04T09:00:00")
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> grpcService.getActiveLesson(request, observer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActiveLesson_multipleActiveLessons_returnsFirstByLessonNumber() {
        // D-02: Two active lessons for the same group — must return lesson with lessonNumber=1
        ScheduleItem item1 = createScheduleItem(100L, 1L, (short) 1, "A101");
        ScheduleItem item2 = createScheduleItem(100L, 1L, (short) 2, "B202");

        Lesson lesson1 = createLesson(item1.getId(), LocalDate.of(2026, 4, 4), LessonStatus.ACTIVE);
        createLesson(item2.getId(), LocalDate.of(2026, 4, 4), LessonStatus.ACTIVE);

        ActiveLessonRequest request = ActiveLessonRequest.newBuilder()
                .setGroupId(100L)
                .setTimestamp("2026-04-04T09:00:00")
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);
        grpcService.getActiveLesson(request, observer);

        ArgumentCaptor<LessonResponse> captor = ArgumentCaptor.forClass(LessonResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        LessonResponse response = captor.getValue();
        assertThat(response.getId()).isEqualTo(lesson1.getId());
        assertThat(response.getLessonNumber()).isEqualTo(1);
    }

    // =========================================================================
    // GRPC-02: GetLessonById
    // =========================================================================

    @Test
    void getLessonById_happyPath_returnsLessonResponse() {
        ScheduleItem item = createScheduleItem(200L, 2L, (short) 3, "C303");
        Lesson lesson = createLesson(item.getId(), LocalDate.of(2026, 4, 5), LessonStatus.PLANNED);

        LessonByIdRequest request = LessonByIdRequest.newBuilder()
                .setLessonId(lesson.getId())
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);
        grpcService.getLessonById(request, observer);

        ArgumentCaptor<LessonResponse> captor = ArgumentCaptor.forClass(LessonResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        LessonResponse response = captor.getValue();
        assertThat(response.getId()).isEqualTo(lesson.getId());
        assertThat(response.getGroupId()).isEqualTo(200L);
        assertThat(response.getSubjectId()).isEqualTo(1L);
        // D-16: teacher_id is reserved in schedule.proto — no getter generated
        assertThat(response.getDate()).isEqualTo("2026-04-05");
        assertThat(response.getLessonNumber()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("planned");
        assertThat(response.getRoom()).isEqualTo("C303");
        assertThat(response.getIsGeoBlocked()).isFalse();
    }

    @Test
    void getLessonById_nonExistentId_throwsResourceNotFoundException() {
        LessonByIdRequest request = LessonByIdRequest.newBuilder()
                .setLessonId(999999L)
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> grpcService.getLessonById(request, observer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // GRPC-03: GetLessonsByGroup
    // =========================================================================

    @Test
    void getLessonsByGroup_happyPath_returnsAllMatchingLessons() {
        ScheduleItem item = createScheduleItem(100L, 1L, (short) 1, "A101");

        createLesson(item.getId(), LocalDate.of(2026, 4, 1), LessonStatus.CLOSED);
        createLesson(item.getId(), LocalDate.of(2026, 4, 8), LessonStatus.ACTIVE);
        createLesson(item.getId(), LocalDate.of(2026, 4, 15), LessonStatus.PLANNED);

        LessonsByGroupRequest request = LessonsByGroupRequest.newBuilder()
                .setGroupId(100L)
                .setSemesterId(1L)
                .setDateFrom("2026-04-01")
                .setDateTo("2026-04-30")
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonsResponse> observer = mock(StreamObserver.class);
        grpcService.getLessonsByGroup(request, observer);

        ArgumentCaptor<LessonsResponse> captor = ArgumentCaptor.forClass(LessonsResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        LessonsResponse response = captor.getValue();
        assertThat(response.getLessonsList()).hasSize(3);
    }

    @Test
    void getLessonsByGroup_dateFromAfterDateTo_throwsIllegalArgumentException() {
        // D-04: dateFrom > dateTo must throw IllegalArgumentException
        LessonsByGroupRequest request = LessonsByGroupRequest.newBuilder()
                .setGroupId(100L)
                .setSemesterId(1L)
                .setDateFrom("2026-04-10")
                .setDateTo("2026-04-01")
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonsResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> grpcService.getLessonsByGroup(request, observer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date_from must not be after date_to");
    }

    @Test
    void getLessonsByGroup_noScheduleItems_returnsEmptyResponse() {
        // Group 999 has no schedule items — must return empty LessonsResponse
        LessonsByGroupRequest request = LessonsByGroupRequest.newBuilder()
                .setGroupId(999L)
                .setSemesterId(1L)
                .setDateFrom("2026-04-01")
                .setDateTo("2026-04-30")
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonsResponse> observer = mock(StreamObserver.class);
        grpcService.getLessonsByGroup(request, observer);

        ArgumentCaptor<LessonsResponse> captor = ArgumentCaptor.forClass(LessonsResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        assertThat(captor.getValue().getLessonsList()).isEmpty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ScheduleItem createScheduleItem(Long groupId, Long semesterId, Short lessonNumber, String room) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(groupId);
        item.setSubjectId(1L);
        item.setSemesterId(semesterId);
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
