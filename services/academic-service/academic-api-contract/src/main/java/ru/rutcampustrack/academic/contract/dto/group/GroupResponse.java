package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.time.OffsetDateTime;

/**
 * Response DTO for a student group with HATEOAS links.
 *
 * <p>BUG-006-5: поле {@code code} удалено, {@code name} — единственный идентификатор.
 */
@Schema(description = "Студенческая группа (HATEOAS Level 3 с _links)")
public class GroupResponse extends RepresentationModel<GroupResponse> {

    private Long id;
    private String name;
    private String alphabeticCode;
    private String numericCode;
    private Integer currentCourse;
    private Integer trainingDurationYears;
    private String durationStatus;
    private boolean active;
    private OffsetDateTime createdAt;

    public GroupResponse() {}

    public GroupResponse(Long id, String name, boolean active, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
    }

    public GroupResponse(Long id, String name, String alphabeticCode, String numericCode,
                         Integer currentCourse, Integer trainingDurationYears,
                         String durationStatus, boolean active, OffsetDateTime createdAt) {
        this(id, name, active, createdAt);
        this.alphabeticCode = alphabeticCode;
        this.numericCode = numericCode;
        this.currentCourse = currentCourse;
        this.trainingDurationYears = trainingDurationYears;
        this.durationStatus = durationStatus;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAlphabeticCode() { return alphabeticCode; }
    public void setAlphabeticCode(String alphabeticCode) { this.alphabeticCode = alphabeticCode; }

    public String getNumericCode() { return numericCode; }
    public void setNumericCode(String numericCode) { this.numericCode = numericCode; }

    public Integer getCurrentCourse() { return currentCourse; }
    public void setCurrentCourse(Integer currentCourse) { this.currentCourse = currentCourse; }

    public Integer getTrainingDurationYears() { return trainingDurationYears; }
    public void setTrainingDurationYears(Integer trainingDurationYears) { this.trainingDurationYears = trainingDurationYears; }

    public String getDurationStatus() { return durationStatus; }
    public void setDurationStatus(String durationStatus) { this.durationStatus = durationStatus; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
