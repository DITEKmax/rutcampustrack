package ru.rutcampustrack.academic.exportjobs;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.time.Instant;
import java.util.UUID;

/** Directed AAA boundary. Tokens exist only on the invocation stack. */
public interface ReportSecurityAuthority {
    Admission admit(StoredReportIdentity identity, boolean requiresFull, UUID jobId, UUID leaseId);

    /** Absolute monotonic cutoff; an authority without deadline support must fail closed. */
    default Admission admit(
            StoredReportIdentity identity,
            boolean requiresFull,
            UUID jobId,
            UUID leaseId,
            long deadlineNanos) {
        throw io.grpc.Status.UNAVAILABLE.asRuntimeException();
    }

    Admission admitPlan(
            StoredReportIdentity identity,
            boolean requiresFull,
            UUID jobId,
            UUID leaseId,
            String planDigest);

    Capability issue(StoredReportIdentity identity, boolean requiresFull, UUID jobId);

    Redemption redeem(String ticket);

    record Admission(
            InternalJwtClaims actor,
            String token,
            Instant expiresAt,
            boolean full,
            AdminAccessProfile adminProfile) {
        public Admission(InternalJwtClaims actor, String token, Instant expiresAt, boolean full) {
            this(actor, token, expiresAt, full, null);
        }

        @Override
        public String toString() {
            return "ReportAdmission[expiresAt=" + expiresAt + "]";
        }
    }

    record Capability(String ticket, Instant expiresAt) {
        @Override
        public String toString() {
            return "ReportCapability[expiresAt=" + expiresAt + "]";
        }
    }

    record Redemption(Admission admission, String kind, String hash, UUID jobId) {}
}
