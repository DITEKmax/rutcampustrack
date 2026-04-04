package ru.rutcampustrack.attendance.contract.dto.report;

import org.springframework.hateoas.RepresentationModel;

import java.util.List;

/**
 * Top-level response for student attendance statistics (RPRT-03, D-13).
 * Contains per-subject breakdown and overall aggregated stats.
 * Extends RepresentationModel for HATEOAS _links support.
 * Plain Java class — no Lombok (contract module rule).
 */
public class StudentStatsResponse extends RepresentationModel<StudentStatsResponse> {

    private final List<SubjectStats> subjects;
    private final OverallStats overall;

    public StudentStatsResponse(List<SubjectStats> subjects, OverallStats overall) {
        this.subjects = subjects;
        this.overall = overall;
    }

    public List<SubjectStats> getSubjects() {
        return subjects;
    }

    public OverallStats getOverall() {
        return overall;
    }
}
