package ru.rutcampustrack.auth.session.port;

import ru.rutcampustrack.auth.session.model.SecurityEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One atomic credential update plus all-session revocation operation. */
public interface CredentialSessionTransactionPort {

    /**
     * One user-row transaction: re-check expectedCurrentHash under lock, then
     * update the credential/password flags, revoke every own session including
     * current, and append PASSWORD_CHANGED before commit. A compare failure or
     * authority failure returns typed failure with no partial revoke/update.
     */
    ChangePasswordResult changePassword(ChangePasswordCommand command);

    enum FailureCode {
        CURRENT_PASSWORD_INVALID,
        PASSWORD_POLICY_VIOLATION,
        INVALID_SESSION,
        SESSION_REVOKED,
        AUTHORITY_UNAVAILABLE
    }

    /** Hashes are opaque boundary values and are always redacted in text. */
    record CredentialHash(String value) {
        public CredentialHash {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("credential hash must be present");
            }
        }

        @Override
        public String toString() {
            return "CredentialHash[<redacted>]";
        }
    }

    record ChangePasswordCommand(
            long userId,
            UUID currentSessionId,
            CredentialHash expectedCurrentHash,
            CredentialHash replacementHash,
            Instant now,
            SecurityEvent passwordChangedEvent
    ) {
        public ChangePasswordCommand {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            currentSessionId = Objects.requireNonNull(currentSessionId, "currentSessionId");
            expectedCurrentHash = Objects.requireNonNull(expectedCurrentHash, "expectedCurrentHash");
            replacementHash = Objects.requireNonNull(replacementHash, "replacementHash");
            now = Objects.requireNonNull(now, "now");
            passwordChangedEvent = Objects.requireNonNull(passwordChangedEvent, "passwordChangedEvent");
            if (passwordChangedEvent.type() != SecurityEvent.Type.PASSWORD_CHANGED
                    || passwordChangedEvent.userId() != userId
                    || !currentSessionId.equals(passwordChangedEvent.sessionId())) {
                throw new IllegalArgumentException("password command requires matching PASSWORD_CHANGED event");
            }
        }

        @Override
        public String toString() {
            return "ChangePasswordCommand[userId=" + userId
                    + ", currentSessionId=" + currentSessionId
                    + ", expectedCurrentHash=<redacted>, replacementHash=<redacted>, now=" + now + ']';
        }
    }

    record ChangePasswordResult(int revokedSessionCount, FailureCode failureCode) {
        public ChangePasswordResult {
            if (revokedSessionCount < 0) {
                throw new IllegalArgumentException("revokedSessionCount must not be negative");
            }
            if ((revokedSessionCount == 0) != (failureCode != null)) {
                throw new IllegalArgumentException("failure must be explicit and success must revoke at least one session");
            }
        }

        public static ChangePasswordResult success(int revokedSessionCount) {
            if (revokedSessionCount <= 0) {
                throw new IllegalArgumentException("password change must revoke current session");
            }
            return new ChangePasswordResult(revokedSessionCount, null);
        }

        public static ChangePasswordResult failure(FailureCode code) {
            return new ChangePasswordResult(0, Objects.requireNonNull(code, "code"));
        }

        public boolean succeeded() {
            return failureCode == null;
        }
    }
}
