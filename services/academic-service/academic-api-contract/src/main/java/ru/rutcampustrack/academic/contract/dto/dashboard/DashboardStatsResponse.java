package ru.rutcampustrack.academic.contract.dto.dashboard;

import org.springframework.hateoas.RepresentationModel;

public class DashboardStatsResponse extends RepresentationModel<DashboardStatsResponse> {

    private long totalStudents;
    private long totalTeachers;
    private long totalGroups;
    private long activeGroups;
    private String activeSemesterName;

    public DashboardStatsResponse() {}

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
