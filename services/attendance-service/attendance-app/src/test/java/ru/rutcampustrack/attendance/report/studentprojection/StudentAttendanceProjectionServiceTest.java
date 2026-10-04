package ru.rutcampustrack.attendance.report.studentprojection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.AcademicSubjectInfo;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.academic.grpc.StudentProjectionMembershipSegment;
import ru.rutcampustrack.academic.grpc.StudentProjectionRankVisibility;
import ru.rutcampustrack.academic.grpc.StudentProjectionScopeResponse;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsByGroupRequest;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAttendanceProjectionServiceTest {

    private static final long SEMESTER_ID = 7L;
    private static final long OWN_ID = 100L;
    private static final long RANK_GROUP_ID = 10L;
    private static final long SUBJECT_ID = 501L;

    @Mock
    private AttendanceReadPort attendanceReadPort;

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    private StudentAttendanceProjectionService service;

    @BeforeEach
    void setUp() {
        service = new StudentAttendanceProjectionService(
                attendanceReadPort,
                academicGrpcClient,
                scheduleGrpcClient,
                Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void joinsOwnMembershipAndUsesFullSemesterCohortForRank() {
        InternalJwtClaims claims = new InternalJwtClaims(
                OWN_ID,
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                1L,
                1L,
                "STUDENT",
                "ACTIVE",
                RANK_GROUP_ID,
                false,
                false);
        when(academicGrpcClient.resolveStudentProjectionScope(SEMESTER_ID))
                .thenReturn(scope());
        when(scheduleGrpcClient.getLessonsByGroup(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    LessonsByGroupRequest request = LessonsByGroupRequest.newBuilder()
                            .setGroupId(invocation.getArgument(0, Long.class))
                            .setSemesterId(invocation.getArgument(1, Long.class))
                            .setDateFrom(invocation.getArgument(2, String.class))
                            .setDateTo(invocation.getArgument(3, String.class))
                            .build();
                    if ("2026-09-10".equals(request.getDateFrom())) {
                        return LessonsResponse.newBuilder()
                                .addLessons(lesson(11L, 101L, "2026-09-15", "closed"))
                                .addLessons(lesson(12L, 102L, "2026-09-16", "closed"))
                                .addLessons(lesson(13L, 103L, "2026-09-17", "cancelled"))
                            .addLessons(lesson(16L, 106L, "2026-09-19", "transferred"))
                                .addLessons(lesson(15L, 105L, "2026-09-18", "closed"))
                                .addLessons(lesson(14L, 104L, "2026-09-21", "planned"))
                                .build();
                    }
                    return LessonsResponse.newBuilder()
                            .addLessons(lesson(10L, 100L, "2026-09-05", "closed"))
                            .addLessons(lesson(11L, 101L, "2026-09-15", "closed"))
                            .addLessons(lesson(12L, 102L, "2026-09-16", "closed"))
                            .addLessons(lesson(13L, 103L, "2026-09-17", "cancelled"))
                            .addLessons(lesson(16L, 106L, "2026-09-19", "transferred"))
                            .addLessons(lesson(15L, 105L, "2026-09-18", "closed"))
                            .addLessons(lesson(14L, 104L, "2026-09-21", "planned"))
                            .build();
                });
        when(attendanceReadPort.findByUserId(OWN_ID, SEMESTER_ID)).thenReturn(List.of(
                mark(11L, OWN_ID, AttendanceStatus.PRESENT, "2026-09-15"),
                mark(12L, OWN_ID, AttendanceStatus.ABSENT, "2026-09-16")));
        when(attendanceReadPort.findByUserIds(List.of(OWN_ID, 200L, 300L), SEMESTER_ID))
                .thenReturn(Map.of(
                        OWN_ID, List.of(
                                mark(11L, OWN_ID, AttendanceStatus.PRESENT, "2026-09-15"),
                                mark(12L, OWN_ID, AttendanceStatus.ABSENT, "2026-09-16")),
                        200L, List.of(
                                mark(10L, 200L, AttendanceStatus.PRESENT, "2026-09-05"),
                                mark(11L, 200L, AttendanceStatus.PRESENT, "2026-09-15"),
                                mark(12L, 200L, AttendanceStatus.ABSENT, "2026-09-16"),
                                mark(15L, 200L, AttendanceStatus.ABSENT, "2026-09-18")),
                        300L, List.of(
                                mark(10L, 300L, AttendanceStatus.ABSENT, "2026-09-05"),
                                mark(11L, 300L, AttendanceStatus.ABSENT, "2026-09-15"),
                                mark(12L, 300L, AttendanceStatus.ABSENT, "2026-09-16"),
                                mark(15L, 300L, AttendanceStatus.ABSENT, "2026-09-18"))));

        StudentAttendanceProjectionService.Projection projection = service.project(
                claims,
                SEMESTER_ID,
                null,
                "days",
                List.of());

        assertThat(projection.metrics().plannedCount()).isEqualTo(4);
        assertThat(projection.metrics().heldCount()).isEqualTo(3);
        assertThat(projection.metrics().presentCount()).isEqualTo(1);
        assertThat(projection.metrics().absentCount()).isEqualTo(2);
        assertThat(projection.metrics().missingClosedCount()).isEqualTo(1);
        var missing = projection.days().stream().flatMap(day -> day.lessons().stream())
                .filter(lesson -> lesson.lessonId() == 15L).findFirst().orElseThrow();
        assertThat(missing.mark()).isNull();
        assertThat(missing.uiStatus()).isEqualTo("ABSENT");
        assertThat(missing.historyStatus()).isEqualTo("ABSENT");
        assertThat(projection.graph().days().stream()
                .filter(point -> point.dateFrom().toString().equals("2026-09-18")))
                .singleElement().satisfies(point -> {
                    assertThat(point.state()).isEqualTo("DATA");
                    assertThat(point.metrics().absentCount()).isEqualTo(1);
                    assertThat(point.metrics().missingClosedCount()).isEqualTo(1);
                });
        assertThat(projection.subjects().getFirst().typeCards().getFirst().history())
                .extracting(StudentAttendanceProjectionService.HistorySegment::status)
                .containsExactly("PRESENT", "ABSENT", "ABSENT", "FUTURE");

        assertThat(projection.ownRank().position()).isEqualTo(2);
        assertThat(projection.ownRank().participantCount()).isEqualTo(3);
        assertThat(projection.days()).flatExtracting(StudentAttendanceProjectionService.Day::lessons)
                .extracting(StudentAttendanceProjectionService.Lesson::lessonId)
                .containsExactly(11L, 12L, 13L, 15L, 14L);

        verify(scheduleGrpcClient).getLessonsByGroup(
                RANK_GROUP_ID, SEMESTER_ID, "2026-09-01", "2026-09-30");
    }

    @Test
    void lateJoinerRankUsesCanonicalSemesterDenominator() {
        InternalJwtClaims claims = new InternalJwtClaims(
                OWN_ID,
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                1L,
                1L,
                "STUDENT",
                "ACTIVE",
                RANK_GROUP_ID,
                false,
                false);
        StudentProjectionScopeResponse rankScope = scope().toBuilder()
                .clearActiveRosterUserIds()
                .addActiveRosterUserIds(OWN_ID)
                .addActiveRosterUserIds(200L)
                .build();
        when(academicGrpcClient.resolveStudentProjectionScope(SEMESTER_ID))
                .thenReturn(rankScope);
        when(scheduleGrpcClient.getLessonsByGroup(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String from = invocation.getArgument(2, String.class);
                    if ("2026-09-10".equals(from)) {
                        return LessonsResponse.newBuilder()
                                .addLessons(lesson(11L, 111L, "2026-09-15", "closed"))
                                .addLessons(lesson(12L, 112L, "2026-09-16", "planned"))
                                .build();
                    }
                    return LessonsResponse.newBuilder()
                            .addLessons(lesson(10L, 110L, "2026-09-05", "closed", 502L))
                            .addLessons(lesson(11L, 111L, "2026-09-15", "closed"))
                            .addLessons(lesson(12L, 112L, "2026-09-16", "planned"))
                            .build();
                });
        when(attendanceReadPort.findByUserId(OWN_ID, SEMESTER_ID)).thenReturn(List.of(
                mark(11L, OWN_ID, AttendanceStatus.PRESENT, "2026-09-15")));
        when(attendanceReadPort.findByUserIds(List.of(OWN_ID, 200L), SEMESTER_ID))
                .thenReturn(Map.of(
                        OWN_ID, List.of(mark(11L, OWN_ID, AttendanceStatus.PRESENT, "2026-09-15")),
                        200L, List.of(
                                mark(10L, 200L, AttendanceStatus.PRESENT, "2026-09-05", 502L),
                                mark(11L, 200L, AttendanceStatus.PRESENT, "2026-09-15"))));

        StudentAttendanceProjectionService.Projection projection = service.project(
                claims,
                SEMESTER_ID,
                null,
                "weeks",
                List.of());

        assertThat(projection.metrics().heldCount()).isEqualTo(1);
        assertThat(projection.metrics().presentCount()).isEqualTo(1);
        // The viewer owns one of two canonical held lessons (1/2); the peer
        // owns both (2/2). The public response exposes only the viewer rank.
        assertThat(projection.ownRank().position()).isEqualTo(2);
        assertThat(projection.ownRank().participantCount()).isEqualTo(2);
        verify(scheduleGrpcClient).getLessonsByGroup(
                RANK_GROUP_ID, SEMESTER_ID, "2026-09-01", "2026-09-30");
    }

    @Test
    void rankingDefaultsToPageContainingOwnRowAndOnlyReturnsAuthorizedCohortNames() {
        InternalJwtClaims claims = new InternalJwtClaims(
                OWN_ID,
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                1L,
                1L,
                "STUDENT",
                "ACTIVE",
                RANK_GROUP_ID,
                false,
                false);
        StudentProjectionScopeResponse rankingScope = scope().toBuilder()
                .addActiveRosterUserIds(400L)
                .build();
        when(academicGrpcClient.resolveStudentProjectionScope(SEMESTER_ID)).thenReturn(rankingScope);
        when(academicGrpcClient.getGroupMembers(RANK_GROUP_ID)).thenReturn(GroupMembersResponse.newBuilder()
                .addStudents(student(OWN_ID, "Текущий студент"))
                .addStudents(student(200L, "Студент группы"))
                .addStudents(student(300L, "Ещё один студент"))
                .addStudents(student(400L, "Первый студент"))
                .addStudents(student(999L, "Вне roster scope"))
                .build());
        when(scheduleGrpcClient.getLessonsByGroup(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String from = invocation.getArgument(2, String.class);
                    if ("2026-09-10".equals(from)) {
                        return LessonsResponse.newBuilder()
                                .addLessons(lesson(11L, 101L, "2026-09-15", "closed"))
                                .addLessons(lesson(12L, 102L, "2026-09-16", "closed"))
                                .addLessons(lesson(13L, 103L, "2026-09-17", "cancelled"))
                            .addLessons(lesson(16L, 106L, "2026-09-19", "transferred"))
                                .addLessons(lesson(15L, 105L, "2026-09-18", "closed"))
                                .addLessons(lesson(14L, 104L, "2026-09-21", "planned"))
                                .build();
                    }
                    return LessonsResponse.newBuilder()
                            .addLessons(lesson(10L, 100L, "2026-09-05", "closed"))
                            .addLessons(lesson(11L, 101L, "2026-09-15", "closed"))
                            .addLessons(lesson(12L, 102L, "2026-09-16", "closed"))
                            .addLessons(lesson(13L, 103L, "2026-09-17", "cancelled"))
                            .addLessons(lesson(16L, 106L, "2026-09-19", "transferred"))
                            .addLessons(lesson(15L, 105L, "2026-09-18", "closed"))
                            .addLessons(lesson(14L, 104L, "2026-09-21", "planned"))
                            .build();
                });
        when(attendanceReadPort.findByUserIds(List.of(OWN_ID, 200L, 300L, 400L), SEMESTER_ID))
                .thenReturn(Map.of(
                        OWN_ID, List.of(
                                mark(11L, OWN_ID, AttendanceStatus.PRESENT, "2026-09-15"),
                                mark(12L, OWN_ID, AttendanceStatus.ABSENT, "2026-09-16")),
                        200L, List.of(
                                mark(10L, 200L, AttendanceStatus.PRESENT, "2026-09-05"),
                                mark(11L, 200L, AttendanceStatus.PRESENT, "2026-09-15"),
                                mark(12L, 200L, AttendanceStatus.ABSENT, "2026-09-16"),
                                mark(15L, 200L, AttendanceStatus.ABSENT, "2026-09-18")),
                        300L, List.of(
                                mark(10L, 300L, AttendanceStatus.ABSENT, "2026-09-05"),
                                mark(11L, 300L, AttendanceStatus.ABSENT, "2026-09-15"),
                                mark(12L, 300L, AttendanceStatus.ABSENT, "2026-09-16"),
                                mark(15L, 300L, AttendanceStatus.ABSENT, "2026-09-18")),
                        400L, List.of(
                                mark(10L, 400L, AttendanceStatus.PRESENT, "2026-09-05"),
                                mark(11L, 400L, AttendanceStatus.PRESENT, "2026-09-15"),
                                mark(12L, 400L, AttendanceStatus.ABSENT, "2026-09-16"),
                                mark(15L, 400L, AttendanceStatus.PRESENT, "2026-09-18"))));

        StudentAttendanceProjectionService.RankingPage page = service.ranking(
                claims, SEMESTER_ID, null, 2);

        assertThat(page.available()).isTrue();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.ownPosition()).isEqualTo(3);
        assertThat(page.rows())
                .extracting(StudentAttendanceProjectionService.RankingRow::studentId,
                        StudentAttendanceProjectionService.RankingRow::name,
                        StudentAttendanceProjectionService.RankingRow::position,
                        StudentAttendanceProjectionService.RankingRow::percentage,
                        StudentAttendanceProjectionService.RankingRow::isSelf)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(OWN_ID, "Текущий студент", 3,
                                new java.math.BigDecimal("25.00"), true),
                        org.assertj.core.groups.Tuple.tuple(300L, "Ещё один студент", 4,
                                new java.math.BigDecimal("0.00"), false));
        verify(academicGrpcClient).getGroupMembers(RANK_GROUP_ID);
    }

    @Test
    void hiddenRankingReturnsEmptyUnavailablePageWithoutReadingPeers() {
        InternalJwtClaims claims = new InternalJwtClaims(
                OWN_ID,
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                1L,
                1L,
                "STUDENT",
                "ACTIVE",
                RANK_GROUP_ID,
                false,
                false);
        when(academicGrpcClient.resolveStudentProjectionScope(SEMESTER_ID))
                .thenReturn(scope().toBuilder()
                        .setRankVisibility(StudentProjectionRankVisibility
                                .STUDENT_PROJECTION_RANK_VISIBILITY_HIDDEN)
                        .build());

        StudentAttendanceProjectionService.RankingPage page = service.ranking(
                claims, SEMESTER_ID, null, 20);

        assertThat(page).isEqualTo(new StudentAttendanceProjectionService.RankingPage(
                false, 0, 20, 0, null, List.of()));
        verifyNoInteractions(attendanceReadPort, scheduleGrpcClient);
    }

    private static StudentProjectionScopeResponse scope() {
        return StudentProjectionScopeResponse.newBuilder()
                .setStudentId(OWN_ID)
                .setSemesterId(SEMESTER_ID)
                .setDateFrom("2026-09-01")
                .setDateTo("2026-09-30")
                .setServerDate("2026-09-20")
                .addActiveRosterUserIds(OWN_ID)
                .addActiveRosterUserIds(200L)
                .addActiveRosterUserIds(300L)
                .addOwnMembershipSegments(StudentProjectionMembershipSegment.newBuilder()
                        .setGroupId(RANK_GROUP_ID)
                        .setDateFrom("2026-09-10")
                        .setDateUntilExclusive("2026-10-01")
                        .addSubjectIds(SUBJECT_ID)
                        .build())
                .addSubjects(AcademicSubjectInfo.newBuilder()
                        .setSubjectId(SUBJECT_ID)
                        .setSubjectName("Математика")
                        .setSubjectType("discipline")
                        .setGroupId(RANK_GROUP_ID)
                        .addLessonTypes("LECTURE")
                        .build())
                .addRankSubjects(AcademicSubjectInfo.newBuilder()
                        .setSubjectId(SUBJECT_ID)
                        .setSubjectName("Математика")
                        .setSubjectType("discipline")
                        .setGroupId(RANK_GROUP_ID)
                        .addLessonTypes("LECTURE")
                        .build())
                .addRankSubjects(AcademicSubjectInfo.newBuilder()
                        .setSubjectId(502L)
                        .setSubjectName("Физика")
                        .setSubjectType("discipline")
                        .setGroupId(RANK_GROUP_ID)
                        .addLessonTypes("LECTURE")
                        .build())
                .setRankVisibility(StudentProjectionRankVisibility
                        .STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE)
                .setRankGroupId(RANK_GROUP_ID)
                .setRankEligible(true)
                .build();
    }

    private static LessonResponse lesson(
            long lessonId,
            long occurrenceId,
            String date,
            String status) {
        return lesson(lessonId, occurrenceId, date, status, SUBJECT_ID);
    }

    private static LessonResponse lesson(
            long lessonId,
            long occurrenceId,
            String date,
            String status,
            long subjectId) {
        return LessonResponse.newBuilder()
                .setId(lessonId)
                .setOccurrenceId(occurrenceId)
                .setGroupId(RANK_GROUP_ID)
                .setSubjectId(subjectId)
                .setSemesterId(SEMESTER_ID)
                .setDate(date)
                .setLessonNumber((int) (lessonId - 9))
                .setStartTime("10:00")
                .setEndTime("11:30")
                .setStatus(status)
                .setLessonType("LECTURE")
                .build();
    }

    private static StudentInfo student(long id, String name) {
        return StudentInfo.newBuilder().setUserId(id).setDisplayName(name).build();
    }

    private static AttendanceRecord mark(
            long lessonId,
            long userId,
            AttendanceStatus status,
            String date) {
        return mark(lessonId, userId, status, date, SUBJECT_ID);
    }

    private static AttendanceRecord mark(
            long lessonId,
            long userId,
            AttendanceStatus status,
            String date,
            long subjectId) {
        return new AttendanceRecord(
                lessonId,
                userId,
                RANK_GROUP_ID,
                subjectId,
                java.time.LocalDate.parse(date),
                null,
                status,
                null,
                null);
    }
}
