package ru.rutcampustrack.attendance.contract.dto.headman;

import java.util.List;

/** Immutable receipt of the targets included and excluded by one confirmed intent. */
public record HeadmanRequestDecisionSelectionResponse(String requestKey, String expectedRevision,
        List<HeadmanRequestLessonResponse> includedLessons, List<HeadmanRequestLessonResponse> excludedLessons) {
    public HeadmanRequestDecisionSelectionResponse {
        includedLessons = List.copyOf(includedLessons);
        excludedLessons = List.copyOf(excludedLessons);
    }
}
