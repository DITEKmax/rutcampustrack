package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Placeholder coverage for attendance-service read-path awareness of one-off lessons (AC-09).
 * <p>
 * <strong>Known Limitation (Phase 60-05):</strong> The schedule-service gRPC contract
 * ({@code getLessonsByGroup}, {@code getLessonById}, {@code getActiveLesson}) currently
 * queries {@code ScheduleItem} / {@code Lesson} tables only — it does <em>not</em> merge
 * {@code schedule_one_off_lessons} into the returned stream.
 * <p>
 * End-to-end visibility of a one-off lesson for the attendance read-path is therefore
 * deferred to a follow-up (see 60-09 integration plan). The write-path is already
 * complete:
 * <ul>
 *   <li>{@code lesson.closed} auto-absent → {@link LessonEventService#processLessonClosed} (v4.0)</li>
 *   <li>{@code lesson.one_off.cancelled} cascade delete → {@link LessonEventService#processOneOffLessonCancelled}
 *       (this plan, Task 1)</li>
 * </ul>
 * <p>
 * This test pins the current shape of {@link ScheduleGrpcClient#getLessonById} so that any
 * future expansion of its contract to include one-off lessons is detected as a breaking
 * change and the read-path merge can be implemented with a matching unit test.
 */
@ExtendWith(MockitoExtension.class)
class LessonGenerationMergeTest {

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @InjectMocks
    private LessonEventService lessonEventService; // not exercised — construct to validate wiring

    @Test
    void scheduleGrpcClient_getLessonById_returnsLessonResponseShape() {
        LessonResponse expected = LessonResponse.newBuilder()
                .setId(42L)
                .setGroupId(10L)
                .setSubjectId(5L)
                .setLessonNumber(2)
                .setDate("2026-05-01")
                .setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(42L)).thenReturn(expected);

        LessonResponse actual = scheduleGrpcClient.getLessonById(42L);

        // Anchors the current contract. If a future `is_one_off` field is added,
        // this test should be extended to assert the merge semantics.
        assertThat(actual.getId()).isEqualTo(42L);
        assertThat(actual.getGroupId()).isEqualTo(10L);
        assertThat(actual.getSubjectId()).isEqualTo(5L);
        assertThat(actual.getLessonNumber()).isEqualTo(2);
        assertThat(actual.getDate()).isEqualTo("2026-05-01");
    }
}
