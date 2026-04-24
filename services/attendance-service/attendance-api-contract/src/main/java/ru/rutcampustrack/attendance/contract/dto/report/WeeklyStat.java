package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One week of student attendance stats for the dashboard line chart (v9.0).
 * week: ISO week number (1..53); label: "Н{weekOfSemester}" for display convenience.
 */
@Schema(description = "Недельная точка графика посещаемости: номер недели семестра, ISO-неделя, label, счётчики, %")
public class WeeklyStat {

    private final int weekOfSemester;
    private final int isoWeek;
    private final String label;
    private final int total;
    private final int attended;
    private final int absent;
    private final int excused;
    private final double percentage;

    public WeeklyStat(int weekOfSemester, int isoWeek, String label,
                      int total, int attended, int absent, int excused, double percentage) {
        this.weekOfSemester = weekOfSemester;
        this.isoWeek = isoWeek;
        this.label = label;
        this.total = total;
        this.attended = attended;
        this.absent = absent;
        this.excused = excused;
        this.percentage = percentage;
    }

    public int getWeekOfSemester() { return weekOfSemester; }
    public int getIsoWeek() { return isoWeek; }
    public String getLabel() { return label; }
    public int getTotal() { return total; }
    public int getAttended() { return attended; }
    public int getAbsent() { return absent; }
    public int getExcused() { return excused; }
    public double getPercentage() { return percentage; }
}
