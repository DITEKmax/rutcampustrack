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

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol) {
        this(userId, displayName, status, symbol, null, null);
    }

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol,
                                  String source) {
        this(userId, displayName, status, symbol, source, null);
    }

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol,
                                  String source, String excuseReason) {
        this.userId = userId;
        this.displayName = displayName;
        this.status = status;
        this.symbol = symbol;
        this.source = source;
        this.excuseReason = excuseReason;
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
}
