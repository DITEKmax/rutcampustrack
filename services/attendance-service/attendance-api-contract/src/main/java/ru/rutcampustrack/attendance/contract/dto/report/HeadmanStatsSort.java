package ru.rutcampustrack.attendance.contract.dto.report;

/** One priority-ordered sort key for a student-statistics column. */
public record HeadmanStatsSort(String field, boolean descending) {
}
