package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Durable cross-service state for one teacher replacement request. */
@Entity
@Table(name = "assignment_replacement_operations")
@Getter
@NoArgsConstructor
public class AssignmentReplacementOperation {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(name = "request_key", nullable = false, updatable = false)
    private UUID requestKey;

    @Column(name = "payload_hash", nullable = false, updatable = false)
    private byte[] payloadHash;

    @Column(name = "source_assignment_id", nullable = false, updatable = false)
    private Long sourceAssignmentId;

    @Column(name = "target_assignment_id", nullable = false, updatable = false)
    private Long targetAssignmentId;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "source_valid_until")
    private LocalDate sourceValidUntil;

    @Column(name = "target_valid_until")
    private LocalDate targetValidUntil;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "schedule_receipt_state")
    private String scheduleReceiptState;

    @Column(name = "schedule_moved_count", nullable = false)
    private long scheduleMovedCount;

    @Column(name = "schedule_skipped_count", nullable = false)
    private long scheduleSkippedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public AssignmentReplacementOperation(UUID operationId,
                                          Long actorId,
                                          UUID requestKey,
                                          byte[] payloadHash,
                                          Long sourceAssignmentId,
                                          Long targetAssignmentId,
                                          LocalDate effectiveFrom,
                                          LocalDate sourceValidUntil,
                                          LocalDate targetValidUntil,
                                          String state) {
        this.operationId = operationId;
        this.actorId = actorId;
        this.requestKey = requestKey;
        this.payloadHash = payloadHash.clone();
        this.sourceAssignmentId = sourceAssignmentId;
        this.targetAssignmentId = targetAssignmentId;
        this.effectiveFrom = effectiveFrom;
        this.sourceValidUntil = sourceValidUntil;
        this.targetValidUntil = targetValidUntil;
        this.state = state;
        this.scheduleMovedCount = 0;
        this.scheduleSkippedCount = 0;
    }

    public void markApplied(String receiptState, long moved, long skipped) {
        this.state = "APPLIED";
        this.scheduleReceiptState = receiptState;
        this.scheduleMovedCount = moved;
        this.scheduleSkippedCount = skipped;
    }

    public void markCommitted(String receiptState, long moved, long skipped) {
        this.state = "COMMITTED";
        this.scheduleReceiptState = receiptState;
        this.scheduleMovedCount = moved;
        this.scheduleSkippedCount = skipped;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
