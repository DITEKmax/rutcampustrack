package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.LocalDate;
import java.util.List;

/** Server-owned query for the headman's current-group semester trend. */
public record HeadmanStatsTrendQueryRequest(
        Mode mode,
        LocalDate weekStart,
        Long subjectId,
        List<String> lessonTypes
) {
    public enum Mode {
        SEMESTER,
        WEEK,
        SUBJECT
    }
}
