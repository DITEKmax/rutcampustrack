package ru.rutcampustrack.attendance.report.teacher;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.grpc.TeacherAcademicGrpcClient;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReportKind;
import ru.rutcampustrack.teacher.grpc.TeacherJournalCell;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalStudent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeacherAttendanceExportServiceTest {
    private static final long TEACHER_ID = 71L;
    private static final long SEMESTER_ID = 9L;
    private static final long GROUP_ID = 33L;
    private static final long SUBJECT_ID = 22L;
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    void htmlExportReadsAllJournalBatchesAndLeavesFutureMarksBlank() {
        Fixture fixture = fixture(100, true);

        TeacherAttendanceExportResponse response = fixture.service().export(request("html"), TEACHER_ID);

        String html = response.getContent().toStringUtf8();
        assertThat(fixture.batches().stream().map(List::size).toList()).containsExactly(100, 1);
        assertThat(html).contains("2026-01-01 — 2026-12-31")
                .contains("2026-10-01")
                .contains("09:00 · Лекция")
                .contains("Запланировано")
                .doesNotContain("PLANNED")
                .contains("overflow:auto")
                .contains("Иван Петров")
                .contains("100/100 (100%)");
        assertThat(occurrences(html, "<th scope=\"col\">" )).isEqualTo(106);
        assertThat(occurrences(html, "<td>+</td>")).isEqualTo(100);
        assertThat(occurrences(html, "<td></td>")).isEqualTo(1);
        assertThat(response.getContentType()).isEqualTo("text/html; charset=UTF-8");
        assertThat(response.getFileName()).endsWith(".html");
        verify(fixture.schedule()).getLessonsByGroup(GROUP_ID, SEMESTER_ID, "2026-01-01", "2026-12-31");
    }

    @Test
    void xlsxHasFrozenHeadingsAndNumericMetricCells() throws Exception {
        Fixture fixture = fixture(1, true);

        TeacherAttendanceExportResponse response = fixture.service().export(request("xlsx"), TEACHER_ID);

        Map<String, byte[]> entries = unzip(response.getContent().toByteArray());
        assertThat(entries.keySet()).contains("xl/worksheets/sheet1.xml", "xl/styles.xml");
        for (byte[] xml : entries.values()) parseXml(xml);
        String sheet = new String(entries.get("xl/worksheets/sheet1.xml"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(sheet).contains("xSplit=\"1\" ySplit=\"6\"")
                .contains("width=\"30\"")
                .contains("width=\"14\"")
                .contains("width=\"18\"")
                .contains("\nЗавершено")
                .doesNotContain("CLOSED")
                .contains("\nЛекция\n№1")
                .contains("<c r=\"C7\" t=\"inlineStr\"><is><t xml:space=\"preserve\"></t></is></c>")
                .contains("<c r=\"F7\" s=\"2\" t=\"n\"><v>1</v></c>");
        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getFileName()).endsWith(".xlsx");
    }

    @Test
    void activeGroupAuthorizationRunsBeforeScheduleOrRendering() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        TeacherAcademicGrpcClient teacherAcademic = mock(TeacherAcademicGrpcClient.class);
        DocumentRendererGrpcClient renderer = mock(DocumentRendererGrpcClient.class);
        when(report.getTeacherStats(any(), eq(TEACHER_ID)))
                .thenThrow(new AccessDeniedException("teacher is not active in the selected group"));
        TeacherAttendanceExportService service = new TeacherAttendanceExportService(
                report, schedule, academic, teacherAcademic, renderer,
                (teacherId, lessonIds) -> TeacherJournalResponse.getDefaultInstance());

        assertThatThrownBy(() -> service.export(request("html"), TEACHER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(schedule, academic, teacherAcademic, renderer);
    }

    private static Fixture fixture(int pastLessonCount, boolean includeFuture) {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        TeacherAcademicGrpcClient teacherAcademic = mock(TeacherAcademicGrpcClient.class);
        DocumentRendererGrpcClient renderer = mock(DocumentRendererGrpcClient.class);
        List<LessonResponse> lessons = new ArrayList<>();
        LocalDate firstDate = LocalDate.parse("2026-01-01");
        for (int index = 0; index < pastLessonCount; index++) {
            lessons.add(lesson(10_000L + index, firstDate.plusDays(index), "CLOSED", index + 1));
        }
        if (includeFuture) {
            lessons.add(lesson(20_000L, LocalDate.parse("2026-10-01"), "PLANNED", 1));
        }
        when(schedule.getLessonsByGroup(GROUP_ID, SEMESTER_ID, "2026-01-01", "2026-12-31"))
                .thenReturn(LessonsResponse.newBuilder().addAllLessons(lessons).build());
        when(teacherAcademic.fullSemesterAssignments(SEMESTER_ID)).thenReturn(TeacherAssignmentsResponse.newBuilder()
                .setSemesterDateFrom("2026-01-01")
                .setSemesterDateTo("2026-12-31")
                .build());
        when(academic.getGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID).setName("УИТ-311").build());
        when(academic.getSubjectDetailsByIds(List.of(SUBJECT_ID)))
                .thenReturn(Map.of(SUBJECT_ID, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        ReportService.TeacherStatsResult emptyStats = stats(List.of());
        ReportService.TeacherStatsResult selectedStats = stats(List.of(student(pastLessonCount)));
        when(report.getTeacherStats(any(), eq(TEACHER_ID))).thenAnswer(invocation -> {
            ReportService.TeacherStatsQuery query = invocation.getArgument(0);
            return query.lessonIds().isEmpty() ? emptyStats : selectedStats;
        });
        List<List<Long>> batches = new ArrayList<>();
        TeacherAttendanceExportService service = new TeacherAttendanceExportService(
                report, schedule, academic, teacherAcademic, renderer,
                (teacherId, ids) -> {
                    batches.add(List.copyOf(ids));
                    TeacherJournalStudent.Builder student = TeacherJournalStudent.newBuilder()
                            .setStudentId(501L).setDisplayName("Иван Петров");
                    ids.forEach(id -> student.addCells(TeacherJournalCell.newBuilder()
                            .setLessonId(id).setStatus("PRESENT").setSymbol("+").setRecordPresent(true)));
                    return TeacherJournalResponse.newBuilder().addStudents(student).build();
                });
        return new Fixture(service, schedule, batches);
    }

    private static LessonResponse lesson(long id, LocalDate date, String state, int number) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(GROUP_ID)
                .setSemesterId(SEMESTER_ID)
                .setSubjectId(SUBJECT_ID)
                .setLessonType("lecture")
                .setDate(date.toString())
                .setStartTime("09:00")
                .setLessonNumber(number)
                .setStatus(state)
                .build();
    }

    private static ReportService.TeacherStatsResult stats(List<ReportService.TeacherStudentStats> students) {
        return new ReportService.TeacherStatsResult(ReportService.TeacherStatsScope.STUDENTS,
                SEMESTER_ID, null, null, 0, students, List.of(), List.of(), NOW);
    }

    private static ReportService.TeacherStudentStats student(int denominator) {
        ReportService.TeacherMetric present = new ReportService.TeacherMetric(denominator, denominator, 100.0);
        ReportService.TeacherMetric zero = new ReportService.TeacherMetric(0, denominator, 0.0);
        return new ReportService.TeacherStudentStats(501L, "Иван Петров", present, present, zero, zero);
    }

    private static TeacherAttendanceExportRequest request(String format) {
        return TeacherAttendanceExportRequest.newBuilder()
                .setSemesterId(SEMESTER_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID)
                .addLessonTypes("lecture")
                .setFormat(format)
                .setReportKind(TeacherAttendanceReportKind.TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL)
                .build();
    }

    private static int occurrences(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                zip.transferTo(content);
                result.put(entry.getName(), content.toByteArray());
            }
        }
        return result;
    }

    private static void parseXml(byte[] value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(value));
    }

    private record Fixture(TeacherAttendanceExportService service,
                           ScheduleGrpcClient schedule,
                           List<List<Long>> batches) {
    }
}
