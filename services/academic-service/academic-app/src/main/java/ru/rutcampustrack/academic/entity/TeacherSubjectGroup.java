package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "teacher_subject_groups",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"teacher_id", "subject_id", "group_id", "semester_id"}))
@Getter
@NoArgsConstructor
public class TeacherSubjectGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;
}
