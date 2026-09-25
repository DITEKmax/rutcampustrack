package ru.rutcampustrack.auth.service;

/** Bounded ticket issue or redemption count was reached. */
public final class ReportDownloadTicketRateLimitException extends RuntimeException {
    public ReportDownloadTicketRateLimitException() {
        super("Report download ticket limit exceeded");
    }
}
