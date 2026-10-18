package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.CredentialReaderInvalidationCommand.*;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.*;

import java.security.MessageDigest;
import java.util.*;

/**
 * Local Auth transactions only. Academic owns the durable group/read guard and every domain effect.
 */
@Service
public final class AuthCredentialReaderInvalidationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AuthInternalActorReader actors;
    private final AuthAdminJdbcAuthority admins;

    public AuthCredentialReaderInvalidationStore(
            JdbcTemplate jdbc, PlatformTransactionManager manager, AuthInternalActorReader actors) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.actors = actors;
        this.admins = new AuthAdminJdbcAuthority(jdbc);
    }

    public CredentialReaderInvalidationResult prepare(
            PrepareCredentialReaderInvalidationRequest request) {
        byte[] canonical = validate(request);
        UUID id = uuid(request.getOperationId());
        return tx.execute(
                status -> {
                    admins.lockGlobal();
                    Op previous = find(id);
                    if (previous != null) {
                        same(previous, request.getCommandHash().toByteArray(), canonical);
                        return result(previous);
                    }
                    var rejected = rejection(id, request.getCommandHash().toByteArray(), canonical);
                    if (rejected != null) return rejected;
                    boolean automatic =
                            request.getAction()
                                            == CredentialReaderInvalidationAction
                                                    .CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE
                                    && request.getSupersedesFailedOperationId().isEmpty();
                    SessionPrincipal actor = automatic ? null : actors.read();
                    var ids = new TreeSet<Long>();
                    if (actor != null) ids.add(actor.userId());
                    request.getGroupsList().stream()
                            .map(CredentialReaderGroup::getHeadmanUserId)
                            .filter(uid -> uid > 0)
                            .forEach(ids::add);
                    lock(ids);
                    AdminAccessProfile profile =
                            actor == null
                                    ? null
                                    : admins.current(AuthInternalActorReader.claims(actor));
                    if (profile != null && !profile.allows(AdminPermission.GROUPS_MANAGE.name()))
                        throw AdminAccessException.denied();
                    Op superseded = null;
                    if (!request.getSupersedesFailedOperationId().isEmpty()) {
                        superseded = find(uuid(request.getSupersedesFailedOperationId()));
                        if (superseded == null
                                || !"FAILED_SAFE".equals(superseded.phase())
                                || superseded.superseded() != null
                                || !Arrays.equals(
                                        intent(parse(superseded.command())), intent(request)))
                            throw conflict();
                    }
                    // Exact complete Auth-eligible headman set for each opaque group; Academic's
                    // durable group guard serializes the local set, including in-flight writers.
                    for (var group : request.getGroupsList()) {
                        List<Long> current =
                                jdbc.queryForList(
                                        """
SELECT u.id FROM user_role_grants h JOIN auth_identities u ON u.id=h.user_id
 JOIN user_role_grants s ON s.user_id=u.id AND s.role='student' AND s.status='active' AND s.group_id=h.group_id
 WHERE h.role='headman' AND h.status='active' AND h.group_id=? AND u.account_state='ACTIVE'
 ORDER BY u.id
""",
                                        Long.class,
                                        group.getGroupId());
                        List<Long> expected =
                                group.getHeadmanUserId() == 0
                                        ? List.of()
                                        : List.of(group.getHeadmanUserId());
                        if (!current.equals(expected)) throw conflict();
                        if (group.getHeadmanUserId() > 0) {
                            var identity = identity(group.getHeadmanUserId());
                            if (identity.fence() != null
                                    || !"ACTIVE".equals(identity.state())
                                    || identity.rv() != group.getExpectedHeadmanRolesVersion())
                                throw conflict();
                        }
                    }
                    Identity stamp = actor == null ? null : identity(actor.userId());
                    long commitRv =
                            actor == null
                                    ? 0
                                    : actor.rolesVersion()
                                            + (request.getGroupsList().stream()
                                                            .anyMatch(
                                                                    g ->
                                                                            g.getHeadmanUserId()
                                                                                    == actor
                                                                                            .userId())
                                                    ? 1
                                                    : 0);
                    jdbc.update(
                            """
INSERT INTO auth_credential_reader_invalidations(operation_id,command_hash,canonical_command,action,automatic,
 actor_id,actor_session_id,actor_session_version,actor_commit_roles_version,actor_credentials_version,actor_binding_version,
 actor_profile_version,supersedes_operation_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
                            id,
                            request.getCommandHash().toByteArray(),
                            canonical,
                            action(request),
                            automatic,
                            actor == null ? null : actor.userId(),
                            actor == null ? null : actor.sessionId(),
                            actor == null ? null : actor.sessionVersion(),
                            actor == null ? null : commitRv,
                            stamp == null ? null : stamp.cv(),
                            stamp == null ? null : stamp.bv(),
                            profile == null ? null : profile.version(),
                            superseded == null ? null : superseded.id());
                    if (superseded != null) {
                        jdbc.update(
                                "UPDATE auth_credential_reader_invalidations SET"
                                        + " superseded_by_operation_id=?,revision=revision+1 WHERE"
                                        + " operation_id=?",
                                id,
                                superseded.id());
                        jdbc.update(
                                "UPDATE auth_credential_reader_groups SET reserved=false WHERE"
                                        + " operation_id=?",
                                superseded.id());
                    }
                    for (var group : request.getGroupsList()) {
                        boolean empty = group.getHeadmanUserId() == 0;
                        jdbc.update(
                                """
INSERT INTO auth_credential_reader_groups(operation_id,group_id,expected_group_revision,headman_user_id,
 expected_roles_version,final_roles_version,expected_academic_revision) VALUES(?,?,?,?,?,?,?)
""",
                                id,
                                group.getGroupId(),
                                group.getExpectedGroupRevision(),
                                empty ? null : group.getHeadmanUserId(),
                                empty ? null : group.getExpectedHeadmanRolesVersion(),
                                empty
                                        ? null
                                        : Math.addExact(group.getExpectedHeadmanRolesVersion(), 1),
                                empty ? null : group.getExpectedHeadmanAcademicRevision());
                    }
                    // All immutable reservations exist before the version trigger admits RV-only
                    // writes.
                    for (var group : request.getGroupsList())
                        if (group.getHeadmanUserId() > 0)
                            if (jdbc.update(
                                            "UPDATE auth_identities SET"
                                                + " roles_version=roles_version+1 WHERE id=? AND"
                                                + " roles_version=? AND fence_operation_id IS NULL",
                                            group.getHeadmanUserId(),
                                            group.getExpectedHeadmanRolesVersion())
                                    != 1) throw conflict();
                    return result(find(id));
                });
    }

    public CredentialReaderInvalidationResult get(GetCredentialReaderInvalidationRequest request) {
        UUID id = uuid(request.getOperationId());
        requireHash(request.getCommandHash().size());
        return tx.execute(
                status -> {
                    Op op = find(id);
                    if (op == null) {
                        var rejected = rejection(id, request.getCommandHash().toByteArray(), null);
                        if (rejected != null) return rejected;
                        throw Status.NOT_FOUND
                                .withDescription("INVALIDATION_NOT_FOUND")
                                .asRuntimeException();
                    }
                    same(op, request.getCommandHash().toByteArray(), null);
                    return result(op);
                });
    }

    public CredentialReaderInvalidationResult fail(
            FailCredentialReaderInvalidationRequest request) {
        UUID id = uuid(request.getOperationId());
        requireHash(request.getCommandHash().size());
        return tx.execute(
                status -> {
                    admins.lockGlobal();
                    Op op = required(id);
                    same(op, request.getCommandHash().toByteArray(), null);
                    if (!"INVALIDATED".equals(op.phase())) return result(op);
                    if (op.revision() != request.getExpectedOperationRevision()) throw conflict();
                    markFailed(op);
                    return result(required(id));
                });
    }

    public CredentialReaderInvalidationResult commit(
            CommitCredentialReaderInvalidationRequest request) {
        UUID id = uuid(request.getOperationId());
        requireHash(request.getCommandHash().size());
        if (request.getSerializedSize() > MAX_BYTES
                || !request.getUnknownFields().asMap().isEmpty()) throw invalid();
        return tx.execute(
                status -> {
                    admins.lockGlobal();
                    Op op = required(id);
                    same(op, request.getCommandHash().toByteArray(), null);
                    if ("COMMITTED".equals(op.phase())) {
                        if (!Arrays.equals(op.receipt(), request.toByteArray())) throw conflict();
                        return result(op);
                    }
                    if (!"INVALIDATED".equals(op.phase())) return result(op);
                    if (op.revision() != request.getExpectedOperationRevision()) throw conflict();
                    var command = parse(op.command());
                    var ids = new TreeSet<Long>();
                    if (op.actor() != null) ids.add(op.actor());
                    command.getGroupsList().stream()
                            .map(CredentialReaderGroup::getHeadmanUserId)
                            .filter(uid -> uid > 0)
                            .forEach(ids::add);
                    lock(ids);
                    validateReceipts(command, request);
                    boolean authorized = actorCurrent(op);
                    for (var group : command.getGroupsList())
                        if (group.getHeadmanUserId() > 0) {
                            var target = identity(group.getHeadmanUserId());
                            if (target.rv()
                                            != Math.addExact(
                                                    group.getExpectedHeadmanRolesVersion(), 1)
                                    || target.fence() != null
                                    || !"ACTIVE".equals(target.state())) authorized = false;
                        }
                    if (!authorized) {
                        markFailed(op);
                        return result(required(id));
                    }
                    Map<Long, AcademicIdentityCommitReceipt> headmen = new HashMap<>();
                    request.getHeadmenList().forEach(r -> headmen.put(r.getUserId(), r));
                    for (var group : request.getGroupsList()) {
                        var descriptor =
                                command.getGroupsList().stream()
                                        .filter(g -> g.getGroupId() == group.getGroupId())
                                        .findFirst()
                                        .orElseThrow();
                        var receipt = headmen.get(descriptor.getHeadmanUserId());
                        jdbc.update(
                                "UPDATE auth_credential_reader_groups SET"
                                    + " committed_group_revision=?,committed_academic_revision=?"
                                    + " WHERE operation_id=? AND group_id=?",
                                group.getCommittedGroupRevision(),
                                receipt == null ? null : receipt.getCommittedAcademicRevision(),
                                id,
                                group.getGroupId());
                    }
                    // Lock the transactional clock before predicting the exact final frontier.
                    long before =
                            Objects.requireNonNull(
                                    jdbc.queryForObject(
                                            "SELECT version FROM auth_identity_catalog_clock WHERE"
                                                    + " singleton=1 FOR UPDATE",
                                            Long.class));
                    long after = Math.addExact(before, request.getHeadmenCount());
                    jdbc.update(
                            "UPDATE auth_credential_reader_invalidations SET"
                                + " phase='COMMITTED',revision=revision+1,committed_catalog_version=?,commit_receipt=?"
                                + " WHERE operation_id=?",
                            after,
                            request.toByteArray(),
                            id);
                    jdbc.update(
                            "UPDATE auth_credential_reader_groups SET reserved=false WHERE"
                                    + " operation_id=?",
                            id);
                    for (var receipt : request.getHeadmenList())
                        jdbc.queryForObject(
                                "SELECT append_auth_identity_catalog_snapshot(?)",
                                Long.class,
                                receipt.getUserId());
                    Long actual =
                            jdbc.queryForObject(
                                    "SELECT version FROM auth_identity_catalog_clock WHERE"
                                            + " singleton=1",
                                    Long.class);
                    if (!Objects.equals(actual, after))
                        throw new IllegalStateException("catalog frontier mismatch");
                    return result(required(id));
                });
    }

    public CredentialReaderInvalidationResult rejectUnprepared(
            RejectUnpreparedCredentialReaderInvalidationRequest request) {
        if (!request.hasCanonicalPrepare()
                || !request.getUnknownFields().asMap().isEmpty()
                || request.getReason()
                        == IdentityOperationRejectionReason
                                .IDENTITY_OPERATION_REJECTION_REASON_UNSPECIFIED
                || request.getReason() == IdentityOperationRejectionReason.UNRECOGNIZED)
            throw invalid();
        var command = request.getCanonicalPrepare();
        byte[] canonical = validate(command);
        UUID id = uuid(command.getOperationId());
        boolean automatic =
                command.getAction()
                                == CredentialReaderInvalidationAction
                                        .CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE
                        && command.getSupersedesFailedOperationId().isEmpty();
        var actor = request.hasActor() ? request.getActor() : null;
        if (automatic
                ? actor != null
                : actor == null
                        || actor.getUserId() <= 0
                        || actor.getSessionVersion() <= 0
                        || actor.getRolesVersion() <= 0
                        || !actor.getUnknownFields().asMap().isEmpty()) throw invalid();
        UUID sid = actor == null ? null : uuid(actor.getSessionId());
        return tx.execute(
                status -> {
                    admins.lockGlobal();
                    Op positive = find(id);
                    if (positive != null) {
                        same(positive, command.getCommandHash().toByteArray(), canonical);
                        long ownBump =
                                actor != null
                                                && command.getGroupsList().stream()
                                                        .anyMatch(
                                                                g ->
                                                                        g.getHeadmanUserId()
                                                                                == actor
                                                                                        .getUserId())
                                        ? 1
                                        : 0;
                        if (positive.automatic() != automatic
                                || !automatic
                                        && (!Objects.equals(positive.actor(), actor.getUserId())
                                                || !Objects.equals(positive.sid(), sid)
                                                || positive.sv() != actor.getSessionVersion()
                                                || positive.actorRv() - ownBump
                                                        != actor.getRolesVersion()))
                            throw conflict();
                        return result(positive);
                    }
                    var previous = rejection(id, command.getCommandHash().toByteArray(), canonical);
                    if (previous != null) {
                        byte[] expected = actor == null ? null : actor.toByteArray();
                        byte[] stored =
                                jdbc.queryForObject(
                                        "SELECT original_actor FROM"
                                            + " auth_credential_reader_rejections WHERE"
                                            + " operation_id=?",
                                        byte[].class,
                                        id);
                        if (!Arrays.equals(expected, stored)) throw conflict();
                        return previous;
                    }
                    if (!automatic
                            && !Boolean.TRUE.equals(
                                    jdbc.queryForObject(
                                            "SELECT EXISTS(SELECT 1 FROM auth_sessions WHERE sid=?"
                                                + " AND user_id=?)",
                                            Boolean.class,
                                            sid,
                                            actor.getUserId())))
                        throw Status.PERMISSION_DENIED
                                .withDescription("INVALIDATION_ACTOR_MISMATCH")
                                .asRuntimeException();
                    jdbc.update(
                            "INSERT INTO"
                                + " auth_credential_reader_rejections(operation_id,command_hash,canonical_command,automatic,original_actor,reason)"
                                + " VALUES(?,?,?,?,?,?)",
                            id,
                            command.getCommandHash().toByteArray(),
                            canonical,
                            automatic,
                            actor == null ? null : actor.toByteArray(),
                            request.getReason()
                                    .name()
                                    .substring("IDENTITY_OPERATION_REJECTION_REASON_".length()));
                    return rejection(id, command.getCommandHash().toByteArray(), canonical);
                });
    }

    private CredentialReaderInvalidationResult rejection(UUID id, byte[] hash, byte[] canonical) {
        var rows =
                jdbc.query(
                        "SELECT command_hash,canonical_command FROM"
                            + " auth_credential_reader_rejections WHERE operation_id=?",
                        (rs, n) -> {
                            if (!MessageDigest.isEqual(hash, rs.getBytes(1))
                                    || canonical != null
                                            && !Arrays.equals(canonical, rs.getBytes(2)))
                                throw conflict();
                            return CredentialReaderInvalidationResult.newBuilder()
                                    .setOperationId(id.toString())
                                    .setCommandHash(com.google.protobuf.ByteString.copyFrom(hash))
                                    .setOperationRevision(1)
                                    .setPhase(
                                            CredentialReaderInvalidationPhase
                                                    .CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED)
                                    .build();
                        },
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean actorCurrent(Op op) {
        if (op.automatic())
            return true; // exact directed service + immutable automatic action, not a human
        // session.
        var actor = identity(op.actor());
        if (!"ACTIVE".equals(actor.state())
                || actor.fence() != null
                || actor.rv() != op.actorRv()
                || actor.cv() != op.actorCv()
                || actor.bv() != op.actorBv()) return false;
        Boolean live =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM auth_sessions s JOIN user_role_grants g ON g.id=s.active_role_grant_id AND g.user_id=s.user_id
 JOIN admin_access_profiles p ON p.user_id=s.user_id WHERE s.sid=? AND s.user_id=? AND s.session_version=?
 AND s.revoked_at IS NULL AND g.role='admin' AND g.status='active' AND p.assigned AND p.version=?)
""",
                        Boolean.class,
                        op.sid(),
                        op.actor(),
                        op.sv(),
                        op.profile());
        if (!Boolean.TRUE.equals(live)) return false;
        try {
            return admins.profile(op.actor()).allows(AdminPermission.GROUPS_MANAGE.name());
        } catch (AdminAccessException denied) {
            return false;
        }
    }

    private static void validateReceipts(
            PrepareCredentialReaderInvalidationRequest command,
            CommitCredentialReaderInvalidationRequest request) {
        if (request.getGroupsCount() != command.getGroupsCount()) throw invalid();
        for (int n = 0; n < command.getGroupsCount(); n++) {
            var g = command.getGroups(n);
            var r = request.getGroups(n);
            if (!r.getUnknownFields().asMap().isEmpty()
                    || r.getGroupId() != g.getGroupId()
                    || r.getPreviousGroupRevision() != g.getExpectedGroupRevision()
                    || r.getCommittedGroupRevision()
                            != Math.addExact(g.getExpectedGroupRevision(), 1)) throw invalid();
        }
        var expected =
                command.getGroupsList().stream()
                        .filter(g -> g.getHeadmanUserId() > 0)
                        .sorted(Comparator.comparingLong(CredentialReaderGroup::getHeadmanUserId))
                        .toList();
        if (request.getHeadmenCount() != expected.size()) throw invalid();
        for (int n = 0; n < expected.size(); n++) {
            var g = expected.get(n);
            var r = request.getHeadmen(n);
            if (!r.getUnknownFields().asMap().isEmpty()
                    || r.getUserId() != g.getHeadmanUserId()
                    || r.getPreviousAcademicRevision() != g.getExpectedHeadmanAcademicRevision()
                    || r.getCommittedAcademicRevision()
                            != Math.addExact(g.getExpectedHeadmanAcademicRevision(), 1)
                    || r.getAppliedAuthRolesVersion()
                            != Math.addExact(g.getExpectedHeadmanRolesVersion(), 1))
                throw invalid();
        }
    }

    private void markFailed(Op op) {
        jdbc.update(
                "UPDATE auth_credential_reader_invalidations SET"
                        + " phase='FAILED_SAFE',revision=revision+1 WHERE operation_id=?",
                op.id());
    }

    private void lock(Set<Long> ids) {
        for (long id : ids)
            if (jdbc.queryForList(
                                    "SELECT id FROM auth_identities WHERE id=? FOR UPDATE",
                                    Long.class,
                                    id)
                            .size()
                    != 1) throw conflict();
    }

    private Identity identity(long id) {
        return jdbc.query(
                        "SELECT"
                            + " roles_version,credentials_version,telegram_binding_version,account_state,fence_operation_id"
                            + " FROM auth_identities WHERE id=?",
                        (rs, n) ->
                                new Identity(
                                        rs.getLong(1),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getString(4),
                                        rs.getObject(5, UUID.class)),
                        id)
                .getFirst();
    }

    private Op required(UUID id) {
        Op op = find(id);
        if (op == null)
            throw Status.NOT_FOUND.withDescription("INVALIDATION_NOT_FOUND").asRuntimeException();
        return op;
    }

    private Op find(UUID id) {
        var rows =
                jdbc.query(
                        "SELECT * FROM auth_credential_reader_invalidations WHERE operation_id=?",
                        (rs, n) ->
                                new Op(
                                        id,
                                        rs.getBytes("command_hash"),
                                        rs.getBytes("canonical_command"),
                                        rs.getString("phase"),
                                        rs.getLong("revision"),
                                        rs.getBoolean("automatic"),
                                        rs.getObject("actor_id", Long.class),
                                        rs.getObject("actor_session_id", UUID.class),
                                        rs.getLong("actor_session_version"),
                                        rs.getLong("actor_commit_roles_version"),
                                        rs.getLong("actor_credentials_version"),
                                        rs.getLong("actor_binding_version"),
                                        rs.getLong("actor_profile_version"),
                                        rs.getLong("committed_catalog_version"),
                                        rs.getBytes("commit_receipt"),
                                        rs.getObject("superseded_by_operation_id", UUID.class)),
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private CredentialReaderInvalidationResult result(Op op) {
        var result =
                CredentialReaderInvalidationResult.newBuilder()
                        .setOperationId(op.id().toString())
                        .setCommandHash(com.google.protobuf.ByteString.copyFrom(op.hash()))
                        .setOperationRevision(op.revision())
                        .setPhase(
                                CredentialReaderInvalidationPhase.valueOf(
                                        "CREDENTIAL_READER_INVALIDATION_PHASE_" + op.phase()))
                        .setCommittedCatalogVersion(op.catalog());
        parse(op.command()).getGroupsList().stream()
                .filter(g -> g.getHeadmanUserId() > 0)
                .sorted(Comparator.comparingLong(CredentialReaderGroup::getHeadmanUserId))
                .forEach(
                        g ->
                                result.addHeadmen(
                                        PreparedIdentityTarget.newBuilder()
                                                .setUserId(g.getHeadmanUserId())
                                                .setFinalRolesVersion(
                                                        Math.addExact(
                                                                g.getExpectedHeadmanRolesVersion(),
                                                                1))
                                                .setExpectedAcademicRevision(
                                                        g.getExpectedHeadmanAcademicRevision())));
        return result.build();
    }

    private static void same(Op op, byte[] hash, byte[] command) {
        if (!MessageDigest.isEqual(op.hash(), hash)
                || command != null && !Arrays.equals(op.command(), command)) throw conflict();
    }

    private static void requireHash(int length) {
        if (length != 32) throw invalid();
    }

    private static PrepareCredentialReaderInvalidationRequest parse(byte[] bytes) {
        try {
            return PrepareCredentialReaderInvalidationRequest.parseFrom(bytes);
        } catch (com.google.protobuf.InvalidProtocolBufferException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String action(PrepareCredentialReaderInvalidationRequest request) {
        return request.getAction()
                .name()
                .substring("CREDENTIAL_READER_INVALIDATION_ACTION_".length());
    }

    private record Identity(long rv, long cv, long bv, String state, UUID fence) {}

    private record Op(
            UUID id,
            byte[] hash,
            byte[] command,
            String phase,
            long revision,
            boolean automatic,
            Long actor,
            UUID sid,
            long sv,
            long actorRv,
            long actorCv,
            long actorBv,
            long profile,
            long catalog,
            byte[] receipt,
            UUID superseded) {}
}
