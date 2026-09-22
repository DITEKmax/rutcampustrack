package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** ADMIN roster and current headman snapshot for one active group. */
@Schema(description = "Состав группы и текущий староста")
public record HeadmanRosterResponse(
        Long groupId,
        Long currentHeadmanId,
        String currentHeadmanFio,
        List<HeadmanCandidateResponse> candidates,
        int activeAssistantCount
) {}
