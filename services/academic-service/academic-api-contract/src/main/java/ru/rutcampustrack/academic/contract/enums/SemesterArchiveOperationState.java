package ru.rutcampustrack.academic.contract.enums;

/** Public progress state for one idempotent archive or restore command. */
public enum SemesterArchiveOperationState {
    PENDING,
    COMPLETED,
    ERROR
}
