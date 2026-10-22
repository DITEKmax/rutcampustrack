package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.IdentityOperationFailure.error;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.security.MessageDigest;
import java.util.*;

/** Auth-owned unchanged HEADMAN result; it never supplies fresh admission on replay. */
final class AuthHeadmanNoopReceipts {
    private final JdbcTemplate jdbc;

    AuthHeadmanNoopReceipts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    IdentityOperationResult find(UUID id, ByteString hash, byte[] canonical) {
        var rows =
                jdbc.query(
                        "SELECT command_hash,canonical_command,result_payload FROM"
                                + " auth_headman_noop_receipts WHERE operation_id=?",
                        (rs, n) -> {
                            if (!MessageDigest.isEqual(rs.getBytes(1), hash.toByteArray())
                                    || canonical != null
                                            && !Arrays.equals(rs.getBytes(2), canonical))
                                throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
                            try {
                                return IdentityOperationResult.parseFrom(rs.getBytes(3));
                            } catch (com.google.protobuf.InvalidProtocolBufferException corrupt) {
                                throw new IllegalStateException(
                                        "invalid stored headman noop receipt", corrupt);
                            }
                        },
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void requireActor(UUID id, IdentityOperationActor actor) {
        Boolean same =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM auth_headman_noop_receipts WHERE operation_id=? AND actor_id=?
 AND actor_session_id=? AND actor_session_version=? AND actor_roles_version=?)
""",
                        Boolean.class,
                        id,
                        actor.getUserId(),
                        UUID.fromString(actor.getSessionId()),
                        actor.getSessionVersion(),
                        actor.getRolesVersion());
        if (!Boolean.TRUE.equals(same)) throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
    }

    /** Global UUID lock, ordered identity locks and current operation permission are held. */
    IdentityOperationResult prepareIfUnchanged(
            PrepareIdentityOperationRequest command,
            SessionPrincipal actor,
            AdminAccessProfile actorProfile,
            byte[] canonical) {
        if (!command.getHeadmanNoopCandidate()) return null;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("headman noop requires local transaction");
        if (command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE
                || command.getTargetsCount() != 1) throw invalid();
        var target = command.getTargets(0);
        if (target.hasTelegramChange()
                || !target.getSupersedesFailedOperationId().isEmpty()
                || target.getDesiredAccountState()
                        != IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE) throw invalid();
        var identities =
                jdbc.query(
                        """
SELECT login,account_state,fence_operation_id,roles_version,credentials_version,telegram_binding_version
 FROM auth_identities WHERE id=?
""",
                        (rs, n) ->
                                new Identity(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getObject(3, UUID.class),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getLong(6)),
                        target.getUserId());
        if (identities.size() != 1) throw stale();
        var identity = identities.getFirst();
        if (!"ACTIVE".equals(identity.state())
                || identity.fence() != null
                || identity.rv() != target.getExpectedRolesVersion()
                || identity.cv() != target.getExpectedCredentialsVersion()
                || identity.bv() != target.getExpectedTelegramBindingVersion()) throw stale();
        // The same committed receipt frontier used by the authoritative identity catalog.
        // Credential-reader commits also advance Academic eligibility revisions.
        Long academicRevision =
                jdbc.queryForObject(
                        """
SELECT COALESCE(MAX(committed_academic_revision),0) FROM (
 SELECT t.committed_academic_revision FROM auth_identity_operation_targets t
 JOIN auth_identity_operations o USING(operation_id) WHERE t.user_id=? AND o.phase='COMMITTED'
 UNION ALL SELECT g.committed_academic_revision FROM auth_credential_reader_groups g
 JOIN auth_credential_reader_invalidations o USING(operation_id)
 WHERE g.headman_user_id=? AND o.phase='COMMITTED'
) revisions
""",
                        Long.class,
                        target.getUserId(),
                        target.getUserId());
        if (academicRevision == null
                || academicRevision <= 0
                || academicRevision != target.getExpectedAcademicRevision()) throw stale();
        var profiles =
                jdbc.query(
                        """
SELECT assigned,mode,permissions,version FROM admin_access_profiles WHERE user_id=?
""",
                        (rs, n) ->
                                AdmissionAdminProfile.newBuilder()
                                        .setAssigned(rs.getBoolean(1))
                                        .setMode(rs.getString(2) == null ? "" : rs.getString(2))
                                        .addAllPermissions(
                                                Arrays.stream((String[]) rs.getArray(3).getArray())
                                                        .distinct()
                                                        .sorted()
                                                        .toList())
                                        .setExpectedVersion(rs.getLong(4))
                                        .build(),
                        target.getUserId());
        var profile =
                profiles.isEmpty()
                        ? AdmissionAdminProfile.getDefaultInstance()
                        : profiles.getFirst();
        if (!profile.equals(target.getDesiredAdminProfile())) throw stale();
        var grants =
                jdbc.query(
                        "SELECT id,role,status,group_id FROM user_role_grants WHERE user_id=? ORDER"
                                + " BY role",
                        (rs, n) -> {
                            var g =
                                    AdmissionGrant.newBuilder()
                                            .setGrantId(rs.getLong(1))
                                            .setRole(rs.getString(2).toUpperCase(Locale.ROOT))
                                            .setStatus(rs.getString(3).toUpperCase(Locale.ROOT));
                            Long group = rs.getObject(4, Long.class);
                            if (group != null) g.setGroupId(group);
                            return g.build();
                        },
                        target.getUserId());
        if (!actorProfile.full()
                && grants.stream()
                        .anyMatch(
                                g -> g.getRole().equals("ADMIN") && g.getStatus().equals("ACTIVE")))
            throw error(Status.PERMISSION_DENIED, "ADMIN_TARGET_PROTECTED");
        if (!grants.equals(target.getDesiredGrantsList())) throw stale();
        var headmen =
                grants.stream()
                        .filter(
                                g ->
                                        g.getRole().equals("HEADMAN")
                                                && g.getStatus().equals("ACTIVE"))
                        .toList();
        if (headmen.size() != 1) throw stale();
        long group = headmen.getFirst().getGroupId();
        if (grants.stream()
                .noneMatch(
                        g ->
                                g.getRole().equals("STUDENT")
                                        && g.getStatus().equals("ACTIVE")
                                        && g.hasGroupId()
                                        && g.getGroupId() == group)) throw stale();
        var current =
                jdbc.queryForList(
                        "SELECT user_id FROM user_role_grants WHERE role='headman' AND"
                                + " status='active' AND group_id=? ORDER BY user_id",
                        Long.class,
                        group);
        if (!current.equals(List.of(target.getUserId()))
                || !Boolean.FALSE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM auth_credential_reader_groups WHERE"
                                        + " reserved AND (headman_user_id=? OR group_id=?))",
                                Boolean.class,
                                target.getUserId(),
                                group))) throw stale();
        var result =
                IdentityOperationResult.newBuilder()
                        .setOperationId(command.getOperationId())
                        .setCommandHash(command.getCommandHash())
                        .setKind(command.getKind())
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP)
                        .setOperationRevision(1)
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(target.getUserId())
                                        .setLogin(identity.login())
                                        .setFinalRolesVersion(identity.rv())
                                        .setCredentialsVersion(identity.cv())
                                        .setTelegramBindingVersion(identity.bv())
                                        .setExpectedAcademicRevision(academicRevision)
                                        .addAllGrants(grants)
                                        .setFinalAdminProfileVersion(profile.getExpectedVersion()))
                        .build();
        jdbc.update(
                """
INSERT INTO auth_headman_noop_receipts(operation_id,command_hash,canonical_command,actor_id,actor_session_id,
 actor_session_version,actor_roles_version,target_id,group_id,academic_revision,result_payload)
 VALUES(?,?,?,?,?,?,?,?,?,?,?)
""",
                UUID.fromString(command.getOperationId()),
                command.getCommandHash().toByteArray(),
                canonical,
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                target.getUserId(),
                group,
                academicRevision,
                result.toByteArray());
        return result;
    }

    private static io.grpc.StatusRuntimeException stale() {
        return error(Status.ABORTED, "IDENTITY_STATE_STALE");
    }

    private static io.grpc.StatusRuntimeException invalid() {
        return error(Status.INVALID_ARGUMENT, "INVALID_IDENTITY_COMMAND");
    }

    private record Identity(String login, String state, UUID fence, long rv, long cv, long bv) {}
}
