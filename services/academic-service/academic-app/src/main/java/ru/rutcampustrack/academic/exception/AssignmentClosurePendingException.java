package ru.rutcampustrack.academic.exception;

import java.util.UUID;

/** The accepted closure must be resumed with the original command identity. */
public final class AssignmentClosurePendingException extends RuntimeException {
    private final Long assignmentId;
    private final UUID requestKey;

    public AssignmentClosurePendingException(Long assignmentId, UUID requestKey) {
        super("Assignment closure is pending");
        this.assignmentId = assignmentId;
        this.requestKey = requestKey;
    }

    public Long assignmentId() {
        return assignmentId;
    }

    public UUID requestKey() {
        return requestKey;
    }
}
