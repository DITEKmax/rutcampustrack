package ru.rutcampustrack.academic.groupavailability;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.identityoperation.AcademicIdentityCacheInvalidator;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityLocalCommand.ApiIntent;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;
import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Own lifecycle ledger, complete headman-set guard, business APPLY and receipts. No remote calls.
 */
@Repository
public class GroupAvailabilityStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RequestContext context;
    private final GroupAvailabilityDomainWriter writer;
    private final AcademicIdentityCacheInvalidator caches;

    public GroupAvailabilityStore(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            RequestContext context,
            GroupAvailabilityDomainWriter writer,
            AcademicIdentityCacheInvalidator caches) {
        this.jdbc = jdbc;
        this.context = context;
        this.writer = writer;
        this.caches = caches;
        this.mapper =
                mapper.copy()
                        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public enum Phase {
        ACCEPTED,
        AUTH_INVALIDATED,
        LOCAL_COMMITTED,
        SUCCEEDED,
        FAILED_SAFE,
        REJECTED
    }

    public record Operation(
            UUID id,
            Phase phase,
            GroupAvailabilityCommand command,
            PrepareCredentialReaderInvalidationRequest auth,
            CredentialReaderInvalidationResult result,
            CommitCredentialReaderInvalidationRequest receipts,
            Long actorId,
            IdentityOperationActor actor,
            UUID supersedes,
            IdentityOperationRejectionReason rejection,
            boolean localRejected,
            ApiIntent apiIntent) {}

    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_group_availability_operations WHERE"
                                + " operation_id=?)",
                        Boolean.class,
                        id));
    }

    @Transactional(readOnly = true)
    public Operation find(UUID id) {
        return load(id, false);
    }

    @Transactional(readOnly = true)
    public Operation findForActor(UUID id, ApiIntent intent) {
        var found =
                jdbc.queryForList(
                        "SELECT operation_id FROM academic_group_availability_operations WHERE"
                                + " operation_id=?",
                        UUID.class,
                        id);
        if (found.isEmpty()) return null;
        var operation = load(id, false);
        var actor = context.getClaims();
        if (actor == null || operation.actorId() == null || operation.actorId() != actor.userId())
            throw Status.NOT_FOUND.asRuntimeException();
        if (!operation.apiIntent().equals(intent)) throw bound();
        return operation;
    }

    @Transactional(readOnly = true)
    public void requireAvailableScope(long group) {
        var pending =
                jdbc.query(
                        "SELECT o.operation_id,o.phase FROM academic_group_availability_guards g"
                            + " JOIN academic_group_availability_operations o ON"
                            + " o.operation_id=g.operation_id WHERE (?=0 OR g.group_id=?) ORDER BY"
                            + " CASE WHEN o.phase='FAILED_SAFE' THEN 0 ELSE 1"
                            + " END,o.created_at,o.operation_id LIMIT 1",
                        (rs, n) ->
                                new PendingScope(
                                        rs.getObject("operation_id", UUID.class),
                                        rs.getString("phase")),
                        group,
                        group);
        if (!pending.isEmpty()) pending(pending.getFirst());
    }

    private record PendingScope(UUID id, String phase) {}

    private static void pending(PendingScope current) {
        if (current.phase().equals("FAILED_SAFE"))
            throw new ru.rutcampustrack.academic.exception.ConflictException(
                    "Требуется восстановление операции жизненного цикла группы",
                    java.util.Map.of("operationId", current.id().toString()));
        throw Status.UNAVAILABLE
                .withDescription("group lifecycle operation is pending")
                .asRuntimeException();
    }

    @Transactional(readOnly = true)
    public Operation failedIntent(
            GroupAvailabilityCommand.Action action, long scope, String digest) {
        var rows =
                jdbc.queryForList(
                        "SELECT operation_id FROM academic_group_availability_operations WHERE"
                            + " action=? AND scope_group_id=? AND request_digest=? AND"
                            + " phase='FAILED_SAFE' AND superseded_by_operation_id IS NULL ORDER BY"
                            + " created_at",
                        UUID.class,
                        action.name(),
                        scope,
                        digest);
        if (rows.size() > 1) throw AuthCredentialReaderInvalidationClient.unavailable();
        return rows.isEmpty() ? null : load(rows.getFirst(), false);
    }

    @Transactional
    public Operation accept(
            UUID id,
            GroupAvailabilityCommand command,
            UUID supersedes,
            Map<Long, IdentityAuthoritySnapshot> refreshed,
            ApiIntent apiIntent) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?,?)", 0x53454D, 1);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,172903))",
                (rs, n) -> true,
                "academic-identity-api:" + id);
        var old =
                jdbc.queryForList(
                        "SELECT operation_id FROM academic_group_availability_operations WHERE"
                                + " operation_id=?",
                        UUID.class,
                        id);
        if (!old.isEmpty()) {
            var existing = load(id, false);
            if (command.action() == GroupAvailabilityCommand.Action.AUTOMATIC_CYCLE
                    && supersedes == null
                    && existing.actorId() == null) {
                if (!hash(bytes(command)).equals(hash(bytes(existing.command())))) throw bound();
                return existing;
            }
            return findForActor(id, apiIntent);
        }
        Boolean used =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operations WHERE"
                                + " operation_id=?) OR EXISTS(SELECT 1 FROM"
                                + " academic_identity_preflight_rejections WHERE operation_id=?)",
                        Boolean.class,
                        id,
                        id);
        if (!Boolean.FALSE.equals(used)) throw bound();
        boolean automatic =
                command.action() == GroupAvailabilityCommand.Action.AUTOMATIC_CYCLE
                        && supersedes == null;
        var actor = automatic ? null : context.getClaims();
        if (!automatic
                && (actor == null
                        || actor.userId() <= 0
                        || actor.sessionId() == null
                        || actor.sessionVersion() <= 0
                        || actor.rolesVersion() <= 0))
            throw Status.UNAUTHENTICATED.asRuntimeException();
        byte[] local = bytes(command);
        Operation previous = supersedes == null ? null : load(supersedes, true);
        if (previous != null
                && (previous.phase() != Phase.FAILED_SAFE
                        || !hash(local).equals(hash(bytes(previous.command()))))) throw conflict();
        var auth =
                PrepareCredentialReaderInvalidationRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setAcademicCommandHash(hash(local))
                        .setAction(wireAction(command.action()));
        if (command.cycle() != null)
            auth.setCycleSemesterId(command.cycle().semester())
                    .setCycleYear(command.cycle().dateTo().getYear());
        if (supersedes != null) auth.setSupersedesFailedOperationId(supersedes.toString());
        if (command.cycle() != null) {
            var cycle =
                    jdbc.queryForList(
                            "SELECT id FROM semesters WHERE id=? FOR UPDATE",
                            Long.class,
                            command.cycle().semester());
            if (cycle.size() != 1) throw conflict();
            var guard =
                    jdbc.queryForList(
                            "SELECT operation_id FROM academic_group_availability_cycle_guards"
                                    + " WHERE cycle_semester_id=?",
                            UUID.class,
                            command.cycle().semester());
            if (previous == null && !guard.isEmpty()) {
                var occupying = load(guard.getFirst(), false);
                pending(new PendingScope(occupying.id(), occupying.phase().name()));
            }
            if (previous != null && (guard.size() != 1 || !guard.getFirst().equals(supersedes)))
                throw conflict();
        }
        var users = new TreeSet<Long>();
        for (var group : command.groups()) {
            var actual =
                    jdbc.queryForList(
                            "SELECT academic_revision FROM groups WHERE id=? FOR UPDATE",
                            Long.class,
                            group.id());
            long required =
                    previous != null && previous.receipts() != null
                            ? group.revision() + 1
                            : group.revision();
            if (actual.size() != 1 || actual.getFirst() != required) throw conflict();
            var guard =
                    jdbc.queryForList(
                            "SELECT operation_id FROM academic_group_availability_guards WHERE"
                                    + " group_id=?",
                            UUID.class,
                            group.id());
            if (previous == null && !guard.isEmpty()) {
                var occupying = load(guard.getFirst(), false);
                pending(new PendingScope(occupying.id(), occupying.phase().name()));
            }
            if (previous != null && (guard.size() != 1 || !guard.getFirst().equals(supersedes)))
                throw conflict();
            Boolean pending =
                    jdbc.queryForObject(
                            """
SELECT EXISTS(SELECT 1 FROM academic_identity_operation_groups g JOIN academic_identity_operations o ON o.operation_id=g.operation_id
 WHERE g.group_id=? AND o.phase NOT IN ('SUCCEEDED','REJECTED')) OR EXISTS(
 SELECT 1 FROM academic_identity_operation_targets t JOIN academic_identity_operations o ON o.operation_id=t.operation_id
 JOIN academic_role_eligibility h ON h.user_id=t.user_id AND h.role='HEADMAN' AND h.status='ACTIVE'
 WHERE h.group_id=? AND o.phase NOT IN ('SUCCEEDED','REJECTED'))
""",
                            Boolean.class,
                            group.id(),
                            group.id());
            if (!Boolean.FALSE.equals(pending)) throw conflict();
            Boolean deleting =
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM subject_deletion_operations WHERE"
                                    + " group_id=? AND phase NOT IN ('COMPLETED','CANCELLED'))",
                            Boolean.class,
                            group.id());
            if (!Boolean.FALSE.equals(deleting)) throw conflict();
        }
        var lockedHeadmen = new TreeSet<Long>();
        for (var group : command.groups())
            lockedHeadmen.addAll(
                    jdbc.queryForList(
                            "SELECT user_id FROM academic_role_eligibility WHERE role='HEADMAN' AND"
                                    + " status='ACTIVE' AND group_id=?",
                            Long.class,
                            group.id()));
        for (long user : lockedHeadmen)
            jdbc.queryForList(
                    "SELECT id FROM academic_profiles WHERE id=? FOR UPDATE", Long.class, user);
        for (var group : command.groups()) {
            var heads =
                    jdbc.query(
                            """
SELECT h.user_id,p.academic_revision,p.applied_auth_roles_version,
 p.archived_at IS NULL AND s.status='ACTIVE' AND s.group_id=h.group_id valid
 FROM academic_role_eligibility h JOIN academic_profiles p ON p.id=h.user_id
 LEFT JOIN academic_role_eligibility s ON s.user_id=h.user_id AND s.role='STUDENT'
 WHERE h.role='HEADMAN' AND h.status='ACTIVE' AND h.group_id=? ORDER BY h.user_id FOR UPDATE OF p
""",
                            (rs, n) -> {
                                if (!rs.getBoolean("valid")) throw conflict();
                                long user = rs.getLong("user_id"),
                                        revision = rs.getLong("academic_revision"),
                                        rv = rs.getLong("applied_auth_roles_version");
                                if (previous != null) {
                                    var live = refreshed.get(user);
                                    if (live == null
                                            || live.getUserId() != user
                                            || live.getAccountState()
                                                    != AuthorityAccountState
                                                            .AUTHORITY_ACCOUNT_STATE_ACTIVE
                                            || live.getAcademicRevision() != revision
                                            || live.getRolesVersion() <= 0) throw conflict();
                                    rv = live.getRolesVersion();
                                }
                                return CredentialReaderGroup.newBuilder()
                                        .setGroupId(group.id())
                                        .setExpectedGroupRevision(group.revision())
                                        .setHeadmanUserId(user)
                                        .setExpectedHeadmanRolesVersion(rv)
                                        .setExpectedHeadmanAcademicRevision(revision)
                                        .build();
                            },
                            group.id());
            if (heads.size() > 1) throw conflict();
            var descriptor =
                    heads.isEmpty()
                            ? CredentialReaderGroup.newBuilder()
                                    .setGroupId(group.id())
                                    .setExpectedGroupRevision(group.revision())
                                    .build()
                            : heads.getFirst();
            auth.addGroups(descriptor);
            if (descriptor.getHeadmanUserId() > 0) users.add(descriptor.getHeadmanUserId());
        }
        // Reconcile retains the original complete group/headman identity manifest.
        if (previous != null) {
            if (auth.getGroupsCount() != previous.auth().getGroupsCount()) throw conflict();
            for (int n = 0; n < auth.getGroupsCount(); n++)
                if (auth.getGroups(n).getHeadmanUserId()
                        != previous.auth().getGroups(n).getHeadmanUserId()) throw conflict();
        }
        auth.setCommandHash(hash(auth.build().toByteArray()));
        var request = auth.build();
        if (local.length > 1048576 || request.getSerializedSize() > 1048576)
            throw Status.RESOURCE_EXHAUSTED.asRuntimeException();
        jdbc.update(
                """
INSERT INTO academic_group_availability_operations(operation_id,action,request_digest,scope_group_id,local_payload,local_command_hash,
 auth_payload,auth_command_hash,actor_mode,actor_id,actor_session_id,actor_session_version,actor_roles_version,supersedes_operation_id,api_action,api_target_id,api_request_digest,phase)
 VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACCEPTED')
""",
                id,
                command.action().name(),
                command.requestDigest(),
                command.scopeGroupId(),
                local,
                hash(local).toByteArray(),
                request.toByteArray(),
                request.getCommandHash().toByteArray(),
                automatic ? "SERVICE" : "HUMAN",
                automatic ? null : actor.userId(),
                automatic ? null : actor.sessionId(),
                automatic ? null : actor.sessionVersion(),
                automatic ? null : actor.rolesVersion(),
                supersedes,
                apiIntent.action(),
                apiIntent.targetId(),
                apiIntent.requestDigest());
        if (command.cycle() != null) {
            if (previous == null)
                jdbc.update(
                        "INSERT INTO"
                            + " academic_group_availability_cycle_guards(cycle_semester_id,operation_id)"
                            + " VALUES(?,?)",
                        command.cycle().semester(),
                        id);
            else if (jdbc.update(
                            "UPDATE academic_group_availability_cycle_guards SET operation_id=?"
                                    + " WHERE cycle_semester_id=? AND operation_id=?",
                            id,
                            command.cycle().semester(),
                            supersedes)
                    != 1) throw conflict();
        }
        for (var group : command.groups()) {
            if (previous == null)
                jdbc.update(
                        "INSERT INTO"
                            + " academic_group_availability_guards(group_id,operation_id,original_group_revision)"
                            + " VALUES(?,?,?)",
                        group.id(),
                        id,
                        group.revision());
            else if (jdbc.update(
                            "UPDATE academic_group_availability_guards SET operation_id=? WHERE"
                                    + " group_id=? AND operation_id=?",
                            id,
                            group.id(),
                            supersedes)
                    != 1) throw conflict();
        }
        if (previous != null) {
            jdbc.update(
                    "UPDATE academic_group_availability_headmen SET operation_id=? WHERE"
                            + " operation_id=?",
                    id,
                    supersedes);
            jdbc.update(
                    "UPDATE academic_group_availability_operations SET superseded_by_operation_id=?"
                            + " WHERE operation_id=?",
                    id,
                    supersedes);
            if (previous.receipts() != null)
                jdbc.update(
                        "UPDATE academic_group_availability_operations SET group_receipts=? WHERE"
                                + " operation_id=?",
                        groupsOnly(previous.receipts()).toByteArray(),
                        id);
        } else
            for (long user : users)
                jdbc.update(
                        "INSERT INTO academic_group_availability_headmen(user_id,operation_id)"
                                + " VALUES(?,?)",
                        user,
                        id);
        return load(id, false);
    }

    /** The existing already-active restore is a durable no-op, with no invented Auth command. */
    @Transactional
    public Operation recordNoop(UUID id, GroupAvailabilityCommand command) {
        var apiIntent =
                GroupAvailabilityIntent.direct(
                        command.action(), command.scopeGroupId(), command.requestDigest());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?,?)", 0x53454D, 1);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,172903))",
                (rs, n) -> true,
                "academic-identity-api:" + id);
        var existing =
                jdbc.queryForList(
                        "SELECT operation_id FROM academic_group_availability_operations WHERE"
                                + " operation_id=?",
                        UUID.class,
                        id);
        if (!existing.isEmpty()) return findForActor(id, apiIntent);
        if (command.action() != GroupAvailabilityCommand.Action.RESTORE
                && !(command.action() == GroupAvailabilityCommand.Action.MANUAL_CYCLE
                        && command.groups().isEmpty())) throw conflict();
        Boolean used =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operations WHERE"
                                + " operation_id=?) OR EXISTS(SELECT 1 FROM"
                                + " academic_identity_preflight_rejections WHERE operation_id=?)",
                        Boolean.class,
                        id,
                        id);
        if (!Boolean.FALSE.equals(used)) throw bound();
        var actor = context.getClaims();
        if (actor == null
                || actor.userId() <= 0
                || actor.sessionId() == null
                || actor.sessionVersion() <= 0
                || actor.rolesVersion() <= 0) throw Status.UNAUTHENTICATED.asRuntimeException();
        if (command.action() == GroupAvailabilityCommand.Action.RESTORE) {
            var active =
                    jdbc.queryForList(
                            "SELECT is_active FROM groups WHERE id=? FOR UPDATE",
                            Boolean.class,
                            command.scopeGroupId());
            if (active.size() != 1
                    || !active.getFirst()
                    || Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM academic_group_availability_guards"
                                        + " WHERE group_id=?) OR EXISTS(SELECT 1 FROM"
                                        + " academic_identity_operation_groups g JOIN"
                                        + " academic_identity_operations o ON"
                                        + " o.operation_id=g.operation_id WHERE g.group_id=? AND"
                                        + " o.phase NOT IN ('SUCCEEDED','REJECTED'))",
                                    Boolean.class,
                                    command.scopeGroupId(),
                                    command.scopeGroupId()))) throw conflict();
        }
        byte[] local = bytes(command),
                empty =
                        CommitCredentialReaderInvalidationRequest.getDefaultInstance()
                                .toByteArray();
        jdbc.update(
                """
INSERT INTO academic_group_availability_operations(operation_id,action,request_digest,scope_group_id,local_payload,local_command_hash,
 local_noop,actor_mode,actor_id,actor_session_id,actor_session_version,actor_roles_version,phase,group_receipts,headman_receipts,api_action,api_target_id,api_request_digest)
 VALUES(?,?,?,?,?,?,true,'HUMAN',?,?,?,?,'SUCCEEDED',?,?,?,?,?)
""",
                id,
                command.action().name(),
                command.requestDigest(),
                command.scopeGroupId(),
                local,
                hash(local).toByteArray(),
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                empty,
                empty,
                apiIntent.action(),
                apiIntent.targetId(),
                apiIntent.requestDigest());
        return load(id, false);
    }

    @Transactional
    public Operation invalidated(Operation expected, CredentialReaderInvalidationResult result) {
        var current = load(expected.id(), true);
        if (current.phase() != Phase.ACCEPTED) return current;
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                    + " phase='AUTH_INVALIDATED',auth_result=?,revision=revision+1,updated_at=now()"
                    + " WHERE operation_id=?",
                result.toByteArray(),
                current.id());
        return load(current.id(), false);
    }

    @Transactional
    public Operation apply(Operation expected) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?,?)", 0x53454D, 1);
        var current = load(expected.id(), true);
        if (current.phase() != Phase.AUTH_INVALIDATED) return current;
        var receipt = CommitCredentialReaderInvalidationRequest.newBuilder();
        if (current.receipts() != null) receipt.addAllGroups(current.receipts().getGroupsList());
        else {
            writer.apply(current.command(), current.actorId(), current.id());
            for (var group : current.command().groups()) {
                if (jdbc.update(
                                "UPDATE groups SET academic_revision=academic_revision+1 WHERE id=?"
                                        + " AND academic_revision=? AND EXISTS(SELECT 1 FROM"
                                        + " academic_group_availability_guards WHERE group_id=? AND"
                                        + " operation_id=?)",
                                group.id(),
                                group.revision(),
                                group.id(),
                                current.id())
                        != 1) throw conflict();
                receipt.addGroups(
                        CredentialReaderGroupCommitReceipt.newBuilder()
                                .setGroupId(group.id())
                                .setPreviousGroupRevision(group.revision())
                                .setCommittedGroupRevision(group.revision() + 1));
            }
        }
        for (var head : current.result().getHeadmenList()) {
            if (jdbc.update(
                            "UPDATE academic_profiles SET"
                                + " academic_revision=academic_revision+1,applied_auth_roles_version=?,updated_at=now()"
                                + " WHERE id=? AND academic_revision=? AND archived_at IS NULL AND"
                                + " EXISTS(SELECT 1 FROM academic_group_availability_headmen WHERE"
                                + " user_id=? AND operation_id=?)",
                            head.getFinalRolesVersion(),
                            head.getUserId(),
                            head.getExpectedAcademicRevision(),
                            head.getUserId(),
                            current.id())
                    != 1) throw conflict();
            receipt.addHeadmen(
                    AcademicIdentityCommitReceipt.newBuilder()
                            .setUserId(head.getUserId())
                            .setPreviousAcademicRevision(head.getExpectedAcademicRevision())
                            .setCommittedAcademicRevision(head.getExpectedAcademicRevision() + 1)
                            .setAppliedAuthRolesVersion(head.getFinalRolesVersion()));
        }
        var built = receipt.build();
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                    + " phase='LOCAL_COMMITTED',group_receipts=COALESCE(group_receipts,?),headman_receipts=?,revision=revision+1,updated_at=now()"
                    + " WHERE operation_id=?",
                groupsOnly(built).toByteArray(),
                CommitCredentialReaderInvalidationRequest.newBuilder()
                        .addAllHeadmen(built.getHeadmenList())
                        .build()
                        .toByteArray(),
                current.id());
        return load(current.id(), false);
    }

    @Transactional
    public Operation rejectionIntent(Operation expected, IdentityOperationRejectionReason reason) {
        var current = load(expected.id(), true);
        if (current.phase() != Phase.ACCEPTED) return current;
        String name = reason.name().replace("IDENTITY_OPERATION_REJECTION_REASON_", "");
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                        + " rejection_reason=COALESCE(rejection_reason,?),updated_at=now() WHERE"
                        + " operation_id=?",
                name,
                current.id());
        return load(current.id(), false);
    }

    @Transactional
    public Operation stopApply(Operation expected) {
        var current = load(expected.id(), true);
        if (current.phase() != Phase.AUTH_INVALIDATED) return current;
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                        + " local_apply_rejected=true,updated_at=now() WHERE operation_id=?",
                current.id());
        return load(current.id(), false);
    }

    @Transactional
    public Operation failed(Operation expected, CredentialReaderInvalidationResult failed) {
        var current = load(expected.id(), true);
        if (current.phase() == Phase.SUCCEEDED || current.phase() == Phase.REJECTED) return current;
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                        + " phase='FAILED_SAFE',auth_result=?,revision=revision+1,updated_at=now()"
                        + " WHERE operation_id=?",
                failed.toByteArray(),
                current.id());
        return load(current.id(), false);
    }

    @Transactional
    public Operation succeeded(Operation expected, CredentialReaderInvalidationResult committed) {
        var current = load(expected.id(), true);
        if (current.phase() != Phase.LOCAL_COMMITTED) return current;
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                    + " phase='SUCCEEDED',auth_result=?,revision=revision+1,updated_at=now(),next_attempt_at=NULL"
                    + " WHERE operation_id=?",
                committed.toByteArray(),
                current.id());
        release(current);
        caches.afterPublicationCommit();
        return load(current.id(), false);
    }

    @Transactional
    public Operation rejected(Operation expected, CredentialReaderInvalidationResult rejected) {
        var current = load(expected.id(), true);
        if (current.phase() != Phase.ACCEPTED) return current;
        jdbc.update(
                "UPDATE academic_group_availability_operations SET"
                    + " phase='REJECTED',auth_result=?,revision=revision+1,updated_at=now(),next_attempt_at=NULL"
                    + " WHERE operation_id=?",
                rejected.toByteArray(),
                current.id());
        if (current.supersedes() != null) {
            jdbc.update(
                    "UPDATE academic_group_availability_guards SET operation_id=? WHERE"
                            + " operation_id=?",
                    current.supersedes(),
                    current.id());
            jdbc.update(
                    "UPDATE academic_group_availability_headmen SET operation_id=? WHERE"
                            + " operation_id=?",
                    current.supersedes(),
                    current.id());
            jdbc.update(
                    "UPDATE academic_group_availability_cycle_guards SET operation_id=? WHERE"
                            + " operation_id=?",
                    current.supersedes(),
                    current.id());
            jdbc.update(
                    "UPDATE academic_group_availability_operations SET"
                            + " superseded_by_operation_id=NULL WHERE operation_id=? AND"
                            + " superseded_by_operation_id=?",
                    current.supersedes(),
                    current.id());
        } else release(current);
        return load(current.id(), false);
    }

    private void release(Operation current) {
        jdbc.update(
                "DELETE FROM academic_group_availability_cycle_guards WHERE operation_id=?",
                current.id());
        jdbc.update(
                "DELETE FROM academic_group_availability_guards WHERE operation_id=?",
                current.id());
        jdbc.update(
                "DELETE FROM academic_group_availability_headmen WHERE operation_id=?",
                current.id());
        caches.afterCommit(
                current.auth().getGroupsList().stream()
                        .map(CredentialReaderGroup::getHeadmanUserId)
                        .filter(u -> u > 0)
                        .distinct()
                        .toList(),
                current.command().groups().stream()
                        .map(GroupAvailabilityCommand.GroupRevision::id)
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<UUID> pending() {
        return jdbc.queryForList(
                "SELECT operation_id FROM academic_group_availability_operations WHERE phase IN"
                        + " ('ACCEPTED','AUTH_INVALIDATED','LOCAL_COMMITTED') AND"
                        + " superseded_by_operation_id IS NULL AND (next_attempt_at IS NULL OR"
                        + " next_attempt_at<=now()) ORDER BY created_at,operation_id LIMIT 20",
                UUID.class);
    }

    @Transactional
    public void retryLater(UUID id) {
        jdbc.update(
                "UPDATE academic_group_availability_operations SET next_attempt_at=now()+interval"
                        + " '30 seconds' WHERE operation_id=?",
                id);
    }

    private Operation load(UUID id, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT * FROM academic_group_availability_operations WHERE operation_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, n) -> {
                            try {
                                byte[] groups = rs.getBytes("group_receipts"),
                                        heads = rs.getBytes("headman_receipts");
                                var receipts =
                                        groups == null
                                                ? null
                                                : CommitCredentialReaderInvalidationRequest
                                                        .parseFrom(groups)
                                                        .toBuilder();
                                if (receipts != null && heads != null)
                                    receipts.addAllHeadmen(
                                            CommitCredentialReaderInvalidationRequest.parseFrom(
                                                            heads)
                                                    .getHeadmenList());
                                Long actor = rs.getObject("actor_id", Long.class);
                                return new Operation(
                                        id,
                                        Phase.valueOf(rs.getString("phase")),
                                        mapper.readValue(
                                                rs.getBytes("local_payload"),
                                                GroupAvailabilityCommand.class),
                                        rs.getBytes("auth_payload") == null
                                                ? null
                                                : PrepareCredentialReaderInvalidationRequest
                                                        .parseFrom(rs.getBytes("auth_payload")),
                                        rs.getBytes("auth_result") == null
                                                ? null
                                                : CredentialReaderInvalidationResult.parseFrom(
                                                        rs.getBytes("auth_result")),
                                        receipts == null ? null : receipts.build(),
                                        actor,
                                        actor == null
                                                ? null
                                                : IdentityOperationActor.newBuilder()
                                                        .setUserId(actor)
                                                        .setSessionId(
                                                                rs.getString("actor_session_id"))
                                                        .setSessionVersion(
                                                                rs.getLong("actor_session_version"))
                                                        .setRolesVersion(
                                                                rs.getLong("actor_roles_version"))
                                                        .build(),
                                        rs.getObject("supersedes_operation_id", UUID.class),
                                        rs.getString("rejection_reason") == null
                                                ? null
                                                : IdentityOperationRejectionReason.valueOf(
                                                        "IDENTITY_OPERATION_REJECTION_REASON_"
                                                                + rs.getString("rejection_reason")),
                                        rs.getBoolean("local_apply_rejected"),
                                        new ApiIntent(
                                                rs.getString("api_action"),
                                                rs.getLong("api_target_id"),
                                                rs.getString("api_request_digest")));
                            } catch (java.io.IOException invalid) {
                                throw AuthCredentialReaderInvalidationClient.unavailable();
                            }
                        },
                        id);
        if (rows.size() != 1) throw Status.NOT_FOUND.asRuntimeException();
        return rows.getFirst();
    }

    private byte[] bytes(GroupAvailabilityCommand command) {
        try {
            return mapper.writeValueAsBytes(command);
        } catch (java.io.IOException invalid) {
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        }
    }

    private static CommitCredentialReaderInvalidationRequest groupsOnly(
            CommitCredentialReaderInvalidationRequest receipt) {
        return CommitCredentialReaderInvalidationRequest.newBuilder()
                .addAllGroups(receipt.getGroupsList())
                .build();
    }

    static ByteString hash(byte[] bytes) {
        try {
            return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static CredentialReaderInvalidationAction wireAction(GroupAvailabilityCommand.Action action) {
        return switch (action) {
            case DEACTIVATE ->
                    CredentialReaderInvalidationAction
                            .CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_DEACTIVATE;
            case RESTORE ->
                    CredentialReaderInvalidationAction
                            .CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_RESTORE;
            case MANUAL_CYCLE ->
                    CredentialReaderInvalidationAction
                            .CREDENTIAL_READER_INVALIDATION_ACTION_MANUAL_CYCLE_ARCHIVE;
            case AUTOMATIC_CYCLE ->
                    CredentialReaderInvalidationAction
                            .CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE;
        };
    }

    static io.grpc.StatusRuntimeException conflict() {
        return Status.ABORTED
                .withDescription("group lifecycle precondition changed")
                .asRuntimeException();
    }

    private static io.grpc.StatusRuntimeException bound() {
        return Status.ALREADY_EXISTS
                .withDescription("operation key already bound")
                .asRuntimeException();
    }
}
