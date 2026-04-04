package ru.rutcampustrack.attendance.contract.dto.report;

/**
 * One cell in the journal grid (D-08) — represents attendance for a student on a given date/lesson.
 * Plain Java class — no Lombok (contract module rule).
 */
public class JournalCell {

    private final String date;
    private final Integer lessonNumber;
    private final String status;
    private final String symbol;

    public JournalCell(String date, Integer lessonNumber, String status, String symbol) {
        this.date = date;
        this.lessonNumber = lessonNumber;
        this.status = status;
        this.symbol = symbol;
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
}
