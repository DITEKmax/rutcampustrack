package ru.rutcampustrack.attendance.report.teacher;

import io.grpc.Status;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.TeacherAcademicGrpcClient;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.teacher.grpc.TeacherStatsExportResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exports the same filtered, sorted server aggregation returned by the teacher stats query. */
public final class TeacherStatsExportService {
    private static final int MAX_EXPORT_RESPONSE_BYTES = 20 * 1024 * 1024;
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String HTML_MIME = "text/html; charset=UTF-8";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "lecture", "Лекция",
            "practice", "Практика",
            "laboratory", "Лабораторная",
            "lab", "Лабораторная");

    private final ReportService reportService;
    private final AcademicGrpcClient academic;
    private final TeacherAcademicGrpcClient teacherAcademic;
    private final DocumentRendererGrpcClient renderer;
    private final TeacherStatsDocxRenderer docxRenderer = new TeacherStatsDocxRenderer();
    private final TeacherStatsTabularRenderer tabularRenderer = new TeacherStatsTabularRenderer();

    public TeacherStatsExportService(ReportService reportService,
                                     AcademicGrpcClient academic,
                                     TeacherAcademicGrpcClient teacherAcademic,
                                     DocumentRendererGrpcClient renderer) {
        this.reportService = reportService;
        this.academic = academic;
        this.teacherAcademic = teacherAcademic;
        this.renderer = renderer;
    }

    public TeacherStatsExportResponse export(ReportService.TeacherStatsQuery query,
                                             long teacherId,
                                             String formatCode) {
        ExportFormat format = ExportFormat.from(formatCode);
        // This is the exact authoritative stats aggregation used by GetTeacherStats:
        // it checks current group authority, dated rosters, CLOSED lessons, filters and sort.
        ReportService.TeacherStatsResult stats = reportService.getTeacherStats(query, teacherId);
        TeacherStatsExportModel model = buildModel(query, stats);
        byte[] content = switch (format) {
            case DOCX -> docxRenderer.render(model);
            case PDF -> renderer.convertDocxForTeacherExport(docxRenderer.render(model), TargetFormat.PDF);
            case PNG -> renderer.convertDocxForTeacherExport(
                    docxRenderer.render(model), TargetFormat.PNG_PAGES_ZIP);
            case HTML -> tabularRenderer.renderHtml(model);
            case XLSX -> tabularRenderer.renderXlsx(model);
        };
        if (content == null || content.length == 0) {
            throw new ReportExportUnavailableException("Teacher statistics export returned no content");
        }
        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Teacher statistics export exceeds the 20 MiB response limit")
                    .asRuntimeException();
        }
        String extension = format == ExportFormat.PNG ? "zip" : format.code;
        String filename = "teacher-stats-" + (query.scope() == ReportService.TeacherStatsScope.GROUPS
                ? "groups" : "students") + "-" + FILE_DATE.format(model.context().semesterFrom())
                + "-" + FILE_DATE.format(model.context().semesterTo()) + "." + extension;
        return TeacherStatsExportResponse.newBuilder()
                .setContent(com.google.protobuf.ByteString.copyFrom(content))
                .setFileName(filename)
                .setContentType(format.contentType)
                .build();
    }

    private TeacherStatsExportModel buildModel(ReportService.TeacherStatsQuery query,
                                               ReportService.TeacherStatsResult stats) {
        if (teacherAcademic == null || academic == null) {
            throw new AcademicServiceUnavailableException("Teacher statistics metadata is unavailable");
        }
        var semester = teacherAcademic.fullSemesterAssignments(query.semesterId());
        LocalDate semesterFrom = parseDate(semester.getSemesterDateFrom(), "semester_date_from");
        LocalDate semesterTo = parseDate(semester.getSemesterDateTo(), "semester_date_to");
        if (semesterTo.isBefore(semesterFrom)) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid semester date range");
        }

        TeacherStatsExportModel.Scope scope = query.scope() == ReportService.TeacherStatsScope.GROUPS
                ? TeacherStatsExportModel.Scope.GROUPS : TeacherStatsExportModel.Scope.STUDENTS;
        List<String> groupLabels;
        String subjectLabel = "";
        List<TeacherStatsExportModel.Row> rows;
        if (scope == TeacherStatsExportModel.Scope.STUDENTS) {
            GroupResponse group = academic.getGroup(query.groupId());
            if (group == null || group.getId() != query.groupId() || group.getName().isBlank()) {
                throw new AcademicServiceUnavailableException("Academic returned incomplete selected-group metadata");
            }
            groupLabels = List.of(group.getName());
            subjectLabel = stats.subjectOptions().stream()
                    .filter(option -> option.groupId() == query.groupId() && option.subjectId() == query.subjectId())
                    .map(ReportService.TeacherStatsSubjectOption::subjectName)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Selected subject is outside the current stats context"));
            rows = stats.students().stream()
                    .map(row -> new TeacherStatsExportModel.Row(row.displayName(),
                            metric(row.present()), metric(row.presentOrExcused()),
                            metric(row.excused()), metric(row.absent()), null))
                    .toList();
        } else {
            groupLabels = stats.groups().stream().map(ReportService.TeacherGroupStats::groupName)
                    .filter(value -> value != null && !value.isBlank()).toList();
            rows = stats.groups().stream()
                    .map(row -> new TeacherStatsExportModel.Row(row.groupName(),
                            metric(row.present()), metric(row.presentOrExcused()),
                            metric(row.excused()), metric(row.absent()), row.lessonsCount()))
                    .toList();
        }

        List<String> typeLabels = query.lessonTypes() == null || query.lessonTypes().isEmpty()
                ? List.of("Все типы занятий")
                : query.lessonTypes().stream().map(TeacherStatsExportService::typeLabel).toList();
        return new TeacherStatsExportModel(
                new TeacherStatsExportModel.Context(scope, query.semesterId(), semesterFrom, semesterTo,
                        stats.periodFrom(), stats.periodTo(), groupLabels, subjectLabel, typeLabels,
                        stats.lessonsCount(), stats.serverNow()), rows);
    }

    private static TeacherStatsExportModel.Metric metric(ReportService.TeacherMetric value) {
        return new TeacherStatsExportModel.Metric(value.numerator(), value.denominator(), BigDecimal.valueOf(value.percent()));
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid " + field);
        }
    }

    private static String typeLabel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new BadRequestException("Lesson type cannot be blank");
        return TYPE_LABELS.getOrDefault(normalized, value.trim());
    }

    private enum ExportFormat {
        DOCX("docx", DOCX_MIME),
        PDF("pdf", "application/pdf"),
        PNG("png", "application/zip"),
        HTML("html", HTML_MIME),
        XLSX("xlsx", XLSX_MIME);

        private final String code;
        private final String contentType;

        ExportFormat(String code, String contentType) {
            this.code = code;
            this.contentType = contentType;
        }

        private static ExportFormat from(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (ExportFormat format : values()) if (format.code.equals(normalized)) return format;
            throw new BadRequestException("Unknown teacher statistics export format");
        }
    }
}
