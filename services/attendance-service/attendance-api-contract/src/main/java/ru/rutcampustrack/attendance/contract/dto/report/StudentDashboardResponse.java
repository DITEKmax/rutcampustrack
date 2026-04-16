package ru.rutcampustrack.attendance.contract.dto.report;

import org.springframework.hateoas.RepresentationModel;

import java.util.List;

/**
 * Aggregated payload for the student PWA / Mini-App home dashboard (v9.0).
 * One-call response combining overall %, status donut breakdown,
 * per-week timeseries, and top missed subjects — avoids chatty clients.
 */
public class StudentDashboardResponse extends RepresentationModel<StudentDashboardResponse> {

    private final OverallStats overall;
    private final StatusBreakdown breakdown;
    private final List<WeeklyStat> weekly;
    private final List<TopMissedSubject> topMissed;

    public StudentDashboardResponse(OverallStats overall,
                                    StatusBreakdown breakdown,
                                    List<WeeklyStat> weekly,
                                    List<TopMissedSubject> topMissed) {
        this.overall = overall;
        this.breakdown = breakdown;
        this.weekly = weekly;
        this.topMissed = topMissed;
    }

    public OverallStats getOverall() { return overall; }
    public StatusBreakdown getBreakdown() { return breakdown; }
    public List<WeeklyStat> getWeekly() { return weekly; }
    public List<TopMissedSubject> getTopMissed() { return topMissed; }
}
