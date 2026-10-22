package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.IdentityOperationFailure.error;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.authoperations.grpc.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/** Fixed archive security admission. Called only inside the operation's local ordered-lock TX. */
final class AuthArchiveStepUpAdmission {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AuthArchiveStepUpAdmission(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    static void validateEnvelope(PrepareIdentityOperationRequest command) {
        boolean present =
                command.getArchiveStepUpProtocolVersion() != 0
                        || !command.getArchivePreviewDigest().isEmpty()
                        || !command.getPasswordStepUpReceiptId().isEmpty();
        boolean archive =
                command.getKind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE;
        if (!present && !archive) return;
        if (command.getArchiveStepUpProtocolVersion() != 1
                || !command.getArchivePreviewDigest().matches("[0-9a-f]{64}")
                || command.getTargetsCount() != 1
                || (!archive
                        && command.getKind()
                                != IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE))
            throw error(Status.INVALID_ARGUMENT, "IDENTITY_COMMAND_INVALID");
        if (!command.getPasswordStepUpReceiptId().isEmpty())
            uuid(command.getPasswordStepUpReceiptId());
    }

    void admit(
            PrepareIdentityOperationRequest command,
            IdentityOperationKind kind,
            SessionPrincipal actor) {
        boolean archive = kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE;
        if (!archive) {
            if (command.getArchiveStepUpProtocolVersion() != 0
                    || !command.getArchivePreviewDigest().isEmpty()
                    || !command.getPasswordStepUpReceiptId().isEmpty())
                throw error(Status.INVALID_ARGUMENT, "IDENTITY_COMMAND_INVALID");
            return;
        }
        if (command.getArchiveStepUpProtocolVersion() != 1 || command.getTargetsCount() != 1)
            throw error(Status.INVALID_ARGUMENT, "IDENTITY_COMMAND_INVALID");
        var target = command.getTargets(0);
        boolean required =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id=? AND"
                                    + " role IN ('teacher','headman') AND status='active')",
                                Boolean.class,
                                target.getUserId()));
        if (!target.getSupersedesFailedOperationId().isEmpty())
            required |=
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM auth_identity_step_up_admissions"
                                        + " WHERE operation_id=? AND step_up_required)",
                                    Boolean.class,
                                    uuid(target.getSupersedesFailedOperationId())));
        UUID receipt =
                command.getPasswordStepUpReceiptId().isEmpty()
                        ? null
                        : uuid(command.getPasswordStepUpReceiptId());
        if (required && receipt == null) throw denied();
        if (receipt != null) {
            var rows =
                    jdbc.queryForList(
                            """
SELECT r.receipt_id FROM auth_password_step_up_receipts r
JOIN auth_identities u ON u.id=r.actor_id JOIN admin_access_profiles p ON p.user_id=u.id
JOIN auth_sessions s ON s.sid=r.session_id AND s.user_id=u.id
WHERE r.receipt_id=? AND r.purpose='USER_ARCHIVE' AND r.target_id=? AND r.operation_id=? AND r.preview_digest=?
 AND r.actor_id=? AND r.session_id=? AND r.session_version=? AND r.roles_version=?
 AND r.roles_version=u.roles_version AND r.credentials_version=u.credentials_version
 AND r.binding_version=u.telegram_binding_version AND r.admin_profile_version=p.version
 AND s.session_version=r.session_version AND s.revoked_at IS NULL AND r.expires_at>?
FOR UPDATE OF r
""",
                            UUID.class,
                            receipt,
                            target.getUserId(),
                            uuid(command.getOperationId()),
                            command.getArchivePreviewDigest(),
                            actor.userId(),
                            actor.sessionId(),
                            actor.sessionVersion(),
                            actor.rolesVersion(),
                            Timestamp.from(clock.instant()));
            if (rows.size() != 1) throw denied();
        }
        jdbc.update(
                "INSERT INTO"
                    + " auth_identity_step_up_admissions(operation_id,protocol_version,command_hash,receipt_id,step_up_required)"
                    + " VALUES (?,1,?,?,?)",
                uuid(command.getOperationId()),
                command.getCommandHash().toByteArray(),
                receipt,
                required);
    }

    boolean owns(UUID operation, byte[] hash, IdentityOperationKind kind) {
        return kind != IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE
                || Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM auth_identity_step_up_admissions WHERE"
                                    + " operation_id=? AND command_hash=?)",
                                Boolean.class,
                                operation,
                                hash));
    }

    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException invalid) {
            throw error(Status.INVALID_ARGUMENT, "IDENTITY_COMMAND_INVALID");
        }
    }

    private static io.grpc.StatusRuntimeException denied() {
        return error(Status.FAILED_PRECONDITION, "ARCHIVE_STEP_UP_REQUIRED");
    }
}
