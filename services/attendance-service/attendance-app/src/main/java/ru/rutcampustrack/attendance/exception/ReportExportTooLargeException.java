package ru.rutcampustrack.attendance.exception;

public final class ReportExportTooLargeException extends RuntimeException {
    public ReportExportTooLargeException(String message) {
        super(message);
    }
}
