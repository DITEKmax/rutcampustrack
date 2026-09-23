package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.SubjectStats;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonInfo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
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

    @Mock
    private Clock clock;

    @Mock
    private JournalAttachmentPort journalAttachmentPort;

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
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-04-01T09:00:00Z"));
        lenient().when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID_1, "Math", SUBJECT_ID_2, "Physics"));
        lenient().when(academicGrpcClient.getSubjectDetailsByIds(any()))
                .thenReturn(Map.of(
                        SUBJECT_ID_1, subjectDetails("Math", "lecture"),
                        SUBJECT_ID_2, subjectDetails("Physics", "lab")));
        lenient().when(academicGrpcClient.getCurrentTeacherSubjects(USER_ID))
                .thenReturn(statsAuthority());
        // Default: treat every lesson id as alive (exists in schedule-service).
        // Tests that care about orphan filtering should override this stub.
        lenient().when(scheduleGrpcClient.getLessonsByIds(any())).thenAnswer(inv -> {
            java.util.List<Long> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                            .setLessonId(id)
                            .setStatus("closed")
                            .build())
                    .toList();
        });
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AttendanceRecord record(Long subjectId, AttendanceStatus status) {
        return recordAtLesson(LESSON_ID, subjectId, status);
    }

    private AttendanceRecord recordAtLesson(Long lessonId, Long subjectId, AttendanceStatus status) {
        return new AttendanceRecord(lessonId, USER_ID, GROUP_ID, subjectId,
                LocalDate.of(2026, 4, 1), 1, status, AttendanceSource.STUDENT_GEO, null);
    }

    private AttendanceRecord recordForUser(Long userId, Long subjectId, AttendanceStatus status) {
        return new AttendanceRecord(LESSON_ID, userId, GROUP_ID, subjectId,
                LocalDate.of(2026, 4, 1), 1, status, AttendanceSource.STUDENT_GEO, null);
    }

    private static AcademicGrpcClient.SubjectDetails subjectDetails(String name, String type) {
        return new AcademicGrpcClient.SubjectDetails(name, type);
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
    // Test 4: Subject names and types resolved via GetSubjectsByIds gRPC (RPRT-03 D-13)
    // -------------------------------------------------------------------------

    @Test
    void stats_subjectNameResolvedViaGrpc() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        record(SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        record(SUBJECT_ID_2, AttendanceStatus.PRESENT)
                ));
        when(academicGrpcClient.getSubjectDetailsByIds(any()))
                .thenReturn(Map.of(
                        SUBJECT_ID_1, subjectDetails("Math", "lecture"),
                        SUBJECT_ID_2, subjectDetails("Physics", "lab")));

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getSubjects()).hasSize(2);

        SubjectStats mathStats = response.getSubjects().stream()
                .filter(s -> s.getSubjectId().equals(SUBJECT_ID_1)).findFirst().orElseThrow();
        assertThat(mathStats.getSubjectName()).isEqualTo("Math");
        assertThat(mathStats.getSubjectType()).isEqualTo("lecture");

        SubjectStats physicsStats = response.getSubjects().stream()
                .filter(s -> s.getSubjectId().equals(SUBJECT_ID_2)).findFirst().orElseThrow();
        assertThat(physicsStats.getSubjectName()).isEqualTo("Physics");
        assertThat(physicsStats.getSubjectType()).isEqualTo("lab");

        verify(academicGrpcClient).getSubjectDetailsByIds(any());
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

    @Test
    void stats_excludesAuthoritativeCancelledAndTransferredLessons() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(
                        recordAtLesson(11L, SUBJECT_ID_1, AttendanceStatus.ABSENT),
                        recordAtLesson(12L, SUBJECT_ID_1, AttendanceStatus.PRESENT)));
        doReturn(List.of(
                LessonInfo.newBuilder().setLessonId(11L).setStatus("cancelled").build(),
                LessonInfo.newBuilder().setLessonId(12L).setStatus("transferred").build()))
                .when(scheduleGrpcClient).getLessonsByIds(any());

        StudentStatsResponse response = reportService.getStudentStats();

        assertThat(response.getSubjects()).isEmpty();
        assertThat(response.getOverall().getTotal()).isZero();
    }

    @Test
    void stats_failsClosedWhenScheduleAuthorityIsMalformed() {
        when(attendanceReadPort.findByUserId(USER_ID, SEMESTER_ID))
                .thenReturn(List.of(record(SUBJECT_ID_1, AttendanceStatus.PRESENT)));
        doReturn(List.of(LessonInfo.newBuilder().setLessonId(LESSON_ID).build()))
                .when(scheduleGrpcClient).getLessonsByIds(any());

        assertThatThrownBy(() -> reportService.getStudentStats())
                .isInstanceOf(ScheduleServiceUnavailableException.class);
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
                .setSemesterId(SEMESTER_ID)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStartTime("08:00")
                .setEndTime("09:30")
                .setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson);

        GroupMembersResponse members = GroupMembersResponse.newBuilder()
                .setAsOfDate("2026-04-01")
                .setSemesterId(SEMESTER_ID)
                .addStudents(StudentInfo.newBuilder().setUserId(100L).setDisplayName("Student A").build())
                .addStudents(StudentInfo.newBuilder().setUserId(101L).setDisplayName("Student B").build())
                .addStudents(StudentInfo.newBuilder().setUserId(102L).setDisplayName("Student C").build())
                .build();
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID)).thenReturn(members);

        when(attendanceReadPort.findByLessonId(LESSON_ID))
                .thenReturn(List.of(
                        recordForUser(100L, SUBJECT_ID_1, AttendanceStatus.PRESENT),
                        recordForUser(101L, SUBJECT_ID_1, AttendanceStatus.EXCUSED)
                ));

        LessonAttendanceResponse response = reportService.getLessonAttendance(LESSON_ID);

        assertThat(response.getEntries()).hasSize(3);

        var entry100 = response.getEntries().stream()
                .filter(e -> e.getUserId().equals(100L)).findFirst().orElseThrow();
        assertThat(entry100.getStatus()).isEqualTo("present");
        assertThat(entry100.getSymbol()).isEqualTo("+");

        var entry102 = response.getEntries().stream()
                .filter(e -> e.getUserId().equals(102L)).findFirst().orElseThrow();
        assertThat(entry102.getStatus()).isNull();
        assertThat(entry102.getSymbol()).isNull();
    }

    @Test
    void lessonAttendance_hidesExpiredJournalAttachmentMetadata() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID_1)
                .setSemesterId(SEMESTER_ID)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStartTime("08:00")
                .setEndTime("09:30")
                .setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson);
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-04-01")
                        .setSemesterId(SEMESTER_ID)
                        .addStudents(StudentInfo.newBuilder().setUserId(USER_ID).build())
                        .build());
        when(attendanceReadPort.findByLessonId(LESSON_ID)).thenReturn(List.of(
                new AttendanceRecord(LESSON_ID, USER_ID, GROUP_ID, SUBJECT_ID_1,
                        LocalDate.of(2026, 4, 1), 1, AttendanceStatus.EXCUSED,
                        AttendanceSource.HEADMAN_EXCUSE, "Болезнь", "ILLNESS", null,
                        "expired-id", "proof.pdf", "application/pdf", 12L)));
        when(journalAttachmentPort.isAvailable(LESSON_ID, USER_ID, "expired-id"))
                .thenReturn(false);

        LessonAttendanceResponse response = reportService.getLessonAttendance(LESSON_ID);

        var entry = response.getEntries().getFirst();
        assertThat(entry.getStatus()).isEqualTo("excused");
        assertThat(entry.getExcuseReason()).isEqualTo("Болезнь");
        assertThat(entry.getAttachmentId()).isNull();
        assertThat(entry.getAttachmentName()).isNull();
        assertThat(entry.getAttachmentContentType()).isNull();
        assertThat(entry.getAttachmentSize()).isNull();
    }

    @Test
    void lessonAttendance_sameDayBeforeStart_isNotEditable() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID_1)
                .setSemesterId(SEMESTER_ID)
                .setDate("2026-04-01")
                .setStartTime("10:00")
                .setEndTime("11:30")
                .setStatus("planned")
                .build();
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson);
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-04-01")
                        .setSemesterId(SEMESTER_ID)
                        .addStudents(StudentInfo.newBuilder().setUserId(USER_ID).build())
                        .build());
        when(attendanceReadPort.findByLessonId(LESSON_ID)).thenReturn(List.of());
        when(clock.instant()).thenReturn(Instant.parse("2026-04-01T06:30:00Z"));

        LessonAttendanceResponse response = reportService.getLessonAttendance(LESSON_ID);

        assertThat(response.isEditable()).isFalse();
        assertThat(response.getEntries().get(0).isEditable()).isFalse();
        assertThat(response.getEntries().get(0).getEditBlockedReason())
                .isEqualTo("Пара ещё не началась");
    }

    @Test
    void lessonAttendance_missingSemester_failsClosedWithoutCurrentRosterFallback() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID_1)
                .setDate("2026-04-01")
                .setStartTime("08:00")
                .setEndTime("09:30")
                .setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson);

        assertThatThrownBy(() -> reportService.getLessonAttendance(LESSON_ID))
                .isInstanceOf(AcademicServiceUnavailableException.class);

        verify(academicGrpcClient, never()).getGroupMembers(GROUP_ID);
    }

    // -------------------------------------------------------------------------
    // Test 8: getJournal cells include lessonId (Phase 55 D-01)
    // -------------------------------------------------------------------------

    @Test
    void getJournal_cellIncludesLessonId() {
        Long knownLessonId = 42L;
        AttendanceRecord recordWithKnownLesson = new AttendanceRecord(
                knownLessonId, USER_ID, GROUP_ID, SUBJECT_ID_1,
                LocalDate.of(2026, 4, 1), 1, AttendanceStatus.PRESENT, AttendanceSource.STUDENT_GEO,
                null
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

    @Test
    void authorizeTeacherWithCurrentAssignmentAllowsMatchingLesson() {
        when(requestContext.getRole()).thenReturn(UserRole.TEACHER);
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson(SUBJECT_ID_1, GROUP_ID));
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-04-01")
                        .setSemesterId(SEMESTER_ID)
                        .build());
        when(attendanceReadPort.findByLessonId(LESSON_ID)).thenReturn(List.of());

        LessonAttendanceResponse response = reportService.getLessonAttendance(LESSON_ID);

        assertThat(response.getEntries()).isEmpty();
        verify(academicGrpcClient).getCurrentTeacherSubjects(USER_ID);
    }

    @Test
    void authorizeTeacherWithHistoricalDifferentSubjectAllowsCurrentGroup() {
        assertThatCode(() -> reportService.authorizeTeacherLesson(
                lesson(SUBJECT_ID_1, GROUP_ID).toBuilder().setAssignedTeacherId(USER_ID + 1).build(),
                USER_ID)).doesNotThrowAnyException();
        verify(academicGrpcClient).getCurrentTeacherSubjects(USER_ID);
    }

    @Test
    void authorizeTeacherWithDifferentSubjectAllowsLessonInCurrentGroup() {
        assertThatCode(() -> reportService.authorizeTeacherLesson(
                lesson(SUBJECT_ID_2, GROUP_ID), USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void authorizeTeacherWithDifferentGroupDeniesLesson() {
        when(requestContext.getRole()).thenReturn(UserRole.TEACHER);
        when(academicGrpcClient.getCurrentTeacherSubjects(USER_ID))
                .thenReturn(TeacherSubjectsResponse.newBuilder()
                        .addSubjects(TeacherSubjectInfo.newBuilder().setGroupId(GROUP_ID + 1).build())
                        .build());
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(lesson(SUBJECT_ID_1, GROUP_ID));

        assertThatThrownBy(() -> reportService.getLessonAttendance(LESSON_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherStatsUsesClosedLessonsAndServerSideMetricCounts() {
        when(scheduleGrpcClient.getLessonsByIds(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of(
                statsLesson(1L, "closed", "2026-04-01"),
                statsLesson(2L, "active", "2026-04-01"),
                statsLesson(3L, "cancelled", "2026-04-02"),
                statsLesson(4L, "closed", "2026-04-02").toBuilder()
                        .setSubjectId(SUBJECT_ID_2).build()));
        when(academicGrpcClient.getGroup(GROUP_ID))
                .thenReturn(ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                        .setId(GROUP_ID).setName("УИТ-311").build());
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(statsRoster());
        when(attendanceReadPort.findByLessonIds(List.of(1L))).thenReturn(List.of(
                recordForUser(100L, SUBJECT_ID_1, AttendanceStatus.PRESENT),
                recordForUser(101L, SUBJECT_ID_1, AttendanceStatus.EXCUSED)));

        ReportService.TeacherStatsResult result = reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(1L, 2L, 3L, 4L), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID, SUBJECT_ID_1, List.of("lecture"), List.of(), List.of(), SEMESTER_ID),
                USER_ID);

        assertThat(result.semesterId()).isEqualTo(SEMESTER_ID);
        assertThat(result.lessonsCount()).isEqualTo(1);
        assertThat(result.subjectOptions()).extracting(ReportService.TeacherStatsSubjectOption::subjectId)
                .containsExactly(SUBJECT_ID_1, SUBJECT_ID_2);
        assertThat(result.students()).hasSize(2);
        ReportService.TeacherStudentStats present = result.students().stream()
                .filter(row -> row.studentId() == 100L).findFirst().orElseThrow();
        assertThat(present.present().numerator()).isEqualTo(1);
        assertThat(present.present().denominator()).isEqualTo(1);
        assertThat(present.presentOrExcused().percent()).isEqualTo(100.0);
        ReportService.TeacherStudentStats excused = result.students().stream()
                .filter(row -> row.studentId() == 101L).findFirst().orElseThrow();
        assertThat(excused.excused().numerator()).isEqualTo(1);
        assertThat(excused.absent().numerator()).isZero();
        verify(attendanceReadPort).findByLessonIds(List.of(1L));
        verify(scheduleGrpcClient).getLessonsByIds(List.of(1L, 2L, 3L, 4L));
        verify(scheduleGrpcClient, never()).getLessonById(anyLong());
    }

    @Test
    void teacherStatsFutureOnlyKeepsRequestedSemesterAndReturnsNoData() {
        when(scheduleGrpcClient.getLessonsByIds(List.of(2L)))
                .thenReturn(List.of(statsLesson(2L, "active", "2026-04-01")));
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID).setName("УИТ-311").build());
        ReportService.TeacherStatsResult result = reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(2L), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID, SUBJECT_ID_1, List.of("lecture"), List.of(), List.of(), SEMESTER_ID),
                USER_ID);

        assertThat(result.semesterId()).isEqualTo(SEMESTER_ID);
        assertThat(result.lessonsCount()).isZero();
        assertThat(result.students()).isEmpty();
        verify(attendanceReadPort, never()).findByLessonIds(any());
    }

    @Test
    void teacherStatsKeepsAuthorizedGroupsWithNoClosedLessons() {
        when(academicGrpcClient.getCurrentTeacherSubjects(USER_ID)).thenReturn(
                TeacherSubjectsResponse.newBuilder()
                        .addSubjects(TeacherSubjectInfo.newBuilder()
                                .setGroupId(GROUP_ID).setSubjectId(SUBJECT_ID_1).build())
                        .addSubjects(TeacherSubjectInfo.newBuilder()
                                .setGroupId(GROUP_ID + 1).setSubjectId(SUBJECT_ID_2).build())
                        .build());
        when(scheduleGrpcClient.getLessonsByIds(List.of(1L, 2L))).thenReturn(List.of(
                statsLesson(1L, "active", "2026-04-01"),
                statsLesson(2L, "planned", "2026-04-02").toBuilder()
                        .setGroupId(GROUP_ID + 1).setSubjectId(SUBJECT_ID_2).build()));
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID).setName("УИТ-311").build());
        when(academicGrpcClient.getGroup(GROUP_ID + 1)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID + 1).setName("УИТ-312").build());

        ReportService.TeacherStatsResult result = reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(1L, 2L), ReportService.TeacherStatsScope.GROUPS,
                        0, 0, List.of(), List.of(), List.of(), SEMESTER_ID),
                USER_ID);

        assertThat(result.lessonsCount()).isZero();
        assertThat(result.groups()).extracting(ReportService.TeacherGroupStats::groupId)
                .containsExactly(GROUP_ID, GROUP_ID + 1);
        assertThat(result.groups()).allSatisfy(group -> {
            assertThat(group.lessonsCount()).isZero();
            assertThat(group.present().denominator()).isZero();
            assertThat(group.presentOrExcused().denominator()).isZero();
            assertThat(group.excused().denominator()).isZero();
            assertThat(group.absent().denominator()).isZero();
        });
        verify(attendanceReadPort, never()).findByLessonIds(any());
    }

    @Test
    void teacherStatsEmptyBatchUsesCurrentAuthorityAndReturnsAuthorizedGroups() {
        when(academicGrpcClient.getCurrentTeacherSubjects(USER_ID)).thenReturn(
                TeacherSubjectsResponse.newBuilder()
                        .addSubjects(TeacherSubjectInfo.newBuilder()
                                .setGroupId(GROUP_ID).setSubjectId(SUBJECT_ID_1).build())
                        .addSubjects(TeacherSubjectInfo.newBuilder()
                                .setGroupId(GROUP_ID + 1).setSubjectId(SUBJECT_ID_2).build())
                        .build());
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID).setName("УИТ-311").build());
        when(academicGrpcClient.getGroup(GROUP_ID + 1)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID + 1).setName("УИТ-312").build());

        ReportService.TeacherStatsResult result = reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(), ReportService.TeacherStatsScope.GROUPS,
                        0, 0, List.of(), List.of(), List.of(), SEMESTER_ID),
                USER_ID);

        assertThat(result.semesterId()).isEqualTo(SEMESTER_ID);
        assertThat(result.lessonsCount()).isZero();
        assertThat(result.groups()).extracting(ReportService.TeacherGroupStats::groupId)
                .containsExactly(GROUP_ID, GROUP_ID + 1);
        assertThat(result.groups()).allSatisfy(group ->
                assertThat(group.present().denominator()).isZero());
        verify(academicGrpcClient).getCurrentTeacherSubjects(USER_ID);
        verify(scheduleGrpcClient, never()).getLessonsByIds(any());
        verify(attendanceReadPort, never()).findByLessonIds(any());
    }

    @Test
    void teacherStatsEmptyStudentBatchStillRejectsForeignGroup() {
        assertThatThrownBy(() -> reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID + 1, SUBJECT_ID_1, List.of(), List.of(), List.of(), SEMESTER_ID),
                USER_ID)).isInstanceOf(AccessDeniedException.class);

        verify(academicGrpcClient).getCurrentTeacherSubjects(USER_ID);
        verify(scheduleGrpcClient, never()).getLessonsByIds(any());
    }

    @Test
    void teacherStatsRejectsLessonOutsideSelectedStudentScope() {
        when(scheduleGrpcClient.getLessonsByIds(List.of(1L)))
                .thenReturn(List.of(statsLesson(1L, "closed", "2026-04-01")));
        assertThatThrownBy(() -> reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(1L), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID + 1, SUBJECT_ID_1, List.of("lecture"), List.of(), List.of(), SEMESTER_ID),
                USER_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherStatsKeepsHistoricalScheduleLessonForCurrentGroupAuthority() {
        when(scheduleGrpcClient.getLessonsByIds(List.of(1L)))
                .thenReturn(List.of(statsLesson(1L, "closed", "2026-04-01")));
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(statsRoster());
        when(attendanceReadPort.findByLessonIds(List.of(1L))).thenReturn(List.of(
                recordForUser(100L, SUBJECT_ID_1, AttendanceStatus.PRESENT)));
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(
                ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                        .setId(GROUP_ID).setName("УИТ-311").build());

        ReportService.TeacherStatsResult result = reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(1L), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID, SUBJECT_ID_1, List.of("lecture"), List.of(), List.of(), SEMESTER_ID),
                USER_ID);

        assertThat(result.lessonsCount()).isEqualTo(1);
        assertThat(result.students()).hasSize(2);
    }

    @Test
    void teacherStatsAppliesServerFilterBeforeReturningRows() {
        when(scheduleGrpcClient.getLessonsByIds(List.of(1L)))
                .thenReturn(List.of(statsLesson(1L, "closed", "2026-04-01")));
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(statsRoster());
        when(attendanceReadPort.findByLessonIds(List.of(1L))).thenReturn(List.of());
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(
                ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                        .setId(GROUP_ID).setName("УИТ-311").build());

        ReportService.TeacherStatsResult result = reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(1L), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID, SUBJECT_ID_1, List.of("lecture"),
                        List.of(new ReportService.TeacherStatsSort("displayName", false)),
                        List.of(new ReportService.TeacherStatsFilter("displayName", "Петров",
                                null, null, null, null)),
                        SEMESTER_ID),
                USER_ID);

        assertThat(result.students()).extracting(ReportService.TeacherStudentStats::displayName)
                .containsExactly("Б Петров");
    }

    @Test
    void teacherStatsRejectsIncompleteGroupProjectionAsDependencyFailure() {
        when(scheduleGrpcClient.getLessonsByIds(List.of(1L)))
                .thenReturn(List.of(statsLesson(1L, "closed", "2026-04-01")));
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(statsRoster());
        when(attendanceReadPort.findByLessonIds(List.of(1L))).thenReturn(List.of());
        when(academicGrpcClient.getGroup(GROUP_ID)).thenReturn(GroupResponse.getDefaultInstance());

        assertThatThrownBy(() -> reportService.getTeacherStats(
                new ReportService.TeacherStatsQuery(
                        List.of(1L), ReportService.TeacherStatsScope.STUDENTS,
                        GROUP_ID, SUBJECT_ID_1, List.of("lecture"), List.of(), List.of(), SEMESTER_ID),
                USER_ID)).isInstanceOf(AcademicServiceUnavailableException.class);
    }

    private LessonInfo statsLesson(long lessonId, String status, String date) {
        return LessonInfo.newBuilder()
                .setLessonId(lessonId)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID_1)
                .setDate(date)
                .setStartsAt(date + "T08:00:00")
                .setAssignmentId(900L)
                .setSemesterId(SEMESTER_ID)
                .setTeacherId(USER_ID)
                .setLessonType("lecture")
                .setStatus(status)
                .build();
    }

    private TeacherSubjectsResponse statsAuthority() {
        return TeacherSubjectsResponse.newBuilder()
                .addSubjects(TeacherSubjectInfo.newBuilder()
                        .setSubjectId(SUBJECT_ID_1)
                        .setGroupId(GROUP_ID)
                        .setAssignmentId(900L)
                        .setSemesterId(SEMESTER_ID)
                        .setLessonType("lecture")
                        .setValidFrom("2026-01-01")
                        .setValidUntilExclusive("2026-07-01")
                        .build())
                .build();
    }

    private GroupMembersResponse statsRoster() {
        return GroupMembersResponse.newBuilder()
                .setAsOfDate("2026-04-01")
                .setSemesterId(SEMESTER_ID)
                .addStudents(StudentInfo.newBuilder().setUserId(100L).setDisplayName("А Иванов").build())
                .addStudents(StudentInfo.newBuilder().setUserId(101L).setDisplayName("Б Петров").build())
                .build();
    }

    private LessonResponse lesson(Long subjectId, Long groupId) {
        return LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(groupId)
                .setSubjectId(subjectId)
                .setSemesterId(SEMESTER_ID)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStartTime("08:00")
                .setEndTime("09:30")
                .setStatus("active")
                .setAssignmentId(900L)
                .setAssignedTeacherId(USER_ID)
                .setLessonType("lecture")
                .build();
    }

    // -------------------------------------------------------------------------
    // v9.0 dashboard: overall + donut breakdown + weekly + top missed
    // -------------------------------------------------------------------------

    private AttendanceRecord recordOn(Long subjectId, AttendanceStatus status,
                                      AttendanceSource source, LocalDate date) {
        return new AttendanceRecord(LESSON_ID, USER_ID, GROUP_ID, subjectId,
                date, 1, status, source, null);
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
        when(academicGrpcClient.getSubjectDetailsByIds(any()))
                .thenReturn(Map.of(
                        SUBJECT_ID_1, subjectDetails("Math", "lecture"),
                        SUBJECT_ID_2, subjectDetails("Physics", "practice")));
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
        assertThat(top.get(0).getSubjectName()).isEqualTo("Physics");
        assertThat(top.get(0).getSubjectType()).isEqualTo("practice");
        assertThat(top.get(0).getAbsent()).isEqualTo(2);
        assertThat(top.get(1).getSubjectId()).isEqualTo(SUBJECT_ID_1);
        assertThat(top.get(1).getSubjectType()).isEqualTo("lecture");
        assertThat(top.get(1).getAbsent()).isEqualTo(1);
    }
}
