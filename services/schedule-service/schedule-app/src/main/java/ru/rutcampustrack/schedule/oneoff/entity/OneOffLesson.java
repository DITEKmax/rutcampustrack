package ru.rutcampustrack.schedule.oneoff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * JPA entity for the schedule_one_off_lessons table.
 * Represents a one-off lesson inserted by the headman for a specific date.
 *
 * D-04: No teacher_id — teacher access goes through TeacherSubjectGroup (D-16).
 * D-21: UNIQUE(group_id, date, lesson_number) enforced at DB level.
 * D-23: semester_id NOT NULL — resolved automatically by date at creation time.
 */
@Entity
@Table(name = "schedule_one_off_lessons")
@Getter
@NoArgsConstructor
public class OneOffLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Setter
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Setter
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Setter
    @Column(nullable = false)
    private LocalDate date;

    @Setter
    @Column(name = "lesson_number", nullable = false)
    private Short lessonNumber;

    @Setter
    @Column(length = 64)
    private String classroom;

    @Setter
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
