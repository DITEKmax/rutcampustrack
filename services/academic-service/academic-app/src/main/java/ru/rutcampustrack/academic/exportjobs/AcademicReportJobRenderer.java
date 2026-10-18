package ru.rutcampustrack.academic.exportjobs;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.Render;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;
import ru.rutcampustrack.shared.security.teacher.TeacherReportScope;

import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/** Closed producer dispatch: three owner HTTP renderers, eight data-only Attendance renderers. */
@Component
public class AcademicReportJobRenderer implements ReportJobRenderer {
    @GrpcClient("report-render-attendance")
    private ReportRenderingServiceGrpc.ReportRenderingServiceBlockingStub attendance;

    private final ExportJobService jobs;
    private final AcademicReportPlanBuilder plans;
    private final String ownerUrl;
    private final String directedToken;
    private final String attendanceAuthority;

    public AcademicReportJobRenderer(
            ExportJobService jobs,
            AcademicReportPlanBuilder plans,
            @Value("${rutcampustrack.export-jobs.owner-url:http://academic-service:9091}")
                    String ownerUrl,
            @Value("${grpc.service-identity.academic-report-to-attendance-token:}")
                    String directedToken,
            @Value(
                            "${grpc.client.report-render-attendance.security.authority-override:attendance-service}")
                    String attendanceAuthority) {
        this.jobs = jobs;
        this.plans = plans;
        this.ownerUrl = ownerUrl;
        this.directedToken = directedToken;
        this.attendanceAuthority = attendanceAuthority;
        URI origin = URI.create(ownerUrl);
        if (!java.util.Set.of("http", "https").contains(origin.getScheme())
                || origin.getHost() == null
                || origin.getUserInfo() != null
                || origin.getQuery() != null
                || origin.getFragment() != null
                || origin.getPath() != null
                        && !origin.getPath().isEmpty()
                        && !origin.getPath().equals("/"))
            throw new IllegalArgumentException("Invalid report owner origin");
    }

    @Override
    public ExportJobService.GeneratedFile render(ExportJobRepository.Job job) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Report rendering under transaction");
        if (job.identity().report().kind().name().startsWith("ADMIN_")) return owner(job);
        // No bearer or initial credential is part of this immutable data-only request.
        var plan = plans.build(job);
        String digest;
        try {
            digest =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(plan.toByteArray()));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        var authority = jobs.renderAdmission(job, digest);
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("attendance-service"),
                                directedToken)
                        .orElseThrow(
                                () -> ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE));
        if (attendance == null) throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        var metadata = new Metadata();
        metadata.put(
                Metadata.Key.of("x-report-capability", Metadata.ASCII_STRING_MARSHALLER),
                authority.token());
        final RenderReportResponse response;
        try {
            response =
                    attendance
                            .withMaxInboundMessageSize(21 * 1024 * 1024)
                            .withCallCredentials(
                                    ServiceTokenCallCredentials.forMethod(
                                            credential,
                                            attendanceAuthority,
                                            ReportRenderingServiceGrpc.getRenderReportMethod()
                                                    .getFullMethodName()))
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                            .withDeadlineAfter(
                                    AcademicReportPlanBuilder.remainingMillis(job),
                                    TimeUnit.MILLISECONDS)
                            .renderReport(RenderReportRequest.newBuilder().setPlan(plan).build());
        } catch (StatusRuntimeException failure) {
            throw ExportJobRepository.error(
                    switch (failure.getStatus().getCode()) {
                        case PERMISSION_DENIED, UNAUTHENTICATED, FAILED_PRECONDITION ->
                                HttpStatus.FORBIDDEN;
                        case RESOURCE_EXHAUSTED -> HttpStatus.PAYLOAD_TOO_LARGE;
                        case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                        default -> HttpStatus.SERVICE_UNAVAILABLE;
                    });
        }
        if (response.getBody().isEmpty()
                || response.getBody().size() > ExportJobRepository.MAX_BYTES
                || !MediaType.parseMediaType(job.identity().report().expectedMediaType())
                        .isCompatibleWith(MediaType.parseMediaType(response.getContentType())))
            throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        String scopeHash =
                LocalReportAuthority.unscoped(job.identity().report())
                        ? TeacherReportScope.of(
                                        plan.getTeacherSubjects().getSubjectsList().stream()
                                                .map(
                                                        subject ->
                                                                new TeacherReportScope.Tuple(
                                                                        subject.getGroupId(),
                                                                        subject.getSubjectId()))
                                                .toList())
                                .sha256()
                        : null;
        return new ExportJobService.GeneratedFile(
                response.getBody().toByteArray(), response.getContentType(), 0, false, scopeHash);
    }

    private ExportJobService.GeneratedFile owner(ExportJobRepository.Job job) {
        var admitted = jobs.renderAdmission(job);
        long remaining = AcademicReportPlanBuilder.remainingMillis(job);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(1));
        factory.setReadTimeout(java.time.Duration.ofMillis(remaining));
        return RestClient.builder()
                .baseUrl(ownerUrl)
                .requestFactory(factory)
                .build()
                .post()
                .uri("/academic/export-jobs/{id}/render", job.id())
                .header("X-Internal-Token", admitted.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Render(job.leaseId()))
                .exchange(
                        (request, response) -> {
                            if (response.getStatusCode().value() != 200)
                                throw ExportJobRepository.error(
                                        response.getStatusCode().is4xxClientError()
                                                ? HttpStatus.valueOf(
                                                        response.getStatusCode().value())
                                                : HttpStatus.SERVICE_UNAVAILABLE);
                            if (response.getHeaders().getContentLength()
                                    > ExportJobRepository.MAX_BYTES)
                                throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
                            byte[] body =
                                    response.getBody()
                                            .readNBytes(ExportJobRepository.MAX_BYTES + 1);
                            if (body.length == 0 || body.length > ExportJobRepository.MAX_BYTES)
                                throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
                            String type = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                            if (type == null
                                    || !MediaType.parseMediaType(
                                                    job.identity().report().expectedMediaType())
                                            .isCompatibleWith(MediaType.parseMediaType(type)))
                                throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
                            String rawRows = response.getHeaders().getFirst("X-Export-Row-Count");
                            String rawFull =
                                    response.getHeaders().getFirst("X-Export-Requires-Full");
                            if (rawRows == null
                                    || !rawRows.matches("[0-9]{1,5}")
                                    || !("true".equals(rawFull) || "false".equals(rawFull)))
                                throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
                            int rows = Integer.parseInt(rawRows);
                            if (rows > 10000)
                                throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
                            return new ExportJobService.GeneratedFile(
                                    body, type, rows, Boolean.parseBoolean(rawFull), null);
                        });
    }
}
