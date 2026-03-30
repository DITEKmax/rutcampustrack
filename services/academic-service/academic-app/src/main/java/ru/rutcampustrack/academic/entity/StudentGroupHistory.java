package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_group_history")
@Getter
@NoArgsConstructor
public class StudentGroupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Setter
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Setter
    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    @Setter
    @Column(name = "left_at")
    private LocalDate leftAt;

    @Setter
    @Column(length = 255)
    private String reason;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
