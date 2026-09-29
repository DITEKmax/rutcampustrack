package ru.rutcampustrack.academic.contract.enums;

/** Typed command sent to a local write-domain barrier over the existing event outbox. */
public enum SemesterArchiveParticipantCommand {
    PREPARE_ARCHIVE,
    PREPARE_RESTORE,
    RELEASE_RESTORE
}
