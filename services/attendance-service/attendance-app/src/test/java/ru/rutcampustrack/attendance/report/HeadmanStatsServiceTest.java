package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsFilter;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsSort;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadmanStatsServiceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
    private static final LocalDate TO = LocalDate.of(2026, 4, 30);
    private static final Instant GENERATED_AT = Instant.parse("2026-04-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(GENERATED_AT, ZoneId.of("Europe/Moscow"));

    @Mock private AcademicGrpcClient academicGrpcClient;
    @Mock private ScheduleGrpcClient scheduleGrpcClient;
    @Mock private AttendanceReadPort attendanceReadPort;
    @Mock private ExcuseRepository excuseRepository;
    @Mock private LateCheckinRepository lateCheckinRepository;
    @Mock private DocumentRendererGrpcClient documentRendererGrpcClient;
    @Mock private RequestContext requestContext;

    private HeadmanStatsService service;

    @BeforeEach
    void setUp() {
        service = new HeadmanStatsService(academicGrpcClient, scheduleGrpcClient, attendanceReadPort,
                excuseRepository, lateCheckinRepository, new HeadmanStatsDocxRenderer(),
                new HeadmanStatsTabularRenderer(), documentRendererGrpcClient, requestContext, CLOCK);
        when(requestContext.isHeadman()).thenReturn(false);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(academicGrpcClient.hasAssistantPermission(10L, "VIEW_STATS"))
                .thenReturn(true, true, true, false, false);
    }

    @Test
    void currentViewStatsAssistantGetsFilteredCanonicalMetricsAndUnpagedExportUntilRevoked() {
        stubStatisticsData();
        HeadmanStatsQueryRequest query = new HeadmanStatsQueryRequest(5L, List.of("lecture"), 0, 1,
                List.of(new HeadmanStatsSort("presentPercent", true)),
                List.of(new HeadmanStatsFilter("presentPercent", null, BigDecimal.valueOf(50), null)));

        var result = service.query(query);

        assertThat(result.context().lessonsCount()).isEqualTo(2); // active, cancelled and deleted lessons are not counted as completed
        assertThat(result.filteredStudents()).isEqualTo(2);
        assertThat(result.rows()).extracting(row -> row.displayName()).containsExactly("Gamma");
        assertThat(result.rows().get(0).metrics().present()).satisfies(metric -> {
            assertThat(metric.numerator()).isEqualTo(2);
            assertThat(metric.denominator()).isEqualTo(2);
        });
        assertThat(result.summary().present().numerator()).isEqualTo(3);
        assertThat(result.summary().present().denominator()).isEqualTo(4);
        assertThat(result.summary().presentOrExcused().numerator()).isEqualTo(4);
        assertThat(result.summary().presentOrExcused().denominator()).isEqualTo(4);
        assertThat(result.summary().excused().numerator()).isEqualTo(1);

        var alphaPage = service.query(new HeadmanStatsQueryRequest(5L, List.of("lecture"), 1, 1,
                List.of(new HeadmanStatsSort("presentPercent", true)),
                List.of(new HeadmanStatsFilter("presentPercent", null, BigDecimal.valueOf(50), null))));
        assertThat(alphaPage.rows()).extracting(row -> row.displayName()).containsExactly("Alpha");
        var lateCounts = alphaPage.rows().get(0).lateCheckin();
        assertThat(lateCounts.submitted()).isGreaterThanOrEqualTo(lateCounts.approved() + lateCounts.rejected());
        assertThat(lateCounts.submitted()).isEqualTo(1);
        assertThat(lateCounts.approved()).isEqualTo(1);

        var exported = service.export(new HeadmanStatsExportRequest(5L, List.of("lecture"),
                List.of(new HeadmanStatsSort("presentPercent", true)),
                List.of(new HeadmanStatsFilter("presentPercent", null, BigDecimal.valueOf(50), null)),
                "html"));
        String html = new String(exported.content(), StandardCharsets.UTF_8);
        assertThat(html).contains("Alpha", "Gamma", "3</td><td>4</td><td>75.0%")
                .doesNotContain("Beta");
        assertThat(exported.fileName()).endsWith("_математика.html");
        assertThat(exported.contentType()).isEqualTo("text/html; charset=UTF-8");

        assertThatThrownBy(() -> service.query(query)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.export(new HeadmanStatsExportRequest(5L, List.of("lecture"),
                List.of(), List.of(), "html"))).isInstanceOf(AccessDeniedException.class);
    }

    private void stubStatisticsData() {
        when(academicGrpcClient.getGroup(10L)).thenReturn(GroupResponse.newBuilder()
                .setId(10L).setName("UVPV511").setIsActive(true).build());
        when(academicGrpcClient.getActiveSemester()).thenReturn(SemesterResponse.newBuilder()
                .setId(1L).setName("Spring 2026").setDateFrom(FROM.toString()).setDateTo(TO.toString()).build());
        when(scheduleGrpcClient.getLessonsByGroup(10L, 1L, FROM.toString(), TO.toString()))
                .thenReturn(LessonsResponse.newBuilder()
                        .addLessons(lesson(100L, "2026-04-01", 1, "closed"))
                        .addLessons(lesson(101L, "2026-04-02", 2, "closed"))
                        .addLessons(lesson(102L, "2026-04-03", 3, "started"))
                        .addLessons(lesson(103L, "2026-04-04", 4, "cancelled"))
                        .addLessons(lesson(104L, "2026-04-05", 5, "deleted"))
                        .build());
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(Map.of(5L, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        List<StudentInfo> students = List.of(student(1L, "Alpha"), student(2L, "Beta"), student(3L, "Gamma"));
        when(academicGrpcClient.getGroupMembers(10L)).thenReturn(GroupMembersResponse.newBuilder()
                .addAllStudents(students).build());
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 1), students));
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 2), 1L))
                .thenReturn(roster(LocalDate.of(2026, 4, 2), students));
        when(attendanceReadPort.findByGroupAndDateRange(10L, FROM, TO)).thenReturn(List.of(
                attendance(100L, 1L, LocalDate.of(2026, 4, 1), 1, AttendanceStatus.PRESENT, AttendanceSource.LATE_CHECKIN),
                attendance(101L, 1L, LocalDate.of(2026, 4, 2), 2, AttendanceStatus.EXCUSED, AttendanceSource.HEADMAN),
                attendance(100L, 2L, LocalDate.of(2026, 4, 1), 1, AttendanceStatus.ABSENT, AttendanceSource.HEADMAN),
                attendance(101L, 2L, LocalDate.of(2026, 4, 2), 2, AttendanceStatus.ABSENT, AttendanceSource.HEADMAN),
                attendance(100L, 3L, LocalDate.of(2026, 4, 1), 1, AttendanceStatus.PRESENT, AttendanceSource.STUDENT_GEO),
                attendance(101L, 3L, LocalDate.of(2026, 4, 2), 2, AttendanceStatus.PRESENT, AttendanceSource.HEADMAN)));
        when(lateCheckinRepository.findByGroupIdAndSemesterIdAndStatusIn(10L, 1L,
                List.of(LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.APPROVED,
                        LateCheckinRequestStatus.REJECTED)))
                .thenReturn(List.of(LateCheckinRequest.builder().id("late-1").studentId(1L).groupId(10L)
                        .lessonId(100L).subjectId(5L).subjectName("Математика").subjectType("lecture")
                        .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                        .status(LateCheckinRequestStatus.APPROVED).origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE)
                        .createdAt(GENERATED_AT.minusSeconds(60)).updatedAt(GENERATED_AT).build()));
        when(excuseRepository.findByLessonIdsInAndStatusIn(List.of(100L, 101L),
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED, ExcuseTicketStatus.REJECTED)))
                .thenReturn(List.of(ExcuseTicket.builder().id("excuse-1").studentId(1L).groupId(10L)
                        .semesterId(1L).lessonIds(List.of(101L)).status(ExcuseTicketStatus.SUBMITTED).build()));
    }

    private static LessonResponse lesson(long id, String date, int number, String status) {
        return LessonResponse.newBuilder().setId(id).setGroupId(10L).setSemesterId(1L)
                .setSubjectId(5L).setDate(date).setStartTime("09:00").setLessonNumber(number)
                .setLessonType("lecture").setStatus(status).build();
    }

    private static StudentInfo student(long id, String name) {
        return StudentInfo.newBuilder().setUserId(id).setDisplayName(name).build();
    }

    private static GroupMembersResponse roster(LocalDate date, List<StudentInfo> students) {
        return GroupMembersResponse.newBuilder().setAsOfDate(date.toString()).setSemesterId(1L)
                .addAllStudents(students).build();
    }

    private static AttendanceRecord attendance(long lessonId, long userId, LocalDate date, int number,
                                               AttendanceStatus status, AttendanceSource source) {
        return new AttendanceRecord(lessonId, userId, 10L, 5L, date, number, status, source, null);
    }
}
