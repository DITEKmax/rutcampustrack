package ru.rutcampustrack.academic.contract.dto.threshold;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.time.OffsetDateTime;

/**
 * Response DTO for a stored attendance threshold record with HATEOAS links.
 * Null groupId = global threshold; null subjectId = applies to all subjects in group.
 */
@Schema(description = "Запись порога посещаемости (HATEOAS Level 3 с _links; null groupId = global, null subjectId = all subjects in group)")
public class ThresholdResponse extends RepresentationModel<ThresholdResponse> {

    private Long id;
    private Long groupId;
    private Long subjectId;
    private int minPercentage;
    private OffsetDateTime updatedAt;

    public ThresholdResponse() {}

    public ThresholdResponse(Long id, Long groupId, Long subjectId,
                             int minPercentage, OffsetDateTime updatedAt) {
        this.id = id;
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.minPercentage = minPercentage;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public int getMinPercentage() { return minPercentage; }
    public void setMinPercentage(int minPercentage) { this.minPercentage = minPercentage; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
