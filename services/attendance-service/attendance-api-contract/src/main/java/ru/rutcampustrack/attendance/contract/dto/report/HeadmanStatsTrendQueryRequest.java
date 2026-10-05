package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.LocalDate;
import java.util.List;

/** Server-owned query for the headman's current-group semester trend. */
public record HeadmanStatsTrendQueryRequest(
        Mode mode,
        LocalDate weekStart,
        Long subjectId,
        List<String> lessonTypes,
        @jakarta.validation.constraints.Positive Long semesterId,
        BucketUnit bucketUnit
) {
    public HeadmanStatsTrendQueryRequest(Mode mode, LocalDate weekStart, Long subjectId, List<String> lessonTypes) {
        this(mode, weekStart, subjectId, lessonTypes, null, null);
    }
    public enum BucketUnit { DAY, WEEK }
    public enum Mode {
        SEMESTER,
        WEEK,
        SUBJECT
    }
}
