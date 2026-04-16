package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.SubjectStats;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReportService stats calculation logic.
 * Verifies: CANCELLED exclusion, attended counts, overall aggregation, gRPC subject name resolution.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AttendanceReadPort attendanceReadPort;

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @Mock
    private SemesterCacheService semesterCacheService;

    @Mock
    private RequestContext requestContext;

    @InjectMocks
    private ReportService reportService;

    private static final Long USER_ID = 101L;
    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID_1 = 5L;
    private static final Long SUBJECT_ID_2 = 6L;
    private static final Long SEMESTER_ID = 1L;
    private static final Long LESSON_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(requestContext.getUserId()).thenReturn(USER_ID);
        lenient().when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        lenient().when(requestContext.isHeadman()).thenReturn(true);
        lenient().when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(semesterCacheService.getActiveSemesterId()).thenReturn(SEMESTER_ID);
        lenient().when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID_1, "Math", SUBJECT_ID_2, "Physics"));
        // Default: treat every lesson id as alive (exists in schedule-service).
        // Tests that care about orphan filtering should override this stub.
        lenient().when(scheduleGrpcClient.getLessonsByIds(any())).thenAnswer(inv -> {
            java.util.List<Long> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                            .setLessonId(id)
                            .build())
                    .toList();
        });
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AttendanceRecord record(Long subjectId, AttendanceStatus status) {
        return new AttendanceRecord(LESSON_ID, USER_ID, GROUP_ID, subjectId,
                LocalDate.of(2026, 4, 1), 1, status, AttendanceSource.STUDENT_GEO);
    }

    private AttendanceRecord recordForUser(Long userId, Long subjectId, AttendanceStatus status) {
        return new AttendanceRecord(LESSON_ID, userId, GROUP_ID, subjectId,
                LocalDate.of(2026, 4, 1), 1, status, AttendanceSource.STUDENT_GEO);
    }

    // -------------------------------------------------------------------------
    // Test 1: CANCELLED excluded from denominator (RPRT-03 core rule D-11)
    // -------------------------------------------------------------------------

    @Test
    void stats_cancelledExcluded() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        record(SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        record(SUBJECT_ID_1, AttendanceStatus.ABSENT),
                        record(SUBJECT_ID_1, AttendanceStatus.CANCELLED)
                ));

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getSubjects()).hasSize(1);
        SubjectStats stats = response.getSubjects().get(0);
        assertThat(stats.getTotal()).isEqualTo(2);
        assertThat(stats.getPercentage()).isEqualTo(50.0);
        assertThat(response.getOverall().getTotal()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Test 2: EXCUSED and FREE_ATTENDANCE count as attended (RPRT-03 D-10)
    // -------------------------------------------------------------------------

    @Test
    void stats_percentageCalculation() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        record(SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        record(SUBJECT_ID_1, AttendanceStatus.EXCUSED),
                        record(SUBJECT_ID_1, AttendanceStatus.FREE_ATTENDANCE),
                        record(SUBJECT_ID_1, AttendanceStatus.ABSENT)
                ));

        StudentStatsResponse response = reportService.getStudentStats();

        SubjectStats stats = response.getSubjects().get(0);
        assertThat(stats.getTotal()).isEqualTo(4);
        assertThat(stats.getAttended()).isEqualTo(3);
        assertThat(stats.getAbsent()).isEqualTo(1);
        assertThat(stats.getPercentage()).isEqualTo(75.0);
    }

    // -------------------------------------------------------------------------
    // Test 3: Overall aggregation sums all subjects correctly (RPRT-03 overall)
    // -------------------------------------------------------------------------

    @Test
    void stats_overallAggregation() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        record(SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        record(SUBJECT_ID_1, AttendanceStatus.ABSENT),
                        record(SUBJECT_ID_2, AttendanceStatus.PRESENT),
                        record(SUBJECT_ID_2, AttendanceStatus.PRESENT)
                ));

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getOverall().getTotal()).isEqualTo(4);
        assertThat(response.getOverall().getAttended()).isEqualTo(3);
        assertThat(response.getOverall().getAbsent()).isEqualTo(1);
        assertThat(response.getOverall().getPercentage()).isEqualTo(75.0);

        SubjectStats subj1 = response.getSubjects().stream()
                .filter(s -> s.getSubjectId().equals(SUBJECT_ID_1)).findFirst().orElseThrow();
        assertThat(subj1.getTotal()).isEqualTo(2);
        assertThat(subj1.getAttended()).isEqualTo(1);

        SubjectStats subj2 = response.getSubjects().stream()
                .filter(s -> s.getSubjectId().equals(SUBJECT_ID_2)).findFirst().orElseThrow();
        assertThat(subj2.getTotal()).isEqualTo(2);
        assertThat(subj2.getAttended()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Test 4: Subject names resolved via getSubjectsByIds gRPC (RPRT-03 D-13)
    // -------------------------------------------------------------------------

    @Test
    void stats_subjectNameResolvedViaGrpc() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        record(SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        record(SUBJECT_ID_2, AttendanceStatus.PRESENT)
                ));
        when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID_1, "Math", SUBJECT_ID_2, "Physics"));

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getSubjects()).hasSize(2);

        SubjectStats mathStats = response.getSubjects().stream()
                .filter(s -> s.getSubjectId().equals(SUBJECT_ID_1)).findFirst().orElseThrow();
        assertThat(mathStats.getSubjectName()).isEqualTo("Math");

        SubjectStats physicsStats = response.getSubjects().stream()
                .filter(s -> s.getSubjectId().equals(SUBJECT_ID_2)).findFirst().orElseThrow();
        assertThat(physicsStats.getSubjectName()).isEqualTo("Physics");

        verify(academicGrpcClient).getSubjectsByIds(any());
    }

    // -------------------------------------------------------------------------
    // Test 5: Empty records — no subjects, zero overall (edge case)
    // -------------------------------------------------------------------------

    @Test
    void stats_emptyRecords() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of());

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getSubjects()).isEmpty();
        assertThat(response.getOverall().getTotal()).isEqualTo(0);
        assertThat(response.getOverall().getPercentage()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Test 6: All CANCELLED — subjects list empty, overall zero (edge case)
    // -------------------------------------------------------------------------

    @Test
    void stats_allCancelled() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        record(SUBJECT_ID_1, AttendanceStatus.CANCELLED),
                        record(SUBJECT_ID_1, AttendanceStatus.CANCELLED)
                ));

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getSubjects()).isEmpty();
        assertThat(response.getOverall().getTotal()).isEqualTo(0);
        assertThat(response.getOverall().getPercentage()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Test 7: Missing student shows ABSENT in left-join (RPRT-01 D-04)
    // -------------------------------------------------------------------------

    @Test
    void lessonAttendance_missingStudentShowsAbsent() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID_1)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson);

        GroupMembersResponse members = GroupMembersResponse.newBuilder()
                .addStudents(StudentInfo.newBuilder().setUserId(100L).setDisplayName("Student A").build())
                .addStudents(StudentInfo.newBuilder().setUserId(101L).setDisplayName("Student B").build())
                .addStudents(StudentInfo.newBuilder().setUserId(102L).setDisplayName("Student C").build())
                .build();
        when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(members);

        when(attendanceReadPort.findByLessonId(LESSON_ID))
                .thenReturn(List.of(
                        recordForUser(100L, SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        recordForUser(101L, SUBJECT_ID_1, AttendanceStatus.EXCUSED)
                ));

        LessonAttendanceResponse response = reportService.getLessonAttendance(LESSON_ID);

        assertThat(response.getEntries()).hasSize(3);

        var entry102 = response.getEntries().stream()
                .filter(e -> e.getUserId().equals(102L)).findFirst().orElseThrow();
        assertThat(entry102.getStatus()).isEqualTo("absent");
        assertThat(entry102.getSymbol()).isEqualTo("н");
    }

    // -------------------------------------------------------------------------
    // Test 8: getJournal cells include lessonId (Phase 55 D-01)
    // -------------------------------------------------------------------------

    @Test
    void getJournal_cellIncludesLessonId() {
        Long knownLessonId = 42L;
        AttendanceRecord recordWithKnownLesson = new AttendanceRecord(
                knownLessonId, USER_ID, GROUP_ID, SUBJECT_ID_1,
                LocalDate.of(2026, 4, 1), 1, AttendanceStatus.PRESENT, AttendanceSource.STUDENT_GEO
        );

        GroupMembersResponse members = GroupMembersResponse.newBuilder()
                .addStudents(StudentInfo.newBuilder().setUserId(USER_ID).setDisplayName("Student A").build())
                .build();
        when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(members);
        when(attendanceReadPort.findByGroupAndSubject(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(recordWithKnownLesson));

        var response = reportService.getJournal(GROUP_ID, SUBJECT_ID_1,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(response.getStudents()).hasSize(1);
        var cells = response.getStudents().get(0).getRecords();
        assertThat(cells).hasSize(1);
        assertThat(cells.get(0).getLessonId()).isNotNull();
        assertThat(cells.get(0).getLessonId()).isEqualTo(knownLessonId);
    }

    // -------------------------------------------------------------------------
    // Test 9: Regular student (not headman) gets AccessDeniedException (D-05)
    // -------------------------------------------------------------------------

    @Test
    void authorizeHeadmanOrTeacher_regularStudentThrows403() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(false);

        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID_1)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStatus("active")
                .build();
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson);

        assertThatThrownBy(() -> reportService.getLessonAttendance(LESSON_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // -------------------------------------------------------------------------
    // v9.0 dashboard: overall + donut breakdown + weekly + top missed
    // -------------------------------------------------------------------------

    private AttendanceRecord recordOn(Long subjectId, AttendanceStatus status,
                                      AttendanceSource source, LocalDate date) {
        return new AttendanceRecord(LESSON_ID, USER_ID, GROUP_ID, subjectId,
                date, 1, status, source);
    }

    private ru.rutcampustrack.academic.grpc.SemesterResponse semester(String dateFrom) {
        return ru.rutcampustrack.academic.grpc.SemesterResponse.newBuilder()
                .setId(SEMESTER_ID)
                .setDateFrom(dateFrom)
                .setDateTo("2026-06-30")
                .build();
    }

    @Test
    void dashboard_forgotToCheckinCountedFromLateCheckinSource() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-02-09"));
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        recordOn(SUBJECT_ID_1, AttendanceStatus.PRESENT,
                                AttendanceSource.STUDENT_GEO, LocalDate.of(2026, 4, 1)),
                        recordOn(SUBJECT_ID_1, AttendanceStatus.PRESENT,
                                AttendanceSource.LATE_CHECKIN, LocalDate.of(2026, 4, 2)),
                        recordOn(SUBJECT_ID_1, AttendanceStatus.ABSENT,
                                AttendanceSource.AUTO_SCHEDULER, LocalDate.of(2026, 4, 3))));

        var dash = reportService.getStudentDashboard(5);

        assertThat(dash.getBreakdown().getPresent()).isEqualTo(2);
        assertThat(dash.getBreakdown().getForgotToCheckin()).isEqualTo(1);
        assertThat(dash.getBreakdown().getAbsent()).isEqualTo(1);
        assertThat(dash.getOverall().getTotal()).isEqualTo(3);
        assertThat(dash.getOverall().getAttended()).isEqualTo(2);
    }

    @Test
    void dashboard_weeklyGroupsByWeekOfSemester() {
        // Semester starts 2026-02-09 (Monday). Week 1 = 2026-02-09..02-15.
        // April 1 (Wed) is week 8; April 8 (Wed) is week 9.
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-02-09"));
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        recordOn(SUBJECT_ID_1, AttendanceStatus.PRESENT,
                                AttendanceSource.STUDENT_GEO, LocalDate.of(2026, 4, 1)),
                        recordOn(SUBJECT_ID_1, AttendanceStatus.PRESENT,
                                AttendanceSource.STUDENT_GEO, LocalDate.of(2026, 4, 1)),
                        recordOn(SUBJECT_ID_1, AttendanceStatus.ABSENT,
                                AttendanceSource.AUTO_SCHEDULER, LocalDate.of(2026, 4, 8))));

        var weekly = reportService.getStudentDashboard(5).getWeekly();

        assertThat(weekly).hasSize(2);
        assertThat(weekly.get(0).getWeekOfSemester()).isEqualTo(8);
        assertThat(weekly.get(0).getLabel()).isEqualTo("Н8");
        assertThat(weekly.get(0).getAttended()).isEqualTo(2);
        assertThat(weekly.get(0).getPercentage()).isEqualTo(100.0);
        assertThat(weekly.get(1).getWeekOfSemester()).isEqualTo(9);
        assertThat(weekly.get(1).getAttended()).isZero();
    }

    @Test
    void dashboard_topMissedSortedByAbsentDescAndCapped() {
        when(academicGrpcClient.getActiveSemester()).thenReturn(semester("2026-02-09"));
        when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID_1, "Math", SUBJECT_ID_2, "Physics"));
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        recordOn(SUBJECT_ID_1, AttendanceStatus.ABSENT,
                                AttendanceSource.AUTO_SCHEDULER, LocalDate.of(2026, 4, 1)),
                        recordOn(SUBJECT_ID_2, AttendanceStatus.ABSENT,
                                AttendanceSource.AUTO_SCHEDULER, LocalDate.of(2026, 4, 1)),
                        recordOn(SUBJECT_ID_2, AttendanceStatus.ABSENT,
                                AttendanceSource.AUTO_SCHEDULER, LocalDate.of(2026, 4, 2)),
                        recordOn(SUBJECT_ID_2, AttendanceStatus.PRESENT,
                                AttendanceSource.STUDENT_GEO, LocalDate.of(2026, 4, 3))));

        var top = reportService.getStudentDashboard(5).getTopMissed();

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getSubjectId()).isEqualTo(SUBJECT_ID_2);
        assertThat(top.get(0).getAbsent()).isEqualTo(2);
        assertThat(top.get(1).getSubjectId()).isEqualTo(SUBJECT_ID_1);
        assertThat(top.get(1).getAbsent()).isEqualTo(1);
    }
}
