package ru.rutcampustrack.attendance.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

class DirectedBotRosterTest {
    @Test
    void actualReportLeftJoinPreservesUnmarkedAndRedactsEditAndExcuseData() {
        AttendanceReadPort records = mock(AttendanceReadPort.class);
        RequestContext httpActor = mock(RequestContext.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        JournalAttachmentPort attachments = mock(JournalAttachmentPort.class);
        ReportService reports =
                new ReportService(
                        records,
                        academic,
                        schedule,
                        mock(SemesterCacheService.class),
                        httpActor,
                        Clock.systemUTC(),
                        attachments);
        when(records.findByLessonId(101L))
                .thenReturn(
                        List.of(
                                record(1, AttendanceStatus.ABSENT),
                                record(2, AttendanceStatus.EXCUSED)));
        var lesson =
                LessonResponse.newBuilder()
                        .setId(101)
                        .setGroupId(33)
                        .setSubjectId(22)
                        .setSemesterId(9)
                        .setDate("2026-10-18")
                        .setStartTime("00:00")
                        .setEndTime("00:30")
                        .setStatus("active")
                        .build();
        var roster =
                GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-10-18")
                        .setSemesterId(9)
                        .addStudents(student(1))
                        .addStudents(student(2))
                        .addStudents(student(3))
                        .build();
        var response = reports.getDirectedBotLessonAttendance(lesson, 33, roster);
        assertThat(response.isEditable()).isFalse();
        assertThat(response.getEntries())
                .extracting(entry -> entry.getStatus())
                .containsExactly("absent", "excused", null);
        assertThat(response.getEntries())
                .allSatisfy(
                        entry -> {
                            assertThat(entry.isEditable()).isFalse();
                            assertThat(entry.getExcuseReason()).isNull();
                            assertThat(entry.getComment()).isNull();
                            assertThat(entry.getAttachmentId()).isNull();
                            assertThat(entry.getApprovedTicket()).isNull();
                        });
        verifyNoInteractions(httpActor, academic, schedule, attachments);
        assertThatThrownBy(() -> reports.getDirectedBotLessonAttendance(lesson, 99, roster))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                reports.getDirectedBotLessonAttendance(
                                        lesson,
                                        33,
                                        roster.toBuilder().setAsOfDate("2026-10-17").build()))
                .isInstanceOf(AcademicServiceUnavailableException.class);
    }

    private static StudentInfo student(long id) {
        return StudentInfo.newBuilder().setUserId(id).setDisplayName("Студент " + id).build();
    }

    private static AttendanceRecord record(long id, AttendanceStatus status) {
        return new AttendanceRecord(
                101L,
                id,
                33L,
                22L,
                LocalDate.of(2026, 10, 18),
                1,
                status,
                AttendanceSource.HEADMAN,
                "private reason",
                "private type",
                "private comment",
                "private attachment",
                "private name",
                "private content",
                12L);
    }
}
