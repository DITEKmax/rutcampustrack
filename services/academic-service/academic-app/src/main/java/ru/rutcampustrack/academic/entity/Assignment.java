package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Immutable effective-dated assignment row from V25. */
@Entity
@Table(name = "assignments")
@Getter
@NoArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private Long teacherId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "semester_id", nullable = false, updatable = false)
    private Long semesterId;

    @Column(name = "lesson_type", nullable = false, updatable = false,
            columnDefinition = "subject_type")
    private SubjectType lessonType;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until_exclusive", updatable = false)
    private LocalDate validUntilExclusive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Assignment(Long teacherId,
                      Long subjectId,
                      Long groupId,
                      Long semesterId,
                      SubjectType lessonType,
                      LocalDate validFrom,
                      LocalDate validUntilExclusive) {
        this.teacherId = teacherId;
        this.subjectId = subjectId;
        this.groupId = groupId;
        this.semesterId = semesterId;
        this.lessonType = lessonType;
        this.validFrom = validFrom;
        this.validUntilExclusive = validUntilExclusive;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
