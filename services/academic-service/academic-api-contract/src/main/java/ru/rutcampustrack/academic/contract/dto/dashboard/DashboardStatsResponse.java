package ru.rutcampustrack.academic.contract.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

/**
 * Response DTO for admin dashboard summary statistics with HATEOAS links.
 */
@Schema(description = "Сводная статистика для админ-дашборда (HATEOAS Level 3 с _links)")
public class DashboardStatsResponse extends RepresentationModel<DashboardStatsResponse> {

    private long totalStudents;
    private long totalTeachers;
    private long totalGroups;
    private long activeGroups;
    private String activeSemesterName;

    public DashboardStatsResponse() {}

    public DashboardStatsResponse(long totalStudents, long totalTeachers,
                                  long totalGroups, long activeGroups,
                                  String activeSemesterName) {
        this.totalStudents = totalStudents;
        this.totalTeachers = totalTeachers;
        this.totalGroups = totalGroups;
        this.activeGroups = activeGroups;
        this.activeSemesterName = activeSemesterName;
    }

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public long getTotalTeachers() { return totalTeachers; }
    public void setTotalTeachers(long totalTeachers) { this.totalTeachers = totalTeachers; }

    public long getTotalGroups() { return totalGroups; }
    public void setTotalGroups(long totalGroups) { this.totalGroups = totalGroups; }

    public long getActiveGroups() { return activeGroups; }
    public void setActiveGroups(long activeGroups) { this.activeGroups = activeGroups; }

    public String getActiveSemesterName() { return activeSemesterName; }
    public void setActiveSemesterName(String activeSemesterName) { this.activeSemesterName = activeSemesterName; }
}
