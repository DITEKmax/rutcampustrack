package ru.rutcampustrack.mobilebff.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Java-first public DTOs for the authenticated student's request inbox. */
public final class StudentRequestApiModels {

    private StudentRequestApiModels() {
    }

    public enum Kind { EXCUSE, LATE_CHECKIN }

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    public enum Origin { MANUAL, AUTO_GEO_FAILURE }

    public enum Bucket { OPEN, ARCHIVE }

    public enum ExcuseReason {
        ILLNESS, MEDICAL_EXAMINATION, COMPETITION_PARTICIPATION, FAMILY_CIRCUMSTANCES, OTHER
    }

    public enum AttachmentState { ACTIVE, EXPIRED }

    @Schema(name = "StudentRequestLesson", requiredProperties = {
            "id", "lessonNumber", "status", "blocked"
    })
    public record Lesson(
            String id,
            int lessonNumber,
            String status,
            boolean blocked,
            @Schema(nullable = true) String subjectId,
            @Schema(nullable = true) String subjectName,
            @Schema(nullable = true) String subjectType,
            @Schema(nullable = true) String semesterId,
            @Schema(nullable = true) LocalDate date,
            @Schema(nullable = true) LocalTime startsAt,
            @Schema(nullable = true) LocalTime endsAt
    ) {
    }

    @Schema(name = "StudentRequestSummary", requiredProperties = {
            "id", "kind", "status", "origin", "lessons", "createdAt", "updatedAt"
    })
    public record Summary(
            String id,
            Kind kind,
            Status status,
            Origin origin,
            List<Lesson> lessons,
            Instant createdAt,
            Instant updatedAt
    ) {
        public Summary {
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    @Schema(name = "StudentRequestDecision", requiredProperties = {"comment", "decidedAt"})
    public record Decision(
            @Schema(nullable = true) String comment,
            @Schema(nullable = true) Instant decidedAt
    ) {
    }

    @Schema(name = "StudentRequestAttachment", requiredProperties = {
            "id", "name", "contentType", "sizeBytes", "sha256", "state", "uploadedAt", "expiresAt", "expiredAt"
    })
    public record Attachment(
            String id,
            String name,
            String contentType,
            long sizeBytes,
            String sha256,
            AttachmentState state,
            Instant uploadedAt,
            Instant expiresAt,
            @Schema(nullable = true) Instant expiredAt
    ) {
    }

    @Schema(name = "StudentRequestDetail", requiredProperties = {
            "summary", "reason", "comment", "decision", "attachments"
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            Summary summary,
            @Schema(nullable = true) ExcuseReason reason,
            @Schema(nullable = true) String comment,
            @Schema(nullable = true) Decision decision,
            List<Attachment> attachments
    ) {
        public Detail {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    @Schema(name = "StudentRequestPage", requiredProperties = {
            "content", "page", "size", "totalElements", "totalPages"
    })
    public record Page(
            List<Summary> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public Page {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    @Schema(name = "StudentRequestReasonOption", requiredProperties = {"code", "label"})
    public record ReasonOption(ExcuseReason code, String label) {
    }

    @Schema(name = "StudentRequestFileLimits", requiredProperties = {
            "maxFiles", "maxBytesPerFile", "maxBytesTotal", "contentTypes", "extensions"
    })
    public record FileLimits(
            int maxFiles,
            long maxBytesPerFile,
            long maxBytesTotal,
            List<String> contentTypes,
            List<String> extensions
    ) {
        public FileLimits {
            contentTypes = contentTypes == null ? List.of() : List.copyOf(contentTypes);
            extensions = extensions == null ? List.of() : List.copyOf(extensions);
        }
    }

    @Schema(name = "StudentRequestBudget", requiredProperties = {
            "semesterId", "limit", "used", "remaining"
    })
    public record Budget(String semesterId, int limit, int used, int remaining) {
    }

    @Schema(name = "StudentRequestPendingRef", requiredProperties = {"id", "kind", "origin"})
    public record PendingRef(String id, Kind kind, Origin origin) {
    }

    @Schema(name = "StudentRequestLessonOption", requiredProperties = {
            "lesson", "excuseEligible", "lateCheckinEligible", "pendingRequests"
    })
    public record LessonOption(
            Lesson lesson,
            boolean excuseEligible,
            boolean lateCheckinEligible,
            List<PendingRef> pendingRequests
    ) {
        public LessonOption {
            pendingRequests = pendingRequests == null ? List.of() : List.copyOf(pendingRequests);
        }
    }

    @Schema(name = "StudentRequestOptions", requiredProperties = {"reasons", "files", "budget", "lessons"})
    public record Options(
            List<ReasonOption> reasons,
            FileLimits files,
            Budget budget,
            List<LessonOption> lessons
    ) {
        public Options {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    @Schema(name = "StudentExcuseRequest", requiredProperties = {"lessonIds", "reason"})
    public record ExcuseRequest(
            @NotNull @Size(min = 1) List<@Pattern(regexp = "^[1-9][0-9]*$") String> lessonIds,
            @NotNull ExcuseReason reason,
            @Size(max = 1000) String comment
    ) {
    }

    @Schema(name = "StudentLateCheckinRequest", requiredProperties = {"lessonId"})
    public record LateCheckinRequest(
            @NotNull @Pattern(regexp = "^[1-9][0-9]*$") String lessonId
    ) {
    }
}
