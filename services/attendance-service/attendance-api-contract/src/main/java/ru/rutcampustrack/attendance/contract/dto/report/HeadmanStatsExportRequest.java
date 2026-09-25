package ru.rutcampustrack.attendance.contract.dto.report;

import java.util.List;

/** Export selection deliberately has no page fields: exports contain every filtered row. */
public record HeadmanStatsExportRequest(
        Long subjectId,
        List<String> lessonTypes,
        List<HeadmanStatsSort> sorts,
        List<HeadmanStatsFilter> filters,
        String format
) {
}
