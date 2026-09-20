package ru.rutcampustrack.schedule.exception;

/**
 * A verified Academic response is incompatible with durable recurring state.
 * The conflict is intentionally typed so callers cannot silently narrow or
 * widen an assignment fence.
 */
public class RecurringProtocolConflictException extends ConflictException {

    public RecurringProtocolConflictException(String message) {
        super(message);
    }
}
