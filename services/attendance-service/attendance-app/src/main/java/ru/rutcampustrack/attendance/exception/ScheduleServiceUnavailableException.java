package ru.rutcampustrack.attendance.exception;

public class ScheduleServiceUnavailableException extends RuntimeException {
    public ScheduleServiceUnavailableException(String message) {
        super(message);
    }
}
