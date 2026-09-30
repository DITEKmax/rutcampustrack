package ru.rutcampustrack.academic.contract.enums;

/** Durable local receipt state returned by one of the three write domains. */
public enum SemesterArchiveParticipantStatus {
    NOT_STARTED,
    PENDING,
    READY,
    PREPARED_RESTORE,
    RELEASE_PENDING,
    RELEASED,
    DELETED
}
