package ru.rutcampustrack.academic.contract.dto.threshold;

import org.springframework.hateoas.RepresentationModel;

public class ResolvedThresholdResponse extends RepresentationModel<ResolvedThresholdResponse> {

    private int minPercentage;
    /** One of: "subject", "group", "global", "default" */
    private String level;
    private Long sourceId;

    public ResolvedThresholdResponse() {}

    public ResolvedThresholdResponse(int minPercentage, String level, Long sourceId) {
        this.minPercentage = minPercentage;
        this.level = level;
        this.sourceId = sourceId;
    }

    public int getMinPercentage() { return minPercentage; }
    public void setMinPercentage(int minPercentage) { this.minPercentage = minPercentage; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
}
