package ru.rutcampustrack.attendance.contract.dto.headman;

import java.util.List;

/** Immutable receipt of the targets included and excluded by one confirmed intent. */
public record HeadmanRequestDecisionSelectionResponse(
        String requestKey,
        String expectedRevision,
        List<HeadmanRequestLessonResponse> includedLessons,
        List<HeadmanRequestLessonResponse> excludedLessons,
        List<HeadmanRequestLessonResponse> beforeLessons) {
    public HeadmanRequestDecisionSelectionResponse {
        includedLessons = List.copyOf(includedLessons);
        excludedLessons = List.copyOf(excludedLessons);
        beforeLessons = beforeLessons == null ? null : List.copyOf(beforeLessons);
    }
}
