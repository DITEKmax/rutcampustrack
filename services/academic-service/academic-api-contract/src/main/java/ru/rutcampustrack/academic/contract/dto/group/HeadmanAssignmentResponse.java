package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/** Durable result returned after the canonical assignment transaction. */
@Schema(description = "Результат назначения старосты")
public record HeadmanAssignmentResponse(
        Long groupId,
        Long headmanId,
        String headmanFio,
        boolean changed,
        boolean activatesDraft,
        int assistantsRevoked
) {}
