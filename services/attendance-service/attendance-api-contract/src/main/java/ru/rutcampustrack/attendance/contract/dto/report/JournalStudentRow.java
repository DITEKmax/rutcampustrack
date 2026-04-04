package ru.rutcampustrack.attendance.contract.dto.report;

import java.util.List;

/**
 * One row per student in the journal grid (RPRT-02).
 * Plain Java class — no Lombok (contract module rule).
 */
public class JournalStudentRow {

    private final Long userId;
    private final String displayName;
    private final List<JournalCell> records;

    public JournalStudentRow(Long userId, String displayName, List<JournalCell> records) {
        this.userId = userId;
        this.displayName = displayName;
        this.records = records;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<JournalCell> getRecords() {
        return records;
    }
}
