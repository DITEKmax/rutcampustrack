package ru.rutcampustrack.auth.exception;

public class TmaValidationException extends RuntimeException {
    public enum Code {
        TMA_INIT_DATA_INVALID,
        TMA_INIT_DATA_EXPIRED,
        TMA_INIT_DATA_NOT_YET_VALID,
        TMA_TELEGRAM_NOT_LINKED
    }
    private final Code code;

    public TmaValidationException(String ignored) {
        this(Code.TMA_INIT_DATA_INVALID);
    }

    public TmaValidationException(Code code) {
        super("Telegram authentication proof was denied");
        this.code = code;
    }

    public Code code() { return code; }
}
