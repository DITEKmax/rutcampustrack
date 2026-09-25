package ru.rutcampustrack.attendance.report.teacher;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.TeacherAcademicGrpcClient;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherStatsExportServiceTest {
    private static final long TEACHER_ID = 71L;
    private static final long SEMESTER_ID = 9L;
    private static final long GROUP_ID = 33L;
    private static final long SUBJECT_ID = 22L;

    @Test
    void exportsBothServerOrderedSummaryScopesInAllFormatsWithValidXmlAndNoZeroPercent() throws Exception {
        ReportService report = mock(ReportService.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        TeacherAcademicGrpcClient teacherAcademic = mock(TeacherAcademicGrpcClient.class);
        DocumentRendererGrpcClient renderer = mock(DocumentRendererGrpcClient.class);
        when(teacherAcademic.fullSemesterAssignments(SEMESTER_ID)).thenReturn(TeacherAssignmentsResponse.newBuilder()
                .setSemesterDateFrom("2026-01-01").setSemesterDateTo("2026-06-30").build());
        when(academic.getGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID).setName("УИТ\u000b-311").build());
        when(renderer.convertDocxForTeacherExport(any(byte[].class), eq(TargetFormat.PDF)))
                .thenReturn("pdf".getBytes(StandardCharsets.UTF_8));
        when(renderer.convertDocxForTeacherExport(any(byte[].class), eq(TargetFormat.PNG_PAGES_ZIP)))
                .thenReturn("zip".getBytes(StandardCharsets.UTF_8));

        TeacherStatsExportService service = new TeacherStatsExportService(report, academic, teacherAcademic, renderer);
        for (ReportService.TeacherStatsScope scope : ReportService.TeacherStatsScope.values()) {
            ReportService.TeacherStatsQuery query = query(scope);
            when(report.getTeacherStats(eq(query), eq(TEACHER_ID))).thenReturn(stats(scope));
            for (String format : List.of("docx", "pdf", "png", "html", "xlsx")) {
                var response = service.export(query, TEACHER_ID, format);
                assertThat(response.getContent()).isNotEmpty();
                assertThat(response.getFileName()).startsWith("teacher-stats-")
                        .endsWith(format.equals("png") ? ".zip" : "." + format);
                assertThat(response.getContentType()).isEqualTo(switch (format) {
                    case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    case "pdf" -> "application/pdf";
                    case "png" -> "application/zip";
                    case "html" -> "text/html; charset=UTF-8";
                    case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    default -> throw new IllegalArgumentException(format);
                });
                if (format.equals("pdf") || format.equals("png")) {
                    assertThat(response.getContent().toStringUtf8())
                            .isEqualTo(format.equals("pdf") ? "pdf" : "zip");
                }

                if (format.equals("docx")) {
                    Map<String, byte[]> entries = unzip(response.getContent().toByteArray());
                    assertThat(entries).containsKey("word/document.xml");
                    parseXml(entries.get("word/document.xml"));
                    String document = new String(entries.get("word/document.xml"), StandardCharsets.UTF_8);
                    assertThat(document).contains(scope == ReportService.TeacherStatsScope.GROUPS
                                    ? "Статистика по моим группам" : "Статистика студентов группы")
                            .contains("Период семестра: 01.01.2026 — 30.06.2026")
                            .contains("Учтено пар: 2")
                            .contains("— (0/0)")
                            .contains(">100.0%</w:t>")
                            .doesNotContain(">0.0%</w:t>", "\u000b", "71");
                    if (scope == ReportService.TeacherStatsScope.GROUPS) {
                        assertThat(document).contains("Пар учтено");
                    } else {
                        assertThat(document).contains("Математика").contains("УИТ-311");
                    }
                }
                if (format.equals("html")) {
                    String html = response.getContent().toStringUtf8();
                    assertThat(html).contains("Период семестра", "Учтено пар", "— (0/0)", "100.0%")
                            .doesNotContain("/ 0.0%</td>", "\u000b", "71");
                    if (scope == ReportService.TeacherStatsScope.STUDENTS) {
                        assertThat(html).contains("УИТ-311", "Математика", "А&amp;Б");
                    }
                }
                if (format.equals("xlsx")) {
                    Map<String, byte[]> entries = unzip(response.getContent().toByteArray());
                    entries.values().forEach(xml -> {
                        try {
                            parseXml(xml);
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    });
                    String sheet = new String(entries.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
                    assertThat(sheet).contains("xSplit=\"1\" ySplit=\"9\"", "t=\"n\"", "—")
                            .doesNotContain("\u000b");
                    if (scope == ReportService.TeacherStatsScope.GROUPS) assertThat(sheet).contains("Пар учтено");
                }
            }
        }
    }

    private static ReportService.TeacherStatsQuery query(ReportService.TeacherStatsScope scope) {
        return new ReportService.TeacherStatsQuery(List.of(101L, 102L), scope,
                scope == ReportService.TeacherStatsScope.STUDENTS ? GROUP_ID : 0,
                scope == ReportService.TeacherStatsScope.STUDENTS ? SUBJECT_ID : 0,
                List.of("lecture"), List.of(new ReportService.TeacherStatsSort(
                scope == ReportService.TeacherStatsScope.STUDENTS ? "displayName" : "groupName", false)),
                List.of(new ReportService.TeacherStatsFilter(
                        scope == ReportService.TeacherStatsScope.STUDENTS ? "displayName" : "groupName",
                        "А", null, null, null, null)), SEMESTER_ID);
    }

    private static ReportService.TeacherStatsResult stats(ReportService.TeacherStatsScope scope) {
        ReportService.TeacherMetric present = new ReportService.TeacherMetric(2, 3, 66.6666667);
        ReportService.TeacherMetric presentOrExcused = new ReportService.TeacherMetric(3, 3, 100.0);
        ReportService.TeacherMetric excused = new ReportService.TeacherMetric(1, 3, 33.3333333);
        ReportService.TeacherMetric noData = new ReportService.TeacherMetric(0, 0, 0.0);
        List<ReportService.TeacherStudentStats> students = scope == ReportService.TeacherStatsScope.STUDENTS
                ? List.of(new ReportService.TeacherStudentStats(701L, "А&Б <Гаврилова>", present,
                        presentOrExcused, excused, noData),
                new ReportService.TeacherStudentStats(702L, "Яковлева", present,
                        presentOrExcused, excused, noData)) : List.of();
        List<ReportService.TeacherGroupStats> groups = scope == ReportService.TeacherStatsScope.GROUPS
                ? List.of(new ReportService.TeacherGroupStats(801L, "Группа Я", 4,
                        present, presentOrExcused, excused, noData),
                new ReportService.TeacherGroupStats(802L, "Группа А", 2,
                        present, presentOrExcused, excused, noData))
                : List.of(new ReportService.TeacherGroupStats(GROUP_ID, "УИТ-311", 2,
                present, presentOrExcused, excused, noData));
        List<ReportService.TeacherStatsSubjectOption> subjects = scope == ReportService.TeacherStatsScope.STUDENTS
                ? List.of(new ReportService.TeacherStatsSubjectOption(GROUP_ID, SUBJECT_ID,
                "Математика", List.of("lecture"))) : List.of();
        return new ReportService.TeacherStatsResult(scope, SEMESTER_ID,
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-05-01"), 2,
                students, groups, subjects, Instant.parse("2026-05-02T10:00:00Z"));
    }

    private static Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private static void parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
}
