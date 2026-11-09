package ru.rutcampustrack.mobilebff.student;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.*;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Service
public class StudentCheckinFacade {
    private final MobileAttendanceClient attendance;
    private final MobileRequestContext requestContext;
    private final io.micrometer.core.instrument.MeterRegistry acquisitionMetrics;

    public StudentCheckinFacade(
            MobileAttendanceClient attendance, MobileRequestContext requestContext) {
        this(
                attendance,
                requestContext,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StudentCheckinFacade(
            MobileAttendanceClient attendance,
            MobileRequestContext requestContext,
            io.micrometer.core.instrument.MeterRegistry acquisitionMetrics) {
        this.attendance = attendance;
        this.requestContext = requestContext;
        this.acquisitionMetrics = acquisitionMetrics;
    }

    public void reportCheckinAcquisition(CheckinAcquisitionReport report) {
        requireStudentScope();
        String[] labels = {"source", report.source().name(), "outcome", report.outcome().name()};
        io.micrometer.core.instrument.Counter.builder("mobile.client.geo.acquisition.reports")
                .description(
                        "Best-effort client-reported sensor completions, not business attempts")
                .tags(labels)
                .register(acquisitionMetrics)
                .increment();
        if (report.durationMs() != null) {
            io.micrometer.core.instrument.Timer.builder("mobile.client.geo.acquisition.duration")
                    .description("Client-reported acquisition duration, not server latency")
                    .tags(labels)
                    .register(acquisitionMetrics)
                    .record(report.durationMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    public CheckinAck checkin(long lessonId, String key, CheckinRequest request) {
        requireStudentScope();
        StudentCheckinCommand.Builder command =
                StudentCheckinCommand.newBuilder().setLessonId(lessonId).setIdempotencyKey(key);
        if (request.geo() instanceof CoordinatesGeo coordinates) {
            command.setCoordinates(
                    Coordinates.newBuilder()
                            .setLatitude(coordinates.latitude())
                            .setLongitude(coordinates.longitude())
                            .build());
        } else if (request.geo() instanceof UnavailableGeo unavailable) {
            command.setUnavailable(
                    GeoUnavailable.newBuilder()
                            .setReason(
                                    ru.rutcampustrack.attendance.grpc.GeoUnavailableReason.valueOf(
                                            "GEO_UNAVAILABLE_REASON_"
                                                    + unavailable.reason().name()))
                            .build());
        }
        if (request.diagnostics() != null) {
            CheckinDiagnostics input = request.diagnostics();
            GeoDiagnostics.Builder diagnostics =
                    GeoDiagnostics.newBuilder()
                            .setSource(
                                    ru.rutcampustrack.attendance.grpc.GeoDiagnosticSource.valueOf(
                                            "GEO_DIAGNOSTIC_SOURCE_" + input.source().name()));
            if (input.accuracyMeters() != null)
                diagnostics.setAccuracyMeters(input.accuracyMeters());
            if (input.fixTimestamp() != null)
                diagnostics.setFixTimestamp(input.fixTimestamp().toString());
            if (input.receivedAt() != null)
                diagnostics.setReceivedAt(input.receivedAt().toString());
            if (input.sampleCount() != null) diagnostics.setSampleCount(input.sampleCount());
            if (input.acquisitionDurationMs() != null)
                diagnostics.setAcquisitionDurationMs(input.acquisitionDurationMs());
            if (input.completionReason() != null)
                diagnostics.setCompletionReason(
                        ru.rutcampustrack.attendance.grpc.GeoCompletionReason.valueOf(
                                "GEO_COMPLETION_REASON_" + input.completionReason().name()));
            command.setDiagnostics(diagnostics);
        }
        StudentCheckinResult result = attendance.checkin(command.build());
        AttendanceProjection attendanceProjection =
                result.hasAttendance()
                                && result.getAttendance().getStatus()
                                        != ru.rutcampustrack.attendance.grpc.AttendanceStatus
                                                .ATTENDANCE_STATUS_UNSPECIFIED
                        ? new AttendanceProjection(
                                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                        .AttendanceStatus.valueOf(
                                        result.getAttendance()
                                                .getStatus()
                                                .name()
                                                .replace("ATTENDANCE_STATUS_", "")),
                                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                        .AttendanceSource.valueOf(
                                        result.getAttendance()
                                                .getSource()
                                                .name()
                                                .replace("ATTENDANCE_SOURCE_", "")),
                                result.getAttendance().hasMarkedAt()
                                        ? Instant.parse(result.getAttendance().getMarkedAt())
                                        : null)
                        : null;
        return new CheckinAck(
                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinOutcome.valueOf(
                        result.getOutcome().name().replace("CHECKIN_OUTCOME_", "")),
                Long.toString(result.getLessonId()),
                attendanceProjection,
                result.hasRequest() ? StudentQueryService.request(result.getRequest()) : null,
                result.hasRetryAt() ? Instant.parse(result.getRetryAt()) : null,
                Instant.parse(result.getServerNow()),
                Map.of(
                        "self",
                        new Link(URI.create("/api/v1/student/lessons/" + lessonId + "/checkin")),
                        "today",
                        new Link(URI.create("/api/v1/student/today"))));
    }

    private void requireStudentScope() {
        InternalJwtClaims claims = requestContext.claims();
        if (!"STUDENT".equalsIgnoreCase(claims.domainRole())) {
            throw new MobileBffException(
                    HttpStatus.FORBIDDEN,
                    ProblemCode.WRONG_ROLE,
                    "Мобильный student API доступен роли STUDENT");
        }
        if (claims.readOnly()) {
            throw new MobileBffException(
                    HttpStatus.FORBIDDEN,
                    ProblemCode.ROLE_READ_ONLY,
                    "Терминальная student-сессия доступна только для чтения");
        }
        if (claims.userId() <= 0 || claims.groupId() == null || claims.groupId() <= 0) {
            throw new MobileBffException(
                    HttpStatus.FORBIDDEN,
                    ProblemCode.OUT_OF_SCOPE,
                    "Не хватает student/group scope");
        }
    }
}
