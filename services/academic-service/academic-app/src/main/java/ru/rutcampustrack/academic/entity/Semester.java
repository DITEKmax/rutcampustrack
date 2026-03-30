package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "semesters")
@Getter
@NoArgsConstructor
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 128)
    private String name;

    @Setter
    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Setter
    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
