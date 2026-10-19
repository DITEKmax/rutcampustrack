package ru.rutcampustrack.academic.identityoperation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authoperations.grpc.AcademicIdentityCommitReceipt;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationActor;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationRejectionReason;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationResult;
import ru.rutcampustrack.authoperations.grpc.PrepareIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.RejectUnpreparedIdentityOperationRequest;

import java.util.List;
import java.util.UUID;

/** Every phase change and its business effect use one own-DB transaction and locked phase CAS. */
@Repository
public class JdbcAcademicIdentityOperationStore implements AcademicIdentityCoordinator.Store {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RequestContext context;
    private final AcademicIdentityDomainWriter writer;
    private final AcademicIdentityCacheInvalidator caches;
    private final ru.rutcampustrack.academic.user.UserArchiveRepository archives;

    public JdbcAcademicIdentityOperationStore(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            RequestContext context,
            AcademicIdentityDomainWriter writer,
            AcademicIdentityCacheInvalidator caches,
            ru.rutcampustrack.academic.user.UserArchiveRepository archives) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.context = context;
        this.writer = writer;
        this.caches = caches;
        this.archives = archives;
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation accept(
            AcademicIdentityCoordinator.AcceptedCommand accepted) {
        var request = accepted.authCommand();
        UUID id = uuid(request.getOperationId());
        lockApiAttempt(id);
        requireNotLocallyRejected(id, local(accepted.localPayload().toByteArray()).intent());
        var actor = context.getClaims();
        if (actor == null
                || actor.userId() <= 0
                || actor.sessionId() == null
                || actor.rolesVersion() <= 0
                || actor.sessionVersion() <= 0) throw Status.UNAUTHENTICATED.asRuntimeException();
        var local = local(accepted.localPayload().toByteArray());
        validateArchiveBinding(request, local);
        requireNotGroupOperation(id, actor.userId());
        Boolean replay =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operations WHERE"
                                + " operation_id=?)",
                        Boolean.class,
                        id);
        if (!Boolean.TRUE.equals(replay)) {
            var groups = new java.util.TreeSet<Long>();
            local.groups().forEach(g -> groups.add(g.groupId()));
            for (var target : request.getTargetsList()) {
                target.getDesiredGrantsList().stream()
                        .filter(g -> g.hasGroupId())
                        .forEach(g -> groups.add(g.getGroupId()));
                if (target.getUserId() > 0)
                    groups.addAll(
                            jdbc.queryForList(
                                    "SELECT group_id FROM academic_role_eligibility WHERE user_id=?"
                                            + " AND group_id IS NOT NULL",
                                    Long.class,
                                    target.getUserId()));
            }
            for (long group : groups) {
                var rows =
                        jdbc.queryForList(
                                "SELECT id FROM groups WHERE id=? FOR UPDATE", Long.class, group);
                if (rows.size() != 1) throw conflict();
                if (!Boolean.FALSE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM academic_group_availability_guards"
                                        + " WHERE group_id=?)",
                                Boolean.class,
                                group)))
                    throw Status.UNAVAILABLE
                            .withDescription("group lifecycle operation is pending")
                            .asRuntimeException();
            }
            if (request.getHeadmanNoopCandidate()) validateHeadmanNoopAdmission(request, local);
        }
        RecoverySource reconciliation = null;
        if (!Boolean.TRUE.equals(replay)
                && request.getKind()
                        == ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                .IDENTITY_OPERATION_KIND_RECONCILE) {
            reconciliation = validateReconciliation(request, local);
        }
        int inserted =
                jdbc.update(
                        """
INSERT INTO academic_identity_operations(operation_id,command_hash,local_command_hash,
  command_payload,local_payload,actor_id,actor_session_id,actor_session_version,
  actor_roles_version,kind,phase,created_at,updated_at,next_attempt_at)
VALUES(?,?,?,?,?,?,?,?,?,?,'ACCEPTED',now(),now(),now()) ON CONFLICT(operation_id) DO NOTHING
""",
                        id,
                        request.getCommandHash().toByteArray(),
                        accepted.localCommandHash().toByteArray(),
                        request.toByteArray(),
                        accepted.localPayload().toByteArray(),
                        actor.userId(),
                        actor.sessionId(),
                        actor.sessionVersion(),
                        actor.rolesVersion(),
                        kind(request));
        var stored = load(id, true);
        Boolean same =
                jdbc.queryForObject(
                        """
SELECT actor_id=? AND command_hash=? AND local_command_hash=?
  AND command_payload=? AND local_payload=? FROM academic_identity_operations WHERE operation_id=?
""",
                        Boolean.class,
                        actor.userId(),
                        request.getCommandHash().toByteArray(),
                        accepted.localCommandHash().toByteArray(),
                        request.toByteArray(),
                        accepted.localPayload().toByteArray(),
                        id);
        if (!Boolean.TRUE.equals(same))
            throw Status.ALREADY_EXISTS
                    .withDescription("operation key already bound")
                    .asRuntimeException();
        if (inserted == 0
                && stored.phase() == AcademicIdentityCoordinator.Phase.ACCEPTED
                && !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT actor_session_id=? AND actor_session_version=? AND"
                                        + " actor_roles_version=? FROM academic_identity_operations"
                                        + " WHERE operation_id=?",
                                Boolean.class,
                                actor.sessionId(),
                                actor.sessionVersion(),
                                actor.rolesVersion(),
                                id)))
            throw Status.FAILED_PRECONDITION
                    .withDescription("original accepted actor session required for Prepare")
                    .asRuntimeException();
        if (inserted == 1) {
            for (int n = 0; n < request.getTargetsCount(); n++) {
                var target = request.getTargets(n);
                jdbc.update(
                        "INSERT INTO"
                            + " academic_identity_operation_targets(operation_id,target_index,user_id,previous_academic_revision,fenced)"
                            + " VALUES(?,?,?,?,false)",
                        id,
                        n,
                        target.getUserId() == 0 ? null : target.getUserId(),
                        target.getExpectedAcademicRevision());
            }
            for (var group : local.groups())
                jdbc.update(
                        "INSERT INTO"
                            + " academic_identity_operation_groups(operation_id,group_id,expected_group_revision,fenced)"
                            + " VALUES(?,?,?,false)",
                        id,
                        group.groupId(),
                        group.expectedRevision());
            if (reconciliation != null) transferReconciliationGuard(id, reconciliation);
            stored = load(id, false);
        }
        return stored;
    }

    /**
     * Validate local purity while existing group locks and then the target profile lock are held.
     */
    private void validateHeadmanNoopAdmission(
            PrepareIdentityOperationRequest request, AcademicIdentityLocalCommand local) {
        if (request.getKind()
                        != ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                .IDENTITY_OPERATION_KIND_HEADMAN_REPLACE
                || request.getTargetsCount() != 1
                || local.profile() == null
                || local.headmanGroupId() == null
                || local.candidateUserId() == null
                || !local.candidateUserId().equals(local.expectedHeadmanId())
                || local.groups().size() != 1
                || local.groups().getFirst().groupId() != local.headmanGroupId()) throw conflict();
        var target = request.getTargets(0);
        long user = local.candidateUserId(), group = local.headmanGroupId();
        for (String role : List.of("STUDENT", "HEADMAN"))
            if (target.getDesiredGrantsList().stream()
                            .filter(
                                    g ->
                                            g.getRole().equals(role)
                                                    && g.getStatus().equals("ACTIVE")
                                                    && g.hasGroupId()
                                                    && g.getGroupId() == group)
                            .count()
                    != 1) throw conflict();
        if (target.getUserId() != user
                || target.hasTelegramChange()
                || local.profile().userId() != user
                || !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM groups WHERE id=? AND is_active AND"
                                        + " academic_revision=?)",
                                Boolean.class,
                                group,
                                local.groups().getFirst().expectedRevision()))) throw conflict();
        var profiles =
                jdbc.query(
                        """
SELECT id,last_name,first_name,middle_name,email,phone,employee_number,avatar_id,directory_status
 FROM academic_profiles WHERE id=? AND archived_at IS NULL AND academic_revision=? FOR UPDATE
""",
                        (rs, n) ->
                                new AcademicIdentityLocalCommand.Profile(
                                        rs.getLong("id"),
                                        rs.getString("last_name"),
                                        rs.getString("first_name"),
                                        rs.getString("middle_name"),
                                        rs.getString("email"),
                                        rs.getString("phone"),
                                        rs.getString("employee_number"),
                                        rs.getString("avatar_id"),
                                        rs.getString("directory_status")),
                        user,
                        target.getExpectedAcademicRevision());
        if (profiles.size() != 1 || !profiles.getFirst().equals(local.profile())) throw conflict();
        var headmen =
                jdbc.queryForList(
                        "SELECT user_id FROM academic_role_eligibility WHERE role='HEADMAN' AND"
                                + " status='ACTIVE' AND group_id=? ORDER BY user_id",
                        Long.class,
                        group);
        if (!headmen.equals(List.of(user))
                || !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
SELECT EXISTS(SELECT 1 FROM academic_role_eligibility WHERE user_id=? AND role='STUDENT' AND status='ACTIVE' AND group_id=?)
 AND NOT EXISTS(SELECT 1 FROM academic_identity_operation_groups WHERE group_id=? AND fenced)
 AND NOT EXISTS(SELECT 1 FROM academic_identity_operation_targets WHERE user_id=? AND fenced)
""",
                                Boolean.class,
                                user,
                                group,
                                group,
                                user))) throw conflict();
    }

    @Transactional(readOnly = true)
    public AcademicIdentityCoordinator.AcceptedCommand acceptedForActor(
            String operationId, AcademicIdentityLocalCommand.ApiIntent intent) {
        var actor = context.getClaims();
        if (actor == null || actor.userId() <= 0) throw Status.UNAUTHENTICATED.asRuntimeException();
        requireNotGroupOperation(uuid(operationId), actor.userId());
        requireNotLocallyRejected(uuid(operationId), intent);
        var commands =
                jdbc.query(
                        "SELECT actor_id,command_payload,local_command_hash,local_payload FROM"
                                + " academic_identity_operations WHERE operation_id=?",
                        (rs, n) -> {
                            if (rs.getLong("actor_id") != actor.userId())
                                throw Status.NOT_FOUND.asRuntimeException();
                            byte[] payload = rs.getBytes("local_payload");
                            var saved = local(payload).intent();
                            // Originating capability is immutable admission evidence, not a new
                            // public request field. Replay old rows with no provenance byteexact.
                            if (!saved.action().equals(intent.action())
                                    || saved.targetId() != intent.targetId()
                                    || !saved.requestDigest().equals(intent.requestDigest()))
                                throw Status.ALREADY_EXISTS
                                        .withDescription(
                                                "operation key already bound to another request")
                                        .asRuntimeException();
                            return new AcademicIdentityCoordinator.AcceptedCommand(
                                    command(rs.getBytes("command_payload")),
                                    com.google.protobuf.ByteString.copyFrom(
                                            rs.getBytes("local_command_hash")),
                                    com.google.protobuf.ByteString.copyFrom(payload));
                        },
                        uuid(operationId));
        return commands.isEmpty() ? null : commands.getFirst();
    }

    @Transactional(readOnly = true)
    public AcademicIdentityCoordinator.Operation findForActor(String operationId) {
        var actor = context.getClaims();
        if (actor == null || actor.userId() <= 0) throw Status.UNAUTHENTICATED.asRuntimeException();
        Boolean owner =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operations WHERE"
                                + " operation_id=? AND actor_id=?)",
                        Boolean.class,
                        uuid(operationId),
                        actor.userId());
        if (!Boolean.TRUE.equals(owner)) throw Status.NOT_FOUND.asRuntimeException();
        return load(uuid(operationId), false);
    }

    /** Same UUID lock orders negative admission against every delayed positive acceptance. */
    private void lockApiAttempt(UUID id) {
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 172903))",
                (rs, row) -> Boolean.TRUE,
                "academic-identity-api:" + id);
    }

    private void requireNotGroupOperation(UUID id, long actor) {
        var owners =
                jdbc.queryForList(
                        "SELECT actor_id FROM academic_group_availability_operations WHERE"
                                + " operation_id=?",
                        Long.class,
                        id);
        if (!owners.isEmpty()) {
            if (owners.getFirst() == null || owners.getFirst() != actor)
                throw Status.NOT_FOUND.asRuntimeException();
            throw Status.ALREADY_EXISTS
                    .withDescription("operation key already bound to group lifecycle")
                    .asRuntimeException();
        }
    }

    private boolean locallyRejected(UUID id, AcademicIdentityLocalCommand.ApiIntent intent) {
        var actor = context.getClaims();
        if (actor == null || actor.userId() <= 0) throw Status.UNAUTHENTICATED.asRuntimeException();
        var matches =
                jdbc.query(
                        "SELECT actor_id,action,target_id,request_digest FROM"
                                + " academic_identity_preflight_rejections WHERE operation_id=?",
                        (rs, row) -> {
                            if (rs.getLong("actor_id") != actor.userId())
                                throw Status.NOT_FOUND.asRuntimeException();
                            if (intent != null
                                    && (!intent.action().equals(rs.getString("action"))
                                            || intent.targetId() != rs.getLong("target_id")
                                            || !intent.requestDigest()
                                                    .equals(rs.getString("request_digest"))))
                                throw Status.ALREADY_EXISTS
                                        .withDescription(
                                                "operation key already bound to another request")
                                        .asRuntimeException();
                            return Boolean.TRUE;
                        },
                        id);
        return !matches.isEmpty();
    }

    private void requireNotLocallyRejected(UUID id, AcademicIdentityLocalCommand.ApiIntent intent) {
        if (locallyRejected(id, intent))
            throw Status.FAILED_PRECONDITION
                    .withDescription("local preflight attempt rejected")
                    .asRuntimeException();
    }

    @Transactional(readOnly = true)
    public void checkLocalRejection(
            String operationId, AcademicIdentityLocalCommand.ApiIntent intent) {
        requireNotLocallyRejected(uuid(operationId), intent);
    }

    /** True only after an immutable no-effect receipt wins the shared UUID serialization. */
    @Transactional
    public boolean rejectPreflight(
            String operationId, AcademicIdentityLocalCommand.ApiIntent intent) {
        UUID id = uuid(operationId);
        lockApiAttempt(id);
        var actor = context.getClaims();
        if (actor == null || actor.userId() <= 0) throw Status.UNAUTHENTICATED.asRuntimeException();
        var groupWinner =
                jdbc.query(
                        "SELECT actor_id,api_action,api_target_id,api_request_digest FROM"
                                + " academic_group_availability_operations WHERE operation_id=?",
                        (rs, n) -> {
                            Long owner = rs.getObject("actor_id", Long.class);
                            if (owner == null || owner != actor.userId())
                                throw Status.NOT_FOUND.asRuntimeException();
                            if (!intent.equals(
                                    new AcademicIdentityLocalCommand.ApiIntent(
                                            rs.getString("api_action"),
                                            rs.getLong("api_target_id"),
                                            rs.getString("api_request_digest"))))
                                throw Status.ALREADY_EXISTS
                                        .withDescription(
                                                "operation key already bound to another request")
                                        .asRuntimeException();
                            return Boolean.TRUE;
                        },
                        id);
        if (!groupWinner.isEmpty()) return false;
        // An accepted winner is authoritative even if this caller failed its own stale preflight.
        var positive =
                jdbc.query(
                        "SELECT actor_id,local_payload FROM academic_identity_operations WHERE"
                                + " operation_id=?",
                        (rs, row) -> {
                            if (rs.getLong("actor_id") != actor.userId())
                                throw Status.NOT_FOUND.asRuntimeException();
                            if (!local(rs.getBytes("local_payload")).intent().equals(intent))
                                throw Status.ALREADY_EXISTS
                                        .withDescription(
                                                "operation key already bound to another request")
                                        .asRuntimeException();
                            return Boolean.TRUE;
                        },
                        id);
        if (!positive.isEmpty()) return false;
        if (!locallyRejected(id, intent))
            jdbc.update(
                    "INSERT INTO"
                        + " academic_identity_preflight_rejections(operation_id,actor_id,action,target_id,request_digest)"
                        + " VALUES(?,?,?,?,?)",
                    id,
                    actor.userId(),
                    intent.action(),
                    intent.targetId(),
                    intent.requestDigest());
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isGroupPreflight(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_preflight_rejections WHERE"
                            + " operation_id=? AND action IN"
                            + " ('GROUP_DEACTIVATE','GROUP_RESTORE','GROUP_MANUAL_CYCLE','GROUP_AUTOMATIC_CYCLE','GROUP_RECONCILE'))",
                        Boolean.class,
                        id));
    }

    @Transactional(readOnly = true)
    public IdentityOperationHttpModels.Operation publicStatusForActor(String operationId) {
        UUID id = uuid(operationId);
        if (locallyRejected(id, null))
            return new ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels
                    .Operation(id, IdentityOperationHttpModels.Status.REJECTED, 0);
        return AcademicIdentityHttpOperations.publicStatus(findForActor(operationId));
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicIdentityCoordinator.Operation find(String id) {
        return load(uuid(id), false);
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation prepared(
            AcademicIdentityCoordinator.Operation expected, IdentityOperationResult prepared) {
        UUID id = uuid(expected.id());
        var current = load(id, true);
        if (current.phase() != AcademicIdentityCoordinator.Phase.ACCEPTED) return current;
        // Serialize pending-fence installation with assignment admission/final activation.
        // Match APPLY's operation -> profile order; CREATE has no local profile yet.
        for (long userId :
                prepared.getTargetsList().stream()
                        .mapToLong(target -> target.getUserId())
                        .distinct()
                        .sorted()
                        .toArray()) {
            jdbc.queryForList(
                    "SELECT id FROM academic_profiles WHERE id=? FOR UPDATE", Long.class, userId);
        }
        for (int n = 0; n < prepared.getTargetsCount(); n++) {
            var target = prepared.getTargets(n);
            if (jdbc.update(
                            "UPDATE academic_identity_operation_targets SET user_id=?,fenced=true"
                                + " WHERE operation_id=? AND target_index=? AND (user_id IS NULL OR"
                                + " user_id=?)",
                            target.getUserId(),
                            id,
                            n,
                            target.getUserId())
                    != 1) throw conflict();
        }
        jdbc.update(
                "UPDATE academic_identity_operation_groups SET fenced=true WHERE operation_id=?",
                id);
        if (jdbc.update(
                        "UPDATE academic_identity_operations SET"
                            + " phase='AUTH_PREPARED',auth_operation_revision=?,auth_prepared_payload=?,revision=revision+1,updated_at=now()"
                            + " WHERE operation_id=? AND phase='ACCEPTED'",
                        prepared.getOperationRevision(),
                        prepared.toByteArray(),
                        id)
                != 1) throw conflict();
        return load(id, false);
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation apply(
            AcademicIdentityCoordinator.Operation expected) {
        UUID id = uuid(expected.id());
        lockReconciliationScopes(id);
        var current = load(id, true);
        if (current.phase() != AcademicIdentityCoordinator.Phase.AUTH_PREPARED) return current;
        byte[] payload =
                jdbc.queryForObject(
                        "SELECT local_payload FROM academic_identity_operations WHERE"
                                + " operation_id=?",
                        byte[].class,
                        id);
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.identity_operation_id',?,true)",
                String.class,
                id.toString());
        var localCommand = local(payload);
        long acceptedActor =
                jdbc.queryForObject(
                        "SELECT actor_id FROM academic_identity_operations WHERE operation_id=?",
                        Long.class,
                        id);
        var reconciliation = reconciliationLink(id);
        var effectiveKind =
                reconciliation == null ? current.command().getKind() : reconciliation.kind();
        boolean archive =
                effectiveKind
                        == ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                .IDENTITY_OPERATION_KIND_ARCHIVE;
        boolean restore =
                effectiveKind
                        == ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                .IDENTITY_OPERATION_KIND_RESTORE;
        if (archive) {
            if (current.command().getTargetsCount() != 1
                    || localCommand.archiveAdmission() == null
                    || !current.command()
                            .getArchivePreviewDigest()
                            .equals(localCommand.archiveAdmission().previewDigest()))
                throw conflict();
            if (reconciliation != null && reconciliation.businessApplied()) {
                if (!observeArchiveWithinRecoveryGuard(
                                id, current.command().getTargets(0).getUserId())
                        .digest()
                        .equals(localCommand.archiveAdmission().expectedLocalDigest()))
                    throw conflict();
            } else {
                if (reconciliation != null) requireNoForeignRegistryGuard(id);
                archives.verifyAcceptedArchive(
                        current.command().getTargets(0).getUserId(), localCommand);
                if (reconciliation != null) requireNoForeignRegistryGuard(id);
            }
        }
        var receipts =
                reconciliation != null && reconciliation.businessApplied()
                        ? applyReconciledRevisions(current, reconciliation)
                        : writer.apply(
                                localCommand,
                                effectiveKind,
                                current.prepared(),
                                jdbc.queryForObject(
                                        "SELECT actor_id FROM academic_identity_operations WHERE"
                                                + " operation_id=?",
                                        Long.class,
                                        id));
        if (archive || restore) {
            if (receipts.size() != 1) throw conflict();
            archives.saveReceipt(
                    id,
                    acceptedActor,
                    receipts.getFirst().getUserId(),
                    archive ? "ARCHIVE" : "RESTORE",
                    archive ? localCommand.archiveAdmission().previewDigest() : null,
                    archive && localCommand.archiveAdmission().requiresPassword());
        }
        for (var receipt : receipts) {
            if (jdbc.update(
                            """
UPDATE academic_identity_operation_targets SET committed_academic_revision=?,applied_auth_roles_version=?
 WHERE operation_id=? AND user_id=? AND previous_academic_revision=? AND committed_academic_revision IS NULL AND fenced
""",
                            receipt.getCommittedAcademicRevision(),
                            receipt.getAppliedAuthRolesVersion(),
                            id,
                            receipt.getUserId(),
                            receipt.getPreviousAcademicRevision())
                    != 1) throw conflict();
        }
        if (jdbc.update(
                        "UPDATE academic_identity_operations SET"
                            + " phase='LOCAL_COMMITTED',revision=revision+1,updated_at=now(),last_failure=NULL"
                            + " WHERE operation_id=? AND phase='AUTH_PREPARED'",
                        id)
                != 1) throw conflict();
        evictAfterCommit(id);
        return load(id, false);
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation nooped(
            AcademicIdentityCoordinator.Operation expected, IdentityOperationResult nooped) {
        UUID id = uuid(expected.id());
        var current = load(id, true);
        AcademicIdentityCoordinator.validateNoop(current, nooped);
        if (current.phase() == AcademicIdentityCoordinator.Phase.SUCCEEDED
                && nooped.equals(current.prepared())) return current;
        if (current.phase() != AcademicIdentityCoordinator.Phase.ACCEPTED
                || !current.command().equals(expected.command())
                || !current.receipts().isEmpty()) throw conflict();
        Boolean untouched =
                jdbc.queryForObject(
                        "SELECT NOT EXISTS(SELECT 1 FROM academic_identity_operation_targets WHERE"
                            + " operation_id=? AND (fenced OR committed_academic_revision IS NOT"
                            + " NULL OR applied_auth_roles_version IS NOT NULL)) AND NOT"
                            + " EXISTS(SELECT 1 FROM academic_identity_operation_groups WHERE"
                            + " operation_id=? AND fenced)",
                        Boolean.class,
                        id,
                        id);
        if (!Boolean.TRUE.equals(untouched)) throw conflict();
        if (jdbc.update(
                        "UPDATE academic_identity_operations SET"
                            + " phase='SUCCEEDED',auth_operation_revision=?,auth_prepared_payload=?,headman_assistants_revoked=CASE"
                            + " WHEN ? THEN 0 ELSE headman_assistants_revoked"
                            + " END,revision=revision+1,updated_at=now(),last_failure=NULL,next_attempt_at=NULL"
                            + " WHERE operation_id=? AND phase='ACCEPTED'",
                        nooped.getOperationRevision(),
                        nooped.toByteArray(),
                        current.command().getHeadmanNoopCandidate(),
                        id)
                != 1) throw conflict();
        // ACCEPTED target/group rows remain immutable evidence with fenced=false. No APPLY,
        // profile/history/outbox/cache change or catalog receipt is manufactured for a no-op.
        return load(id, false);
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation succeeded(
            AcademicIdentityCoordinator.Operation expected, IdentityOperationResult committed) {
        UUID id = uuid(expected.id());
        var current = load(id, true);
        if (current.phase() == AcademicIdentityCoordinator.Phase.SUCCEEDED) return current;
        if (current.phase() != AcademicIdentityCoordinator.Phase.LOCAL_COMMITTED
                || committed.getCommittedCatalogVersion() <= 0) throw conflict();
        Long frontier =
                jdbc.queryForObject(
                        "SELECT applied_catalog_version FROM academic_identity_catalog_cursor WHERE"
                                + " singleton",
                        Long.class);
        if (frontier == null || frontier < committed.getCommittedCatalogVersion()) {
            throw Status.UNAVAILABLE
                    .withDescription("identity catalog catch-up pending")
                    .asRuntimeException();
        }
        if (jdbc.update(
                        "UPDATE academic_identity_operations SET"
                            + " phase='SUCCEEDED',auth_operation_revision=?,auth_prepared_payload=?,committed_catalog_version=?,revision=revision+1,updated_at=now(),last_failure=NULL"
                            + " WHERE operation_id=? AND phase='LOCAL_COMMITTED'",
                        committed.getOperationRevision(),
                        committed.toByteArray(),
                        committed.getCommittedCatalogVersion(),
                        id)
                != 1) throw conflict();
        jdbc.update(
                "UPDATE academic_identity_operation_targets SET fenced=false WHERE operation_id=?",
                id);
        jdbc.update(
                "UPDATE academic_identity_operation_groups SET fenced=false WHERE operation_id=?",
                id);
        evictAfterCommit(id);
        caches.afterPublicationCommit();
        return load(id, false);
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation failedSafe(
            AcademicIdentityCoordinator.Operation expected, IdentityOperationResult failed) {
        UUID id = uuid(expected.id());
        var current = load(id, true);
        if (current.phase() == AcademicIdentityCoordinator.Phase.SUCCEEDED
                || current.phase() == AcademicIdentityCoordinator.Phase.REJECTED) throw conflict();
        jdbc.update(
                "UPDATE academic_identity_operations SET"
                    + " phase='FAILED_SAFE',auth_operation_revision=?,auth_prepared_payload=?,last_failure='AUTH_CONFLICT',revision=revision+1,updated_at=now()"
                    + " WHERE operation_id=?",
                failed.getOperationRevision(),
                failed.toByteArray(),
                id);
        return load(id, false);
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation rejectedBeforeCommit(
            AcademicIdentityCoordinator.Operation expected) {
        UUID id = uuid(expected.id());
        var current = load(id, true);
        if (current.phase() != AcademicIdentityCoordinator.Phase.AUTH_PREPARED) return current;
        jdbc.update(
                "UPDATE academic_identity_operations SET"
                    + " phase='FAILED_SAFE',last_failure='LOCAL_REJECTED',revision=revision+1,updated_at=now()"
                    + " WHERE operation_id=? AND phase='AUTH_PREPARED'",
                id);
        return load(id, false);
    }

    @Override
    @Transactional
    public RejectUnpreparedIdentityOperationRequest prepareRejection(
            AcademicIdentityCoordinator.Operation expected,
            IdentityOperationRejectionReason reason) {
        return prepareRejection(expected, reason, null);
    }

    @Override
    @Transactional
    public RejectUnpreparedIdentityOperationRequest prepareRejection(
            AcademicIdentityCoordinator.Operation expected,
            IdentityOperationRejectionReason reason,
            String code) {
        if (code != null
                && (!"TELEGRAM_ID_CONFLICT".equals(code)
                        || reason
                                != IdentityOperationRejectionReason
                                        .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT))
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        UUID id = uuid(expected.id());
        if (load(id, true).phase() != AcademicIdentityCoordinator.Phase.ACCEPTED) return null;
        String value =
                switch (reason) {
                    case IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED -> "ACTOR_DENIED";
                    case IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT -> "TARGET_CONFLICT";
                    case IDENTITY_OPERATION_REJECTION_REASON_ACADEMIC_REJECTED ->
                            "ACADEMIC_REJECTED";
                    default -> throw Status.INVALID_ARGUMENT.asRuntimeException();
                };
        jdbc.update(
                "UPDATE academic_identity_operations SET rejection_code=CASE WHEN rejection_reason"
                        + " IS NULL THEN ? ELSE rejection_code END,"
                        + " rejection_reason=coalesce(rejection_reason,?),updated_at=now() WHERE"
                        + " operation_id=? AND phase='ACCEPTED'",
                code,
                value,
                id);
        return rejectionRequest(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RejectUnpreparedIdentityOperationRequest pendingRejection(
            AcademicIdentityCoordinator.Operation expected) {
        return rejectionRequest(uuid(expected.id()));
    }

    private RejectUnpreparedIdentityOperationRequest rejectionRequest(UUID id) {
        var requests =
                jdbc.query(
                        "SELECT"
                            + " command_payload,rejection_reason,actor_id,actor_session_id,actor_session_version,actor_roles_version"
                            + " FROM academic_identity_operations WHERE operation_id=? AND"
                            + " phase='ACCEPTED' AND rejection_reason IS NOT NULL",
                        (rs, n) ->
                                RejectUnpreparedIdentityOperationRequest.newBuilder()
                                        .setCanonicalPrepare(
                                                command(rs.getBytes("command_payload")))
                                        .setReason(
                                                IdentityOperationRejectionReason.valueOf(
                                                        "IDENTITY_OPERATION_REJECTION_REASON_"
                                                                + rs.getString("rejection_reason")))
                                        .setActor(
                                                IdentityOperationActor.newBuilder()
                                                        .setUserId(rs.getLong("actor_id"))
                                                        .setSessionId(
                                                                rs.getString("actor_session_id"))
                                                        .setSessionVersion(
                                                                rs.getLong("actor_session_version"))
                                                        .setRolesVersion(
                                                                rs.getLong("actor_roles_version")))
                                        .build(),
                        id);
        return requests.isEmpty() ? null : requests.getFirst();
    }

    @Override
    @Transactional
    public AcademicIdentityCoordinator.Operation rejected(
            AcademicIdentityCoordinator.Operation expected, IdentityOperationResult rejected) {
        UUID id = uuid(expected.id());
        lockReconciliationScopes(id);
        var current = load(id, true);
        if (current.phase() != AcademicIdentityCoordinator.Phase.ACCEPTED) return current;
        jdbc.update(
                "UPDATE academic_identity_operations SET"
                    + " phase='REJECTED',auth_operation_revision=?,auth_prepared_payload=?,revision=revision+1,updated_at=now(),next_attempt_at=NULL"
                    + " WHERE operation_id=? AND phase='ACCEPTED'",
                rejected.getOperationRevision(),
                rejected.toByteArray(),
                id);
        returnRejectedReconciliationGuard(id);
        // Normal ACCEPTED has no guards; reconciliation returned its transferred guards above.
        // Auth's immutable negative reservation excludes a delayed positive PREPARE.
        return load(id, false);
    }

    @Transactional(readOnly = true)
    public List<String> pending() {
        return jdbc.queryForList(
                """
                SELECT operation_id::text FROM academic_identity_operations
                 WHERE (phase IN ('ACCEPTED','AUTH_PREPARED','LOCAL_COMMITTED')
                   OR phase='FAILED_SAFE' AND last_failure='LOCAL_REJECTED')
                   AND (next_attempt_at IS NULL OR next_attempt_at<=now())
                 ORDER BY created_at,operation_id LIMIT 20
                """,
                String.class);
    }

    @Transactional
    public void retryLater(String id) {
        jdbc.update(
                "UPDATE academic_identity_operations SET"
                        + " attempt_count=attempt_count+1,next_attempt_at=now()+interval '30"
                        + " seconds',last_failure=CASE WHEN last_failure='LOCAL_REJECTED' THEN"
                        + " last_failure ELSE 'RECOVERY_REQUIRED' END WHERE operation_id=? AND"
                        + " phase NOT IN ('SUCCEEDED','REJECTED')",
                uuid(id));
    }

    private void evictAfterCommit(UUID id) {
        var users =
                jdbc.queryForList(
                        "SELECT user_id FROM academic_identity_operation_targets WHERE"
                                + " operation_id=? AND user_id IS NOT NULL ORDER BY target_index",
                        Long.class,
                        id);
        var groups =
                jdbc.queryForList(
                        "SELECT group_id FROM academic_identity_operation_groups WHERE"
                                + " operation_id=? ORDER BY group_id",
                        Long.class,
                        id);
        caches.afterCommit(users, groups);
    }

    /** Private saved material, never a user authority or a public payload. */
    public record RecoverySource(
            UUID failedId,
            UUID originalId,
            ru.rutcampustrack.authoperations.grpc.IdentityOperationKind kind,
            boolean businessApplied,
            AcademicIdentityLocalCommand originalLocal,
            com.google.protobuf.ByteString originalLocalHash,
            AcademicIdentityCoordinator.Operation failed,
            java.util.Map<Long, Long> revisions) {}

    private record ReconciliationLink(
            UUID failedId,
            UUID originalId,
            ru.rutcampustrack.authoperations.grpc.IdentityOperationKind kind,
            boolean businessApplied,
            com.google.protobuf.ByteString originalLocalHash) {}

    public record RecoveryAuthorization(
            ru.rutcampustrack.authoperations.grpc.IdentityOperationKind kind,
            String originatingPermission,
            boolean telegramChange) {}

    @Transactional(readOnly = true)
    public RecoveryAuthorization reconciliationAuthorization(UUID failedId) {
        var failed = load(failedId, false);
        var link = reconciliationLink(failedId);
        var original = link == null ? failed : load(link.originalId(), false);
        byte[] payload =
                jdbc.queryForObject(
                        "SELECT local_payload FROM academic_identity_operations WHERE"
                                + " operation_id=?",
                        byte[].class,
                        uuid(original.id()));
        var domain = local(payload);
        return new RecoveryAuthorization(
                original.command().getKind(),
                domain.intent().admissionPermission(),
                original.command().getTargetsList().stream()
                        .anyMatch(
                                ru.rutcampustrack.authoperations.grpc.IdentityTargetCommand
                                        ::hasTelegramChange));
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public RecoverySource reconciliationSource(UUID failedId) {
        return recoverySource(failedId, false);
    }

    private RecoverySource recoverySource(UUID failedId, boolean lock) {
        var failed = load(failedId, lock);
        if (failed.phase() != AcademicIdentityCoordinator.Phase.FAILED_SAFE
                || failed.prepared() == null
                || failed.prepared().getPhase()
                        != ru.rutcampustrack.authoperations.grpc.IdentityOperationPhase
                                .IDENTITY_OPERATION_PHASE_FAILED_SAFE)
            throw Status.FAILED_PRECONDITION
                    .withDescription("confirmed failed-safe operation required")
                    .asRuntimeException();
        var successors =
                jdbc.queryForList(
                        "SELECT operation_id FROM academic_identity_reconciliations WHERE"
                                + " failed_operation_id=? AND active",
                        UUID.class,
                        failedId);
        if (!successors.isEmpty())
            throw Status.FAILED_PRECONDITION
                    .withDescription("failed operation already has a reconciliation attempt")
                    .asRuntimeException();
        var link = reconciliationLink(failedId);
        UUID original = link == null ? failedId : link.originalId();
        var root = load(original, false);
        var kind = link == null ? root.command().getKind() : link.kind();
        if (kind
                == ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                        .IDENTITY_OPERATION_KIND_RECONCILE)
            throw Status.UNAVAILABLE.asRuntimeException();
        byte[] payload =
                jdbc.queryForObject(
                        "SELECT local_payload FROM academic_identity_operations WHERE"
                                + " operation_id=?",
                        byte[].class,
                        original);
        byte[] hash =
                jdbc.queryForObject(
                        "SELECT local_command_hash FROM academic_identity_operations WHERE"
                                + " operation_id=?",
                        byte[].class,
                        original);
        new AcademicIdentityCoordinator.AcceptedCommand(
                root.command(),
                com.google.protobuf.ByteString.copyFrom(hash),
                com.google.protobuf.ByteString.copyFrom(payload));
        if (link != null
                && (link.kind() != root.command().getKind()
                        || !link.originalLocalHash()
                                .equals(com.google.protobuf.ByteString.copyFrom(hash))))
            throw conflict();
        if (!failed.receipts().isEmpty()) requireCompleteReceipts(failed);
        boolean applied = !failed.receipts().isEmpty() || link != null && link.businessApplied();
        if (applied) requireBusinessReceiptEvidence(original, failed.prepared().getTargetsList());
        var revisions = new java.util.TreeMap<Long, Long>();
        for (int index = 0; index < failed.prepared().getTargetsCount(); index++) {
            long user = failed.prepared().getTargets(index).getUserId();
            long revision =
                    failed.receipts().isEmpty()
                            ? failed.command().getTargets(index).getExpectedAcademicRevision()
                            : failed.receipts().get(index).getCommittedAcademicRevision();
            if (user <= 0 || revisions.put(user, revision) != null) throw conflict();
        }
        return new RecoverySource(
                failedId,
                original,
                kind,
                applied,
                local(payload),
                com.google.protobuf.ByteString.copyFrom(hash),
                failed,
                java.util.Map.copyOf(revisions));
    }

    private ReconciliationLink reconciliationLink(UUID operation) {
        var links =
                jdbc.query(
                        "SELECT"
                            + " failed_operation_id,original_operation_id,authorization_kind,business_effects_committed,original_local_command_hash"
                            + " FROM academic_identity_reconciliations WHERE operation_id=? AND"
                            + " active",
                        (rs, n) ->
                                new ReconciliationLink(
                                        rs.getObject(1, UUID.class),
                                        rs.getObject(2, UUID.class),
                                        ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                                .valueOf(
                                                        "IDENTITY_OPERATION_KIND_"
                                                                + rs.getString(3)),
                                        rs.getBoolean(4),
                                        com.google.protobuf.ByteString.copyFrom(rs.getBytes(5))),
                        operation);
        return links.isEmpty() ? null : links.getFirst();
    }

    /** Exact own snapshot/locks; the only permitted pending scope is the saved failed operation. */
    private void requireRecoveryScope(RecoverySource source, boolean lock) {
        for (var group : source.originalLocal().groups()) {
            var revisions =
                    jdbc.queryForList(
                            "SELECT academic_revision FROM groups WHERE id=?"
                                    + (lock ? " FOR UPDATE" : ""),
                            Long.class,
                            group.groupId());
            long expected = group.expectedRevision() + (source.businessApplied() ? 1 : 0);
            if (revisions.size() != 1 || revisions.getFirst() != expected) throw conflict();
            Boolean foreign =
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM academic_identity_operation_groups WHERE"
                                + " group_id=? AND fenced AND operation_id<>?) OR EXISTS(SELECT 1"
                                + " FROM academic_group_availability_guards WHERE group_id=?)",
                            Boolean.class,
                            group.groupId(),
                            source.failedId(),
                            group.groupId());
            if (!Boolean.FALSE.equals(foreign)) throw Status.UNAVAILABLE.asRuntimeException();
        }
        for (var entry : new java.util.TreeMap<>(source.revisions()).entrySet()) {
            var revisions =
                    jdbc.queryForList(
                            "SELECT academic_revision FROM academic_profiles WHERE id=?"
                                    + (lock ? " FOR UPDATE" : ""),
                            Long.class,
                            entry.getKey());
            if (entry.getValue() == 0
                    ? !revisions.isEmpty()
                    : revisions.size() != 1 || !entry.getValue().equals(revisions.getFirst()))
                throw conflict();
            Boolean foreign =
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM academic_identity_operation_targets WHERE"
                                + " user_id=? AND fenced AND operation_id<>?) OR EXISTS(SELECT 1"
                                + " FROM academic_group_availability_headmen WHERE user_id=?)",
                            Boolean.class,
                            entry.getKey(),
                            source.failedId(),
                            entry.getKey());
            if (!Boolean.FALSE.equals(foreign)) throw Status.UNAVAILABLE.asRuntimeException();
        }
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ru.rutcampustrack.academic.user.UserArchiveRepository.Local observeReconciliationArchive(
            RecoverySource expected) {
        var source = recoverySource(expected.failedId(), false);
        if (source.kind()
                        != ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                .IDENTITY_OPERATION_KIND_ARCHIVE
                || !source.equals(expected)
                || source.revisions().size() != 1) throw conflict();
        requireRecoveryScope(source, false);
        var observed =
                observeArchiveWithinRecoveryGuard(
                        source.failedId(), source.revisions().keySet().iterator().next());
        return new ru.rutcampustrack.academic.user.UserArchiveRepository.Local(
                observed.userId(),
                observed.digest(),
                observed.status(),
                observed.groupCount(),
                observed.headmanCount(),
                observed.soleTeacherCount(),
                observed.requiresPassword()
                        || source.originalLocal().archiveAdmission().requiresPassword(),
                observed.observedAt());
    }

    /** Called only within the caller's own snapshot/locked acceptance or APPLY transaction. */
    ru.rutcampustrack.academic.user.UserArchiveRepository.Local observeArchiveWithinRecoveryGuard(
            UUID allowedOperation, long target) {
        requireNoForeignRegistryGuard(allowedOperation);
        var observed = archives.observe(target);
        // Acceptance/APPLY use READ_COMMITTED with the target/group rows locked. A new
        // unrelated guard installed during the aggregate must still win over an unchanged digest.
        requireNoForeignRegistryGuard(allowedOperation);
        return observed;
    }

    private void requireNoForeignRegistryGuard(UUID allowedOperation) {
        // This remains an unscoped administrative aggregate: all other guards deny it.
        Boolean foreign =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operation_targets WHERE"
                                + " fenced AND operation_id<>?) OR EXISTS(SELECT 1 FROM"
                                + " academic_identity_operation_groups WHERE fenced AND"
                                + " operation_id<>?) OR EXISTS(SELECT 1 FROM"
                                + " academic_group_availability_guards) OR EXISTS(SELECT 1 FROM"
                                + " academic_group_availability_headmen)",
                        Boolean.class,
                        allowedOperation,
                        allowedOperation);
        if (!Boolean.FALSE.equals(foreign)) throw Status.UNAVAILABLE.asRuntimeException();
    }

    private RecoverySource validateReconciliation(
            PrepareIdentityOperationRequest request, AcademicIdentityLocalCommand retry) {
        if (request.getTargetsCount() == 0 || !"RECONCILE".equals(retry.intent().action()))
            throw conflict();
        UUID failedId = uuid(request.getTargets(0).getSupersedesFailedOperationId());
        var source = recoverySource(failedId, false);
        if (uuid(request.getOperationId()).equals(failedId)
                || request.getTargetsCount() != source.revisions().size()
                || retry.intent().admissionPermission() != null
                || !source.originalLocal()
                        .forReconciliation(retry.intent(), retry.archiveAdmission())
                        .equals(retry)) throw conflict();
        if ((source.kind()
                        == ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                                .IDENTITY_OPERATION_KIND_ARCHIVE)
                != (retry.archiveAdmission() != null)) throw conflict();
        requireRecoveryScope(source, true);
        load(failedId, true);
        if (!source.equals(recoverySource(failedId, false))) throw conflict();
        for (var target : request.getTargetsList()) {
            if (!failedId.toString().equals(target.getSupersedesFailedOperationId())
                    || !source.revisions().containsKey(target.getUserId())
                    || source.revisions().get(target.getUserId())
                            != target.getExpectedAcademicRevision()) throw conflict();
        }
        if (retry.archiveAdmission() != null) {
            long target = request.getTargets(0).getUserId();
            var observed = observeArchiveWithinRecoveryGuard(source.failedId(), target);
            archives.validatePreview(
                    context.getClaims().userId(),
                    target,
                    retry.archiveAdmission().previewDigest(),
                    observed);
            if (!observed.digest().equals(retry.archiveAdmission().expectedLocalDigest())
                    || source.originalLocal().archiveAdmission().requiresPassword()
                            && !retry.archiveAdmission().requiresPassword()
                    || request.getArchiveStepUpProtocolVersion() != 1
                    || !request.getArchivePreviewDigest()
                            .equals(retry.archiveAdmission().previewDigest())
                    || retry.archiveAdmission().requiresPassword()
                            && request.getPasswordStepUpReceiptId().isBlank()) throw conflict();
        }
        return source;
    }

    private void transferReconciliationGuard(UUID operation, RecoverySource source) {
        jdbc.update(
                "INSERT INTO"
                    + " academic_identity_reconciliations(operation_id,failed_operation_id,original_operation_id,original_local_command_hash,authorization_kind,business_effects_committed)"
                    + " VALUES(?,?,?,?,?,?)",
                operation,
                source.failedId(),
                source.originalId(),
                source.originalLocalHash().toByteArray(),
                source.kind().name().substring("IDENTITY_OPERATION_KIND_".length()),
                source.businessApplied());
        jdbc.update(
                "UPDATE academic_identity_operation_targets SET fenced=false WHERE operation_id=?",
                source.failedId());
        jdbc.update(
                "UPDATE academic_identity_operation_groups SET fenced=false WHERE operation_id=?",
                source.failedId());
        jdbc.update(
                "UPDATE academic_identity_operation_targets SET fenced=true WHERE operation_id=?",
                operation);
        jdbc.update(
                "UPDATE academic_identity_operation_groups SET fenced=true WHERE operation_id=?",
                operation);
    }

    private void returnRejectedReconciliationGuard(UUID operation) {
        var link = reconciliationLink(operation);
        if (link == null) return;
        var previous = load(link.failedId(), true);
        if (previous.phase() != AcademicIdentityCoordinator.Phase.FAILED_SAFE) throw conflict();
        jdbc.update(
                "UPDATE academic_identity_operation_targets SET fenced=false WHERE operation_id=?",
                operation);
        jdbc.update(
                "UPDATE academic_identity_operation_groups SET fenced=false WHERE operation_id=?",
                operation);
        jdbc.update(
                "UPDATE academic_identity_operation_targets SET fenced=true WHERE operation_id=?"
                        + " AND user_id IS NOT NULL",
                link.failedId());
        jdbc.update(
                "UPDATE academic_identity_operation_groups SET fenced=true WHERE operation_id=?",
                link.failedId());
        jdbc.update(
                "UPDATE academic_identity_reconciliations SET active=false WHERE operation_id=? AND"
                        + " active",
                operation);
    }

    private List<AcademicIdentityCommitReceipt> applyReconciledRevisions(
            AcademicIdentityCoordinator.Operation operation, ReconciliationLink link) {
        verifyStagedBusiness(link, operation);
        var receipts = new java.util.ArrayList<AcademicIdentityCommitReceipt>();
        for (var target : operation.prepared().getTargetsList()) {
            if (jdbc.update(
                            "UPDATE academic_profiles SET"
                                + " academic_revision=academic_revision+1,applied_auth_roles_version=?,updated_at=now()"
                                + " WHERE id=? AND academic_revision=?",
                            target.getFinalRolesVersion(),
                            target.getUserId(),
                            target.getExpectedAcademicRevision())
                    != 1) throw conflict();
            receipts.add(
                    AcademicIdentityCommitReceipt.newBuilder()
                            .setUserId(target.getUserId())
                            .setPreviousAcademicRevision(target.getExpectedAcademicRevision())
                            .setCommittedAcademicRevision(target.getExpectedAcademicRevision() + 1)
                            .setAppliedAuthRolesVersion(target.getFinalRolesVersion())
                            .build());
        }
        return List.copyOf(receipts);
    }

    private void verifyStagedBusiness(
            ReconciliationLink link, AcademicIdentityCoordinator.Operation operation) {
        byte[] payload =
                jdbc.queryForObject(
                        "SELECT local_payload FROM academic_identity_operations WHERE"
                                + " operation_id=?",
                        byte[].class,
                        link.originalId());
        var domain = local(payload);
        for (var group : domain.groups()) {
            var revisions =
                    jdbc.queryForList(
                            "SELECT academic_revision FROM groups WHERE id=? FOR UPDATE",
                            Long.class,
                            group.groupId());
            if (revisions.size() != 1 || revisions.getFirst() != group.expectedRevision() + 1)
                throw conflict();
        }
        requireBusinessReceiptEvidence(link.originalId(), operation.prepared().getTargetsList());
    }

    private void lockReconciliationScopes(UUID operation) {
        var link = reconciliationLink(operation);
        if (link == null) return;
        jdbc.queryForList(
                "SELECT g.id FROM groups g JOIN academic_identity_operation_groups f ON"
                        + " f.group_id=g.id WHERE f.operation_id=? ORDER BY g.id FOR UPDATE OF g",
                Long.class,
                operation);
        jdbc.queryForList(
                "SELECT p.id FROM academic_profiles p JOIN academic_identity_operation_targets f ON"
                        + " f.user_id=p.id WHERE f.operation_id=? ORDER BY p.id FOR UPDATE OF p",
                Long.class,
                operation);
        load(link.failedId(), true);
    }

    private void requireBusinessReceiptEvidence(
            UUID original,
            List<ru.rutcampustrack.authoperations.grpc.PreparedIdentityTarget> targets) {
        var candidates =
                jdbc.queryForList(
                        "SELECT operation_id FROM academic_identity_operations WHERE operation_id=?"
                            + " UNION SELECT operation_id FROM academic_identity_reconciliations"
                            + " WHERE original_operation_id=?",
                        UUID.class,
                        original,
                        original);
        for (UUID candidate : candidates) {
            var operation = load(candidate, false);
            if (operation.receipts().isEmpty()) continue;
            requireCompleteReceipts(operation);
            if (!operation.receipts().stream()
                    .map(AcademicIdentityCommitReceipt::getUserId)
                    .toList()
                    .equals(
                            targets.stream()
                                    .map(
                                            ru.rutcampustrack.authoperations.grpc
                                                            .PreparedIdentityTarget
                                                    ::getUserId)
                                    .toList())) throw conflict();
            return;
        }
        throw conflict();
    }

    static void requireCompleteReceipts(AcademicIdentityCoordinator.Operation operation) {
        if (operation.prepared() == null
                || operation.receipts().size() != operation.prepared().getTargetsCount())
            throw conflict();
        long previous = 0;
        for (int index = 0; index < operation.receipts().size(); index++) {
            var receipt = operation.receipts().get(index);
            var target = operation.prepared().getTargets(index);
            if (receipt.getUserId() <= previous
                    || receipt.getUserId() != target.getUserId()
                    || target.getExpectedAcademicRevision() < 0
                    || target.getFinalRolesVersion() <= 0
                    || receipt.getPreviousAcademicRevision() != target.getExpectedAcademicRevision()
                    || receipt.getCommittedAcademicRevision()
                            != receipt.getPreviousAcademicRevision() + 1
                    || receipt.getAppliedAuthRolesVersion() != target.getFinalRolesVersion())
                throw conflict();
            previous = receipt.getUserId();
        }
    }

    private AcademicIdentityCoordinator.Operation load(UUID id, boolean lock) {
        var operations =
                jdbc.query(
                        "SELECT phase,command_payload,auth_prepared_payload,rejection_code FROM"
                                + " academic_identity_operations WHERE operation_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, n) ->
                                new AcademicIdentityCoordinator.Operation(
                                        id.toString(),
                                        AcademicIdentityCoordinator.Phase.valueOf(
                                                rs.getString("phase")),
                                        command(rs.getBytes("command_payload")),
                                        prepared(rs.getBytes("auth_prepared_payload")),
                                        List.of(),
                                        rs.getString("rejection_code")),
                        id);
        if (operations.size() != 1)
            throw Status.NOT_FOUND
                    .withDescription("identity operation not found")
                    .asRuntimeException();
        var operation = operations.getFirst();
        var receipts =
                jdbc.query(
                        "SELECT"
                            + " user_id,previous_academic_revision,committed_academic_revision,applied_auth_roles_version"
                            + " FROM academic_identity_operation_targets WHERE operation_id=? AND"
                            + " committed_academic_revision IS NOT NULL ORDER BY target_index",
                        (rs, n) ->
                                AcademicIdentityCommitReceipt.newBuilder()
                                        .setUserId(rs.getLong("user_id"))
                                        .setPreviousAcademicRevision(
                                                rs.getLong("previous_academic_revision"))
                                        .setCommittedAcademicRevision(
                                                rs.getLong("committed_academic_revision"))
                                        .setAppliedAuthRolesVersion(
                                                rs.getLong("applied_auth_roles_version"))
                                        .build(),
                        id);
        return new AcademicIdentityCoordinator.Operation(
                operation.id(),
                operation.phase(),
                operation.command(),
                operation.prepared(),
                receipts,
                operation.rejectionCode());
    }

    private static void validateArchiveBinding(
            PrepareIdentityOperationRequest request, AcademicIdentityLocalCommand local) {
        if (request.getKind()
                == ru.rutcampustrack.authoperations.grpc.IdentityOperationKind
                        .IDENTITY_OPERATION_KIND_ARCHIVE) {
            if (request.getTargetsCount() != 1
                    || !"ARCHIVE".equals(local.intent().action())
                    || request.getTargets(0).getUserId() != local.intent().targetId()
                    || local.archiveAdmission() == null
                    || request.getArchiveStepUpProtocolVersion() != 1
                    || !request.getArchivePreviewDigest()
                            .equals(local.archiveAdmission().previewDigest())
                    || local.archiveAdmission().requiresPassword()
                            && request.getPasswordStepUpReceiptId().isBlank())
                throw Status.INVALID_ARGUMENT
                        .withDescription("exact archive admission required")
                        .asRuntimeException();
        }
    }

    private AcademicIdentityLocalCommand local(byte[] payload) {
        try {
            return mapper.readerFor(AcademicIdentityLocalCommand.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
        } catch (java.io.IOException invalid) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("invalid local identity command")
                    .asRuntimeException();
        }
    }

    private static PrepareIdentityOperationRequest command(byte[] payload) {
        try {
            return PrepareIdentityOperationRequest.parseFrom(payload);
        } catch (InvalidProtocolBufferException corrupt) {
            throw new IllegalStateException("invalid durable identity command");
        }
    }

    private static IdentityOperationResult prepared(byte[] payload) {
        if (payload == null) return null;
        try {
            return IdentityOperationResult.parseFrom(payload);
        } catch (InvalidProtocolBufferException corrupt) {
            throw new IllegalStateException("invalid durable identity result");
        }
    }

    private static String kind(PrepareIdentityOperationRequest request) {
        String name = request.getKind().name();
        if (!name.startsWith("IDENTITY_OPERATION_KIND_") || name.endsWith("UNSPECIFIED"))
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        return name.substring("IDENTITY_OPERATION_KIND_".length());
    }

    private static UUID uuid(String id) {
        try {
            var parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException invalid) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("canonical operation UUID required")
                    .asRuntimeException();
        }
    }

    private static io.grpc.StatusRuntimeException conflict() {
        return Status.ABORTED
                .withDescription("identity operation phase changed")
                .asRuntimeException();
    }
}
