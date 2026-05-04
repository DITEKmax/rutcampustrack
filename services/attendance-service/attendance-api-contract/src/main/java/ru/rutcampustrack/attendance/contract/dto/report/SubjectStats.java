package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Attendance stats for one subject (RPRT-03, D-13).
 * subjectName is resolved via gRPC GetSubjectsByIds call in the service layer.
 * Plain Java class — no Lombok (contract module rule).
 */
@Schema(description = "Статистика по одному предмету: ID, название, счётчики статусов, процент посещения")
public class SubjectStats {

    private final Long subjectId;
    private final String subjectName;
    private final String subjectType;
    private final int total;
    private final int attended;
    private final int absent;
    private final int excused;
    private final double percentage;

    public SubjectStats(Long subjectId, String subjectName, int total, int attended,
                        int absent, int excused, double percentage) {
        this(subjectId, subjectName, "", total, attended, absent, excused, percentage);
    }

    public SubjectStats(Long subjectId, String subjectName, String subjectType, int total, int attended,
                        int absent, int excused, double percentage) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.subjectType = subjectType;
        this.total = total;
        this.attended = attended;
        this.absent = absent;
        this.excused = excused;
        this.percentage = percentage;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public int getTotal() {
        return total;
    }

    public int getAttended() {
        return attended;
    }

    public int getAbsent() {
        return absent;
    }

    public int getExcused() {
        return excused;
    }

    public double getPercentage() {
        return percentage;
    }
}
