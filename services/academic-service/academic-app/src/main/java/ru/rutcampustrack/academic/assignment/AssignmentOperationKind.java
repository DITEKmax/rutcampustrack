package ru.rutcampustrack.academic.assignment;

import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;

/** Frozen text codes shared with the existing durable assignment protocol. */
public enum AssignmentOperationKind {
    REPLACE("REPLACE"),
    CLOSE_UNASSIGN("CLOSE_UNASSIGN"),
    ADOPT_UNASSIGNED("ADOPT_UNASSIGNED");

    private final String code;

    AssignmentOperationKind(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AssignmentOperationKind fromCode(String code) {
        for (AssignmentOperationKind value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new UnknownAssignmentCodeException();
    }
}
