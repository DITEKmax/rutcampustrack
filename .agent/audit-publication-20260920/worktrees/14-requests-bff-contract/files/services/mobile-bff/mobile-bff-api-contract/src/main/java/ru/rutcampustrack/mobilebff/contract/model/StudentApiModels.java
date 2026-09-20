package ru.rutcampustrack.mobilebff.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Public JS-STUDENT-01-r1 value types. Nested records keep the bounded contract
 * together; Springdoc names are pinned explicitly for stable generated clients.
 */
public final class StudentApiModels {

    private StudentApiModels() {
    }

    public enum ActiveRole { STUDENT }
    public enum Capability { TODAY, GEO_CHECKIN, OFFLINE_SEMESTER_SCHEDULE }
    public enum LessonType { LECTURE, PRACTICE, LAB }
    public enum LessonStatus { PLANNED, ACTIVE, CLOSED, CANCELLED }
    public enum RoomChangeState { UNCHANGED, CHANGED, UNKNOWN }
    public enum AttendanceStatus { PRESENT, ABSENT, EXCUSED }
    public enum AttendanceSource { STUDENT_GEO, LATE_CHECKIN, HEADMAN, TEACHER, SYSTEM }
    public enum EligibilityReason {
        ELIGIBLE,
        ALREADY_PRESENT,
        LESSON_CANCELLED,
        TOO_EARLY,
        WINDOW_CLOSED,
        GEO_BLOCKED,
        PENDING_CONFIRMATION,
        COOLDOWN,
        HEADMAN_ABSENT_REQUIRES_APPEAL,
        HEADMAN_USES_JOURNAL,
        DEPENDENCY_UNAVAILABLE
    }
    public enum RequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }
    public enum RequestOrigin { AUTO_GEO_FAILURE }
    public enum ResolutionReason { GEO_CONFIRMED, HEADMAN_APPROVED, HEADMAN_REJECTED, STUDENT_CANCELLED, PRESENT_PRIORITY }
    public enum CheckinOutcome { PRESENT, PENDING_CONFIRMATION }
    public enum GeoKind { COORDINATES, UNAVAILABLE }
    public enum GeoUnavailableReason { PERMISSION_DENIED, POSITION_UNAVAILABLE, TIMEOUT }
    public enum ProblemCode {
        INVALID_REQUEST,
        INVALID_IDEMPOTENCY_KEY,
        INVALID_SESSION,
        WRONG_ROLE,
        OUT_OF_SCOPE,
        HOMEWORK_NOT_FOUND,
        LESSON_NOT_FOUND,
        REQUEST_NOT_FOUND,
        ATTACHMENT_NOT_FOUND,
        REQUEST_CONFLICT,
        ATTACHMENT_EXPIRED,
        PAYLOAD_TOO_LARGE,
        CHECKIN_COOLDOWN,
        MANUAL_ABSENCE_REQUIRES_APPEAL,
        IDEMPOTENCY_PAYLOAD_MISMATCH,
        CHECKIN_NOT_ELIGIBLE,
        DEPENDENCY_UNAVAILABLE,
        INTERNAL_ERROR
    }

    @Schema(name = "Link", requiredProperties = {"href"})
    public record Link(URI href) {
    }

    @Schema(name = "StudentUser", requiredProperties = {"id", "displayName"})
    public record StudentUser(String id, String displayName) {
    }

    @Schema(name = "GroupSummary", requiredProperties = {"id", "name"})
    public record GroupSummary(String id, String name) {
    }

    @Schema(name = "SemesterSummary", requiredProperties = {"id", "name", "startsOn", "endsOn"})
    public record SemesterSummary(String id, String name, LocalDate startsOn, LocalDate endsOn) {
    }

    @Schema(
            name = "StudentSession",
            requiredProperties = {"user", "activeRole", "group", "semester", "capabilities", "serverNow", "_links"}
    )
    public record SessionResponse(
            StudentUser user,
            ActiveRole activeRole,
            @Schema(nullable = true) GroupSummary group,
            @Schema(nullable = true) SemesterSummary semester,
            List<Capability> capabilities,
            Instant serverNow,
            @JsonProperty("_links") Map<String, Link> links
    ) {
    }

    @Schema(name = "Subject", requiredProperties = {"id", "name", "type"})
    public record SubjectProjection(String id, String name, LessonType type) {
    }

    @Schema(name = "Room", requiredProperties = {"current", "previous", "changeState"})
    public record RoomProjection(
            @Schema(nullable = true) String current,
            @Schema(nullable = true) String previous,
            RoomChangeState changeState
    ) {
    }

    @Schema(
            name = "LessonSchedule",
            requiredProperties = {
                    "id", "date", "lessonNumber", "startsAt", "endsAt", "status", "subject", "room"
            }
    )
    public record LessonScheduleProjection(
            String id,
            LocalDate date,
            int lessonNumber,
            LocalTime startsAt,
            LocalTime endsAt,
            LessonStatus status,
            SubjectProjection subject,
            RoomProjection room
    ) {
    }

    @Schema(name = "Attendance", requiredProperties = {"status", "source", "markedAt"})
    public record AttendanceProjection(
            AttendanceStatus status,
            AttendanceSource source,
            @Schema(nullable = true) Instant markedAt
    ) {
    }

    @Schema(name = "CheckinEligibility", requiredProperties = {"allowed", "reason", "retryAt"})
    public record CheckinEligibility(
            boolean allowed,
            EligibilityReason reason,
            @Schema(nullable = true) Instant retryAt
    ) {
    }

    @Schema(
            name = "AutomaticCheckinRequest",
            requiredProperties = {"id", "status", "origin", "resolutionReason"}
    )
    public record AutomaticRequestProjection(
            String id,
            RequestStatus status,
            RequestOrigin origin,
            @Schema(nullable = true) ResolutionReason resolutionReason
    ) {
    }

    @Schema(
            name = "TodayLesson",
            requiredProperties = {"schedule", "attendance", "checkinEligibility", "request"}
    )
    public record TodayLessonResponse(
            LessonScheduleProjection schedule,
            @Schema(nullable = true) AttendanceProjection attendance,
            CheckinEligibility checkinEligibility,
            @Schema(nullable = true) AutomaticRequestProjection request
    ) {
    }

    @Schema(
            name = "StudentToday",
            requiredProperties = {"date", "timeZone", "serverNow", "lessons", "_links"}
    )
    public record TodayResponse(
            LocalDate date,
            String timeZone,
            Instant serverNow,
            List<TodayLessonResponse> lessons,
            @JsonProperty("_links") Map<String, Link> links
    ) {
    }

    @Schema(
            name = "StudentSemesterSchedule",
            requiredProperties = {"semester", "dateFrom", "dateTo", "updatedAt", "lessons", "_links"}
    )
    public record ScheduleResponse(
            SemesterSummary semester,
            LocalDate dateFrom,
            LocalDate dateTo,
            Instant updatedAt,
            List<LessonScheduleProjection> lessons,
            @JsonProperty("_links") Map<String, Link> links
    ) {
    }

    @Schema(name = "StudentHomeworkSemester", requiredProperties = {"id", "name", "dateFrom", "dateTo"})
    public record HomeworkSemester(String id, String name, LocalDate dateFrom, LocalDate dateTo) {
    }

    @Schema(name = "StudentHomeworkSubject", requiredProperties = {"id", "name"})
    public record HomeworkSubject(String id, String name) {
    }

    @Schema(
            name = "StudentHomeworkItem",
            requiredProperties = {
                    "id", "subject", "title", "description", "link", "lessonDate", "lessonNumber", "completed",
                    "completedAt"
            }
    )
    public record HomeworkItem(
            String id,
            HomeworkSubject subject,
            String title,
            String description,
            @Schema(nullable = true) String link,
            LocalDate lessonDate,
            int lessonNumber,
            boolean completed,
            @Schema(nullable = true) Instant completedAt
    ) {
    }

    @Schema(
            name = "StudentHomework",
            requiredProperties = {"semester", "from", "to", "serverNow", "items"}
    )
    public record HomeworkResponse(
            HomeworkSemester semester,
            LocalDate from,
            LocalDate to,
            Instant serverNow,
            List<HomeworkItem> items
    ) {
    }

    @Schema(name = "StudentHomeworkCompletionCommand", requiredProperties = {"completed"})
    public record HomeworkCompletionRequest(@NotNull Boolean completed) {
    }

    @Schema(name = "StudentHomeworkCompletion", requiredProperties = {"id", "completed", "completedAt"})
    public record HomeworkCompletionResponse(
            String id,
            boolean completed,
            @Schema(nullable = true) Instant completedAt
    ) {
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "kind",
            visible = true
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CoordinatesGeo.class, name = "COORDINATES"),
            @JsonSubTypes.Type(value = UnavailableGeo.class, name = "UNAVAILABLE")
    })
    @Schema(
            name = "GeoInput",
            discriminatorProperty = "kind",
            oneOf = {CoordinatesGeo.class, UnavailableGeo.class}
    )
    public sealed interface GeoInput permits CoordinatesGeo, UnavailableGeo {
        GeoKind kind();
    }

    @Schema(
            name = "CoordinatesGeo",
            requiredProperties = {"kind", "latitude", "longitude"}
    )
    public record CoordinatesGeo(
            @NotNull @Schema(allowableValues = "COORDINATES") GeoKind kind,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) implements GeoInput {
    }

    @Schema(name = "UnavailableGeo", requiredProperties = {"kind", "reason"})
    public record UnavailableGeo(
            @NotNull @Schema(allowableValues = "UNAVAILABLE") GeoKind kind,
            @NotNull GeoUnavailableReason reason
    ) implements GeoInput {
    }

    @Schema(name = "StudentCheckinCommand", requiredProperties = {"geo"})
    public record CheckinRequest(@NotNull @Valid GeoInput geo) {
    }

    @Schema(
            name = "StudentCheckinAck",
            requiredProperties = {"outcome", "lessonId", "attendance", "request", "retryAt", "serverNow", "_links"}
    )
    public record CheckinAck(
            CheckinOutcome outcome,
            String lessonId,
            @Schema(nullable = true) AttendanceProjection attendance,
            @Schema(nullable = true) AutomaticRequestProjection request,
            @Schema(nullable = true) Instant retryAt,
            Instant serverNow,
            @JsonProperty("_links") Map<String, Link> links
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            name = "MobileProblemDetails",
            description = "RFC 9457 Problem Details with a stable machine code",
            requiredProperties = {"status", "type", "title", "detail", "instance", "timestamp", "code"}
    )
    public record MobileProblemDetails(
            int status,
            URI type,
            String title,
            String detail,
            URI instance,
            Instant timestamp,
            @Schema(nullable = true) String traceId,
            ProblemCode code,
            @Schema(nullable = true) Instant retryAt,
            @Schema(nullable = true) Map<String, String> extras
    ) {
    }
}
