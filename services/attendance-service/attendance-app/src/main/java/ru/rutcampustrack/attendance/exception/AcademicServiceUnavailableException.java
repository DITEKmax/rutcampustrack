package ru.rutcampustrack.attendance.exception;

public class AcademicServiceUnavailableException extends RuntimeException {
    public AcademicServiceUnavailableException(String message) {
        super(message);
    }
}
