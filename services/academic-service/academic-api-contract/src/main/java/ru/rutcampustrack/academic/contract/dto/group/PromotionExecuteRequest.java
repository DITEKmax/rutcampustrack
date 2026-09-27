package ru.rutcampustrack.academic.contract.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Confirms exactly one previously returned cycle/scope/plan version. */
public class PromotionExecuteRequest {
    @NotNull
    @Positive
    private Long cycleSemesterId;

    @NotBlank
    @Size(max = 64)
    private String previewVersion;

    @Positive
    private Long groupId;

    public PromotionExecuteRequest() {}

    public Long getCycleSemesterId() { return cycleSemesterId; }
    public void setCycleSemesterId(Long cycleSemesterId) { this.cycleSemesterId = cycleSemesterId; }

    public String getPreviewVersion() { return previewVersion; }
    public void setPreviewVersion(String previewVersion) { this.previewVersion = previewVersion; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
}
