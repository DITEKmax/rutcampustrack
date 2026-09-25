package ru.rutcampustrack.auth.service;

import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Redis value. It contains the current identity binding and typed report only, never caller tokens. */
public record StoredReportDownloadTicket(
        int schemaVersion,
        long userId,
        UUID sessionId,
        long sessionVersion,
        long rolesVersion,
        AuthRole role,
        RoleStatus status,
        Long groupId,
        boolean headman,
        boolean readOnly,
        IssueReportDownloadTicketRequest report,
        Instant expiresAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public StoredReportDownloadTicket {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || userId <= 0
                || sessionVersion <= 0 || rolesVersion <= 0) {
            throw new IllegalArgumentException("ticket identity is invalid");
        }
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        role = Objects.requireNonNull(role, "role");
        status = Objects.requireNonNull(status, "status");
        if (groupId != null && groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive");
        }
        if (headman != (role == AuthRole.HEADMAN) || readOnly != status.isReadOnly()) {
            throw new IllegalArgumentException("ticket identity flags do not match role status");
        }
        report = Objects.requireNonNull(report, "report");
        if (!report.isParametersConsistent()) {
            throw new IllegalArgumentException("ticket report parameters are invalid");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public SessionPrincipal principal() {
        return new SessionPrincipal(userId, sessionId, sessionVersion, rolesVersion,
                role, status, groupId, headman, readOnly);
    }
}
