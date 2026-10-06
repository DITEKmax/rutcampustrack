package ru.rutcampustrack.academic.exception;

import java.util.List;
import java.util.UUID;

/** The admission is durable; the caller must resume these exact targets. */
public final class AssignmentAdoptionPendingException extends RuntimeException {
    public record PendingAdoption(Long targetAssignmentId, UUID adoptionOperationId) { }
    private final Long subjectId;
    private final List<PendingAdoption> pendingAdoptions;

    public AssignmentAdoptionPendingException(Long subjectId, List<PendingAdoption> pendingAdoptions) {
        super("Assignment adoption is pending");
        this.subjectId = subjectId;
        this.pendingAdoptions = List.copyOf(pendingAdoptions);
    }
    public Long subjectId() { return subjectId; }
    public List<PendingAdoption> pendingAdoptions() { return pendingAdoptions; }
}
