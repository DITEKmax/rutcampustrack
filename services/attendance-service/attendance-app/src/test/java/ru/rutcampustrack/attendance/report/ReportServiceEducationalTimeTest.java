package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.web.time.EducationalTime;

class ReportServiceEducationalTimeTest {
    private static final Instant BEFORE_START = Instant.parse("2026-10-14T05:29:59Z");
    private static final Instant AT_START = Instant.parse("2026-10-14T05:30:00Z");

    @Test
    void lessonRosterUsesSelectedEducationalTimeAndFallsBackToRealClock() {
        assertRosterEditable(BEFORE_START, null, false);
        assertRosterEditable(AT_START, null, true);
        assertRosterEditable(AT_START, BEFORE_START, false);
        assertRosterEditable(Instant.parse("2026-10-13T21:17:00Z"), AT_START, true);
    }

    private void assertRosterEditable(
            Instant realNow, Instant educationalNow, boolean expectedEditable) {
        long lessonId = 1L;
        long groupId = 10L;
        long semesterId = 2L;
        long studentId = 101L;
        LocalDate lessonDate = LocalDate.of(2026, 10, 14);
        AttendanceReadPort attendance = mock(AttendanceReadPort.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        RequestContext actor = mock(RequestContext.class);

        when(actor.getRole()).thenReturn(UserRole.STUDENT);
        when(actor.getGroupId()).thenReturn(groupId);
        when(academic.hasAssistantPermission(groupId, "VIEW_STATS")).thenReturn(true);
        when(academic.hasAssistantPermission(groupId, "MARK_ATTENDANCE")).thenReturn(true);
        when(academic.getHeadmanJournalSemesterState(groupId, semesterId))
                .thenReturn(SemesterStateResponse.newBuilder().setId(semesterId).build());
        when(schedule.getLessonById(lessonId))
                .thenReturn(
                        LessonResponse.newBuilder()
                                .setId(lessonId)
                                .setGroupId(groupId)
                                .setSubjectId(5L)
                                .setSemesterId(semesterId)
                                .setDate(lessonDate.toString())
                                .setLessonNumber(1)
                                .setStartTime("08:30")
                                .setEndTime("09:50")
                                .setStatus("planned")
                                .build());
        when(academic.getGroupMembers(groupId, lessonDate, semesterId))
                .thenReturn(
                        GroupMembersResponse.newBuilder()
                                .setAsOfDate(lessonDate.toString())
                                .setSemesterId(semesterId)
                                .addStudents(StudentInfo.newBuilder().setUserId(studentId))
                                .build());
        when(attendance.findByLessonId(lessonId)).thenReturn(List.of());

        try (var context = new AnnotationConfigApplicationContext()) {
            var beans = context.getBeanFactory();
            beans.registerSingleton("attendance", attendance);
            beans.registerSingleton("academic", academic);
            beans.registerSingleton("schedule", schedule);
            beans.registerSingleton("semesters", mock(SemesterCacheService.class));
            beans.registerSingleton("actor", actor);
            beans.registerSingleton("clock", Clock.fixed(realNow, ZoneOffset.UTC));
            beans.registerSingleton("attachments", mock(JournalAttachmentPort.class));
            beans.registerSingleton("tickets", mock(HeadmanApprovedTicketResolver.class));
            if (educationalNow != null) {
                beans.registerSingleton(
                        "educationalTime",
                        new EducationalTime(Clock.fixed(educationalNow, ZoneOffset.UTC)));
            }
            context.register(ReportService.class);
            context.refresh();

            var roster = context.getBean(ReportService.class).getLessonAttendance(lessonId);

            assertThat(roster.isEditable())
                    .as("roster editable at real %s / educational %s", realNow, educationalNow)
                    .isEqualTo(expectedEditable);
            assertThat(roster.getEntries()).hasSize(1);
            assertThat(roster.getEntries().get(0).getUserId()).isEqualTo(studentId);
            assertThat(roster.getEntries().get(0).isEditable()).isEqualTo(expectedEditable);
        }
    }
}
