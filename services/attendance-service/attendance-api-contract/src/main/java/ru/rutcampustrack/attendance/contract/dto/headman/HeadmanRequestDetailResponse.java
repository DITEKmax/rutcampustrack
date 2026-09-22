package ru.rutcampustrack.attendance.contract.dto.headman;

import java.util.List;

public record HeadmanRequestDetailResponse(
        HeadmanRequestSummaryResponse summary,
        List<HeadmanRequestLessonResponse> lessons,
        List<HeadmanRequestAttachmentResponse> attachments
) {
    public HeadmanRequestDetailResponse {
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
