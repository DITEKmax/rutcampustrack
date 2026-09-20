package ru.rutcampustrack.attendance.exception;

/** Stable type for transport adapters; callers must not classify by message text. */
public final class InvalidIdempotencyKeyException extends BadRequestException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key должен быть visible ASCII длиной 16..128");
    }
}
