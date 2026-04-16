package ru.rutcampustrack.attendance.contract.dto.report;

/**
 * One cell in the journal grid (D-08) — represents attendance for a student on a given date/lesson.
 * Plain Java class — no Lombok (contract module rule).
 * lessonId added (Phase 55 D-01) to support headman cell-click marking via
 * PUT /attendance/lessons/{lessonId}/students/{userId}.
 */
public class JournalCell {

    private final Long lessonId;
    private final String date;
    private final Integer lessonNumber;
    private final String status;
    private final String symbol;
    /**
     * Human-readable excuse reason shown next to "у" in the journal.
     * Non-null only when status is EXCUSED / FREE_ATTENDANCE and the cascade
     * from an approved excuse ticket stamped it onto the document.
     */
    private final String excuseReason;

    public JournalCell(Long lessonId, String date, Integer lessonNumber, String status, String symbol) {
        this(lessonId, date, lessonNumber, status, symbol, null);
    }

    public JournalCell(Long lessonId, String date, Integer lessonNumber, String status, String symbol,
                       String excuseReason) {
        this.lessonId = lessonId;
        this.date = date;
        this.lessonNumber = lessonNumber;
        this.status = status;
        this.symbol = symbol;
        this.excuseReason = excuseReason;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public String getDate() {
        return date;
    }

    public Integer getLessonNumber() {
        return lessonNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExcuseReason() {
        return excuseReason;
    }
}
