package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.contract.enums.SemesterType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for an academic semester with HATEOAS links.
 */
@Schema(description = "Академический семестр (HATEOAS Level 3 с _links)")
public class SemesterResponse extends RepresentationModel<SemesterResponse> {

    private Long id;
    private String name;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private boolean active;
    private OffsetDateTime createdAt;
    private SemesterType semesterType;
    private Integer academicYear;
    private boolean archived;
    private SemesterTransition transition = SemesterTransition.NONE;
    private long stateVersion;
    private boolean releasePending;

    public SemesterResponse() {}

    public SemesterResponse(Long id, String name, LocalDate dateFrom, LocalDate dateTo,
                            boolean active, OffsetDateTime createdAt, SemesterType semesterType,
                            Integer academicYear) {
        this(id, name, dateFrom, dateTo, active, createdAt, semesterType, academicYear,
                false, SemesterTransition.NONE, 0L, false);
    }

    public SemesterResponse(Long id, String name, LocalDate dateFrom, LocalDate dateTo,
                            boolean active, OffsetDateTime createdAt, SemesterType semesterType,
                            Integer academicYear, boolean archived, SemesterTransition transition,
                            long stateVersion) {
        this(id, name, dateFrom, dateTo, active, createdAt, semesterType, academicYear,
                archived, transition, stateVersion, false);
    }

    public SemesterResponse(Long id, String name, LocalDate dateFrom, LocalDate dateTo,
                            boolean active, OffsetDateTime createdAt, SemesterType semesterType,
                            Integer academicYear, boolean archived, SemesterTransition transition,
                            long stateVersion, boolean releasePending) {
        this.id = id;
        this.name = name;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.active = active;
        this.createdAt = createdAt;
        this.semesterType = semesterType;
        this.academicYear = academicYear;
        this.archived = archived;
        this.transition = transition == null ? SemesterTransition.NONE : transition;
        this.stateVersion = stateVersion;
        this.releasePending = releasePending;
    }

    public SemesterResponse(Long id, String name, LocalDate dateFrom, LocalDate dateTo,
                            boolean active, OffsetDateTime createdAt, SemesterType semesterType) {
        this(id, name, dateFrom, dateTo, active, createdAt, semesterType, null);
    }

    public SemesterResponse(Long id, String name, LocalDate dateFrom, LocalDate dateTo,
                            boolean active, OffsetDateTime createdAt) {
        this(id, name, dateFrom, dateTo, active, createdAt, null, null);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }

    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public SemesterType getSemesterType() { return semesterType; }
    public void setSemesterType(SemesterType semesterType) { this.semesterType = semesterType; }

    public Integer getAcademicYear() { return academicYear; }
    public void setAcademicYear(Integer academicYear) { this.academicYear = academicYear; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public SemesterTransition getTransition() { return transition; }
    public void setTransition(SemesterTransition transition) {
        this.transition = transition == null ? SemesterTransition.NONE : transition;
    }

    public long getStateVersion() { return stateVersion; }
    public void setStateVersion(long stateVersion) { this.stateVersion = stateVersion; }

    public boolean isReleasePending() { return releasePending; }
    public void setReleasePending(boolean releasePending) { this.releasePending = releasePending; }

    @Schema(description = "Запись блокирует изменения, пока семестр архивирован или переход не завершён")
    public boolean isWriteBlocked() {
        return archived || releasePending || transition != SemesterTransition.NONE;
    }
}
