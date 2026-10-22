package ru.rutcampustrack.academic.exception;

public final class AssignmentIdempotencyConflictException extends ConflictException {
    public AssignmentIdempotencyConflictException() {
        super("Ключ уже использован для другой операции");
    }
}
