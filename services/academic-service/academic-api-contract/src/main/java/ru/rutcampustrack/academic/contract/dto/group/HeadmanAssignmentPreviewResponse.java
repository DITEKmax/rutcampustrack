package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/** Server-computed consequence preview; it never mutates the group. */
@Schema(description = "Предпросмотр назначения старосты")
public record HeadmanAssignmentPreviewResponse(
        Long groupId,
        Long currentHeadmanId,
        String currentHeadmanFio,
        Long candidateId,
        String candidateFio,
        boolean sameHeadman,
        boolean activatesDraft,
        int assistantsToRevoke
) {}
