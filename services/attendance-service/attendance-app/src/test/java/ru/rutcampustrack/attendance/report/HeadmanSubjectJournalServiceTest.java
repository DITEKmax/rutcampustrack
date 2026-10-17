package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanSubjectJournalExportRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.*;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.attendance.report.teacher.*;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.*;
import ru.rutcampustrack.schedule.grpc.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class HeadmanSubjectJournalServiceTest {
    @Mock AcademicGrpcClient academic;
    @Mock ScheduleGrpcClient schedule;
    @Mock AttendanceReadPort attendance;
    @Mock DocumentRendererGrpcClient documents;
    @Mock RequestContext context;
    HeadmanWeeklyReportService service;
    final LocalDate day = LocalDate.parse("2026-04-27");

    @BeforeEach
    void setUp() {
        service =
                new HeadmanWeeklyReportService(
                        academic,
                        schedule,
                        attendance,
                        new TeacherAttendanceDocxRenderer(),
                        new HeadmanWeeklyTabularRenderer(),
                        documents,
                        context,
                        Clock.fixed(
                                Instant.parse("2026-05-02T09:00:00Z"), ZoneId.of("Europe/Moscow")),
                        new ReportExecution(
                                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        lenient().when(context.getGroupId()).thenReturn(10L);
        lenient().when(academic.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(true);
        lenient()
                .when(academic.getActiveSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(1)
                                .setName("Весна")
                                .setDateFrom("2026-04-01")
                                .setDateTo("2026-05-31")
                                .build());
        lenient()
                .when(academic.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(
                        Map.of(
                                5L,
                                new AcademicGrpcClient.SubjectDetails(
                                        "Математика",
                                        "lecture",
                                        10L,
                                        List.of("lecture", "practice"))));
        lenient()
                .when(academic.getGroup(10L))
                .thenReturn(GroupResponse.newBuilder().setId(10).setName("ГР-311").build());
    }

    @Test
    void subjectJournalKeepsChronologyCancelledColumnsAndDatedMembershipWithoutInventingMarks() {
        when(schedule.getLessonsByGroup(10L, 1L, "2026-04-01", "2026-05-03"))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(103, "2026-04-28", 1, "lecture", "closed"))
                                .addLessons(lesson(102, "2026-04-27", 3, "lecture", "cancelled"))
                                .addLessons(lesson(101, "2026-04-27", 2, "practice", "closed"))
                                .addLessons(lesson(100, "2026-04-27", 1, "lecture", "closed"))
                                .build());
        when(academic.getGroupMembers(10L, day, 1L))
                .thenReturn(roster(day, student(1, "Альфа"), student(2, "Переведённый")));
        when(academic.getGroupMembers(10L, day.plusDays(1), 1L))
                .thenReturn(roster(day.plusDays(1), student(1, "Альфа"), student(3, "Новый")));
        when(attendance.findByGroupAndDateRange(
                        10L, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-05-03")))
                .thenReturn(
                        List.of(
                                mark(100, 1, day, 1, AttendanceStatus.PRESENT),
                                mark(101, 2, day, 2, AttendanceStatus.EXCUSED),
                                mark(102, 1, day, 3, AttendanceStatus.ABSENT),
                                mark(103, 2, day.plusDays(1), 1, AttendanceStatus.PRESENT)));
        var model = service.buildSubjectJournalModel(request("html"));
        assertThat(model.columns())
                .extracting(TeacherAttendanceExportModel.Column::lessonId)
                .containsExactly(100L, 101L, 102L, 103L);
        assertThat(model.columns().get(2).state()).isEqualTo("CANCELLED");
        assertThat(model.context().periodTo()).isEqualTo(LocalDate.parse("2026-05-03"));
        assertThat(model.rows())
                .extracting(TeacherAttendanceExportModel.Row::studentId)
                .containsExactly(1L, 3L, 2L);
        var moved = model.rows().stream().filter(r -> r.studentId() == 2).findFirst().orElseThrow();
        assertThat(moved.cellsByLessonId()).containsOnlyKeys(101L);
        var alpha = model.rows().getFirst();
        assertThat(alpha.cellsByLessonId()).containsOnlyKeys(100L);
        String html =
                new String(
                        service.exportSubjectJournal(request("html")).content(),
                        StandardCharsets.UTF_8);
        assertThat(html)
                .contains("Математика", "Отменено", "Переведённый", "2026-04-01", "2026-05-03");
    }

    @Test
    void foreignSubjectAndRevokedHelperNeverProduceAFile() {
        lenient()
                .when(schedule.getLessonsByGroup(10L, 1L, "2026-04-01", "2026-05-03"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        lenient()
                .when(
                        attendance.findByGroupAndDateRange(
                                10L, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-05-03")))
                .thenReturn(List.of());
        when(academic.getSubjectDetailsByIds(List.of(5L)))
                .thenReturn(
                        Map.of(
                                5L,
                                new AcademicGrpcClient.SubjectDetails(
                                        "Другой",
                                        "lecture",
                                        "permissive"
                                                        .equals(
                                                                System.getenv(
                                                                        "RCT_EXPORT_SCOPE_FIXTURE"))
                                                ? 10L
                                                : 99L,
                                        List.of("lecture", "practice"))));
        assertThatThrownBy(() -> service.exportSubjectJournal(request("html")))
                .isInstanceOf(AccessDeniedException.class);
        when(academic.hasAssistantPermission(10L, "VIEW_STATS")).thenReturn(false);
        assertThatThrownBy(() -> service.exportSubjectJournal(request("html")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(schedule, attendance, documents);
    }

    @Test
    void nonActualTypeAndNextWeekSelectionAreRejected() {
        assertThatThrownBy(
                        () ->
                                service.exportSubjectJournal(
                                        new HeadmanSubjectJournalExportRequest(
                                                5L, List.of("lab"), null, "html")))
                .isInstanceOf(ReportValidationException.class);
        assertThatThrownBy(
                        () ->
                                service.exportSubjectJournal(
                                        new HeadmanSubjectJournalExportRequest(
                                                5L,
                                                List.of("lecture"),
                                                List.of(LocalDate.parse("2026-05-04")),
                                                "html")))
                .isInstanceOf(ReportValidationException.class);
        verifyNoInteractions(schedule, attendance, documents);
    }

    @Test
    void selectedWeeksAreExplicitInEveryDirectFileAndNativeSizeFailureIsTyped() throws Exception {
        when(schedule.getLessonsByGroup(10L, 1L, "2026-04-01", "2026-05-03"))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendance.findByGroupAndDateRange(
                        10L, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-05-03")))
                .thenReturn(List.of());
        for (String format : List.of("html", "docx", "xlsx")) {
            var selected =
                    new HeadmanSubjectJournalExportRequest(
                            5L, List.of("lecture"), List.of(day), format);
            byte[] file = service.exportSubjectJournal(selected).content();
            String text;
            if (format.equals("html")) text = new String(file, StandardCharsets.UTF_8);
            else {
                StringBuilder xml = new StringBuilder();
                try (var zip =
                        new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(file))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null)
                        if (entry.getName().endsWith(".xml"))
                            xml.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
                text = xml.toString();
            }
            assertThat(text).contains("Математика", "Выбранные недели", "2026-04-27");
        }
        when(documents.convertDocxForHeadmanWeeklyExport(any(), any()))
                .thenThrow(io.grpc.Status.RESOURCE_EXHAUSTED.asRuntimeException());
        assertThatThrownBy(() -> service.exportSubjectJournal(request("pdf")))
                .isInstanceOf(ReportExportTooLargeException.class);
    }

    private static HeadmanSubjectJournalExportRequest request(String format) {
        return new HeadmanSubjectJournalExportRequest(
                5L, List.of("lecture", "practice"), null, format);
    }

    private static StudentInfo student(long id, String name) {
        return StudentInfo.newBuilder().setUserId(id).setDisplayName(name).build();
    }

    private static GroupMembersResponse roster(LocalDate date, StudentInfo... students) {
        return GroupMembersResponse.newBuilder()
                .setSemesterId(1)
                .setAsOfDate(date.toString())
                .addAllStudents(List.of(students))
                .build();
    }

    private static LessonResponse lesson(
            long id, String date, int number, String type, String state) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(10)
                .setSemesterId(1)
                .setSubjectId(5)
                .setDate(date)
                .setStartTime(String.format(java.util.Locale.ROOT, "%02d:00", number + 7))
                .setLessonNumber(number)
                .setLessonType(type)
                .setStatus(state)
                .build();
    }

    private static AttendanceRecord mark(
            long lesson, long student, LocalDate date, int number, AttendanceStatus status) {
        return new AttendanceRecord(
                lesson, student, 10L, 5L, date, number, status, AttendanceSource.HEADMAN, null);
    }
}
