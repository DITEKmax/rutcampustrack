package ru.rutcampustrack.attendance.studentrequest;

import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Public domain records consumed by a later transport adapter.
 *
 * <p>These records deliberately contain no Spring MVC, gRPC or generated API
 * types.  They are the frozen boundary between the attendance domain and the
 * PWA/TMA transport work.
 */
public final class StudentRequestModels {

    private StudentRequestModels() {
    }

    /**
     * Immutable authenticated principal supplied by a trusted transport adapter.
     * The request domain never reads servlet, gRPC or message-thread context.
     */
    public record Identity(long userId, UserRole role, Long groupId, boolean headman) {
    }

    public record AttachmentInput(
            String originalFilename,
            String declaredContentType,
            byte[] bytes
    ) {
        public AttachmentInput {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }

    public record ExcuseSubmission(
            List<Long> lessonIds,
            ExcuseType reason,
            String comment,
            List<AttachmentInput> attachments,
            String idempotencyKey
    ) {
        public ExcuseSubmission {
            lessonIds = lessonIds == null ? null : List.copyOf(lessonIds);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }

        public ExcuseSubmission(List<Long> lessonIds, ExcuseType reason,
                                String comment, List<AttachmentInput> attachments) {
            this(lessonIds, reason, comment, attachments, null);
        }
    }

    public record LateCheckinSubmission(
            long lessonId,
            String idempotencyKey
    ) {
        public LateCheckinSubmission(long lessonId) {
            this(lessonId, null);
        }
    }

    public record LessonSnapshot(
            long lessonId,
            long groupId,
            long subjectId,
            /** Snapshot at submit time; schedule/academic reads are not needed for archive. */
            String subjectName,
            String subjectType,
            long semesterId,
            int lessonNumber,
            LocalDate date,
            LocalTime startsAt,
            LocalTime endsAt,
            String status,
            boolean blocked
    ) {
        /** Compatibility constructor for callers that only have schedule fields. */
        public LessonSnapshot(long lessonId, long groupId, long subjectId, long semesterId,
                              int lessonNumber, LocalDate date, LocalTime startsAt,
                              LocalTime endsAt, String status, boolean blocked) {
            this(lessonId, groupId, subjectId, null, null, semesterId, lessonNumber,
                    date, startsAt, endsAt, status, blocked);
        }
    }

    public record AttachmentDescriptor(
            String id,
            String name,
            String contentType,
            long size,
            String sha256,
            AttachmentState state,
            Instant uploadedAt,
            Instant expiresAt,
            Instant expiredAt
    ) {
    }

    public record RequestSummary(
            String id,
            StudentRequestKind kind,
            StudentRequestStatus status,
            StudentRequestOrigin origin,
            List<LessonSnapshot> lessons,
            Instant createdAt,
            Instant updatedAt
    ) {
        public RequestSummary {
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    public record Decision(
            Long decidedBy,
            String comment,
            Instant decidedAt
    ) {
    }

    public record RequestDetail(
            RequestSummary summary,
            ExcuseType reason,
            String comment,
            List<AttachmentDescriptor> attachments,
            Decision decision
    ) {
        public RequestDetail {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    /**
     * Canonical notification context resolved from one persisted request.
     * Transport events never supply group, student or private detail fields.
     */
    public record NotificationResolution(
            long groupId,
            long studentId,
            String studentName,
            RequestDetail detail
    ) {
    }

    public record RequestPage(
            List<RequestSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public RequestPage {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    public record ReasonOption(ExcuseType code, String label) {
    }

    public record FileLimits(
            int maxFiles,
            long maxBytesPerFile,
            long maxBytesTotal,
            List<String> contentTypes,
            List<String> extensions
    ) {
        public FileLimits {
            contentTypes = List.copyOf(contentTypes);
            extensions = List.copyOf(extensions);
        }
    }

    public record Budget(
            long semesterId,
            int limit,
            int used,
            int remaining
    ) {
    }

    public record PendingRequestRef(
            String id,
            StudentRequestKind kind,
            StudentRequestOrigin origin
    ) {
    }

    public record LessonOption(
            LessonSnapshot lesson,
            boolean excuseEligible,
            boolean lateCheckinEligible,
            List<PendingRequestRef> pendingRequests
    ) {
        public LessonOption {
            pendingRequests = pendingRequests == null ? List.of() : List.copyOf(pendingRequests);
        }
    }

    public record RequestOptions(
            List<ReasonOption> reasons,
            FileLimits files,
            Budget budget,
            List<LessonOption> lessons
    ) {
        public RequestOptions {
            reasons = List.copyOf(reasons);
            lessons = List.copyOf(lessons);
        }
    }

    public record AttachmentDownload(
            byte[] bytes,
            String contentType,
            String filename
    ) {
        public AttachmentDownload {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
