package ru.rutcampustrack.attendance.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Identity;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.Instant;
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
    private final ExcuseRepository excuseRepository = mock(ExcuseRepository.class);
    private final CheckinPairStateRepository pairRepository = mock(CheckinPairStateRepository.class);
    private final StudentAttendanceSnapshotService service = new StudentAttendanceSnapshotService(
            scheduleGrpcClient, attendanceRepository, lateCheckinRepository, excuseRepository, pairRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(LessonResponse.newBuilder()
                .setId(LESSON_ID).setGroupId(GROUP_ID).setDate("2026-09-06")
                .setStartTime("09:00").setEndTime("11:00").setStatus("active").build());
        when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID)).thenReturn(Optional.empty());
    }

    @Test
    void pendingAutoRequestBeforeRetryAtReportsCooldown() {
        Instant retryAt = NOW.plusSeconds(300);

        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.PENDING, retryAt);

        assertThat(entry.eligibility().allowed()).isFalse();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.COOLDOWN);
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
    void pendingAutoRequestAtRetryBoundaryAllowsNewGeoCheck() {
        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.PENDING, NOW);

        assertThat(entry.eligibility().allowed()).isTrue();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.ELIGIBLE);
        assertThat(entry.eligibility().retryAt()).isNull();
    }

    @Test
    void pendingAutoRequestAfterRetryAtAllowsNewGeoCheck() {
        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.PENDING, NOW.minusSeconds(1));

        assertThat(entry.eligibility()).isEqualTo(new StudentAttendanceSnapshotService.Eligibility(
                true, StudentAttendanceSnapshotService.EligibilityReason.ELIGIBLE, null));
    }

    @Test
    void genericPendingRequestRemainsBlockedEvenWithHistoricalAutoRequest() {
        snapshot(LateCheckinRequestStatus.REJECTED, NOW);
        when(lateCheckinRepository.findFirstByStudentIdAndLessonIdAndStatus(
                STUDENT_ID, LESSON_ID, LateCheckinRequestStatus.PENDING))
                .thenReturn(Optional.of(LateCheckinRequest.builder().status(LateCheckinRequestStatus.PENDING).build()));

        StudentAttendanceSnapshotService.Entry entry = service.getSnapshot(
                new Identity(STUDENT_ID, "STUDENT", GROUP_ID, false, "Student", false), List.of(LESSON_ID))
                .entries().getFirst();

        assertThat(entry.eligibility()).isEqualTo(new StudentAttendanceSnapshotService.Eligibility(
                false, StudentAttendanceSnapshotService.EligibilityReason.PENDING_CONFIRMATION, null));
    }

    @Test
    void pendingAutoRequestDoesNotBypassHeadmanAbsence() {
        when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID))
                .thenReturn(Optional.of(AttendanceDocument.builder()
                        .status(AttendanceStatus.ABSENT).source(AttendanceSource.HEADMAN).build()));

        assertThat(snapshot(LateCheckinRequestStatus.PENDING, NOW).eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.HEADMAN_ABSENT_REQUIRES_APPEAL);
    }

    @Test
    void submittedExcuseBlocksPendingAutoRetryAfterCooldown() {
        when(excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                STUDENT_ID, List.of(LESSON_ID), List.of(ExcuseTicketStatus.SUBMITTED))).thenReturn(true);

        assertThat(snapshot(LateCheckinRequestStatus.PENDING, NOW).eligibility())
                .isEqualTo(new StudentAttendanceSnapshotService.Eligibility(
                        false, StudentAttendanceSnapshotService.EligibilityReason.PENDING_CONFIRMATION, null));
    }

    @Test
    void pendingAutoRequestDoesNotBypassLessonGuards() {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(LESSON_ID);
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson.toBuilder().setStatus("cancelled").build());
        assertThat(snapshot(LateCheckinRequestStatus.PENDING, NOW).eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.LESSON_CANCELLED);

        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson.toBuilder().setIsGeoBlocked(true).build());
        assertThat(snapshot(LateCheckinRequestStatus.PENDING, NOW).eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.GEO_BLOCKED);

        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson.toBuilder().setEndTime("09:00").build());
        assertThat(snapshot(LateCheckinRequestStatus.PENDING, NOW).eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.WINDOW_CLOSED);
    }

    @Test
    void readOnlyIdentityCanReadSnapshotEligibility() {
        StudentAttendanceSnapshotService.Entry entry = snapshot(
                LateCheckinRequestStatus.PENDING, NOW.plusSeconds(300), true);

        assertThat(entry.eligibility().allowed()).isFalse();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.COOLDOWN);
    }

    @Test
    void closedMissingMarkHasAuthoritativeAbsenceWithoutInventedTime() {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(LESSON_ID);
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson.toBuilder().setStatus("closed").build());

        StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.REJECTED, NOW);

        assertThat(entry.attendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(entry.attendanceSource()).isEqualTo(AttendanceSource.AUTO_SCHEDULER);
        assertThat(entry.markedAt()).isNull();
        assertThat(entry.eligibility().reason())
                .isEqualTo(StudentAttendanceSnapshotService.EligibilityReason.WINDOW_CLOSED);
    }

    @Test
    void absenceFallbackDoesNotApplyToUnheldOrExcludedLessons() {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(LESSON_ID);
        for (String status : List.of("planned", "active", "cancelled", "transferred")) {
            when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson.toBuilder().setStatus(status).build());
            StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.REJECTED, NOW);
            assertThat(entry.attendanceStatus()).as(status).isNull();
            assertThat(entry.attendanceSource()).as(status).isNull();
            assertThat(entry.markedAt()).as(status).isNull();
        }
    }

    @Test
    void closedFallbackPreservesStoredStatusSourceAndTimestamp() {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(LESSON_ID);
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson.toBuilder().setStatus("closed").build());
        Instant markedAt = NOW.minusSeconds(30);
        for (AttendanceStatus status : List.of(AttendanceStatus.PRESENT, AttendanceStatus.EXCUSED, AttendanceStatus.ABSENT)) {
            when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID))
                    .thenReturn(Optional.of(AttendanceDocument.builder().status(status)
                            .source(AttendanceSource.HEADMAN).updatedAt(markedAt).build()));
            StudentAttendanceSnapshotService.Entry entry = snapshot(LateCheckinRequestStatus.REJECTED, NOW);
            assertThat(entry.attendanceStatus()).isEqualTo(status);
            assertThat(entry.attendanceSource()).isEqualTo(AttendanceSource.HEADMAN);
            assertThat(entry.markedAt()).isEqualTo(markedAt);
        }
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
        when(lateCheckinRepository.findFirstByStudentIdAndLessonIdAndStatus(
                STUDENT_ID, LESSON_ID, LateCheckinRequestStatus.PENDING))
                .thenReturn(status == LateCheckinRequestStatus.PENDING
                        ? Optional.of(LateCheckinRequest.builder().status(status)
                        .origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE).build()) : Optional.empty());
        when(pairRepository.findById(any())).thenReturn(Optional.of(CheckinPairStateDocument.builder()
                .studentId(STUDENT_ID).lessonId(LESSON_ID).groupId(GROUP_ID).retryAt(retryAt).build()));
        return service.getSnapshot(new Identity(STUDENT_ID, "STUDENT", GROUP_ID, false, "Student", readOnly), List.of(LESSON_ID))
                .entries().getFirst();
    }
}
