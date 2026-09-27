package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/** Group deliberately left untouched by the selected cycle. */
@Schema(description = "Группа, пропущенная в цикле перевода")
public class PromotionSkippedItem {
    public enum Reason { ALREADY_PROCESSED, CREATED_AFTER_CYCLE_END }

    private Long id;
    private String name;
    private long studentCount;
    private Reason reason;
    private PromotionPreviewItem.Action previousAction;
    private String previousFrom;
    private String previousTo;

    public PromotionSkippedItem() {}

    public PromotionSkippedItem(Long id, String name, long studentCount, Reason reason,
                                PromotionPreviewItem.Action previousAction,
                                String previousFrom, String previousTo) {
        this.id = id;
        this.name = name;
        this.studentCount = studentCount;
        this.reason = reason;
        this.previousAction = previousAction;
        this.previousFrom = previousFrom;
        this.previousTo = previousTo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getStudentCount() { return studentCount; }
    public void setStudentCount(long studentCount) { this.studentCount = studentCount; }
    public Reason getReason() { return reason; }
    public void setReason(Reason reason) { this.reason = reason; }
    public PromotionPreviewItem.Action getPreviousAction() { return previousAction; }
    public void setPreviousAction(PromotionPreviewItem.Action previousAction) { this.previousAction = previousAction; }
    public String getPreviousFrom() { return previousFrom; }
    public void setPreviousFrom(String previousFrom) { this.previousFrom = previousFrom; }
    public String getPreviousTo() { return previousTo; }
    public void setPreviousTo(String previousTo) { this.previousTo = previousTo; }
}
