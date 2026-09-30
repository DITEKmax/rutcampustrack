package ru.rutcampustrack.academic.contract.enums;

/** A durable, write-blocking transition coordinated across semester writers. */
public enum SemesterTransition {
    NONE,
    ARCHIVING,
    RESTORING,
    DELETING
}
