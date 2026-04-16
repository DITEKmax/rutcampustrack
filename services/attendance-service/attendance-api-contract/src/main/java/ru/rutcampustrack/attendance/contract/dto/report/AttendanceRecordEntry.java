package ru.rutcampustrack.attendance.contract.dto.report;

/**
 * Single attendance record entry for student records list (RPRT-04).
 * Plain Java class — no Lombok (contract module rule).
 */
public class AttendanceRecordEntry {

    private final Long lessonId;
    private final Long subjectId;
    private final String lessonDate;
    private final Integer lessonNumber;
    private final String status;
    private final String symbol;
    private final String source;
    /** Human-readable excuse reason; non-null only for EXCUSED / FREE_ATTENDANCE. */
    private final String excuseReason;

    public AttendanceRecordEntry(Long lessonId, Long subjectId, String lessonDate,
                                  Integer lessonNumber, String status, String symbol, String source) {
        this(lessonId, subjectId, lessonDate, lessonNumber, status, symbol, source, null);
    }

    public AttendanceRecordEntry(Long lessonId, Long subjectId, String lessonDate,
                                  Integer lessonNumber, String status, String symbol, String source,
                                  String excuseReason) {
        this.lessonId = lessonId;
        this.subjectId = subjectId;
        this.lessonDate = lessonDate;
        this.lessonNumber = lessonNumber;
        this.status = status;
        this.symbol = symbol;
        this.source = source;
        this.excuseReason = excuseReason;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getLessonDate() {
        return lessonDate;
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

    public String getSource() {
        return source;
    }

    public String getExcuseReason() {
        return excuseReason;
    }
}
