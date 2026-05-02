package ru.rutcampustrack.attendance.exception;

public class ReportValidationException extends RuntimeException {
    public ReportValidationException(String message) {
        super(message);
    }
}
