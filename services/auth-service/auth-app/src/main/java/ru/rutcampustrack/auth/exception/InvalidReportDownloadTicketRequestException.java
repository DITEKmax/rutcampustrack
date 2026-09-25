package ru.rutcampustrack.auth.exception;

/** Malformed report selector; deliberately contains no caller-provided value. */
public final class InvalidReportDownloadTicketRequestException extends RuntimeException {
    public InvalidReportDownloadTicketRequestException() {
        super("Report parameters do not match the selected kind");
    }
}
