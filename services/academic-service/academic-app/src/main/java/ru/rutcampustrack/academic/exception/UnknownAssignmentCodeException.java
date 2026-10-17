package ru.rutcampustrack.academic.exception;

/** A mandatory persisted assignment code cannot be interpreted safely. */
public final class UnknownAssignmentCodeException extends ConflictException {
    public UnknownAssignmentCodeException() {
        super("Состояние назначения несовместимо с текущей операцией");
    }
}
