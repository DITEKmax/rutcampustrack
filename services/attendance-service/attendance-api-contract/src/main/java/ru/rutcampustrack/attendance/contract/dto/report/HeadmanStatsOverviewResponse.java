package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.LocalDate;

public record HeadmanStatsOverviewResponse(long groupId, Membership membership, Today today) {
    public record Membership(long active, long total) {}

    public record Today(
            LocalDate date,
            HeadmanStatsTrendResponse.TrendMetric present,
            HeadmanStatsTrendResponse.TrendMetric presentOrExcused) {}
}
