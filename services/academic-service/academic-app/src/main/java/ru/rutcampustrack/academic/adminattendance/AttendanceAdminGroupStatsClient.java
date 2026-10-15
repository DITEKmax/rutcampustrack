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

    @GrpcClient("attendance-service")
    private AttendanceAdminGroupStatsGrpcServiceGrpc
                    .AttendanceAdminGroupStatsGrpcServiceBlockingStub
            stub;

    public AttendanceAdminGroupStatsClient(
            @Value("${grpc.service-identity.academic-to-attendance-token:}") String serviceToken,
            @Value("${grpc.service-identity.attendance-authority:attendance-service:19093}")
                    String targetAuthority) {
        this.serviceToken = serviceToken;
        this.targetAuthority = targetAuthority;
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

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Посещаемость групп недоступна");
    }
}
