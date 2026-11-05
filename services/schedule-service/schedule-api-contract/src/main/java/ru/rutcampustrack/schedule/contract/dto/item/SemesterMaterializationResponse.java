package ru.rutcampustrack.schedule.contract.dto.item;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** Separate materialization control receipt; never interpreted as a deletion receipt. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SemesterMaterializationResponse(
        UUID operationId,
        String state,
        long createdCount,
        long pendingCount,
        long coverageUnavailableCount) {}
