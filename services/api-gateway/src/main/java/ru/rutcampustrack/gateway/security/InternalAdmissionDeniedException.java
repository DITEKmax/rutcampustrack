package ru.rutcampustrack.gateway.security;

import org.springframework.http.HttpStatus;

/**
 * A live auth authority denial that the gateway may expose with a typed public
 * response.  The upstream detail is intentionally never retained.
 */
public final class InternalAdmissionDeniedException extends RuntimeException {

    private final HttpStatus publicStatus;
    private final String publicCode;

    public InternalAdmissionDeniedException(HttpStatus publicStatus, String publicCode) {
        super("Internal admission denied");
        this.publicStatus = publicStatus;
        this.publicCode = publicCode;
    }

    public HttpStatus publicStatus() {
        return publicStatus;
    }

    public String publicCode() {
        return publicCode;
    }
}
