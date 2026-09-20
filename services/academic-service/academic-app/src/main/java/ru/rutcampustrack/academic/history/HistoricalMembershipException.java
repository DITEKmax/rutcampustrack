package ru.rutcampustrack.academic.history;

/** Typed boundary failure for managed historical membership reads/writes. */
public final class HistoricalMembershipException extends RuntimeException {

    public enum Code {
        INVALID_ARGUMENT,
        NOT_FOUND,
        FAILED_PRECONDITION,
        UNSUPPORTED_MUTATION
    }

    private final Code code;

    public HistoricalMembershipException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static HistoricalMembershipException invalid(String message) {
        return new HistoricalMembershipException(Code.INVALID_ARGUMENT, message);
    }

    public static HistoricalMembershipException notFound(String message) {
        return new HistoricalMembershipException(Code.NOT_FOUND, message);
    }

    public static HistoricalMembershipException precondition(String message) {
        return new HistoricalMembershipException(Code.FAILED_PRECONDITION, message);
    }

    public static HistoricalMembershipException unsupported(String message) {
        return new HistoricalMembershipException(Code.UNSUPPORTED_MUTATION, message);
    }
}
