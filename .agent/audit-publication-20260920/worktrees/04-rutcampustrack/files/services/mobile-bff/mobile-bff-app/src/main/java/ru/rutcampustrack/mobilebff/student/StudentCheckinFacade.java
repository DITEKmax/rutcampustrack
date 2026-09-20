package ru.rutcampustrack.mobilebff.student;

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

import org.springframework.http.HttpStatus;

@Service
public class StudentCheckinFacade {
    private final MobileAttendanceClient attendance;
    private final MobileRequestContext requestContext;

    public StudentCheckinFacade(MobileAttendanceClient attendance, MobileRequestContext requestContext) {
        this.attendance = attendance;
        this.requestContext = requestContext;
    }

    public CheckinAck checkin(long lessonId, String key, CheckinRequest request) {
        requireStudentScope();
        StudentCheckinCommand.Builder command = StudentCheckinCommand.newBuilder()
                .setLessonId(lessonId).setIdempotencyKey(key);
        if (request.geo() instanceof CoordinatesGeo coordinates) {
            command.setCoordinates(Coordinates.newBuilder()
                    .setLatitude(coordinates.latitude()).setLongitude(coordinates.longitude()).build());
        } else if (request.geo() instanceof UnavailableGeo unavailable) {
            command.setUnavailable(GeoUnavailable.newBuilder().setReason(
                    ru.rutcampustrack.attendance.grpc.GeoUnavailableReason.valueOf(
                    "GEO_UNAVAILABLE_REASON_" + unavailable.reason().name())).build());
        }
        StudentCheckinResult result = attendance.checkin(command.build());
        AttendanceProjection attendanceProjection = result.hasAttendance()
                && result.getAttendance().getStatus()
                != ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_UNSPECIFIED
                ? new AttendanceProjection(
                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.AttendanceStatus.valueOf(
                        result.getAttendance().getStatus().name()
                        .replace("ATTENDANCE_STATUS_", "")),
                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.AttendanceSource.valueOf(
                        result.getAttendance().getSource().name()
                        .replace("ATTENDANCE_SOURCE_", "")),
                result.getAttendance().hasMarkedAt()
                        ? Instant.parse(result.getAttendance().getMarkedAt()) : null)
                : null;
        return new CheckinAck(
                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinOutcome.valueOf(
                        result.getOutcome().name().replace("CHECKIN_OUTCOME_", "")),
                Long.toString(result.getLessonId()), attendanceProjection,
                result.hasRequest() ? StudentQueryService.request(result.getRequest()) : null,
                result.hasRetryAt() ? Instant.parse(result.getRetryAt()) : null,
                Instant.parse(result.getServerNow()),
                Map.of("self", new Link(URI.create("/api/v1/student/lessons/" + lessonId + "/checkin")),
                        "today", new Link(URI.create("/api/v1/student/today"))));
    }

    private void requireStudentScope() {
        InternalJwtClaims claims = requestContext.claims();
        if (!"STUDENT".equalsIgnoreCase(claims.role())) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.WRONG_ROLE,
                    "Мобильный student API доступен роли STUDENT");
        }
        if (claims.readOnly()) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.ROLE_READ_ONLY,
                    "Терминальная student-сессия доступна только для чтения");
        }
        if (claims.userId() <= 0
                || claims.groupId() == null || claims.groupId() <= 0) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                    "Не хватает student/group scope");
        }
    }
}
