package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nested DTO representing one student's attendance status in a lesson attendance list (RPRT-01).
 * Plain Java class — no Lombok (contract module rule).
 */
@Schema(description = "Запись посещаемости студента в списке пары: ID, имя, статус, символ, источник, причина excuse")
public class StudentAttendanceEntry {

    private final Long userId;
    private final String displayName;
    private final String status;
    private final String symbol;
    /**
     * v9.0: attendance source (student_geo / headman / auto_scheduler / late_checkin / headman_excuse).
     * NULL when the student has no attendance document yet (auto-absent only).
     * Used by PWA/Mini-App headman sheet to show "ст" badge for self-marked students.
     */
    private final String source;
    /**
     * Human-readable excuse reason shown next to "у" in lesson rosters.
     * Non-null only when status is EXCUSED / FREE_ATTENDANCE via the excuse cascade.
     */
    private final String excuseReason;
    private final String excuseType;
    private final String comment;
    private final String attachmentId;
    private final String attachmentName;
    private final String attachmentContentType;
    private final Long attachmentSize;
    private final boolean editable;
    private final String editBlockedReason;

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol) {
        this(userId, displayName, status, symbol, null, null,
                null, null, null, null, null, null, false, null);
    }

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol,
                                  String source) {
        this(userId, displayName, status, symbol, source, null,
                null, null, null, null, null, null, false, null);
    }

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol,
                                  String source, String excuseReason) {
        this(userId, displayName, status, symbol, source, excuseReason,
                null, null, null, null, null, null, false, null);
    }

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol,
                                  String source, String excuseReason, String excuseType,
                                  String comment, String attachmentId, String attachmentName,
                                  String attachmentContentType, Long attachmentSize,
                                  boolean editable, String editBlockedReason) {
        this.userId = userId;
        this.displayName = displayName;
        this.status = status;
        this.symbol = symbol;
        this.source = source;
        this.excuseReason = excuseReason;
        this.excuseType = excuseType;
        this.comment = comment;
        this.attachmentId = attachmentId;
        this.attachmentName = attachmentName;
        this.attachmentContentType = attachmentContentType;
        this.attachmentSize = attachmentSize;
        this.editable = editable;
        this.editBlockedReason = editBlockedReason;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSource() {
        return source;
    }

    public String getExcuseReason() {
        return excuseReason;
    }

    public String getExcuseType() {
        return excuseType;
    }

    public String getComment() {
        return comment;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public String getAttachmentContentType() {
        return attachmentContentType;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public boolean isEditable() {
        return editable;
    }

    public String getEditBlockedReason() {
        return editBlockedReason;
    }
}
