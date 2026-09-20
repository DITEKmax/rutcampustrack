package ru.rutcampustrack.schedule.exception;

/**
 * Fail-closed response for lifecycle operations whose canonical recurring
 * writer has not been implemented yet.
 */
public class RecurringLifecycleNotReadyException extends ConflictException {

    public RecurringLifecycleNotReadyException(String operation) {
        super("schedule lifecycle operation is not ready: " + operation);
    }
}
