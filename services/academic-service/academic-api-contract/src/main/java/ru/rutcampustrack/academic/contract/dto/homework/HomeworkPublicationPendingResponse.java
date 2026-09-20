package ru.rutcampustrack.academic.contract.dto.homework;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;

import java.util.Objects;
import java.util.UUID;

@Schema(name = "HomeworkPublicationPendingResponse")
public record HomeworkPublicationPendingResponse(
        String homeworkId,
        String bindingId,
        UUID requestKey,
        HomeworkPublicationState state
) {
    public HomeworkPublicationPendingResponse {
        requirePositiveDecimal(homeworkId, "homeworkId");
        requirePositiveDecimal(bindingId, "bindingId");
        Objects.requireNonNull(requestKey, "requestKey");
        Objects.requireNonNull(state, "state");
        if (state != HomeworkPublicationState.PENDING) {
            throw new IllegalArgumentException("pending response state must be PENDING");
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }
}
