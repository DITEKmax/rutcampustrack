package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "headman_assistants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "student_id"}))
@Getter
@NoArgsConstructor
public class HeadmanAssistant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Setter
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", nullable = false, columnDefinition = "varchar(64)[]")
    private String[] permissions;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @Setter
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public HeadmanAssistant(Long groupId, Long studentId, String[] permissions, Long assignedBy) {
        this.groupId = groupId;
        this.studentId = studentId;
        this.permissions = permissions;
        this.assignedBy = assignedBy;
        this.isActive = true;
    }

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now();
        }
    }
}
