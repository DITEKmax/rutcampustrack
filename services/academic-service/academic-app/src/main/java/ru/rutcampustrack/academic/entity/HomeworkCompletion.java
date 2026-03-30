package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "homework_completions",
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
}
