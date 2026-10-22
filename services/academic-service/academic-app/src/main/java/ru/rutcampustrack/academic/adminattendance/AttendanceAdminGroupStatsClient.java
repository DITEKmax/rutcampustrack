package ru.rutcampustrack.academic.adminattendance;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Both the service direction and current end-user authority are authenticated. */
@Component
public class AttendanceAdminGroupStatsClient {
    private static final String METHOD =
            "rutcampustrack.attendance.AttendanceAdminGroupStatsGrpcService/GetGroupSemesterStats";
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private final String serviceToken;
    private final String targetAuthority;
    private final String reportToken;
    private final String reportAuthority;
    private final ru.rutcampustrack.academic.exportjobs.AdminAttendanceReportBatcher batches;

    @GrpcClient("report-render-attendance")
    private ru.rutcampustrack.reportrendering.grpc.ReportRenderingServiceGrpc
                    .ReportRenderingServiceBlockingStub
            reportStub;

    @GrpcClient("attendance-service")
    private AttendanceAdminGroupStatsGrpcServiceGrpc
                    .AttendanceAdminGroupStatsGrpcServiceBlockingStub
            stub;

    public AttendanceAdminGroupStatsClient(
            @Value("${grpc.service-identity.academic-to-attendance-token:}") String serviceToken,
            @Value("${grpc.service-identity.attendance-authority:attendance-service:19093}")
                    String targetAuthority,
            @Value("${grpc.service-identity.academic-report-to-attendance-token:}")
                    String reportToken,
            @Value(
                            "${grpc.client.report-render-attendance.security.authority-override:attendance-service}")
                    String reportAuthority,
            ru.rutcampustrack.academic.exportjobs.AdminAttendanceReportBatcher batches) {
        this.serviceToken = serviceToken;
        this.targetAuthority = targetAuthority;
        this.reportToken = reportToken;
        this.reportAuthority = reportAuthority;
        this.batches = batches;
    }

    public List<AdminGroupAttendanceTotals> read(
            AdminAttendanceReadRepository.Selection selected, String token) {
        if (token == null || token.isBlank()) throw AdminAccessException.denied();
        DirectedServiceCredential credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("attendance-service"),
                                serviceToken)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "Отчёт недоступен"));
        Metadata headers = new Metadata();
        headers.put(TOKEN, token);
        var semester = selected.semester();
        AdminGroupStatsQuery query =
                AdminGroupStatsQuery.newBuilder()
                        .setSemesterId(Long.parseLong(semester.id()))
                        .setDateFrom(semester.dateFrom().toString())
                        .setDateTo(semester.dateTo().toString())
                        .addAllGroupIds(selected.groups().keySet().stream().sorted().toList())
                        .build();
        try {
            AdminGroupStatsSnapshot response =
                    stub.withCallCredentials(
                                    new ServiceTokenCallCredentials(
                                            credential, targetAuthority, METHOD))
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                            .withDeadlineAfter(15, TimeUnit.SECONDS)
                            .getGroupSemesterStats(query);
            if (response.getSemesterId() != query.getSemesterId()
                    || !response.getDateFrom().equals(query.getDateFrom())
                    || !response.getDateTo().equals(query.getDateTo())
                    || response.getGroupsCount() != selected.groups().size()) throw unavailable();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (var row : response.getGroupsList()) {
                if (!selected.groups().containsKey(row.getGroupId())
                        || !seen.add(row.getGroupId())
                        || row.getCountedLessons() < 0
                        || row.getPresent() < 0
                        || row.getExcused() < 0
                        || row.getAbsent() < 0
                        || row.getOpportunities() < 0
                        || Math.addExact(
                                        Math.addExact(row.getPresent(), row.getExcused()),
                                        row.getAbsent())
                                != row.getOpportunities()
                        || row.getCountedLessons() == 0 && row.getOpportunities() != 0
                        || row.hasPresentPercent() != (row.getOpportunities() > 0)
                        || row.hasPresentOrExcusedPercent() != (row.getOpportunities() > 0)
                        || row.hasPresentPercent()
                                && (!Double.isFinite(row.getPresentPercent())
                                        || !Double.isFinite(row.getPresentOrExcusedPercent())
                                        || row.getPresentPercent() < 0
                                        || row.getPresentOrExcusedPercent() > 100
                                        || row.getPresentPercent()
                                                > row.getPresentOrExcusedPercent()))
                    throw unavailable();
            }
            return List.copyOf(response.getGroupsList());
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.PERMISSION_DENIED
                    || error.getStatus().getCode() == Status.Code.UNAUTHENTICATED)
                throw AdminAccessException.denied();
            if (error.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Сузь поиск групп");
            if (error.getStatus().getCode() == Status.Code.FAILED_PRECONDITION)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Данные посещаемости недоступны");
            throw unavailable();
        } catch (ArithmeticException invalid) {
            throw unavailable();
        }
    }

    /** Only owner-created exact job scopes may enter the data-only report method. */
    public List<AdminGroupAttendanceTotals> readReport(
            AdminAttendanceReadRepository.Selection selected,
            ru.rutcampustrack.academic.exportjobs.ExportJobService.GroupAttendanceReadScope scope) {
        scope.check();
        var totals = new java.util.TreeMap<Long, long[]>();
        for (long group : selected.groups().keySet()) totals.put(group, new long[5]);
        batches.read(
                scope,
                selected,
                batch -> {
                    var plan = batch.plan();
                    var query = plan.getAdminGroupStats();
                    var admitted = scope.admitPlan(plan);
                    var credential =
                            DirectedServiceCredential.tryCreate(
                                            ServicePrincipal.ACADEMIC_SERVICE,
                                            new ServicePrincipal("attendance-service"),
                                            reportToken)
                                    .orElseThrow(AttendanceAdminGroupStatsClient::unavailable);
                    if (reportStub == null) throw unavailable();
                    var headers = new Metadata();
                    headers.put(
                            Metadata.Key.of(
                                    "x-report-capability", Metadata.ASCII_STRING_MARSHALLER),
                            admitted.token());
                    final AdminGroupStatsSnapshot response;
                    try {
                        response =
                                reportStub
                                        .withMaxInboundMessageSize(21 * 1024 * 1024)
                                        .withCallCredentials(
                                                ServiceTokenCallCredentials.forMethod(
                                                        credential,
                                                        reportAuthority,
                                                        ru.rutcampustrack.reportrendering.grpc
                                                                .ReportRenderingServiceGrpc
                                                                .getReadAdminGroupStatsMethod()
                                                                .getFullMethodName()))
                                        .withInterceptors(
                                                MetadataUtils.newAttachHeadersInterceptor(headers))
                                        .withDeadlineAfter(
                                                Math.min(
                                                        TimeUnit.SECONDS.toNanos(15),
                                                        scope.remainingNanos()),
                                                TimeUnit.NANOSECONDS)
                                        .readAdminGroupStats(
                                                ru.rutcampustrack.reportrendering.grpc
                                                        .RenderReportRequest.newBuilder()
                                                        .setPlan(plan)
                                                        .build());
                    } catch (StatusRuntimeException error) {
                        switch (error.getStatus().getCode()) {
                            case UNAUTHENTICATED, PERMISSION_DENIED ->
                                    throw AdminAccessException.denied();
                            case RESOURCE_EXHAUSTED ->
                                    throw new ResponseStatusException(
                                            HttpStatus.PAYLOAD_TOO_LARGE, "Сузь поиск групп");
                            case FAILED_PRECONDITION ->
                                    throw new ResponseStatusException(
                                            HttpStatus.CONFLICT, "Данные посещаемости недоступны");
                            default -> throw unavailable();
                        }
                    }
                    scope.check();
                    accumulate(batch, response, totals);
                });
        scope.check();
        return finish(totals);
    }

    static List<AdminGroupAttendanceTotals> finish(java.util.Map<Long, long[]> totals) {
        var result = new java.util.ArrayList<AdminGroupAttendanceTotals>();
        for (var entry : totals.entrySet()) {
            long[] row = entry.getValue();
            var value =
                    AdminGroupAttendanceTotals.newBuilder()
                            .setGroupId(entry.getKey())
                            .setCountedLessons(row[0])
                            .setPresent(row[1])
                            .setAbsent(row[2])
                            .setExcused(row[3])
                            .setOpportunities(row[4]);
            if (row[4] > 0)
                value.setPresentPercent(row[1] * 100.0 / row[4])
                        .setPresentOrExcusedPercent(Math.addExact(row[1], row[3]) * 100.0 / row[4]);
            result.add(value.build());
        }
        return List.copyOf(result);
    }

    static void accumulate(
            ru.rutcampustrack.academic.exportjobs.AdminAttendanceReportBatcher.Batch batch,
            AdminGroupStatsSnapshot response,
            java.util.Map<Long, long[]> totals) {
        var plan = batch.plan();
        var query = plan.getAdminGroupStats();
        if (response.getSerializedSize() > 20 * 1024 * 1024
                || response.getSemesterId() != query.getSemesterId()
                || !response.getDateFrom().equals(query.getDateFrom())
                || !response.getDateTo().equals(query.getDateTo())
                || response.getGroupsCount() != query.getGroupIdsCount()) throw unavailable();
        var seen = new java.util.HashSet<Long>();
        for (var row : response.getGroupsList()) {
            long counted =
                    plan.getLessonsList().stream()
                            .filter(
                                    l ->
                                            l.getGroupId() == row.getGroupId()
                                                    && l.getStatus().equalsIgnoreCase("closed"))
                            .count();
            if (!query.getGroupIdsList().contains(row.getGroupId())
                    || !totals.containsKey(row.getGroupId())
                    || !seen.add(row.getGroupId())
                    || row.getCountedLessons() != counted
                    || row.getPresent() < 0
                    || row.getAbsent() < 0
                    || row.getExcused() < 0
                    || row.getOpportunities() < 0
                    || Math.addExact(
                                    Math.addExact(row.getPresent(), row.getExcused()),
                                    row.getAbsent())
                            != row.getOpportunities()
                    || row.getCountedLessons() == 0 && row.getOpportunities() != 0
                    || row.hasPresentPercent() != (row.getOpportunities() > 0)
                    || row.hasPresentOrExcusedPercent() != (row.getOpportunities() > 0)
                    || row.hasPresentPercent()
                            && (!Double.isFinite(row.getPresentPercent())
                                    || !Double.isFinite(row.getPresentOrExcusedPercent())
                                    || row.getPresentPercent() < 0
                                    || row.getPresentOrExcusedPercent() > 100
                                    || row.getPresentPercent() > row.getPresentOrExcusedPercent()))
                throw unavailable();
            long expectedOpportunities = 0;
            for (var lesson : plan.getLessonsList()) {
                if (lesson.getGroupId() != row.getGroupId()
                        || !lesson.getStatus().equalsIgnoreCase("closed")) continue;
                var roster =
                        plan.getRostersList().stream()
                                .filter(
                                        r ->
                                                r.getGroupId() == lesson.getGroupId()
                                                        && r.getRoster()
                                                                .getAsOfDate()
                                                                .equals(lesson.getDate())
                                                        && r.getRoster().getSemesterId()
                                                                == lesson.getSemesterId())
                                .findFirst()
                                .orElseThrow(AttendanceAdminGroupStatsClient::unavailable);
                expectedOpportunities =
                        Math.addExact(expectedOpportunities, roster.getRoster().getStudentsCount());
            }
            // Canonical CANCELLED marks are excluded from the denominator, including
            // a legal cancellation after this closed Schedule snapshot was captured.
            if (row.getOpportunities() > expectedOpportunities) throw unavailable();
            long[] target = totals.get(row.getGroupId());
            // Each later student fragment echoes the same exact lesson count, accumulated once.
            if (batch.countLessons()) target[0] = Math.addExact(target[0], row.getCountedLessons());
            target[1] = Math.addExact(target[1], row.getPresent());
            target[2] = Math.addExact(target[2], row.getAbsent());
            target[3] = Math.addExact(target[3], row.getExcused());
            target[4] = Math.addExact(target[4], row.getOpportunities());
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Посещаемость групп недоступна");
    }
}
