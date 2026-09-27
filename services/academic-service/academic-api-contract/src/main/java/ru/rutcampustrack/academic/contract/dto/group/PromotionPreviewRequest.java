package ru.rutcampustrack.academic.contract.dto.group;

import jakarta.validation.constraints.Positive;

/** Optional single-group scope; an omitted ID previews all eligible groups. */
public class PromotionPreviewRequest {
    @Positive
    private Long groupId;

    public PromotionPreviewRequest() {}

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
}
