package ru.rutcampustrack.attendance.contract.dto.report;

import java.math.BigDecimal;

/** Inclusive range for numeric columns or case-insensitive contains for displayName. */
public record HeadmanStatsFilter(
        String field,
        String contains,
        BigDecimal minimum,
        BigDecimal maximum
) {
}
