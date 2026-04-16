package ru.rutcampustrack.attendance.contract.dto.report;

/**
 * Donut-chart breakdown of a student's attendance by status (v9.0 dashboard).
 * "forgotToCheckin" is a sub-slice of present — records with source=LATE_CHECKIN
 * (student asked headman to confirm because they forgot to geo-mark).
 */
public class StatusBreakdown {

    private final int present;
    private final int absent;
    private final int excused;
    private final int freeAttendance;
    private final int forgotToCheckin;
    private final int cancelled;

    public StatusBreakdown(int present, int absent, int excused, int freeAttendance,
                           int forgotToCheckin, int cancelled) {
        this.present = present;
        this.absent = absent;
        this.excused = excused;
        this.freeAttendance = freeAttendance;
        this.forgotToCheckin = forgotToCheckin;
        this.cancelled = cancelled;
    }

    public int getPresent() { return present; }
    public int getAbsent() { return absent; }
    public int getExcused() { return excused; }
    public int getFreeAttendance() { return freeAttendance; }
    public int getForgotToCheckin() { return forgotToCheckin; }
    public int getCancelled() { return cancelled; }
}
