package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.IdentityOperationFailure.error;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.security.MessageDigest;
import java.util.*;

/** Narrow terminal receipt. Reads of a past result never constitute current profile authority. */
final class AuthAdminAccessNoopReceipts {
    private final JdbcTemplate jdbc;

    AuthAdminAccessNoopReceipts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    IdentityOperationResult find(UUID id, ByteString hash, byte[] canonical) {
        var rows =
                jdbc.query(
                        "SELECT command_hash,canonical_command,result_payload FROM"
                            + " auth_admin_access_noop_receipts WHERE operation_id=?",
                        (rs, n) -> {
                            if (!MessageDigest.isEqual(rs.getBytes(1), hash.toByteArray())
                                    || canonical != null
                                            && !Arrays.equals(rs.getBytes(2), canonical))
                                throw conflict();
                            try {
                                return IdentityOperationResult.parseFrom(rs.getBytes(3));
                            } catch (com.google.protobuf.InvalidProtocolBufferException corrupt) {
                                throw new IllegalStateException(
                                        "invalid stored noop receipt", corrupt);
                            }
                        },
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void requireActor(UUID id, IdentityOperationActor actor) {
        Boolean matches =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM auth_admin_access_noop_receipts WHERE operation_id=? AND actor_id=?
 AND actor_session_id=? AND actor_session_version=? AND actor_roles_version=?)
""",
                        Boolean.class,
                        id,
                        actor.getUserId(),
                        UUID.fromString(actor.getSessionId()),
                        actor.getSessionVersion(),
                        actor.getRolesVersion());
        if (!Boolean.TRUE.equals(matches)) throw conflict();
    }

    /**
     * Caller already holds global operation lock + ordered actor/target locks and admitted FULL.
     */
    IdentityOperationResult prepareIfUnchanged(
            PrepareIdentityOperationRequest command, SessionPrincipal actor, byte[] canonical) {
        if (command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS
                || command.getTargetsCount() != 1) return null;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("noop receipt requires local transaction");
        var target = command.getTargets(0);
        if (target.getUserId() == actor.userId())
            throw error(Status.PERMISSION_DENIED, "ADMIN_ACCESS_SELF_CHANGE_DENIED");
        var identity =
                jdbc.query(
                                "SELECT"
                                    + " login,account_state,fence_operation_id,roles_version,credentials_version,telegram_binding_version"
                                    + " FROM auth_identities WHERE id=?",
                                (rs, n) ->
                                        new Identity(
                                                rs.getString(1),
                                                rs.getString(2),
                                                rs.getObject(3, UUID.class),
                                                rs.getLong(4),
                                                rs.getLong(5),
                                                rs.getLong(6)),
                                target.getUserId())
                        .getFirst();
        if (!"ACTIVE".equals(identity.state()))
            throw error(Status.PERMISSION_DENIED, "ADMIN_TARGET_PROTECTED");
        if (identity.fence() != null
                || identity.rv() != target.getExpectedRolesVersion()
                || identity.cv() != target.getExpectedCredentialsVersion()
                || identity.bv() != target.getExpectedTelegramBindingVersion()) throw stale();
        var profiles =
                jdbc.query(
                        "SELECT assigned,mode,permissions,version FROM admin_access_profiles WHERE"
                            + " user_id=?",
                        (rs, n) ->
                                AdmissionAdminProfile.newBuilder()
                                        .setAssigned(rs.getBoolean(1))
                                        .setMode(rs.getString(2) == null ? "" : rs.getString(2))
                                        .addAllPermissions(
                                                Arrays.asList((String[]) rs.getArray(3).getArray()))
                                        .setExpectedVersion(rs.getLong(4))
                                        .build(),
                        target.getUserId());
        var current =
                profiles.isEmpty()
                        ? AdmissionAdminProfile.getDefaultInstance()
                        : profiles.getFirst();
        if (current.getExpectedVersion() != target.getDesiredAdminProfile().getExpectedVersion())
            throw stale();
        // Compare normalized permission catalogues, preserving existing PUT set semantics.
        boolean profileEqual =
                current.getAssigned() == target.getDesiredAdminProfile().getAssigned()
                        && current.getMode().equals(target.getDesiredAdminProfile().getMode())
                        && permissions(current)
                                .equals(permissions(target.getDesiredAdminProfile()));
        var grants =
                jdbc.query(
                        "SELECT id,role,status,group_id FROM user_role_grants WHERE user_id=? ORDER"
                            + " BY role",
                        (rs, n) -> {
                            var grant =
                                    AdmissionGrant.newBuilder()
                                            .setGrantId(rs.getLong(1))
                                            .setRole(rs.getString(2).toUpperCase(Locale.ROOT))
                                            .setStatus(rs.getString(3).toUpperCase(Locale.ROOT));
                            Long group = rs.getObject(4, Long.class);
                            if (group != null) grant.setGroupId(group);
                            return grant.build();
                        },
                        target.getUserId());
        if (!profileEqual
                || !grants.equals(target.getDesiredGrantsList())
                || target.hasTelegramChange()
                || !target.getSupersedesFailedOperationId().isEmpty()
                || target.getDesiredAccountState()
                        != IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE) return null;
        boolean activeAdmin =
                grants.stream()
                        .anyMatch(
                                g -> "ADMIN".equals(g.getRole()) && "ACTIVE".equals(g.getStatus()));
        if (activeAdmin != current.getAssigned())
            throw error(Status.INVALID_ARGUMENT, "INVALID_IDENTITY_COMMAND");
        var result =
                IdentityOperationResult.newBuilder()
                        .setOperationId(command.getOperationId())
                        .setCommandHash(command.getCommandHash())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS)
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP)
                        .setOperationRevision(1)
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(target.getUserId())
                                        .setLogin(identity.login())
                                        .setFinalRolesVersion(identity.rv())
                                        .setCredentialsVersion(identity.cv())
                                        .setTelegramBindingVersion(identity.bv())
                                        .setExpectedAcademicRevision(
                                                target.getExpectedAcademicRevision())
                                        .addAllGrants(grants)
                                        .setFinalAdminProfileVersion(current.getExpectedVersion()))
                        .build();
        jdbc.update(
                """
INSERT INTO auth_admin_access_noop_receipts(operation_id,command_hash,canonical_command,actor_id,actor_session_id,
 actor_session_version,actor_roles_version,target_id,result_payload) VALUES(?,?,?,?,?,?,?,?,?)
""",
                UUID.fromString(command.getOperationId()),
                command.getCommandHash().toByteArray(),
                canonical,
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                target.getUserId(),
                result.toByteArray());
        return result;
    }

    private static List<String> permissions(AdmissionAdminProfile profile) {
        if (!profile.getAssigned()) return profile.getPermissionsList();
        try {
            return AdminPermission.validate(profile.getMode(), profile.getPermissionsList());
        } catch (AdminAccessException invalid) {
            throw error(Status.PERMISSION_DENIED, "ADMIN_PROFILE_INVALID");
        }
    }

    private static io.grpc.StatusRuntimeException conflict() {
        return error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
    }

    private static io.grpc.StatusRuntimeException stale() {
        return error(Status.ABORTED, "IDENTITY_STATE_STALE");
    }

    private record Identity(String login, String state, UUID fence, long rv, long cv, long bv) {}
}
