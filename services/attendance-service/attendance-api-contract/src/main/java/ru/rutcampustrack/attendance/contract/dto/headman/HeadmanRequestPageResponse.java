package ru.rutcampustrack.attendance.contract.dto.headman;

import java.util.List;

public record HeadmanRequestPageResponse(
        List<HeadmanRequestSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public HeadmanRequestPageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
