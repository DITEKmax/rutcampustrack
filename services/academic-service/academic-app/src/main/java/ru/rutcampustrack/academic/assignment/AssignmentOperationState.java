package ru.rutcampustrack.academic.assignment;

import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;

/** Frozen text codes shared with the existing durable assignment protocol. */
public enum AssignmentOperationState {
    PREPARED("PREPARED"),
    APPLIED("APPLIED"),
    COMMITTED("COMMITTED");

    private final String code;

    AssignmentOperationState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AssignmentOperationState fromCode(String code) {
        for (AssignmentOperationState value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new UnknownAssignmentCodeException();
    }
}
