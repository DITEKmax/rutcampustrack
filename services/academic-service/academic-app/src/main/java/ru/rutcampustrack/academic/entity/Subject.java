package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import java.time.OffsetDateTime;

@Entity
@Table(name = "subjects")
@Getter
@NoArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 255)
    private String name;

    @Setter
    @Column(nullable = false)
    private SubjectType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
