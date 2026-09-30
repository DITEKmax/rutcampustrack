package ru.rutcampustrack.academic.exception;

/** A required deletion authority/participant could not provide a bounded response. */
public class SemesterDeletionDependencyUnavailableException extends RuntimeException {

    public SemesterDeletionDependencyUnavailableException(String message) {
        super(message);
    }

    public SemesterDeletionDependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
