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
