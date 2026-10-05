package ru.rutcampustrack.auth.session.port;

import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.model.SessionState;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Atomic session boundary. Each method is one transaction-shaped operation;
 * the domain service must not compose a read and a write to emulate CAS.
 */
public interface SessionStatePort {

    /**
     * Atomically locks the authoritative user row, re-reads the credential
     * proof (when password auth) and the current roles version/grants, then
     * inserts the session and LOGIN event. A stale caller snapshot must return
     * SESSION_STATE_STALE before any insert; a role grant is never resurrected
     * from the authentication-time hint.
     */
    CreateSessionResult createSession(CreateSessionCommand command);

    /** One consistent live-state read; expiry/revocation is evaluated by the adapter. */
    SnapshotResult snapshot(SnapshotCommand command);

    /**
     * Locks user then session, checks expected session version and the actual
     * own selectable grant, and changes the active grant plus ROLE_CHANGED in
     * one transaction. Selecting the already-active grant is an unchanged
     * success; stale versions never become last-writer-wins.
     */
    RoleSelectionResult selectRole(SelectRoleCommand command);

    /**
     * Performs strict refresh CAS under the session lock. Current JTI wins and
     * rotates to replacement with fixed absolute expiry; previous returns
     * REFRESH_ALREADY_ROTATED and older/unknown returns REFRESH_REJECTED,
     * without replay grace or benign-loser revocation.
     */
    RefreshResult refresh(RefreshCommand command);

    /** Revokes exactly one own session and appends CURRENT_LOGOUT atomically. */
    RevokeResult revokeCurrent(RevokeCurrentCommand command);

    /** Locks user and live actor, fences both versions, then revokes only the owned target with audit. */
    RevokeResult revokeSelected(RevokeSelectedCommand command);

    /** Revokes every own session, including current, and appends LOGOUT_ALL atomically. */
    RevokeAllResult revokeAll(RevokeAllCommand command);

    enum FailureCode {
        INVALID_SESSION,
        SESSION_NOT_FOUND,
        SESSION_REVOKED,
        SESSION_STATE_STALE,
        SESSION_VERSION_CONFLICT,
        ROLE_NOT_GRANTED,
        ROLE_NOT_SELECTABLE,
        ROLE_READ_ONLY,
        REFRESH_ALREADY_ROTATED,
        REFRESH_REJECTED,
        AUTHORITY_UNAVAILABLE,
        INVALID_GRANT,
        DUPLICATE_GRANT,
        INVALID_ARGUMENT
    }

    /** Expected credential hash observed during authentication, never plaintext. */
    record CredentialProof(String expectedCredentialHash) {
        public CredentialProof {
            if (expectedCredentialHash == null || expectedCredentialHash.isBlank()) {
                throw new IllegalArgumentException("expectedCredentialHash must be present");
            }
        }

        @Override
        public String toString() {
            return "CredentialProof[expectedCredentialHash=<redacted>]";
        }
    }

    /**
     * Grants and rolesVersion are the authentication-time snapshot hint. The
     * implementation must compare them with a fresh authoritative snapshot
     * while holding the user lock before persisting the supplied state.
     */
    record CreateSessionCommand(
            SessionState state,
            long rolesVersion,
            List<RoleGrant> grants,
            CredentialProof credentialProof,
            SecurityEvent loginEvent
    ) {
        public CreateSessionCommand {
            state = Objects.requireNonNull(state, "state");
            if (rolesVersion <= 0) {
                throw new IllegalArgumentException("rolesVersion must be positive");
            }
            grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
            for (RoleGrant grant : grants) {
                Objects.requireNonNull(grant, "grants cannot contain null");
            }
            loginEvent = Objects.requireNonNull(loginEvent, "loginEvent");
            if (loginEvent.type() != SecurityEvent.Type.LOGIN
                    || loginEvent.userId() != state.userId()
                    || !state.sessionId().equals(loginEvent.sessionId())) {
                throw new IllegalArgumentException("create command requires matching LOGIN event");
            }
            if (state.authMethod() == AuthMethod.PASSWORD && credentialProof == null) {
                throw new IllegalArgumentException("password create requires credential proof");
            }
            if (state.authMethod() != AuthMethod.PASSWORD && credentialProof != null) {
                throw new IllegalArgumentException("credential proof is only valid for password auth");
            }
        }
    }

    record SnapshotCommand(long userId, UUID sessionId, Instant now) {
        public SnapshotCommand {
            requireUserAndSession(userId, sessionId);
            Objects.requireNonNull(now, "now");
        }
    }

    record SelectRoleCommand(
            long userId,
            UUID sessionId,
            AuthRole role,
            long expectedSessionVersion,
            Instant now,
            SecurityEvent roleChangedEvent
    ) {
        public SelectRoleCommand {
            requireUserAndSession(userId, sessionId);
            role = Objects.requireNonNull(role, "role");
            if (expectedSessionVersion <= 0) {
                throw new IllegalArgumentException("expectedSessionVersion must be positive");
            }
            Objects.requireNonNull(now, "now");
            roleChangedEvent = Objects.requireNonNull(roleChangedEvent, "roleChangedEvent");
            if (roleChangedEvent.type() != SecurityEvent.Type.ROLE_CHANGED
                    || roleChangedEvent.userId() != userId
                    || !sessionId.equals(roleChangedEvent.sessionId())) {
                throw new IllegalArgumentException("select command requires matching ROLE_CHANGED event");
            }
        }
    }

    record RefreshCommand(
            long userId,
            UUID sessionId,
            UUID presentedJti,
            UUID replacementJti,
            Instant now
    ) {
        public RefreshCommand {
            requireUserAndSession(userId, sessionId);
            presentedJti = Objects.requireNonNull(presentedJti, "presentedJti");
            replacementJti = Objects.requireNonNull(replacementJti, "replacementJti");
            if (presentedJti.equals(replacementJti)) {
                throw new IllegalArgumentException("replacementJti must differ from presentedJti");
            }
            Objects.requireNonNull(now, "now");
        }

        @Override
        public String toString() {
            return "RefreshCommand[userId=" + userId + ", sessionId=" + sessionId
                    + ", presentedJti=<redacted>, replacementJti=<redacted>, now=" + now + ']';
        }
    }

    record RevokeCurrentCommand(
            long userId,
            UUID sessionId,
            Instant now,
            SecurityEvent logoutEvent
    ) {
        public RevokeCurrentCommand {
            requireUserAndSession(userId, sessionId);
            Objects.requireNonNull(now, "now");
            logoutEvent = Objects.requireNonNull(logoutEvent, "logoutEvent");
            if (logoutEvent.type() != SecurityEvent.Type.CURRENT_LOGOUT
                    || logoutEvent.userId() != userId
                    || !sessionId.equals(logoutEvent.sessionId())) {
                throw new IllegalArgumentException("current revoke requires matching CURRENT_LOGOUT event");
            }
        }
    }

    record RevokeSelectedCommand(
            long userId,
            UUID actorSessionId,
            UUID targetSessionId,
            long expectedSessionVersion,
            long expectedRolesVersion,
            Instant now
    ) {
        public RevokeSelectedCommand {
            requireUserAndSession(userId, actorSessionId);
            Objects.requireNonNull(targetSessionId, "targetSessionId");
            Objects.requireNonNull(now, "now");
            if (expectedSessionVersion <= 0 || expectedRolesVersion <= 0) {
                throw new IllegalArgumentException("expected versions must be positive");
            }
        }
    }

    record RevokeAllCommand(
            long userId,
            UUID currentSessionId,
            Instant now,
            SecurityEvent logoutAllEvent
    ) {
        public RevokeAllCommand {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            Objects.requireNonNull(currentSessionId, "currentSessionId");
            Objects.requireNonNull(now, "now");
            logoutAllEvent = Objects.requireNonNull(logoutAllEvent, "logoutAllEvent");
            if (logoutAllEvent.type() != SecurityEvent.Type.LOGOUT_ALL
                    || logoutAllEvent.userId() != userId
                    || !currentSessionId.equals(logoutAllEvent.sessionId())) {
                throw new IllegalArgumentException("all revoke requires matching LOGOUT_ALL event");
            }
        }
    }

    record CreateSessionResult(SessionSnapshot snapshot, FailureCode failureCode) {
        public CreateSessionResult {
            requireExactlyOne(snapshot, failureCode);
        }

        public static CreateSessionResult success(SessionSnapshot snapshot) {
            return new CreateSessionResult(Objects.requireNonNull(snapshot, "snapshot"), null);
        }

        public static CreateSessionResult failure(FailureCode code) {
            return new CreateSessionResult(null, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return snapshot != null;
        }
    }

    record SnapshotResult(SessionSnapshot snapshot, FailureCode failureCode) {
        public SnapshotResult {
            requireExactlyOne(snapshot, failureCode);
        }

        public static SnapshotResult success(SessionSnapshot snapshot) {
            return new SnapshotResult(Objects.requireNonNull(snapshot, "snapshot"), null);
        }

        public static SnapshotResult failure(FailureCode code) {
            return new SnapshotResult(null, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return snapshot != null;
        }
    }

    record RoleSelectionResult(SessionSnapshot snapshot, FailureCode failureCode) {
        public RoleSelectionResult {
            requireExactlyOne(snapshot, failureCode);
        }

        public static RoleSelectionResult success(SessionSnapshot snapshot) {
            return new RoleSelectionResult(Objects.requireNonNull(snapshot, "snapshot"), null);
        }

        public static RoleSelectionResult failure(FailureCode code) {
            return new RoleSelectionResult(null, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return snapshot != null;
        }
    }

    record RefreshResult(SessionSnapshot snapshot, UUID replacementJti, FailureCode failureCode) {
        public RefreshResult {
            if (snapshot == null && replacementJti != null) {
                throw new IllegalArgumentException("failure cannot expose replacement JTI");
            }
            if (snapshot != null && replacementJti == null) {
                throw new IllegalArgumentException("success requires replacement JTI");
            }
            requireExactlyOne(snapshot, failureCode);
        }

        public static RefreshResult success(SessionSnapshot snapshot, UUID replacementJti) {
            return new RefreshResult(Objects.requireNonNull(snapshot, "snapshot"),
                    Objects.requireNonNull(replacementJti, "replacementJti"), null);
        }

        public static RefreshResult failure(FailureCode code) {
            return new RefreshResult(null, null, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return snapshot != null;
        }

        @Override
        public String toString() {
            return succeeded()
                    ? "RefreshResult[snapshot=" + snapshot + ", replacementJti=<redacted>]"
                    : "RefreshResult[failureCode=" + failureCode + ']';
        }
    }

    record RevokeResult(SessionSnapshot snapshot, boolean alreadyRevoked, FailureCode failureCode) {
        public RevokeResult {
            requireExactlyOne(snapshot, failureCode);
            if (snapshot != null && !snapshot.isRevoked()) {
                throw new IllegalArgumentException("successful revoke must return revoked snapshot");
            }
        }

        public static RevokeResult success(SessionSnapshot snapshot, boolean alreadyRevoked) {
            return new RevokeResult(Objects.requireNonNull(snapshot, "snapshot"), alreadyRevoked, null);
        }

        public static RevokeResult failure(FailureCode code) {
            return new RevokeResult(null, false, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return snapshot != null;
        }
    }

    record RevokeAllResult(int revokedSessionCount, FailureCode failureCode) {
        public RevokeAllResult {
            if (revokedSessionCount < 0) {
                throw new IllegalArgumentException("revokedSessionCount must not be negative");
            }
            if ((revokedSessionCount == 0) != (failureCode != null)) {
                throw new IllegalArgumentException("failure must be explicit and success must revoke at least one session");
            }
        }

        public static RevokeAllResult success(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("revoke-all success must include current session");
            }
            return new RevokeAllResult(count, null);
        }

        public static RevokeAllResult failure(FailureCode code) {
            return new RevokeAllResult(0, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return failureCode == null;
        }
    }

    private static void requireUserAndSession(long userId, UUID sessionId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(sessionId, "sessionId");
    }

    private static void requireExactlyOne(Object value, FailureCode code) {
        if ((value == null) == (code == null)) {
            throw new IllegalArgumentException("result must contain exactly one value or failure");
        }
    }
}
