package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem;

import java.time.OffsetDateTime;

/** Immutable idempotency/history row for one group in one completed spring cycle. */
@Entity
@Table(name = "group_promotion_cycle_record", uniqueConstraints = @UniqueConstraint(
        name = "group_promotion_cycle_semester_group_uq",
        columnNames = {"cycle_semester_id", "group_id"}))
public class GroupPromotionCycleRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_semester_id", nullable = false)
    private Long cycleSemesterId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PromotionPreviewItem.Action action;

    @Column(name = "from_name", nullable = false, length = 32)
    private String fromName;

    @Column(name = "to_name", length = 32)
    private String toName;

    @Column(name = "student_count", nullable = false)
    private long studentCount;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected GroupPromotionCycleRecord() {}

    public GroupPromotionCycleRecord(Long cycleSemesterId, Long groupId,
                                     PromotionPreviewItem.Action action,
                                     String fromName, String toName,
                                     long studentCount, OffsetDateTime processedAt) {
        this.cycleSemesterId = cycleSemesterId;
        this.groupId = groupId;
        this.action = action;
        this.fromName = fromName;
        this.toName = toName;
        this.studentCount = studentCount;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public Long getCycleSemesterId() { return cycleSemesterId; }
    public Long getGroupId() { return groupId; }
    public PromotionPreviewItem.Action getAction() { return action; }
    public String getFromName() { return fromName; }
    public String getToName() { return toName; }
    public long getStudentCount() { return studentCount; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
