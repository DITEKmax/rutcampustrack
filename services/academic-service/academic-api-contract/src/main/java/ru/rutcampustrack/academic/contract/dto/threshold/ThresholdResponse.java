package ru.rutcampustrack.academic.contract.dto.threshold;

import org.springframework.hateoas.RepresentationModel;

import java.time.OffsetDateTime;

public class ThresholdResponse extends RepresentationModel<ThresholdResponse> {

    private Long id;
    private Long groupId;
    private Long subjectId;
    private int minPercentage;
    private OffsetDateTime updatedAt;

    public ThresholdResponse() {}

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
