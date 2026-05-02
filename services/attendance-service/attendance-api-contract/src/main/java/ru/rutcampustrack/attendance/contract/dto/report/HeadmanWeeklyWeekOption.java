package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Academic week available for headman weekly report export")
public class HeadmanWeeklyWeekOption {

    private final int weekOfSemester;
    private final int isoWeek;
    private final String label;
    private final LocalDate weekStart;
    private final LocalDate weekEnd;
    private final boolean current;

    public HeadmanWeeklyWeekOption(int weekOfSemester,
                                   int isoWeek,
                                   String label,
                                   LocalDate weekStart,
                                   LocalDate weekEnd,
                                   boolean current) {
        this.weekOfSemester = weekOfSemester;
        this.isoWeek = isoWeek;
        this.label = label;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.current = current;
    }

    public int getWeekOfSemester() {
        return weekOfSemester;
    }

    public int getIsoWeek() {
        return isoWeek;
    }

    public String getLabel() {
        return label;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public boolean isCurrent() {
        return current;
    }
}
