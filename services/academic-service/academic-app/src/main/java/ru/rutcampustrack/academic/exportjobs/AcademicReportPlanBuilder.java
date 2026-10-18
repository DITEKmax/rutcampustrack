package ru.rutcampustrack.academic.exportjobs;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.teacher.grpc.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Builds the closed data-only producer request from owner snapshots and fixed bounded reads. */
@Component
public class AcademicReportPlanBuilder {
    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub schedule;

    private final ReportPlanSnapshotReader snapshots;
    private final LocalReportAuthority local;
    private final String scheduleToken;
    private final String scheduleAuthority;

    public AcademicReportPlanBuilder(
            ReportPlanSnapshotReader snapshots,
            LocalReportAuthority local,
            @Value("${grpc.service-identity.academic-report-to-schedule-token:}")
                    String scheduleToken,
            @Value("${grpc.client.schedule-service.security.authority-override:schedule-service}")
                    String scheduleAuthority) {
        this.snapshots = snapshots;
        this.local = local;
        this.scheduleToken = scheduleToken;
        this.scheduleAuthority = scheduleAuthority;
    }

    public ReportRenderPlan build(ExportJobRepository.Job job) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Report plan transport under transaction");
        var discovery = snapshots.discover(job);
        var lessons = new ArrayList<LessonResponse>();
        var report = job.identity().report();
        var teacherScope =
                job.identity().actor().role().equals("TEACHER")
                        ? local.teacherScope(job.identity().actor().userId())
                        : null;
        if (discovery.semesterId() != null)
            for (Long group : discovery.groupIds()) {
                long millis = remainingMillis(job);
                if (schedule == null)
                    throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
                var credential =
                        ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential.tryCreate(
                                        ru.rutcampustrack.shared.security.grpc.ServicePrincipal
                                                .ACADEMIC_SERVICE,
                                        ru.rutcampustrack.shared.security.grpc.ServicePrincipal
                                                .SCHEDULE_SERVICE,
                                        scheduleToken)
                                .orElseThrow(
                                        () ->
                                                ExportJobRepository.error(
                                                        HttpStatus.SERVICE_UNAVAILABLE));
                var response =
                        schedule.withCallCredentials(
                                        ru.rutcampustrack.shared.security.grpc
                                                .ServiceTokenCallCredentials.forMethod(
                                                credential,
                                                scheduleAuthority,
                                                ScheduleGrpcServiceGrpc.getGetLessonsByGroupMethod()
                                                        .getFullMethodName()))
                                .withDeadlineAfter(Math.min(3000, millis), TimeUnit.MILLISECONDS)
                                .getLessonsByGroup(
                                        LessonsByGroupRequest.newBuilder()
                                                .setGroupId(group)
                                                .setSemesterId(discovery.semesterId())
                                                .setDateFrom(discovery.from().toString())
                                                .setDateTo(discovery.to().toString())
                                                .build());
                if (response.getLessonsCount() > 5000)
                    throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
                for (var lesson : response.getLessonsList()) {
                    if (lesson.getGroupId() != group
                            || lesson.getSemesterId() != discovery.semesterId())
                        throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
                    if (teacherScope != null
                            && !teacherScope
                                    .tuples()
                                    .contains(
                                            new ru.rutcampustrack.shared.security.teacher
                                                    .TeacherReportScope.Tuple(
                                                    lesson.getGroupId(), lesson.getSubjectId())))
                        continue;
                    lessons.add(lesson);
                    if (lessons.size() > 5000)
                        throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
                }
            }
        remainingMillis(job);
        var plan = snapshots.snapshot(job, discovery, lessons, Instant.now()).toBuilder();
        switch (report.kind()) {
            case TEACHER_JOURNAL -> {
                var p = report.teacherJournal();
                var range = local.semesterRange(p.semesterId());
                var request =
                        TeacherAttendanceExportRequest.newBuilder()
                                .setSemesterId(p.semesterId())
                                .setGroupId(p.groupId())
                                .setSubjectId(p.subjectId())
                                .addAllLessonTypes(p.lessonTypes())
                                .setFormat(p.format().code())
                                .setReportKind(
                                        TeacherAttendanceReportKind
                                                .TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL)
                                .setDateFrom(
                                        (p.dateFrom() == null ? range.from() : p.dateFrom())
                                                .toString())
                                .setDateTo(
                                        (p.dateTo() == null ? range.to() : p.dateTo()).toString())
                                .setPastOnly(p.pastOnly());
                if (p.lessonId() != null) request.setLessonId(Long.parseLong(p.lessonId()));
                plan.setTeacherJournal(request);
            }
            case TEACHER_STATS -> {
                var p = report.teacherStats();
                var scope = scope(p.scope());
                var request =
                        TeacherStatsRequest.newBuilder()
                                .setSemesterId(p.semesterId())
                                .setScope(scope)
                                .addAllLessonTypes(types(p.lessonTypes()))
                                .addAllLessonIds(
                                        plan.getLessonsList().stream()
                                                .map(LessonResponse::getId)
                                                .toList())
                                .addAllSorts(sorts(p.sorts(), scope))
                                .addAllFilters(filters(p.filters(), scope));
                if (p.groupId() != null) request.setGroupId(p.groupId());
                if (p.subjectId() != null) request.setSubjectId(p.subjectId());
                plan.setTeacherStats(
                        TeacherStatsExportRequest.newBuilder()
                                .setStatsRequest(request)
                                .setFormat(p.format().code()));
            }
            case HEADMAN_WEEKLY_CURRENT ->
                    plan.setWeekly(
                            ReportWeeklySelector.newBuilder()
                                    .addWeekStarts(
                                            report.headmanWeeklyCurrent().weekStart().toString())
                                    .setFormat(report.headmanWeeklyCurrent().format().code()));
            case HEADMAN_WEEKLY_SELECTED ->
                    plan.setWeekly(
                            ReportWeeklySelector.newBuilder()
                                    .addAllWeekStarts(
                                            report.headmanWeeklySelected().weekStarts().stream()
                                                    .map(LocalDate::toString)
                                                    .toList())
                                    .setFormat(report.headmanWeeklySelected().format().code()));
            case HEADMAN_STATS -> {
                var p = report.headmanStats();
                var request = ReportHeadmanStatsSelector.newBuilder().setFormat(p.format().code());
                if (job.identity().semesterId() != null)
                    request.setSemesterId(job.identity().semesterId());
                if (p.subjectId() != null) request.setSubjectId(p.subjectId());
                if (p.lessonTypes() != null) request.addAllLessonTypes(p.lessonTypes());
                if (p.sorts() != null)
                    for (var sort : p.sorts())
                        request.addSorts(
                                ReportStatsSort.newBuilder()
                                        .setField(sort.field())
                                        .setDescending(sort.descending()));
                if (p.filters() != null)
                    for (var filter : p.filters()) {
                        var value = ReportStatsFilter.newBuilder().setField(filter.field());
                        if (filter.contains() != null) value.setContains(filter.contains());
                        if (filter.minimum() != null)
                            value.setMinimum(filter.minimum().toPlainString());
                        if (filter.maximum() != null)
                            value.setMaximum(filter.maximum().toPlainString());
                        request.addFilters(value);
                    }
                plan.setHeadmanStats(request);
            }
            case HEADMAN_STATS_TREND -> {
                var p = report.headmanStatsTrend();
                var request =
                        ReportTrendSelector.newBuilder()
                                .setMode(p.mode().name())
                                .setFormat(p.format().code());
                if (job.identity().semesterId() != null)
                    request.setSemesterId(job.identity().semesterId());
                if (p.weekStart() != null) request.setWeekStart(p.weekStart().toString());
                if (p.subjectId() != null) request.setSubjectId(p.subjectId());
                if (p.lessonTypes() != null) request.addAllLessonTypes(p.lessonTypes());
                if (p.bucketUnit() != null) request.setBucketUnit(p.bucketUnit().name());
                plan.setTrend(request);
            }
            case HEADMAN_GROUP_COMPOSITION ->
                    plan.setComposition(
                            ReportCompositionSelector.newBuilder()
                                    .setFormat(report.headmanGroupComposition().format().code()));
            case HEADMAN_SUBJECT_JOURNAL -> {
                var p = report.headmanSubjectJournal();
                var request =
                        ReportSubjectJournalSelector.newBuilder()
                                .setSubjectId(p.subjectId())
                                .addAllLessonTypes(p.lessonTypes())
                                .setFormat(p.format().code());
                if (p.weekStarts() != null)
                    request.addAllWeekStarts(
                            p.weekStarts().stream().map(LocalDate::toString).toList());
                plan.setSubjectJournal(request);
            }
            default -> throw ExportJobRepository.error(HttpStatus.BAD_REQUEST);
        }
        if (plan.build().getSerializedSize() > 20 * 1024 * 1024)
            throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
        remainingMillis(job);
        local.require(job.identity(), false);
        return plan.build();
    }

    static long remainingMillis(ExportJobRepository.Job job) {
        long millis = Duration.between(Instant.now(), job.leaseUntil().minusSeconds(30)).toMillis();
        if (millis <= 0) throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        return millis;
    }

    private static List<String> types(List<String> values) {
        if (values == null || values.isEmpty()) throw bad();
        var types = values.stream().map(v -> v.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        if (types.stream().anyMatch(v -> !Set.of("lecture", "practice", "lab").contains(v)))
            throw bad();
        return types;
    }

    private static TeacherStatsScope scope(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "students", "student" -> TeacherStatsScope.TEACHER_STATS_STUDENTS;
            case "groups", "group" -> TeacherStatsScope.TEACHER_STATS_GROUPS;
            default -> throw bad();
        };
    }

    private static boolean column(String value, TeacherStatsScope scope) {
        return scope == TeacherStatsScope.TEACHER_STATS_STUDENTS && value.equals("displayName")
                || scope == TeacherStatsScope.TEACHER_STATS_GROUPS
                        && Set.of("groupName", "lessonsCount").contains(value)
                || Set.of("present", "presentOrExcused", "excused", "absent").contains(value);
    }

    private static List<TeacherStatsSort> sorts(List<String> values, TeacherStatsScope scope) {
        if (values == null) return List.of();
        var result = new ArrayList<TeacherStatsSort>();
        var seen = new HashSet<String>();
        for (String raw : values) {
            String value = raw.trim();
            boolean descending = value.startsWith("-");
            if (descending) value = value.substring(1);
            if (value.endsWith(":asc") || value.endsWith(":desc")) {
                descending = value.endsWith(":desc");
                value = value.substring(0, value.length() - (descending ? 5 : 4));
            }
            if (!column(value, scope) || !seen.add(value)) throw bad();
            result.add(
                    TeacherStatsSort.newBuilder()
                            .setColumn(value)
                            .setDescending(descending)
                            .build());
        }
        return result;
    }

    private static List<TeacherStatsFilter> filters(List<String> values, TeacherStatsScope scope) {
        if (values == null) return List.of();
        var result = new ArrayList<TeacherStatsFilter>();
        for (String raw : values) {
            String value = raw.trim();
            String operator =
                    value.contains("~")
                            ? "~"
                            : value.contains(">=") ? ">=" : value.contains("<=") ? "<=" : null;
            if (operator == null) throw bad();
            int index = value.indexOf(operator);
            String column = value.substring(0, index).trim();
            String operand = value.substring(index + operator.length()).trim();
            if (!column(column, scope) || operand.isBlank()) throw bad();
            var filter = TeacherStatsFilter.newBuilder().setColumn(column);
            if (operator.equals("~")) {
                if (!Set.of("displayName", "groupName").contains(column)) throw bad();
                filter.setContains(operand);
            } else
                try {
                    if (column.equals("lessonsCount")) {
                        int number = Integer.parseInt(operand);
                        if (operator.equals(">=")) filter.setMinValue(number);
                        else filter.setMaxValue(number);
                    } else {
                        double number = Double.parseDouble(operand);
                        if (!Double.isFinite(number)) throw bad();
                        if (operator.equals(">=")) filter.setMinPercent(number);
                        else filter.setMaxPercent(number);
                    }
                } catch (NumberFormatException invalid) {
                    throw bad();
                }
            result.add(filter.build());
        }
        return result;
    }

    private static org.springframework.web.server.ResponseStatusException bad() {
        return ExportJobRepository.error(HttpStatus.BAD_REQUEST);
    }
}
