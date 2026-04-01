package ru.rutcampustrack.schedule.exception;

/**
 * Thrown when a lesson state transition is invalid
 * (e.g., cancelling an already closed lesson, restoring a non-cancelled lesson).
 * Mapped to HTTP 422 by GlobalExceptionHandler.
 */
public class InvalidLessonStateException extends RuntimeException {

    public InvalidLessonStateException(String message) {
        super(message);
    }
}
