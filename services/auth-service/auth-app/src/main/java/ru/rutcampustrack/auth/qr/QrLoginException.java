package ru.rutcampustrack.auth.qr;

/** Public failures contain neither capabilities nor persistence/parser details. */
public final class QrLoginException extends RuntimeException {
    public enum Code { NOT_FOUND, EXPIRED, NOT_READY, ALREADY_DECIDED, SOURCE_DENIED, REPLAY_DENIED, RATE_LIMITED, UNAVAILABLE }
    private final Code code;
    private final long retryAfter;
    public QrLoginException(Code code) { this(code, 0); }
    public QrLoginException(Code code, long retryAfter) { super(code.name()); this.code = code; this.retryAfter = retryAfter; }
    public Code code() { return code; }
    public long retryAfter() { return retryAfter; }
}
