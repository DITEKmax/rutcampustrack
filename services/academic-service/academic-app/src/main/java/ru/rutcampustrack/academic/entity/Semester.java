package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;

import java.util.UUID;

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
    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "archive_transition", nullable = false, length = 10)
    private SemesterTransition archiveTransition = SemesterTransition.NONE;

    @Setter
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Setter
    @Column(name = "archive_release_pending", nullable = false)
    private boolean releasePending;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_phase", length = 16)
    private SemesterDeletionPhase deletionPhase;

    @Setter
    @Column(name = "transition_operation_id")
    private UUID transitionOperationId;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "first_week_type", nullable = false, length = 10)
    private String firstWeekType = "odd";

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "semester_type", length = 8)
    private SemesterType semesterType;

    @Setter
    @Column(name = "academic_year")
    private Integer academicYear;
}
