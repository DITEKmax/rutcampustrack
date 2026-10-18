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
import ru.rutcampustrack.attendance.report.planned.*;
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
    private static final Map<String, String> TYPE_LABELS =
            Map.of(
                    "lecture", "Лекция",
                    "practice", "Практика",
                    "laboratory", "Лабораторная",
                    "lab", "Лабораторная");

    private final ReportService reportService;
    private final ReportAcademicReadPort academic;
    private final TeacherAssignmentReadPort teacherAcademic;
    private final DocumentRendererGrpcClient renderer;
    private final TeacherStatsDocxRenderer docxRenderer = new TeacherStatsDocxRenderer();
    private final TeacherStatsTabularRenderer tabularRenderer = new TeacherStatsTabularRenderer();

    public TeacherStatsExportService(
            ReportService reportService,
            AcademicGrpcClient academic,
            TeacherAcademicGrpcClient teacherAcademic,
            DocumentRendererGrpcClient renderer) {
        this(
                reportService,
                ReportAcademicReadPort.live(academic),
                teacherAcademic == null ? null : teacherAcademic::fullSemesterAssignments,
                renderer);
    }

    private TeacherStatsExportService(
            ReportService reportService,
            ReportAcademicReadPort academic,
            TeacherAssignmentReadPort teacherAcademic,
            DocumentRendererGrpcClient renderer) {
        this.reportService = reportService;
        this.academic = academic;
        this.teacherAcademic = teacherAcademic;
        this.renderer = renderer;
    }

    public static TeacherStatsExportService forPlan(
            AdmittedReportPlan plan, ReportService reports, DocumentRendererGrpcClient converter) {
        return new TeacherStatsExportService(reports, plan, plan, converter);
    }

    public TeacherStatsExportResponse export(
            ReportService.TeacherStatsQuery query, long teacherId, String formatCode) {
        ExportFormat format = ExportFormat.from(formatCode);
        // This is the exact authoritative stats aggregation used by GetTeacherStats:
        // it checks current group authority, dated rosters, CLOSED lessons, filters and sort.
        boolean unscoped =
                query.scope() == ReportService.TeacherStatsScope.GROUPS
                        && query.groupId() == 0
                        && query.subjectId() == 0;
        var authorized =
                unscoped ? reportService.getAuthorizedTeacherStats(query, teacherId) : null;
        ReportService.TeacherStatsResult stats =
                unscoped ? authorized.stats() : reportService.getTeacherStats(query, teacherId);
        String scopeHash = unscoped ? authorized.scope().sha256() : "";
        TeacherStatsExportModel model = buildModel(query, stats);
        byte[] content =
                switch (format) {
                    case DOCX -> docxRenderer.render(model);
                    case PDF ->
                            renderer.convertDocxForTeacherExport(
                                    docxRenderer.render(model), TargetFormat.PDF);
                    case PNG ->
                            renderer.convertDocxForTeacherExport(
                                    docxRenderer.render(model), TargetFormat.PNG_PAGES_ZIP);
                    case HTML -> tabularRenderer.renderHtml(model);
                    case XLSX -> tabularRenderer.renderXlsx(model);
                };
        if (content == null || content.length == 0) {
            throw new ReportExportUnavailableException(
                    "Teacher statistics export returned no content");
        }
        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Teacher statistics export exceeds the 20 MiB response limit")
                    .asRuntimeException();
        }
        String extension = format == ExportFormat.PNG ? "zip" : format.code;
        String filename =
                "teacher-stats-"
                        + (query.scope() == ReportService.TeacherStatsScope.GROUPS
                                ? "groups"
                                : "students")
                        + "-"
                        + FILE_DATE.format(model.context().semesterFrom())
                        + "-"
                        + FILE_DATE.format(model.context().semesterTo())
                        + "."
                        + extension;
        return TeacherStatsExportResponse.newBuilder()
                .setContent(com.google.protobuf.ByteString.copyFrom(content))
                .setFileName(filename)
                .setContentType(format.contentType)
                .setTeacherScopeHash(scopeHash)
                .build();
    }

    private TeacherStatsExportModel buildModel(
            ReportService.TeacherStatsQuery query, ReportService.TeacherStatsResult stats) {
        if (teacherAcademic == null || academic == null) {
            throw new AcademicServiceUnavailableException(
                    "Teacher statistics metadata is unavailable");
        }
        var semester = teacherAcademic.fullSemesterAssignments(query.semesterId());
        if (semester == null)
            throw new AcademicServiceUnavailableException(
                    "Teacher semester metadata is unavailable");
        LocalDate semesterFrom = parseDate(semester.getSemesterDateFrom(), "semester_date_from");
        LocalDate semesterTo = parseDate(semester.getSemesterDateTo(), "semester_date_to");
        if (semesterTo.isBefore(semesterFrom)) {
            throw new AcademicServiceUnavailableException(
                    "Academic returned an invalid semester date range");
        }

        TeacherStatsExportModel.Scope scope =
                query.scope() == ReportService.TeacherStatsScope.GROUPS
                        ? TeacherStatsExportModel.Scope.GROUPS
                        : TeacherStatsExportModel.Scope.STUDENTS;
        boolean allGroups =
                scope == TeacherStatsExportModel.Scope.GROUPS
                        && query.groupId() == 0
                        && query.subjectId() == 0;
        List<String> groupLabels;
        String subjectLabel;
        if (allGroups) {
            groupLabels =
                    stats.groups().stream()
                            .map(ReportService.TeacherGroupStats::groupName)
                            .toList();
            subjectLabel = "Предметы с текущим назначением преподавателя";
        } else {
            GroupResponse group = academic.getGroup(query.groupId());
            if (group == null || group.getId() != query.groupId() || group.getName().isBlank()) {
                throw new AcademicServiceUnavailableException(
                        "Academic returned incomplete selected-group metadata");
            }
            groupLabels = List.of(group.getName());
            subjectLabel = selectedSubjectLabel(query, stats, semester, group.getName());
        }
        List<TeacherStatsExportModel.Row> rows;
        if (scope == TeacherStatsExportModel.Scope.STUDENTS) {
            rows =
                    stats.students().stream()
                            .map(
                                    row ->
                                            new TeacherStatsExportModel.Row(
                                                    row.displayName(),
                                                    metric(row.present()),
                                                    metric(row.presentOrExcused()),
                                                    metric(row.excused()),
                                                    metric(row.absent()),
                                                    null))
                            .toList();
        } else {
            rows =
                    stats.groups().stream()
                            .map(
                                    row ->
                                            new TeacherStatsExportModel.Row(
                                                    row.groupName(),
                                                    metric(row.present()),
                                                    metric(row.presentOrExcused()),
                                                    metric(row.excused()),
                                                    metric(row.absent()),
                                                    row.lessonsCount()))
                            .toList();
        }

        List<String> typeLabels =
                query.lessonTypes() == null || query.lessonTypes().isEmpty()
                        ? List.of("Все типы занятий")
                        : query.lessonTypes().stream()
                                .map(TeacherStatsExportService::typeLabel)
                                .toList();
        return new TeacherStatsExportModel(
                new TeacherStatsExportModel.Context(
                        scope,
                        query.semesterId(),
                        semesterFrom,
                        semesterTo,
                        stats.periodFrom(),
                        stats.periodTo(),
                        groupLabels,
                        subjectLabel,
                        typeLabels,
                        stats.lessonsCount(),
                        stats.serverNow()),
                rows);
    }

    private static String selectedSubjectLabel(
            ReportService.TeacherStatsQuery query,
            ReportService.TeacherStatsResult stats,
            ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse semester,
            String selectedGroupName) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (var option : stats.subjectOptions()) {
            if (option.groupId() <= 0
                    || option.subjectId() <= 0
                    || option.groupId() != query.groupId()
                    || option.subjectName() == null
                    || option.subjectName().isBlank()) {
                throw new AcademicServiceUnavailableException(
                        "Incomplete selected-group subject metadata");
            }
            if (option.subjectId() == query.subjectId()) names.add(option.subjectName());
        }
        for (var assignment : semester.getAssignmentsList()) {
            if (assignment.getAssignmentId() <= 0
                    || assignment.getTeacherId() <= 0
                    || assignment.getGroupId() <= 0
                    || assignment.getSubjectId() <= 0
                    || assignment.getSemesterId() != query.semesterId()
                    || assignment.getSubjectName().isBlank()
                    || assignment.getGroupName().isBlank()) {
                throw new AcademicServiceUnavailableException(
                        "Incomplete teacher semester assignment metadata");
            }
            if (assignment.getGroupId() == query.groupId()
                    && !assignment.getGroupName().equals(selectedGroupName)) {
                throw new AcademicServiceUnavailableException(
                        "Conflicting selected group metadata");
            }
            if (assignment.getGroupId() == query.groupId()
                    && assignment.getSubjectId() == query.subjectId()) {
                names.add(assignment.getSubjectName());
            }
        }
        if (names.size() > 1) {
            throw new AcademicServiceUnavailableException("Conflicting selected subject metadata");
        }
        if (names.isEmpty())
            throw new BadRequestException("Selected subject is outside the current stats context");
        return names.iterator().next();
    }

    private static TeacherStatsExportModel.Metric metric(ReportService.TeacherMetric value) {
        return new TeacherStatsExportModel.Metric(
                value.numerator(), value.denominator(), BigDecimal.valueOf(value.percent()));
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
