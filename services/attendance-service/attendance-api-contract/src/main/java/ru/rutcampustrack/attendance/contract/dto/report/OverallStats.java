package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregated attendance stats across all subjects (RPRT-03).
 * Plain Java class — no Lombok (contract module rule).
 */
@Schema(description = "Агрегированная статистика посещаемости: всего, присутствовал, пропустил, уважительных, процент")
public class OverallStats {

    private final int total;
    private final int attended;
    private final int absent;
    private final int excused;
    private final double percentage;

    public OverallStats(int total, int attended, int absent, int excused, double percentage) {
        this.total = total;
        this.attended = attended;
        this.absent = absent;
        this.excused = excused;
        this.percentage = percentage;
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
