package ru.rutcampustrack.schedule.constructor;

/** Closed public failure codes; private participant data is never put in a response. */
public final class ConstructorLifecycleException extends RuntimeException {
    private final int status;
    private final String code;

    public ConstructorLifecycleException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ConstructorLifecycleException conflict(String code) {
        return new ConstructorLifecycleException(409, code, "Расписание изменилось; обнови расчёт");
    }
}
