package ru.rutcampustrack.academic.contract.dto.threshold;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

/**
 * Response DTO for the effective resolved threshold for a student/group/subject combination.
 * The level indicates which tier of the threshold hierarchy provided the value.
 */
@Schema(description = "Эффективный порог для комбинации студент/группа/предмет (HATEOAS Level 3; level = subject|group|global)")
public class ResolvedThresholdResponse extends RepresentationModel<ResolvedThresholdResponse> {

    private int minPercentage;
    /** Threshold resolution level: "subject", "group", or "global". */
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
