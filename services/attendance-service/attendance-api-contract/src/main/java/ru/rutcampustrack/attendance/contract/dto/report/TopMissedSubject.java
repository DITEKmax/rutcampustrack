package ru.rutcampustrack.attendance.contract.dto.report;

/**
 * A single entry in the "top missed subjects" list on the student dashboard (v9.0).
 * Sorted by absent desc on the server.
 */
public class TopMissedSubject {

    private final Long subjectId;
    private final String subjectName;
    private final int absent;
    private final int total;

    public TopMissedSubject(Long subjectId, String subjectName, int absent, int total) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.absent = absent;
        this.total = total;
    }

    public Long getSubjectId() { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public int getAbsent() { return absent; }
    public int getTotal() { return total; }
}
