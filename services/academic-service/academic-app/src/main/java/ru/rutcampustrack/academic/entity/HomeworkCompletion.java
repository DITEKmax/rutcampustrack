package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "homework_completions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"homework_id", "student_id"}))
@Getter
@NoArgsConstructor
public class HomeworkCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "homework_id", nullable = false)
    private Long homeworkId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private OffsetDateTime completedAt;

    public HomeworkCompletion(Long homeworkId, Long studentId) {
        this.homeworkId = homeworkId;
        this.studentId = studentId;
    }

    @PrePersist
    protected void onCreate() {
        if (completedAt == null) {
            completedAt = OffsetDateTime.now();
        }
    }
}
