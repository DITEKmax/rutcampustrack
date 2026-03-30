package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "attendance_thresholds",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "subject_id"}))
@Getter
@NoArgsConstructor
public class AttendanceThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "group_id")
    private Long groupId;

    @Setter
    @Column(name = "subject_id")
    private Long subjectId;

    @Setter
    @Column(name = "threshold_pct", nullable = false)
    private int thresholdPct;

    @Column(name = "set_by", nullable = false)
    private Long setBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AttendanceThreshold(Long groupId, Long subjectId, int thresholdPct, Long setBy) {
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.thresholdPct = thresholdPct;
        this.setBy = setBy;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
