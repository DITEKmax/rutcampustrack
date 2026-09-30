package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPriorState;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionCounts;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "semester_archive_operations")
@Getter
@Setter
@NoArgsConstructor
public class SemesterArchiveOperation {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private long actorId;

    @Column(name = "semester_id", nullable = false, updatable = false)
    private long semesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 8)
    private SemesterArchiveAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_state", nullable = false, length = 10)
    private SemesterArchiveOperationState operationState;

    @Column(name = "retryable", nullable = false)
    private boolean retryable;

    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition", nullable = false, length = 10)
    private SemesterTransition transition;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "release_pending", nullable = false)
    private boolean releasePending;

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_status", nullable = false, length = 20)
    private SemesterArchiveParticipantStatus academic;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status", nullable = false, length = 20)
    private SemesterArchiveParticipantStatus schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private SemesterArchiveParticipantStatus attendance;

    @Column(name = "blocking_reason")
    private String blockingReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_phase", length = 16)
    private SemesterDeletionPhase deletePhase;

    @Enumerated(EnumType.STRING)
    @Column(name = "prior_state", length = 12)
    private SemesterDeletionPriorState priorState;

    @Column(name = "semester_name", length = 128)
    private String semesterName;

    @Column(name = "original_state_version")
    private Long originalStateVersion;

    @Column(name = "preview_digest", length = 64)
    private String previewDigest;

    @Column(name = "academic_participant_digest", length = 64)
    private String academicParticipantDigest;

    @Column(name = "schedule_participant_digest", length = 64)
    private String scheduleParticipantDigest;

    @Column(name = "attendance_participant_digest", length = 64)
    private String attendanceParticipantDigest;

    @Column(name = "schedule_templates_count")
    private Long scheduleTemplatesCount;

    @Column(name = "one_off_lessons_count")
    private Long oneOffLessonsCount;

    @Column(name = "lessons_count")
    private Long lessonsCount;

    @Column(name = "assignments_count")
    private Long assignmentsCount;

    @Column(name = "homeworks_count")
    private Long homeworksCount;

    @Column(name = "attendance_marks_count")
    private Long attendanceMarksCount;

    @Column(name = "student_requests_count")
    private Long studentRequestsCount;

    @Column(name = "prepare_expires_at")
    private OffsetDateTime prepareExpiresAt;

    @Column(name = "irreversible_intent", nullable = false)
    private boolean irreversibleIntent;

    @Column(name = "academic_sealed", nullable = false)
    private boolean academicSealed;

    @Column(name = "schedule_sealed", nullable = false)
    private boolean scheduleSealed;

    @Column(name = "attendance_sealed", nullable = false)
    private boolean attendanceSealed;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public SemesterArchiveOperation(UUID operationId,
                                    UUID idempotencyKey,
                                    long actorId,
                                    long semesterId,
                                    SemesterArchiveAction action) {
        this.operationId = operationId;
        this.idempotencyKey = idempotencyKey;
        this.actorId = actorId;
        this.semesterId = semesterId;
        this.action = action;
        this.operationState = SemesterArchiveOperationState.PENDING;
        this.retryable = true;
        this.transition = SemesterTransition.NONE;
        this.academic = SemesterArchiveParticipantStatus.NOT_STARTED;
        this.schedule = SemesterArchiveParticipantStatus.NOT_STARTED;
        this.attendance = SemesterArchiveParticipantStatus.NOT_STARTED;
    }

    public SemesterDeletionCounts deletionCounts() {
        if (scheduleTemplatesCount == null || oneOffLessonsCount == null || lessonsCount == null
                || assignmentsCount == null || homeworksCount == null
                || attendanceMarksCount == null || studentRequestsCount == null) {
            return null;
        }
        return new SemesterDeletionCounts(scheduleTemplatesCount, oneOffLessonsCount, lessonsCount,
                assignmentsCount, homeworksCount, attendanceMarksCount, studentRequestsCount);
    }

    public void setDeletionCounts(SemesterDeletionCounts counts) {
        this.scheduleTemplatesCount = counts.scheduleTemplates();
        this.oneOffLessonsCount = counts.oneOffLessons();
        this.lessonsCount = counts.lessons();
        this.assignmentsCount = counts.assignments();
        this.homeworksCount = counts.homeworks();
        this.attendanceMarksCount = counts.attendanceMarks();
        this.studentRequestsCount = counts.studentRequests();
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
