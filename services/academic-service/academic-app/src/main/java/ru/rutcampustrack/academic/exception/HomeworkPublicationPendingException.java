package ru.rutcampustrack.academic.exception;

import ru.rutcampustrack.academic.contract.dto.homework.HomeworkPublicationPendingResponse;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;

import java.util.UUID;

/**
 * The Schedule confirm outcome is unknown after durable Academic PENDING
 * content.  The caller must retry the same idempotency key; no 201 is emitted
 * before the binding becomes ACTIVE.
 */
public class HomeworkPublicationPendingException extends RuntimeException {

    private final HomeworkPublicationPendingResponse response;

    public HomeworkPublicationPendingException(long homeworkId, long bindingId, UUID requestKey) {
        super("homework publication is pending confirmation in schedule-service");
        this.response = new HomeworkPublicationPendingResponse(
                Long.toString(homeworkId), Long.toString(bindingId),
                requestKey, HomeworkPublicationState.PENDING);
    }

    public HomeworkPublicationPendingResponse getResponse() {
        return response;
    }
}
