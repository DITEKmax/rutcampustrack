package ru.rutcampustrack.schedule.contract.dto.item;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** An opaque control receipt. COMPLETED alone confirms removal of business rows. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ConstructorOperationResponse(
        UUID operationId, String state, long deletedCount, long protectedCount, String errorCode) {
    public boolean completed() {
        return "COMPLETED".equals(state);
    }
}
