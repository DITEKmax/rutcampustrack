package ru.rutcampustrack.gateway.security;

/** A bounded Auth-side ticket issuance/redemption limit was reached. */
public final class InternalReportTicketRateLimitedException extends RuntimeException {
    public InternalReportTicketRateLimitedException() {
        super("Report ticket request limit exceeded");
    }
}
