package ru.rutcampustrack.academic.exportjobs;

import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.teacher.TeacherReportScope;

import java.time.Instant;
import java.util.Objects;

/** Durable, immutable report plan. Contains identity generations, never a signed token. */
public record StoredReportIdentity(
        InternalJwtClaims actor,
        IssueReportDownloadTicketRequest report,
        Instant expiresAt,
        long academicRevision,
        Long semesterId,
        TeacherReportScope teacherScope) {
    public StoredReportIdentity {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(report);
        Objects.requireNonNull(expiresAt);
        if (actor.userId() <= 0
                || actor.sessionId() == null
                || actor.sessionVersion() <= 0
                || actor.rolesVersion() <= 0
                || academicRevision <= 0
                || !report.isParametersConsistent())
            throw new IllegalArgumentException("Invalid report identity");
    }

    public StoredReportIdentity withTeacherScope(TeacherReportScope scope) {
        return new StoredReportIdentity(
                actor, report, expiresAt, academicRevision, semesterId, scope);
    }

    @Override
    public String toString() {
        return "StoredReportIdentity[kind=" + report.kind() + "]";
    }
}
