package ru.rutcampustrack.academic.exception;

/** Raised while the immutable-assignment lifecycle close operation is fenced. */
public class AssignmentClosureNotReadyException extends RuntimeException {
    public AssignmentClosureNotReadyException() {
        super("Закрытие назначения пока недоступно");
    }
}
