package ru.rutcampustrack.academic.contract.enums;

/** Safe user-visible progress for a durable final semester deletion. */
public enum SemesterDeletionPhase {
    PREPARING,
    RELEASING,
    DELETING,
    COMPLETED,
    CANCELLED
}
