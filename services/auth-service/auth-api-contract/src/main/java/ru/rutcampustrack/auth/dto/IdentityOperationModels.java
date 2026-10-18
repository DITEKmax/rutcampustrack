package ru.rutcampustrack.auth.dto;

import java.util.List;
import java.util.UUID;

/** Nonsecret durable operation status. Protobuf owns internal command messages. */
public final class IdentityOperationModels {
    private IdentityOperationModels() {}

    public enum State {
        ACCEPTED,
        AUTH_PREPARED,
        LOCAL_COMMITTED,
        SUCCEEDED,
        FAILED_SAFE
    }

    public record Status(
            UUID operationId,
            String kind,
            State state,
            long revision,
            List<Long> affectedUserIds,
            String resultRef,
            String errorCode) {
        public Status {
            affectedUserIds = List.copyOf(affectedUserIds);
        }
    }
}
