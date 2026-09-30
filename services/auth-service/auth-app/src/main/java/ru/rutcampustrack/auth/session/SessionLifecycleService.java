package ru.rutcampustrack.auth.session;

import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates pure domain validation and one atomic port call per command.
 * It intentionally does not mint JWTs, access the database, or compose reads
 * and writes into a pretend compare-and-set.
 */
public final class SessionLifecycleService {

    private final SessionStatePort sessionStatePort;
    private final CredentialSessionTransactionPort credentialPort;
    private final ActiveRolePolicy activeRolePolicy;
    private final PasswordPolicy passwordPolicy;

    public SessionLifecycleService(
            SessionStatePort sessionStatePort,
            CredentialSessionTransactionPort credentialPort
    ) {
        this(sessionStatePort, credentialPort, new ActiveRolePolicy(), new PasswordPolicy());
    }

    public SessionLifecycleService(
            SessionStatePort sessionStatePort,
            CredentialSessionTransactionPort credentialPort,
            ActiveRolePolicy activeRolePolicy,
            PasswordPolicy passwordPolicy
    ) {
        this.sessionStatePort = Objects.requireNonNull(sessionStatePort, "sessionStatePort");
        this.credentialPort = Objects.requireNonNull(credentialPort, "credentialPort");
        this.activeRolePolicy = Objects.requireNonNull(activeRolePolicy, "activeRolePolicy");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
    }

    public SessionStatePort.CreateSessionResult createSession(CreateSessionRequest request) {
        Objects.requireNonNull(request, "request");
        ActiveRolePolicy.Evaluation evaluation = activeRolePolicy.evaluate(request.userId(), request.grants());
        if (!evaluation.succeeded()) {
            return SessionStatePort.CreateSessionResult.failure(map(evaluation.code()));
        }

        RoleGrant defaultGrant = evaluation.defaultGrant();
        SessionState state = new SessionState(
                request.sessionId(),
                request.userId(),
                defaultGrant == null ? null : defaultGrant.grantId(),
                1,
                request.currentRefreshJti(),
                null,
                request.refreshExpiresAt(),
                request.createdAt(),
                request.createdAt(),
                null,
                null,
                request.authMethod(),
                request.clientLabel(),
                request.locationLabel()
        );
        SessionStatePort.CredentialProof credentialProof = request.expectedCredentialHash() == null
                ? null
                : new SessionStatePort.CredentialProof(request.expectedCredentialHash());
        SecurityEvent loginEvent = new SecurityEvent(
                request.userId(), request.sessionId(), SecurityEvent.Type.LOGIN,
                request.createdAt(), request.authMethod(), request.clientLabel(), request.locationLabel()
        );
        return sessionStatePort.createSession(new SessionStatePort.CreateSessionCommand(
                state, request.rolesVersion(), evaluation.grants(), credentialProof, loginEvent
        ));
    }

    public SessionStatePort.SnapshotResult snapshot(SnapshotRequest request) {
        Objects.requireNonNull(request, "request");
        return sessionStatePort.snapshot(new SessionStatePort.SnapshotCommand(
                request.userId(), request.sessionId(), request.now()
        ));
    }

    public SessionStatePort.RoleSelectionResult selectRole(SelectRoleRequest request) {
        Objects.requireNonNull(request, "request");
        SecurityEvent event = new SecurityEvent(
                request.userId(), request.sessionId(), SecurityEvent.Type.ROLE_CHANGED,
                request.now(), request.authMethod(), request.clientLabel(), request.locationLabel()
        );
        return sessionStatePort.selectRole(new SessionStatePort.SelectRoleCommand(
                request.userId(), request.sessionId(), request.role(), request.expectedSessionVersion(),
                request.now(), event
        ));
    }

    public SessionStatePort.RefreshResult refresh(RefreshRequest request) {
        Objects.requireNonNull(request, "request");
        return sessionStatePort.refresh(new SessionStatePort.RefreshCommand(
                request.userId(), request.sessionId(), request.presentedJti(),
                request.replacementJti(), request.now()
        ));
    }

    public SessionStatePort.RevokeResult revokeCurrent(RevokeRequest request) {
        Objects.requireNonNull(request, "request");
        SecurityEvent event = new SecurityEvent(
                request.userId(), request.sessionId(), SecurityEvent.Type.CURRENT_LOGOUT,
                request.now(), request.authMethod(), request.clientLabel(), request.locationLabel()
        );
        return sessionStatePort.revokeCurrent(new SessionStatePort.RevokeCurrentCommand(
                request.userId(), request.sessionId(), request.now(), event
        ));
    }

    public SessionStatePort.RevokeAllResult revokeAll(RevokeAllRequest request) {
        Objects.requireNonNull(request, "request");
        SecurityEvent event = new SecurityEvent(
                request.userId(), request.currentSessionId(), SecurityEvent.Type.LOGOUT_ALL,
                request.now(), request.authMethod(), request.clientLabel(), request.locationLabel()
        );
        return sessionStatePort.revokeAll(new SessionStatePort.RevokeAllCommand(
                request.userId(), request.currentSessionId(), request.now(), event
        ));
    }

    /**
     * Validates the requested password and delegates the atomic credential operation.
     *
     * <p>The trusted integration caller must derive {@code replacementHash} from this
     * exact validated {@code newPassword} value, without normalization, truncation,
     * or another transformation. Hashing belongs to that trusted integration boundary;
     * the pure domain does not apply BCrypt.
     */
    public CredentialSessionTransactionPort.ChangePasswordResult changePassword(
            ChangePasswordRequest request
    ) {
        Objects.requireNonNull(request, "request");
        PasswordPolicy.Validation validation = passwordPolicy.validate(request.newPassword());
        if (!validation.valid()) {
            return CredentialSessionTransactionPort.ChangePasswordResult.failure(
                    CredentialSessionTransactionPort.FailureCode.PASSWORD_POLICY_VIOLATION
            );
        }
        SecurityEvent event = new SecurityEvent(
                request.userId(), request.currentSessionId(), SecurityEvent.Type.PASSWORD_CHANGED,
                request.now(), request.authMethod(), request.clientLabel(), request.locationLabel()
        );
        return credentialPort.changePassword(new CredentialSessionTransactionPort.ChangePasswordCommand(
                request.userId(), request.currentSessionId(), request.expectedCurrentHash(),
                request.replacementHash(), request.now(), event
        ));
    }

    public boolean issuePasswordResetTicket(
            long userId,
            CredentialSessionTransactionPort.CredentialHash ticketHash,
            Instant expiresAt
    ) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return credentialPort.issuePasswordResetTicket(
                userId, Objects.requireNonNull(ticketHash, "ticketHash"),
                Objects.requireNonNull(expiresAt, "expiresAt"));
    }

    public CredentialSessionTransactionPort.PasswordResetResult completePasswordReset(
            PasswordResetRequest request
    ) {
        Objects.requireNonNull(request, "request");
        PasswordPolicy.Validation validation = passwordPolicy.validate(request.newPassword());
        if (!validation.valid()) {
            return CredentialSessionTransactionPort.PasswordResetResult.failure(
                    CredentialSessionTransactionPort.PasswordResetFailureCode.PASSWORD_POLICY_VIOLATION);
        }
        return credentialPort.completePasswordReset(new CredentialSessionTransactionPort.PasswordResetCommand(
                request.ticketHash(), request.replacementHash(), request.now()));
    }

    private static SessionStatePort.FailureCode map(ActiveRolePolicy.Code code) {
        return switch (code) {
            case FOREIGN_GRANT -> SessionStatePort.FailureCode.INVALID_GRANT;
            case DUPLICATE_GRANT -> SessionStatePort.FailureCode.DUPLICATE_GRANT;
            case INVALID_GRANT -> SessionStatePort.FailureCode.INVALID_GRANT;
            case ROLE_NOT_GRANTED -> SessionStatePort.FailureCode.ROLE_NOT_GRANTED;
            case ROLE_NOT_SELECTABLE -> SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE;
            case OK -> throw new IllegalArgumentException("cannot map successful role evaluation");
        };
    }

    public record CreateSessionRequest(
            long userId,
            UUID sessionId,
            UUID currentRefreshJti,
            Instant createdAt,
            Instant refreshExpiresAt,
            AuthMethod authMethod,
            long rolesVersion,
            List<RoleGrant> grants,
            String expectedCredentialHash,
            String clientLabel,
            String locationLabel
    ) {
        public CreateSessionRequest {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            currentRefreshJti = Objects.requireNonNull(currentRefreshJti, "currentRefreshJti");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt");
            if (!refreshExpiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("refreshExpiresAt must be after createdAt");
            }
            authMethod = Objects.requireNonNull(authMethod, "authMethod");
            if (rolesVersion <= 0) {
                throw new IllegalArgumentException("rolesVersion must be positive");
            }
            grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
            if (authMethod == AuthMethod.PASSWORD
                    && (expectedCredentialHash == null || expectedCredentialHash.isBlank())) {
                throw new IllegalArgumentException("password session requires expected credential hash");
            }
            if (authMethod != AuthMethod.PASSWORD && expectedCredentialHash != null) {
                throw new IllegalArgumentException("non-password session cannot carry credential hash");
            }
            validateLabel(clientLabel, "clientLabel");
            validateLabel(locationLabel, "locationLabel");
        }

        @Override
        public String toString() {
            return "CreateSessionRequest[userId=" + userId + ", sessionId=" + sessionId
                    + ", currentRefreshJti=<redacted>, createdAt=" + createdAt
                    + ", refreshExpiresAt=" + refreshExpiresAt + ", authMethod=" + authMethod
                    + ", rolesVersion=" + rolesVersion + ", grants=" + grants
                    + ", expectedCredentialHash=<redacted>]";
        }
    }

    public record SnapshotRequest(long userId, UUID sessionId, Instant now) {
        public SnapshotRequest {
            requireUserAndSession(userId, sessionId);
            Objects.requireNonNull(now, "now");
        }
    }

    public record SelectRoleRequest(
            long userId,
            UUID sessionId,
            AuthRole role,
            long expectedSessionVersion,
            Instant now,
            AuthMethod authMethod,
            String clientLabel,
            String locationLabel
    ) {
        public SelectRoleRequest {
            requireUserAndSession(userId, sessionId);
            role = Objects.requireNonNull(role, "role");
            if (expectedSessionVersion <= 0) {
                throw new IllegalArgumentException("expectedSessionVersion must be positive");
            }
            now = Objects.requireNonNull(now, "now");
            validateLabel(clientLabel, "clientLabel");
            validateLabel(locationLabel, "locationLabel");
        }
    }

    public record RefreshRequest(
            long userId,
            UUID sessionId,
            UUID presentedJti,
            UUID replacementJti,
            Instant now
    ) {
        public RefreshRequest {
            requireUserAndSession(userId, sessionId);
            presentedJti = Objects.requireNonNull(presentedJti, "presentedJti");
            replacementJti = Objects.requireNonNull(replacementJti, "replacementJti");
            if (presentedJti.equals(replacementJti)) {
                throw new IllegalArgumentException("replacementJti must differ from presentedJti");
            }
            now = Objects.requireNonNull(now, "now");
        }

        @Override
        public String toString() {
            return "RefreshRequest[userId=" + userId + ", sessionId=" + sessionId
                    + ", presentedJti=<redacted>, replacementJti=<redacted>, now=" + now + ']';
        }
    }

    public record RevokeRequest(
            long userId,
            UUID sessionId,
            Instant now,
            AuthMethod authMethod,
            String clientLabel,
            String locationLabel
    ) {
        public RevokeRequest {
            requireUserAndSession(userId, sessionId);
            now = Objects.requireNonNull(now, "now");
            validateLabel(clientLabel, "clientLabel");
            validateLabel(locationLabel, "locationLabel");
        }
    }

    public record RevokeAllRequest(
            long userId,
            UUID currentSessionId,
            Instant now,
            AuthMethod authMethod,
            String clientLabel,
            String locationLabel
    ) {
        public RevokeAllRequest {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            currentSessionId = Objects.requireNonNull(currentSessionId, "currentSessionId");
            now = Objects.requireNonNull(now, "now");
            validateLabel(clientLabel, "clientLabel");
            validateLabel(locationLabel, "locationLabel");
        }
    }

    /**
     * Password change input. The trusted caller supplies a replacement hash derived
     * from the exact validated {@code newPassword} value, without normalization or
     * truncation; the pure domain does not hash the password.
     */
    public record ChangePasswordRequest(
            long userId,
            UUID currentSessionId,
            CredentialSessionTransactionPort.CredentialHash expectedCurrentHash,
            CredentialSessionTransactionPort.CredentialHash replacementHash,
            CharSequence newPassword,
            Instant now,
            AuthMethod authMethod,
            String clientLabel,
            String locationLabel
    ) {
        public ChangePasswordRequest {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            currentSessionId = Objects.requireNonNull(currentSessionId, "currentSessionId");
            expectedCurrentHash = Objects.requireNonNull(expectedCurrentHash, "expectedCurrentHash");
            replacementHash = Objects.requireNonNull(replacementHash, "replacementHash");
            now = Objects.requireNonNull(now, "now");
            validateLabel(clientLabel, "clientLabel");
            validateLabel(locationLabel, "locationLabel");
        }

        @Override
        public String toString() {
            return "ChangePasswordRequest[userId=" + userId + ", currentSessionId=" + currentSessionId
                    + ", expectedCurrentHash=<redacted>, replacementHash=<redacted>"
                    + ", newPassword=<redacted>, now=" + now + ']';
        }
    }

    public record PasswordResetRequest(
            CredentialSessionTransactionPort.CredentialHash ticketHash,
            CredentialSessionTransactionPort.CredentialHash replacementHash,
            CharSequence newPassword,
            Instant now
    ) {
        public PasswordResetRequest {
            ticketHash = Objects.requireNonNull(ticketHash, "ticketHash");
            replacementHash = Objects.requireNonNull(replacementHash, "replacementHash");
            newPassword = Objects.requireNonNull(newPassword, "newPassword");
            now = Objects.requireNonNull(now, "now");
        }

        @Override
        public String toString() {
            return "PasswordResetRequest[ticketHash=<redacted>, replacementHash=<redacted>, "
                    + "newPassword=<redacted>, now=" + now + ']';
        }
    }

    private static void requireUserAndSession(long userId, UUID sessionId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(sessionId, "sessionId");
    }

    private static void validateLabel(String value, String field) {
        if (value != null && value.length() > 160) {
            throw new IllegalArgumentException(field + " must be at most 160 characters");
        }
    }
}
