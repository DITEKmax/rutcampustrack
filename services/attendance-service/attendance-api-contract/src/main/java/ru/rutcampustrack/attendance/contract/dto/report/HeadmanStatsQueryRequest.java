package ru.rutcampustrack.attendance.contract.dto.report;

import java.util.List;

/** Server-side query for one of the two headman student-statistics blocks. */
public record HeadmanStatsQueryRequest(
        Long subjectId,
        List<String> lessonTypes,
        Integer page,
        Integer size,
        List<HeadmanStatsSort> sorts,
        List<HeadmanStatsFilter> filters
) {
}
