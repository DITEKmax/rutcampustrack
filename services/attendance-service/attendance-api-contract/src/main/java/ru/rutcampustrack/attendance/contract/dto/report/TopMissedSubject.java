package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single entry in the "top missed subjects" list on the student dashboard (v9.0).
 * Sorted by absent desc on the server.
 */
@Schema(description = "Предмет из топа пропусков: ID, название, тип занятия, число пропусков, всего пар")
public class TopMissedSubject {

    private final Long subjectId;
    private final String subjectName;
    private final String subjectType;
    private final int absent;
    private final int total;

    public TopMissedSubject(Long subjectId, String subjectName, int absent, int total) {
        this(subjectId, subjectName, "", absent, total);
    }

    public TopMissedSubject(Long subjectId, String subjectName, String subjectType, int absent, int total) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.subjectType = subjectType;
        this.absent = absent;
        this.total = total;
    }

    public Long getSubjectId() { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public String getSubjectType() { return subjectType; }
    public int getAbsent() { return absent; }
    public int getTotal() { return total; }
}
