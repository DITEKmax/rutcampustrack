package ru.rutcampustrack.academic.contract.dto.semester;

import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for an academic semester with HATEOAS links.
 */
public class SemesterResponse extends RepresentationModel<SemesterResponse> {

    private Long id;
    private String name;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private boolean active;
    private OffsetDateTime createdAt;

    public SemesterResponse() {}

    public SemesterResponse(Long id, String name, LocalDate dateFrom, LocalDate dateTo,
                            boolean active, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.active = active;
        this.createdAt = createdAt;
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
}
