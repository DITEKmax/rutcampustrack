package ru.rutcampustrack.attendance.contract.dto.report;

/**
 * Nested DTO representing one student's attendance status in a lesson attendance list (RPRT-01).
 * Plain Java class — no Lombok (contract module rule).
 */
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

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol) {
        this(userId, displayName, status, symbol, null);
    }

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol,
                                  String source) {
        this.userId = userId;
        this.displayName = displayName;
        this.status = status;
        this.symbol = symbol;
        this.source = source;
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
}
