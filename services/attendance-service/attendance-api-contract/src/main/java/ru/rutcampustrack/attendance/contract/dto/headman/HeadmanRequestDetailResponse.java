package ru.rutcampustrack.attendance.contract.dto.headman;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record HeadmanRequestDetailResponse(
        HeadmanRequestSummaryResponse summary,
        List<HeadmanRequestLessonResponse> lessons,
        List<HeadmanRequestAttachmentResponse> attachments,
        @JsonInclude(JsonInclude.Include.ALWAYS)
                List<HeadmanRequestLessonResponse> currentAffectedLessons,
        @JsonInclude(JsonInclude.Include.ALWAYS) String decisionRevision,
        @JsonInclude(JsonInclude.Include.ALWAYS)
                HeadmanRequestDecisionSelectionResponse decisionSelection) {
    public HeadmanRequestDetailResponse {
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        // Terminal history stays readable if Schedule cannot resolve its current IDs.
        // Null explicitly means unavailable; an empty list must not imply zero targets.
        currentAffectedLessons =
                currentAffectedLessons == null ? null : List.copyOf(currentAffectedLessons);
    }

    public HeadmanRequestDetailResponse(
            HeadmanRequestSummaryResponse summary,
            List<HeadmanRequestLessonResponse> lessons,
            List<HeadmanRequestAttachmentResponse> attachments,
            List<HeadmanRequestLessonResponse> currentAffectedLessons) {
        this(summary, lessons, attachments, currentAffectedLessons, null, null);
    }

    public HeadmanRequestDetailResponse(
            HeadmanRequestSummaryResponse summary,
            List<HeadmanRequestLessonResponse> lessons,
            List<HeadmanRequestAttachmentResponse> attachments) {
        this(summary, lessons, attachments, null, null, null);
    }
}
