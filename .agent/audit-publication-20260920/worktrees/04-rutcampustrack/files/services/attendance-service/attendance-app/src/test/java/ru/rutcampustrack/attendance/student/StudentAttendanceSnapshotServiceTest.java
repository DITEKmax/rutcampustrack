package ru.rutcampustrack.attendance.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Identity;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentAttendanceSnapshotServiceTest {

    private static final long STUDENT_ID = 100L;
    private static final long GROUP_ID = 10L;
    private static final long LESSON_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-06T10:00:00Z");

    private final ScheduleGrpcClient scheduleGrpcClient = mock(ScheduleGrpcClient.class);
    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final LateCheckinRepository lateCheckinRepository = mock(LateCheckinRepository.class);
    private final CheckinPairStateRepository pairRepository = mock(CheckinPairStateRepository.class);
    private final StudentAttendanceSnapshotService service = new StudentAttendanceSnapshotService(
            scheduleGrpcClient, attendanceRepository, lateCheckinRepository, pairRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(LessonResponse.newBuilder()
                .setId(LESSON_ID).setGroupId(GROUP_ID).setDate("2026-09-06")
                .setStartTime("09:00").setEndTime("11:00").setStatus("active").build());
        when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID)).thenReturn(Optional.empty());
    }

    @Test
    void pendingRequestBeforeRetryAtReportsPendingConfirmation() {
        Instant retryAt = NOW.plusSeconds(300);

        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.PENDING, retryAt);

        assertThat(entry.eligibility().allowed()).isFalse();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.PENDING_CONFIRMATION);
        assertThat(entry.eligibility().retryAt()).isEqualTo(retryAt);
    }

    @Test
    void nonPendingRequestBeforeRetryAtReportsCooldown() {
        Instant retryAt = NOW.plusSeconds(300);

        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.REJECTED, retryAt);

        assertThat(entry.eligibility().allowed()).isFalse();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.COOLDOWN);
        assertThat(entry.eligibility().retryAt()).isEqualTo(retryAt);
    }

    @Test
    void retryAtBoundaryRemainsEligible() {
        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.PENDING, NOW);

        assertThat(entry.eligibility().allowed()).isTrue();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.ELIGIBLE);
    }

    @Test
    void readOnlyIdentityCanReadSnapshotEligibility() {
        StudentAttendanceSnapshotService.Entry entry = snapshot(
                LateCheckinRequestStatus.PENDING, NOW.plusSeconds(300), true);

        assertThat(entry.eligibility().allowed()).isFalse();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.PENDING_CONFIRMATION);
    }

    private StudentAttendanceSnapshotService.Entry snapshot(LateCheckinRequestStatus status, Instant retryAt) {
        return snapshot(status, retryAt, false);
    }

    private StudentAttendanceSnapshotService.Entry snapshot(
            LateCheckinRequestStatus status, Instant retryAt, boolean readOnly) {
        when(lateCheckinRepository.findFirstByStudentIdAndLessonIdAndOriginOrderByUpdatedAtDesc(
                STUDENT_ID, LESSON_ID, LateCheckinRequestOrigin.AUTO_GEO_FAILURE))
                .thenReturn(Optional.of(LateCheckinRequest.builder().studentId(STUDENT_ID).lessonId(LESSON_ID)
                        .groupId(GROUP_ID).status(status).origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE).build()));
        when(pairRepository.findById(any())).thenReturn(Optional.of(CheckinPairStateDocument.builder()
                .studentId(STUDENT_ID).lessonId(LESSON_ID).groupId(GROUP_ID).retryAt(retryAt).build()));
        return service.getSnapshot(new Identity(STUDENT_ID, "STUDENT", GROUP_ID, false, "Student", readOnly), List.of(LESSON_ID))
                .entries().getFirst();
    }
}
