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

    public StudentAttendanceEntry(Long userId, String displayName, String status, String symbol) {
        this.userId = userId;
        this.displayName = displayName;
        this.status = status;
        this.symbol = symbol;
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
}
