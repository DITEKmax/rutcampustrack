package ru.rutcampustrack.schedule.exception;

/** A completed coordinator validation refused this attempt before one-off creation. */
public class OneOffCreateRejectedException extends ConflictException {
    public OneOffCreateRejectedException(String message) {
        super(message);
    }
}
