package ru.rutcampustrack.academic.assignment;

import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;

/** Frozen database and gRPC text codes for an assignment's publication lifecycle. */
public enum AssignmentLifecycle {
    ACTIVE("ACTIVE"),
    PREPARED("PREPARED"),
    CLOSED("CLOSED");

    private final String code;

    AssignmentLifecycle(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AssignmentLifecycle fromCode(String code) {
        for (AssignmentLifecycle value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new UnknownAssignmentCodeException();
    }
}
