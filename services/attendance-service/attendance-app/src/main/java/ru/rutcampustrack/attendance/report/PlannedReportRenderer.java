package ru.rutcampustrack.attendance.report;

import com.google.protobuf.ByteString;

import org.springframework.stereotype.Component;

import ru.rutcampustrack.attendance.contract.dto.report.*;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.report.planned.AdmittedReportPlan;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportService;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportService;
import ru.rutcampustrack.reportrendering.grpc.RenderReportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherStatsScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Composes the actual existing renderers with immutable report reads and local Attendance marks.
 */
@Component
public final class PlannedReportRenderer {
    private final ReportService reports;
    private final HeadmanWeeklyReportService weekly;
    private final HeadmanStatsService stats;
    private final HeadmanGroupCompositionService composition;
    private final HeadmanStatsTrendRenderer trend;
    private final DocumentRendererGrpcClient converter;

    public PlannedReportRenderer(
            ReportService reports,
            HeadmanWeeklyReportService weekly,
            HeadmanStatsService stats,
            HeadmanGroupCompositionService composition,
            HeadmanStatsTrendRenderer trend,
            DocumentRendererGrpcClient converter) {
        this.reports = reports;
        this.weekly = weekly;
        this.stats = stats;
        this.composition = composition;
        this.trend = trend;
        this.converter = converter;
    }

    public ru.rutcampustrack.attendance.grpc.AdminGroupStatsSnapshot readAdminGroupStats(
            AdmittedReportPlan plan) {
        return AdminGroupStatsReadService.readPlanned(reports, plan);
    }

    public RenderReportResponse render(AdmittedReportPlan plan) {
        var source = plan.source();
        return switch (source.getReportKind()) {
            case "TEACHER_JOURNAL" -> {
                var result =
                        TeacherAttendanceExportService.forPlan(
                                        plan, reports.forPlan(plan), converter)
                                .export(source.getTeacherJournal(), plan.getUserId());
                yield response(
                        result.getContent().toByteArray(),
                        result.getContentType(),
                        result.getFileName());
            }
            case "TEACHER_STATS" -> {
                var selection = source.getTeacherStats();
                var result =
                        TeacherStatsExportService.forPlan(plan, reports.forPlan(plan), converter)
                                .export(
                                        teacherStats(selection.getStatsRequest()),
                                        plan.getUserId(),
                                        selection.getFormat());
                yield response(
                        result.getContent().toByteArray(),
                        result.getContentType(),
                        result.getFileName());
            }
            case "HEADMAN_WEEKLY_CURRENT", "HEADMAN_WEEKLY_SELECTED" -> {
                var selection = source.getWeekly();
                List<LocalDate> dates = dates(selection.getWeekStartsList());
                var planned = weekly.forPlan(plan);
                var result =
                        "HEADMAN_WEEKLY_CURRENT".equals(source.getReportKind())
                                ? planned.exportSingleWeek(dates.getFirst(), selection.getFormat())
                                : planned.exportSelectedWeeks(
                                        new HeadmanWeeklyExportRequest(
                                                dates, selection.getFormat()));
                yield response(result.content(), result.contentType(), result.fileName());
            }
            case "HEADMAN_STATS" -> {
                var selection = source.getHeadmanStats();
                var result =
                        stats.forPlan(plan)
                                .export(
                                        new HeadmanStatsExportRequest(
                                                selection.hasSubjectId()
                                                        ? selection.getSubjectId()
                                                        : null,
                                                selection.getLessonTypesList(),
                                                selection.getSortsList().stream()
                                                        .map(
                                                                value ->
                                                                        new HeadmanStatsSort(
                                                                                value.getField(),
                                                                                value
                                                                                        .getDescending()))
                                                        .toList(),
                                                selection.getFiltersList().stream()
                                                        .map(
                                                                value ->
                                                                        new HeadmanStatsFilter(
                                                                                value.getField(),
                                                                                value.getContains(),
                                                                                value.hasMinimum()
                                                                                        ? decimal(
                                                                                                value
                                                                                                        .getMinimum())
                                                                                        : null,
                                                                                value.hasMaximum()
                                                                                        ? decimal(
                                                                                                value
                                                                                                        .getMaximum())
                                                                                        : null))
                                                        .toList(),
                                                selection.getFormat(),
                                                selection.hasSemesterId()
                                                        ? selection.getSemesterId()
                                                        : null));
                yield response(result.content(), result.contentType(), result.fileName());
            }
            case "HEADMAN_STATS_TREND" -> {
                var selection = source.getTrend();
                var query =
                        new HeadmanStatsTrendQueryRequest(
                                HeadmanStatsTrendQueryRequest.Mode.valueOf(selection.getMode()),
                                selection.getWeekStart().isEmpty()
                                        ? null
                                        : LocalDate.parse(selection.getWeekStart()),
                                selection.hasSubjectId() ? selection.getSubjectId() : null,
                                selection.getLessonTypesList(),
                                selection.hasSemesterId() ? selection.getSemesterId() : null,
                                selection.hasBucketUnit()
                                        ? HeadmanStatsTrendQueryRequest.BucketUnit.valueOf(
                                                selection.getBucketUnit())
                                        : null);
                var result =
                        new HeadmanStatsTrendExportService(stats.forPlan(plan), trend)
                                .export(
                                        new HeadmanStatsTrendExportRequest(
                                                query, selection.getFormat()));
                yield response(result.content(), result.contentType(), result.fileName());
            }
            case "HEADMAN_GROUP_COMPOSITION" -> {
                var result = composition.forPlan(plan).export(source.getComposition().getFormat());
                yield response(result.content(), result.contentType(), result.fileName());
            }
            case "HEADMAN_SUBJECT_JOURNAL" -> {
                var selection = source.getSubjectJournal();
                var result =
                        weekly.forPlan(plan)
                                .exportSubjectJournal(
                                        new HeadmanSubjectJournalExportRequest(
                                                selection.getSubjectId(),
                                                selection.getLessonTypesList(),
                                                dates(selection.getWeekStartsList()),
                                                selection.getFormat()));
                yield response(result.content(), result.contentType(), result.fileName());
            }
            default -> throw new ReportValidationException("Неизвестный тип отчёта Attendance");
        };
    }

    private static ReportService.TeacherStatsQuery teacherStats(TeacherStatsRequest value) {
        if (value.getScope() != TeacherStatsScope.TEACHER_STATS_STUDENTS
                && value.getScope() != TeacherStatsScope.TEACHER_STATS_GROUPS) {
            throw new ReportValidationException("Некорректный scope статистики преподавателя");
        }
        return new ReportService.TeacherStatsQuery(
                value.getLessonIdsList(),
                value.getScope() == TeacherStatsScope.TEACHER_STATS_STUDENTS
                        ? ReportService.TeacherStatsScope.STUDENTS
                        : ReportService.TeacherStatsScope.GROUPS,
                value.getGroupId(),
                value.getSubjectId(),
                value.getLessonTypesList(),
                value.getSortsList().stream()
                        .map(
                                sort ->
                                        new ReportService.TeacherStatsSort(
                                                sort.getColumn(), sort.getDescending()))
                        .toList(),
                value.getFiltersList().stream()
                        .map(
                                filter ->
                                        new ReportService.TeacherStatsFilter(
                                                filter.getColumn(),
                                                filter.getContains(),
                                                filter.hasMinPercent()
                                                        ? filter.getMinPercent()
                                                        : null,
                                                filter.hasMaxPercent()
                                                        ? filter.getMaxPercent()
                                                        : null,
                                                filter.hasMinValue() ? filter.getMinValue() : null,
                                                filter.hasMaxValue() ? filter.getMaxValue() : null))
                        .toList(),
                value.getSemesterId());
    }

    private static List<LocalDate> dates(List<String> values) {
        if (values.size() > 64)
            throw new ReportValidationException("Можно выбрать не более 64 недель");
        return values.stream().map(LocalDate::parse).toList();
    }

    private static BigDecimal decimal(String value) {
        if (value.length() > 64)
            throw new ReportValidationException("Некорректная граница фильтра");
        return new BigDecimal(value);
    }

    private static RenderReportResponse response(byte[] content, String type, String name) {
        if (content == null
                || content.length == 0
                || type == null
                || type.isBlank()
                || name == null
                || name.isBlank()) {
            throw new ReportExportUnavailableException("Сервис формирования не вернул файл отчёта");
        }
        // Keep the 20 MiB body plus all protobuf metadata inside the directed 21 MiB envelope.
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024
                || type.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256)
            throw new ReportExportTooLargeException("Метаданные файла отчёта превышают лимит");
        if (content.length > AdmittedReportPlan.MAX_BYTES)
            throw new ReportExportTooLargeException("Файл отчёта превышает 20 МиБ");
        return RenderReportResponse.newBuilder()
                .setBody(ByteString.copyFrom(content))
                .setContentType(type)
                .setFilename(name)
                .build();
    }
}
