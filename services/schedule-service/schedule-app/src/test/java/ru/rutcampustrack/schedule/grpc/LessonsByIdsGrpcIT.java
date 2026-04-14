package ru.rutcampustrack.schedule.grpc;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for GRPC-04: GetLessonsByIds (AC-11).
 *
 * Covers decisions D-25 and FR-1 validation path:
 * - Returns compact LessonInfo (lessonId, groupId, subjectId, startsAt) for each lesson found
 * - Silently skips unknown ids (no NOT_FOUND)
 * - Handles empty request list gracefully
 *
 * Pattern mirrors ScheduleGrpcServiceImplTest: direct invocation + mock StreamObserver.
 */
class LessonsByIdsGrpcIT extends AbstractScheduleIntegrationTest {

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
    void getLessonsByIds_happyPath_returnsInfoForEachLesson() {
        ScheduleItem item1 = createScheduleItem(100L, 11L, (short) 1, LocalTime.of(8, 30), "A101");
        ScheduleItem item2 = createScheduleItem(100L, 12L, (short) 2, LocalTime.of(10, 15), "A102");
        ScheduleItem item3 = createScheduleItem(200L, 13L, (short) 3, LocalTime.of(12, 0), "B303");

        Lesson lesson1 = createLesson(item1.getId(), LocalDate.of(2026, 4, 1));
        Lesson lesson2 = createLesson(item2.getId(), LocalDate.of(2026, 4, 2));
        Lesson lesson3 = createLesson(item3.getId(), LocalDate.of(2026, 4, 3));

        LessonsByIdsRequest request = LessonsByIdsRequest.newBuilder()
                .addAllLessonIds(List.of(lesson1.getId(), lesson2.getId(), lesson3.getId()))
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonsByIdsResponse> observer = mock(StreamObserver.class);
        grpcService.getLessonsByIds(request, observer);

        ArgumentCaptor<LessonsByIdsResponse> captor = ArgumentCaptor.forClass(LessonsByIdsResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        List<LessonInfo> lessons = captor.getValue().getLessonsList();
        assertThat(lessons).hasSize(3);

        LessonInfo info1 = lessons.stream().filter(l -> l.getLessonId() == lesson1.getId()).findFirst().orElseThrow();
        assertThat(info1.getGroupId()).isEqualTo(100L);
        assertThat(info1.getSubjectId()).isEqualTo(11L);
        assertThat(info1.getStartsAt()).isEqualTo("2026-04-01T08:30");

        LessonInfo info3 = lessons.stream().filter(l -> l.getLessonId() == lesson3.getId()).findFirst().orElseThrow();
        assertThat(info3.getGroupId()).isEqualTo(200L);
        assertThat(info3.getSubjectId()).isEqualTo(13L);
        assertThat(info3.getStartsAt()).isEqualTo("2026-04-03T12:00");
    }

    @Test
    void getLessonsByIds_nonExistentIds_returnsEmptyList() {
        LessonsByIdsRequest request = LessonsByIdsRequest.newBuilder()
                .addAllLessonIds(List.of(99999L, 99998L))
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonsByIdsResponse> observer = mock(StreamObserver.class);
        grpcService.getLessonsByIds(request, observer);

        ArgumentCaptor<LessonsByIdsResponse> captor = ArgumentCaptor.forClass(LessonsByIdsResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        assertThat(captor.getValue().getLessonsList()).isEmpty();
    }

    @Test
    void getLessonsByIds_emptyRequest_returnsEmptyList() {
        LessonsByIdsRequest request = LessonsByIdsRequest.newBuilder().build();

        @SuppressWarnings("unchecked")
        StreamObserver<LessonsByIdsResponse> observer = mock(StreamObserver.class);
        grpcService.getLessonsByIds(request, observer);

        ArgumentCaptor<LessonsByIdsResponse> captor = ArgumentCaptor.forClass(LessonsByIdsResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        assertThat(captor.getValue().getLessonsList()).isEmpty();
    }

    // =========================================================================
    // Helpers (mirror ScheduleGrpcServiceImplTest pattern)
    // =========================================================================

    private ScheduleItem createScheduleItem(Long groupId, Long subjectId, Short lessonNumber,
                                            LocalTime startTime, String room) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(groupId);
        item.setSubjectId(subjectId);
        item.setTeacherId(1L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 1);
        item.setLessonNumber(lessonNumber);
        item.setStartTime(startTime);
        item.setEndTime(startTime.plusHours(1).plusMinutes(30));
        item.setWeekType(WeekType.ALL);
        item.setRoom(room);
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long scheduleItemId, LocalDate date) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setGeoBlocked(false);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
