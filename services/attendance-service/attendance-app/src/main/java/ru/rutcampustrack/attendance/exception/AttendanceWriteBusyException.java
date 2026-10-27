package ru.rutcampustrack.attendance.exception;

/** All bounded attempts ended in confirmed, transient Mongo transaction aborts. */
public final class AttendanceWriteBusyException extends RuntimeException {
    public static final String PUBLIC_DETAIL =
            "Не удалось сохранить отметку из-за одновременного изменения данных. Повтори попытку.";

    public AttendanceWriteBusyException(Throwable cause) {
        super(PUBLIC_DETAIL, cause);
    }
}
