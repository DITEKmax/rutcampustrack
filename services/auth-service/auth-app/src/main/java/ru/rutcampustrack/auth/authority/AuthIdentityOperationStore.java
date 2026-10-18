package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.IdentityOperationFailure.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/** Fixed Auth-owned operation store. Every transaction is local and contains no RPC. */
@Service
public class AuthIdentityOperationStore {
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final AuthAdminJdbcAuthority admins;
    private final AuthInternalActorReader actors;
    private final AuthIdentityRejectionStore rejections;
    private final AuthAdminAccessNoopReceipts noops;
    private final ru.rutcampustrack.auth.event.AdminAccessChangedOutbox adminAccessEvents;
    private final AuthTelegramBindingReservations telegramReservations;
    private final AuthArchiveStepUpAdmission archiveAdmission;
    private final PasswordEncoder passwords;
    private final BcryptConcurrencyGuard bcrypt;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public AuthIdentityOperationStore(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager manager,
            AuthInternalActorReader actors,
            PasswordEncoder passwords,
            BcryptConcurrencyGuard bcrypt) {
        this(jdbc, mapper, manager, actors, passwords, bcrypt, java.time.Clock.systemUTC());
    }

    AuthIdentityOperationStore(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager manager,
            AuthInternalActorReader actors,
            PasswordEncoder passwords,
            BcryptConcurrencyGuard bcrypt,
            java.time.Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(manager);
        this.admins = new AuthAdminJdbcAuthority(jdbc);
        this.rejections = new AuthIdentityRejectionStore(jdbc);
        this.noops = new AuthAdminAccessNoopReceipts(jdbc);
        this.adminAccessEvents =
                new ru.rutcampustrack.auth.event.AdminAccessChangedOutbox(jdbc, mapper);
        this.telegramReservations = new AuthTelegramBindingReservations(jdbc);
        this.archiveAdmission = new AuthArchiveStepUpAdmission(jdbc, clock);
        this.actors = actors;
        this.passwords = passwords;
        this.bcrypt = bcrypt;
    }

    public IdentityOperationResult prepare(PrepareIdentityOperationRequest request) {
        validate(request);
        UUID id = uuid(request.getOperationId());
        byte[] canonical = request.toBuilder().clearCommandHash().build().toByteArray();
        if (!MessageDigest.isEqual(sha256(canonical), request.getCommandHash().toByteArray()))
            throw invalid();
        // Expensive BCrypt runs before locks. A repeated accepted operation does
        // not need a fresh actor bearer, but may never alter its stored command.
        var previous = find(id);
        if (previous != null) return requireSame(previous, request.getCommandHash(), canonical);
        var rejected = rejections.find(id, request.getCommandHash(), canonical);
        if (rejected != null) return rejected;
        var noop = noops.find(id, request.getCommandHash(), canonical);
        if (noop != null) return noop;
        SessionPrincipal actor = actors.read();
        String initial =
                request.getKind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE
                        ? initial()
                        : null;
        String hash = initial == null ? null : bcrypt.execute(() -> passwords.encode(initial));
        return tx.execute(
                status -> {
                    lockGlobal();
                    var raced = find(id);
                    if (raced != null)
                        return requireSame(raced, request.getCommandHash(), canonical);
                    var racedRejection = rejections.find(id, request.getCommandHash(), canonical);
                    if (racedRejection != null) return racedRejection;
                    var racedNoop = noops.find(id, request.getCommandHash(), canonical);
                    if (racedNoop != null) return racedNoop;
                    var ids = new TreeSet<Long>();
                    ids.add(actor.userId());
                    request.getTargetsList().stream()
                            .map(IdentityTargetCommand::getUserId)
                            .filter(uid -> uid > 0)
                            .forEach(ids::add);
                    ids.forEach(this::lockIdentity);
                    AdminAccessProfile actorProfile =
                            admins.current(AuthInternalActorReader.claims(actor));
                    Reconciliation reconciliation = reconciliation(request);
                    IdentityOperationKind authorizationKind =
                            reconciliation == null
                                    ? request.getKind()
                                    : reconciliation.authorizationKind();
                    requireCommandPermission(actorProfile, authorizationKind, request);
                    validateHeadmanAggregate(request);
                    var unchanged = noops.prepareIfUnchanged(request, actor, canonical);
                    if (unchanged != null) return unchanged;
                    var actorState = identity(actor.userId());
                    long actorCommitRv =
                            request.getTargetsList().stream()
                                            .anyMatch(t -> t.getUserId() == actor.userId())
                                    ? Math.addExact(actor.rolesVersion(), 1)
                                    : actor.rolesVersion();
                    jdbc.update(
                            """
INSERT INTO auth_identity_operations(operation_id,command_hash,canonical_command,kind,
    actor_id,actor_session_id,actor_session_version,actor_roles_version,
    actor_credentials_version,actor_binding_version,actor_commit_roles_version,actor_admin_profile_version,authorization_kind)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
                            id,
                            request.getCommandHash().toByteArray(),
                            canonical,
                            kind(request.getKind()),
                            actor.userId(),
                            actor.sessionId(),
                            actor.sessionVersion(),
                            actor.rolesVersion(),
                            actorState.credentials(),
                            actorState.binding(),
                            actorCommitRv,
                            actorProfile.version(),
                            kind(authorizationKind));
                    archiveAdmission.admit(request, authorizationKind, actor);
                    for (var target : request.getTargetsList())
                        prepareTarget(
                                id,
                                request,
                                authorizationKind,
                                reconciliation,
                                actor,
                                actorProfile,
                                target,
                                initial,
                                hash);
                    return result(find(id));
                });
    }

    public IdentityOperationResult get(GetIdentityOperationRequest request) {
        UUID id = uuid(request.getOperationId());
        requireHash(request.getCommandHash());
        return tx.execute(
                status -> {
                    var op = find(id);
                    if (op == null) {
                        var rejection = rejections.find(id, request.getCommandHash(), null);
                        if (rejection != null) return rejection;
                        var noop = noops.find(id, request.getCommandHash(), null);
                        if (noop != null) return noop;
                        throw error(Status.NOT_FOUND, "OPERATION_NOT_FOUND");
                    }
                    requireHash(op, request.getCommandHash());
                    return result(op);
                });
    }

    public IdentityOperationResult commit(CommitIdentityOperationRequest request) {
        UUID id = uuid(request.getOperationId());
        requireHash(request.getCommandHash());
        return tx.execute(
                status -> {
                    lockGlobal();
                    var noop = noops.find(id, request.getCommandHash(), null);
                    if (noop != null) return noop;
                    var op = lockOperation(id);
                    requireHash(op, request.getCommandHash());
                    if (!"PREPARED_FENCED".equals(op.phase())) return result(op);
                    if (op.revision() != request.getExpectedOperationRevision()) throw stale();
                    var targets = targets(id);
                    var ids = new TreeSet<Long>();
                    ids.add(op.actor());
                    targets.forEach(t -> ids.add(t.userId()));
                    ids.forEach(this::lockIdentity);
                    if (!actorStillAuthorizes(op)) {
                        terminal(op, "ACTOR_AUTHORITY_CHANGED");
                        return result(find(id));
                    }
                    if (!archiveAdmission.owns(op.id(), op.hash(), op.authorizationKind())) {
                        terminal(op, "ARCHIVE_STEP_UP_ADMISSION_MISSING");
                        return result(find(id));
                    }
                    if (request.getReceiptsCount() != targets.size()) throw invalid();
                    Map<Long, AcademicIdentityCommitReceipt> receipts = new HashMap<>();
                    for (var receipt : request.getReceiptsList())
                        if (receipts.put(receipt.getUserId(), receipt) != null) throw invalid();
                    var command = parse(op.command());
                    for (int i = 0; i < targets.size(); i++) {
                        var target = targets.get(i);
                        var current = identity(target.userId());
                        if (!id.equals(current.fence())
                                || current.rv() != target.finalRv()
                                || current.credentials() != target.credentials()
                                || current.binding() != target.binding()) {
                            terminal(op, "TARGET_AUTHORITY_CHANGED");
                            return result(find(id));
                        }
                        var delta = command.getTargets(i);
                        if (delta.hasTelegramChange()
                                && delta.getTelegramChange().hasTelegramId()
                                && !telegramReservations.owns(
                                        delta.getTelegramChange().getTelegramId(),
                                        target.userId(),
                                        id)) {
                            terminal(op, "TELEGRAM_RESERVATION_CHANGED");
                            return result(find(id));
                        }
                        var receipt = receipts.get(target.userId());
                        if (receipt == null
                                || receipt.getPreviousAcademicRevision()
                                        != target.academicRevision()
                                || receipt.getCommittedAcademicRevision()
                                        <= target.academicRevision()
                                || receipt.getAppliedAuthRolesVersion() != target.finalRv())
                            throw stale();
                    }
                    for (var target : targets) {
                        var receipt = receipts.get(target.userId());
                        jdbc.update(
                                "UPDATE auth_identity_operation_targets SET"
                                        + " committed_academic_revision=? WHERE operation_id=? AND"
                                        + " user_id=?",
                                receipt.getCommittedAcademicRevision(),
                                id,
                                target.userId());
                        applyGrants(target);
                        applyAdminProfile(target);
                    }
                    Long clock =
                            jdbc.queryForObject(
                                    "SELECT version FROM auth_identity_catalog_clock WHERE"
                                            + " singleton=1 FOR UPDATE",
                                    Long.class);
                    long committedVersion =
                            Math.addExact(Objects.requireNonNull(clock), targets.size());
                    jdbc.update(
                            """
UPDATE auth_identity_operations SET phase='COMMITTED',revision=revision+1,
    committed_catalog_version=?,updated_at=NOW(),finished_at=NOW() WHERE operation_id=?
""",
                            committedVersion,
                            id);
                    for (int i = 0; i < targets.size(); i++) {
                        Target target = targets.get(i);
                        var delta = command.getTargets(i);
                        if (delta.hasTelegramChange()) {
                            var binding = delta.getTelegramChange();
                            jdbc.update(
                                    """
UPDATE auth_identities SET account_state=?,fence_operation_id=NULL,
    telegram_id=?,telegram_username=?,updated_at=NOW() WHERE id=? AND fence_operation_id=?
""",
                                    target.accountState(),
                                    binding.hasTelegramId() ? binding.getTelegramId() : null,
                                    binding.hasTelegramUsername()
                                            ? binding.getTelegramUsername()
                                            : null,
                                    target.userId(),
                                    id);
                        } else
                            jdbc.update(
                                    "UPDATE auth_identities SET"
                                        + " account_state=?,fence_operation_id=NULL,updated_at=NOW()"
                                        + " WHERE id=? AND fence_operation_id=?",
                                    target.accountState(),
                                    target.userId(),
                                    id);
                        // A new authority generation never revives an old access token.
                        // Archive additionally destroys all current session admission.
                        if ("ARCHIVED".equals(target.accountState()))
                            jdbc.update(
                                    """
UPDATE auth_sessions SET revoked_at=NOW(),revoke_reason='SECURITY_REVOKED'
WHERE user_id=? AND revoked_at IS NULL
""",
                                    target.userId());
                    }
                    telegramReservations.releaseCommitted(id);
                    Long actual =
                            jdbc.queryForObject(
                                    "SELECT version FROM auth_identity_catalog_clock WHERE"
                                            + " singleton=1",
                                    Long.class);
                    if (!Objects.equals(actual, committedVersion))
                        throw new IllegalStateException("identity catalog commit receipt mismatch");
                    if (op.authorizationKind()
                            == IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS) {
                        for (int i = 0; i < targets.size(); i++) {
                            var desired = command.getTargets(i).getDesiredAdminProfile();
                            long finalVersion =
                                    json(targets.get(i).profile()).path("version").asLong();
                            if (desired.getAssigned()
                                    && finalVersion > desired.getExpectedVersion())
                                adminAccessEvents.append(
                                        op.id(),
                                        targets.get(i).userId(),
                                        op.actor(),
                                        desired,
                                        finalVersion);
                        }
                    }
                    return result(find(id));
                });
    }

    public IdentityOperationResult fail(FailIdentityOperationRequest request) {
        UUID id = uuid(request.getOperationId());
        requireHash(request.getCommandHash());
        if (!Set.of("ACADEMIC_VALIDATION_FAILED", "ACADEMIC_APPLY_FAILED", "ACADEMIC_CONFLICT")
                .contains(request.getErrorCode())) throw invalid();
        return tx.execute(
                status -> {
                    lockGlobal();
                    var noop = noops.find(id, request.getCommandHash(), null);
                    if (noop != null) return noop;
                    var op = lockOperation(id);
                    requireHash(op, request.getCommandHash());
                    if (!"PREPARED_FENCED".equals(op.phase())) return result(op);
                    if (op.revision() != request.getExpectedOperationRevision()) throw stale();
                    terminal(op, request.getErrorCode());
                    return result(find(id));
                });
    }

    public IdentityOperationResult rejectUnprepared(
            RejectUnpreparedIdentityOperationRequest request) {
        if (!request.hasCanonicalPrepare()
                || !request.hasActor()
                || request.getReason() == IdentityOperationRejectionReason.UNRECOGNIZED
                || request.getReason()
                        == IdentityOperationRejectionReason
                                .IDENTITY_OPERATION_REJECTION_REASON_UNSPECIFIED
                || !request.getUnknownFields().asMap().isEmpty()) throw invalid();
        var command = request.getCanonicalPrepare();
        validate(command);
        UUID id = uuid(command.getOperationId());
        byte[] canonical = command.toBuilder().clearCommandHash().build().toByteArray();
        if (!MessageDigest.isEqual(sha256(canonical), command.getCommandHash().toByteArray()))
            throw invalid();
        var actor = request.getActor();
        UUID sid = uuid(actor.getSessionId());
        if (actor.getUserId() <= 0
                || actor.getSessionVersion() <= 0
                || actor.getRolesVersion() <= 0
                || !actor.getUnknownFields().asMap().isEmpty()) throw invalid();
        return tx.execute(
                status -> {
                    lockGlobal();
                    var positive = find(id);
                    if (positive != null) {
                        if (positive.actor() != actor.getUserId()
                                || !positive.actorSession().equals(sid)
                                || positive.actorSessionVersion() != actor.getSessionVersion()
                                || !Objects.equals(
                                        jdbc.queryForObject(
                                                "SELECT actor_roles_version FROM"
                                                        + " auth_identity_operations WHERE"
                                                        + " operation_id=?",
                                                Long.class,
                                                id),
                                        actor.getRolesVersion()))
                            throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
                        return requireSame(positive, command.getCommandHash(), canonical);
                    }
                    var noop = noops.find(id, command.getCommandHash(), canonical);
                    if (noop != null) {
                        noops.requireActor(id, actor);
                        return noop;
                    }
                    var negative = rejections.find(id, command.getCommandHash(), canonical);
                    if (negative != null) {
                        rejections.requireActor(id, actor);
                        return negative;
                    }
                    // Denial cannot grant rights, revive admission, cancel a positive
                    // operation or clear a fence. Persisted SID ownership survives expiry.
                    Boolean owned =
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM auth_sessions WHERE sid=? AND"
                                            + " user_id=?)",
                                    Boolean.class,
                                    sid,
                                    actor.getUserId());
                    if (!Boolean.TRUE.equals(owned))
                        throw error(Status.PERMISSION_DENIED, "OPERATION_ACTOR_MISMATCH");
                    return rejections.insert(command, canonical, actor, request.getReason());
                });
    }

    private Reconciliation reconciliation(PrepareIdentityOperationRequest request) {
        if (request.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE)
            return null;
        UUID previousId = uuid(request.getTargets(0).getSupersedesFailedOperationId());
        if (request.getTargetsList().stream()
                .anyMatch(t -> !previousId.equals(uuid(t.getSupersedesFailedOperationId()))))
            throw invalid();
        Operation previous = lockOperation(previousId);
        if (!"FAILED_SAFE".equals(previous.phase())) throw stale();
        Map<Long, Target> oldTargets = new TreeMap<>();
        targets(previousId).forEach(t -> oldTargets.put(t.userId(), t));
        if (!oldTargets
                .keySet()
                .equals(
                        new TreeSet<>(
                                request.getTargetsList().stream()
                                        .map(IdentityTargetCommand::getUserId)
                                        .toList()))) throw invalid();
        PrepareIdentityOperationRequest oldCommand = parse(previous.command());
        for (int i = 0; i < request.getTargetsCount(); i++) {
            requireSameReconciliationIntent(oldCommand.getTargets(i), request.getTargets(i));
        }
        return new Reconciliation(previous.authorizationKind(), oldTargets);
    }

    /** Recovery refreshes generations and actor only; it cannot broaden a failed fixed intent. */
    static void requireSameReconciliationIntent(
            IdentityTargetCommand old, IdentityTargetCommand next) {
        if (!reconciliationIntent(old).equals(reconciliationIntent(next)))
            throw error(Status.PERMISSION_DENIED, "RECONCILIATION_INTENT_CHANGED");
    }

    private static IdentityTargetCommand reconciliationIntent(IdentityTargetCommand target) {
        var normalized =
                target.toBuilder()
                        .clearUserId()
                        .clearExpectedRolesVersion()
                        .clearExpectedCredentialsVersion()
                        .clearExpectedTelegramBindingVersion()
                        .clearExpectedAcademicRevision()
                        .clearSupersedesFailedOperationId()
                        .setDesiredAdminProfile(
                                target.getDesiredAdminProfile().toBuilder().clearExpectedVersion())
                        .clearDesiredGrants();
        target.getDesiredGrantsList()
                .forEach(g -> normalized.addDesiredGrants(g.toBuilder().clearGrantId()));
        return normalized.build();
    }

    private record Reconciliation(
            IdentityOperationKind authorizationKind, Map<Long, Target> targets) {}

    private void prepareTarget(
            UUID operation,
            PrepareIdentityOperationRequest command,
            IdentityOperationKind authorizationKind,
            Reconciliation reconciliation,
            SessionPrincipal actor,
            AdminAccessProfile actorProfile,
            IdentityTargetCommand delta,
            String initial,
            String hash) {
        long user = delta.getUserId();
        boolean create = user == 0;
        Identity before = null;
        if (create) {
            user = next("auth_identities");
            var material = command.getCreateIdentity();
            if (material.getTelegramId() > 0)
                telegramReservations.requireAvailable(material.getTelegramId(), 0);
            String digits = material.getLoginDigits();
            if (digits.isEmpty()) {
                Long sequence =
                        jdbc.queryForObject("SELECT nextval('teacher_login_seq')", Long.class);
                digits =
                        String.format(
                                Locale.ROOT,
                                "%04d",
                                Math.floorMod(Objects.requireNonNull(sequence), 10_000));
            }
            String login =
                    AuthLoginNames.reserve(
                            material.getLoginPrefix(),
                            digits,
                            candidate ->
                                    Boolean.TRUE.equals(
                                            jdbc.queryForObject(
                                                    "SELECT EXISTS(SELECT 1 FROM auth_identities"
                                                            + " WHERE login=?)",
                                                    Boolean.class,
                                                    candidate)));
            jdbc.update(
                    """
INSERT INTO auth_identities(id,login,password_hash,initial_password,telegram_id,telegram_username,
    provision_operation_id,fence_operation_id) VALUES(?,?,?,?,?,?,?,?)
""",
                    user,
                    login,
                    hash,
                    initial,
                    material.getTelegramId() == 0 ? null : material.getTelegramId(),
                    material.getTelegramUsername().isEmpty()
                            ? null
                            : material.getTelegramUsername(),
                    operation,
                    operation);
        } else {
            before = identity(user);
            if (before.rv() != delta.getExpectedRolesVersion()
                    || before.credentials() != delta.getExpectedCredentialsVersion()
                    || before.binding() != delta.getExpectedTelegramBindingVersion()) throw stale();
            if (before.fence() != null) {
                if (command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE
                        || !before.fence().equals(uuid(delta.getSupersedesFailedOperationId()))
                        || !"FAILED_SAFE".equals(lockOperation(before.fence()).phase()))
                    throw stale();
            }
            if (!actorProfile.full() && admins.activeAdmin(user))
                throw error(Status.PERMISSION_DENIED, "ADMIN_TARGET_PROTECTED");
            if (delta.getDesiredAccountState()
                            == IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED
                    && command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE
                    && command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE)
                throw invalid();
            if (!before.state()
                            .equals(
                                    delta.getDesiredAccountState()
                                                    == IdentityAccountState
                                                            .IDENTITY_ACCOUNT_STATE_ARCHIVED
                                            ? "ARCHIVED"
                                            : "ACTIVE")
                    && !Set.of(
                                    IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE,
                                    IdentityOperationKind.IDENTITY_OPERATION_KIND_RESTORE,
                                    IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE)
                            .contains(command.getKind())) throw invalid();
        }
        var old =
                jdbc.query(
                        "SELECT id,role,status,group_id FROM user_role_grants WHERE user_id=? ORDER"
                                + " BY role",
                        (rs, n) ->
                                AdmissionGrant.newBuilder()
                                        .setGrantId(rs.getLong(1))
                                        .setRole(rs.getString(2).toUpperCase(Locale.ROOT))
                                        .setStatus(rs.getString(3).toUpperCase(Locale.ROOT))
                                        .mergeFrom(group(rs.getObject(4, Long.class)))
                                        .build(),
                        user);
        Map<String, AdmissionGrant> existing = new HashMap<>();
        old.forEach(g -> existing.put(g.getRole(), g));
        var desired = new ArrayList<AdmissionGrant>();
        for (var grant : delta.getDesiredGrantsList()) {
            var prior = existing.remove(grant.getRole());
            AdmissionGrant reserved =
                    reconciliation == null
                            ? null
                            : grants(reconciliation.targets().get(user).grants()).stream()
                                    .filter(g -> g.getRole().equals(grant.getRole()))
                                    .findFirst()
                                    .orElse(null);
            if ((prior == null
                            && grant.getGrantId() != 0
                            && (reserved == null || grant.getGrantId() != reserved.getGrantId()))
                    || (prior != null && grant.getGrantId() != prior.getGrantId())) throw stale();
            if (!Objects.equals(prior, grant)) requireGrantChange(authorizationKind, grant);
            if ("ADMIN".equals(grant.getRole()) && (prior == null || !prior.equals(grant))) {
                if (!actorProfile.full())
                    throw error(Status.PERMISSION_DENIED, "ADMIN_FULL_REQUIRED");
                if (user == actor.userId())
                    throw error(Status.PERMISSION_DENIED, "ADMIN_ACCESS_SELF_CHANGE_DENIED");
            }
            desired.add(
                    grant.toBuilder()
                            .setGrantId(
                                    prior == null
                                            ? (reserved == null
                                                    ? next("user_role_grants")
                                                    : reserved.getGrantId())
                                            : prior.getGrantId())
                            .build());
        }
        if (!existing.isEmpty()) throw stale();
        var profile = delta.getDesiredAdminProfile();
        var oldProfiles =
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
                        user);
        var oldProfile =
                oldProfiles.isEmpty()
                        ? AdmissionAdminProfile.getDefaultInstance()
                        : oldProfiles.getFirst();
        if (profile.getExpectedVersion() != oldProfile.getExpectedVersion()) throw stale();
        boolean profileChanged = !profile.equals(oldProfile);
        if (profileChanged) {
            if (!Set.of(
                            IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE,
                            IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS,
                            IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE)
                    .contains(authorizationKind)) throw invalid();
            if (!actorProfile.full()) throw error(Status.PERMISSION_DENIED, "ADMIN_FULL_REQUIRED");
            if (user == actor.userId())
                throw error(Status.PERMISSION_DENIED, "ADMIN_ACCESS_SELF_CHANGE_DENIED");
        }
        if (!create
                && authorizationKind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE
                && user == actor.userId())
            throw error(Status.PERMISSION_DENIED, "SELF_ARCHIVE_DENIED");
        boolean desiredAdmin =
                desired.stream()
                        .anyMatch(
                                g -> "ADMIN".equals(g.getRole()) && "ACTIVE".equals(g.getStatus()));
        if (desiredAdmin != profile.getAssigned()) throw invalid();
        if (!create
                && oldProfile.getAssigned()
                && "FULL".equals(oldProfile.getMode())
                && (!desiredAdmin
                        || !"FULL".equals(profile.getMode())
                        || delta.getDesiredAccountState()
                                == IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED))
            admins.protectLastFull(user);
        long rv = create ? 1 : Math.addExact(before.rv(), 1);
        if (!create)
            jdbc.update(
                    "UPDATE auth_identities SET"
                            + " roles_version=?,fence_operation_id=?,updated_at=NOW() WHERE id=?",
                    rv,
                    operation,
                    user);
        long cv = create ? 1 : before.credentials(), bv = create ? 1 : before.binding();
        long finalBinding = bv;
        if (delta.hasTelegramChange()) {
            if (!Set.of(
                            IdentityOperationKind.IDENTITY_OPERATION_KIND_TELEGRAM_BINDING,
                            IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                            IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE)
                    .contains(authorizationKind)) throw invalid();
            if (authorizationKind == IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE
                    && desired.stream().noneMatch(g -> "STUDENT".equals(g.getRole())))
                throw invalid();
            Long oldTelegram =
                    jdbc.queryForObject(
                            "SELECT telegram_id FROM auth_identities WHERE id=?", Long.class, user);
            Long newTelegram =
                    delta.getTelegramChange().hasTelegramId()
                            ? delta.getTelegramChange().getTelegramId()
                            : null;
            if (!Objects.equals(oldTelegram, newTelegram)) finalBinding = Math.addExact(bv, 1);
        }
        long finalProfileVersion = oldProfile.getExpectedVersion() + (profileChanged ? 1 : 0);
        jdbc.update(
                """
INSERT INTO auth_identity_operation_targets(operation_id,user_id,expected_roles_version,final_roles_version,
    expected_credentials_version,prepared_credentials_version,expected_binding_version,prepared_binding_version,
    expected_academic_revision,desired_grants,desired_admin_profile,desired_account_state,supersedes_failed_operation_id,final_binding_version)
VALUES(?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)
""",
                operation,
                user,
                delta.getExpectedRolesVersion(),
                rv,
                delta.getExpectedCredentialsVersion(),
                cv,
                delta.getExpectedTelegramBindingVersion(),
                bv,
                delta.getExpectedAcademicRevision(),
                grantsJson(desired),
                profileJson(profile, finalProfileVersion),
                delta.getDesiredAccountState()
                                == IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED
                        ? "ARCHIVED"
                        : "ACTIVE",
                delta.getSupersedesFailedOperationId().isEmpty()
                        ? null
                        : uuid(delta.getSupersedesFailedOperationId()),
                finalBinding);
        if (delta.hasTelegramChange() && delta.getTelegramChange().hasTelegramId())
            telegramReservations.claim(
                    delta.getTelegramChange().getTelegramId(),
                    user,
                    operation,
                    delta.getSupersedesFailedOperationId().isEmpty()
                            ? null
                            : uuid(delta.getSupersedesFailedOperationId()));
    }

    private boolean actorStillAuthorizes(Operation op) {
        var actor = identity(op.actor());
        if (actor.rv() != op.actorCommitRv()
                || actor.credentials() != op.actorCredentials()
                || actor.binding() != op.actorBinding()
                || !"ACTIVE".equals(actor.state())
                || (actor.fence() != null && !actor.fence().equals(op.id()))) return false;
        Boolean live =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM auth_sessions s JOIN user_role_grants g ON g.id=s.active_role_grant_id AND g.user_id=s.user_id
  JOIN admin_access_profiles p ON p.user_id=s.user_id
  WHERE s.sid=? AND s.user_id=? AND s.session_version=? AND s.revoked_at IS NULL
    AND g.role='admin' AND g.status='active' AND p.assigned AND p.version=?)
""",
                        Boolean.class,
                        op.actorSession(),
                        op.actor(),
                        op.actorSessionVersion(),
                        op.actorProfileVersion());
        if (!Boolean.TRUE.equals(live)) return false;
        var profile =
                jdbc.query(
                                "SELECT mode,permissions FROM admin_access_profiles WHERE"
                                        + " user_id=?",
                                (rs, n) ->
                                        new AdminAccessProfile(
                                                op.actor(),
                                                true,
                                                rs.getString(1),
                                                Arrays.asList((String[]) rs.getArray(2).getArray()),
                                                op.actorProfileVersion()),
                                op.actor())
                        .getFirst();
        try {
            var permissions = AdminPermission.validate(profile.mode(), profile.permissions());
            requireCommandPermission(
                    new AdminAccessProfile(
                            op.actor(),
                            true,
                            profile.mode(),
                            "FULL".equals(profile.mode())
                                    ? AdminPermission.allCodes()
                                    : permissions,
                            profile.version()),
                    op.authorizationKind(),
                    parse(op.command()));
            return true;
        } catch (AdminAccessException | io.grpc.StatusRuntimeException denied) {
            return false;
        }
    }

    private void applyGrants(Target target) {
        for (var grant : grants(target.grants()))
            jdbc.update(
                    """
INSERT INTO user_role_grants(id,user_id,role,status,group_id) VALUES(?,?,?,?,?)
ON CONFLICT(user_id,role) DO UPDATE SET status=EXCLUDED.status,group_id=EXCLUDED.group_id,updated_at=NOW()
WHERE user_role_grants.id=EXCLUDED.id
  AND (user_role_grants.status IS DISTINCT FROM EXCLUDED.status
    OR user_role_grants.group_id IS DISTINCT FROM EXCLUDED.group_id)
""",
                    grant.getGrantId(),
                    target.userId(),
                    grant.getRole().toLowerCase(Locale.ROOT),
                    grant.getStatus().toLowerCase(Locale.ROOT),
                    grant.hasGroupId() ? grant.getGroupId() : null);
    }

    private void applyAdminProfile(Target target) {
        var p = json(target.profile());
        long version = p.path("version").asLong();
        if (version == 0) return;
        List<String> permissions = new ArrayList<>();
        p.path("permissions").forEach(n -> permissions.add(n.asText()));
        jdbc.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(
                                    """
INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) VALUES(?,?,?,?,?)
ON CONFLICT(user_id) DO UPDATE SET assigned=EXCLUDED.assigned,mode=EXCLUDED.mode,
    permissions=EXCLUDED.permissions,version=EXCLUDED.version,updated_at=NOW()
""");
                    statement.setLong(1, target.userId());
                    statement.setBoolean(2, p.path("assigned").asBoolean());
                    statement.setString(
                            3, p.path("mode").asText().isEmpty() ? null : p.path("mode").asText());
                    statement.setArray(
                            4,
                            connection.createArrayOf("text", permissions.toArray(String[]::new)));
                    statement.setLong(5, version);
                    return statement;
                });
    }

    private IdentityOperationResult result(Operation op) {
        var result =
                IdentityOperationResult.newBuilder()
                        .setOperationId(op.id().toString())
                        .setCommandHash(ByteString.copyFrom(op.hash()))
                        .setKind(parse(op.command()).getKind())
                        .setOperationRevision(op.revision())
                        .setPhase(
                                IdentityOperationPhase.valueOf(
                                        "IDENTITY_OPERATION_PHASE_" + op.phase()))
                        .setCommittedCatalogVersion(op.catalogVersion())
                        .setErrorCode(op.error() == null ? "" : op.error());
        for (var target : targets(op.id())) {
            String login =
                    jdbc.queryForObject(
                            "SELECT login FROM auth_identities WHERE id=?",
                            String.class,
                            target.userId());
            result.addTargets(
                    PreparedIdentityTarget.newBuilder()
                            .setUserId(target.userId())
                            .setLogin(login)
                            .setFinalRolesVersion(target.finalRv())
                            .setCredentialsVersion(target.credentials())
                            .setTelegramBindingVersion(target.finalBinding())
                            .setExpectedAcademicRevision(target.academicRevision())
                            .addAllGrants(grants(target.grants()))
                            .setFinalAdminProfileVersion(
                                    json(target.profile()).path("version").asLong()));
        }
        return result.build();
    }

    private IdentityOperationResult requireSame(Operation op, ByteString hash, byte[] command) {
        requireHash(op, hash);
        if (!Arrays.equals(op.command(), command))
            throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
        return result(op);
    }

    private void requireHash(Operation op, ByteString hash) {
        if (!MessageDigest.isEqual(op.hash(), hash.toByteArray()))
            throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
    }

    private void terminal(Operation op, String code) {
        jdbc.update(
                "UPDATE auth_identity_operations SET"
                    + " phase='FAILED_SAFE',revision=revision+1,error_code=?,updated_at=NOW(),finished_at=NOW()"
                    + " WHERE operation_id=?",
                code,
                op.id());
    }

    private void lockGlobal() {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", AuthAdminJdbcAuthority.GLOBAL_LOCK);
    }

    private void lockIdentity(long id) {
        if (jdbc.queryForList(
                        "SELECT id FROM auth_identities WHERE id=? FOR UPDATE", Long.class, id)
                .isEmpty()) throw error(Status.NOT_FOUND, "IDENTITY_NOT_FOUND");
    }

    private long next(String table) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT nextval(pg_get_serial_sequence(?, 'id'))", Long.class, table));
    }

    private Identity identity(long id) {
        var rows =
                jdbc.query(
                        "SELECT"
                            + " roles_version,credentials_version,telegram_binding_version,fence_operation_id,account_state"
                            + " FROM auth_identities WHERE id=?",
                        (rs, n) ->
                                new Identity(
                                        rs.getLong(1),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getObject(4, UUID.class),
                                        rs.getString(5)),
                        id);
        if (rows.isEmpty()) throw error(Status.NOT_FOUND, "IDENTITY_NOT_FOUND");
        return rows.getFirst();
    }

    private Operation lockOperation(UUID id) {
        var op = find(id, true);
        if (op == null) throw error(Status.NOT_FOUND, "OPERATION_NOT_FOUND");
        return op;
    }

    private Operation find(UUID id) {
        return find(id, false);
    }

    private Operation find(UUID id, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT * FROM auth_identity_operations WHERE operation_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, n) -> {
                            if (rs.getBoolean("initial_admin_bootstrap"))
                                throw error(
                                        Status.PERMISSION_DENIED, "BOOTSTRAP_OPERATION_RESERVED");
                            return new Operation(
                                    id,
                                    rs.getBytes("command_hash"),
                                    rs.getBytes("canonical_command"),
                                    rs.getString("phase"),
                                    rs.getLong("revision"),
                                    rs.getString("error_code"),
                                    rs.getLong("actor_id"),
                                    rs.getObject("actor_session_id", UUID.class),
                                    rs.getLong("actor_session_version"),
                                    rs.getLong("actor_commit_roles_version"),
                                    rs.getLong("actor_credentials_version"),
                                    rs.getLong("actor_binding_version"),
                                    rs.getLong("actor_admin_profile_version"),
                                    rs.getLong("committed_catalog_version"),
                                    IdentityOperationKind.valueOf(
                                            "IDENTITY_OPERATION_KIND_"
                                                    + rs.getString("authorization_kind")));
                        },
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<Target> targets(UUID id) {
        return jdbc.query(
                "SELECT * FROM auth_identity_operation_targets WHERE operation_id=? ORDER BY"
                        + " user_id",
                (rs, n) ->
                        new Target(
                                rs.getLong("user_id"),
                                rs.getLong("final_roles_version"),
                                rs.getLong("prepared_credentials_version"),
                                rs.getLong("prepared_binding_version"),
                                rs.getLong("expected_academic_revision"),
                                rs.getString("desired_grants"),
                                rs.getString("desired_admin_profile"),
                                rs.getString("desired_account_state"),
                                rs.getLong("final_binding_version")),
                id);
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("invalid stored identity command");
        }
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("invalid identity command");
        }
    }

    private String grantsJson(List<AdmissionGrant> grants) {
        return encode(
                grants.stream()
                        .map(
                                g -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("id", g.getGrantId());
                                    m.put("role", g.getRole());
                                    m.put("status", g.getStatus());
                                    m.put("group", g.hasGroupId() ? g.getGroupId() : null);
                                    return m;
                                })
                        .toList());
    }

    private List<AdmissionGrant> grants(String value) {
        var result = new ArrayList<AdmissionGrant>();
        json(value)
                .forEach(
                        n -> {
                            var g =
                                    AdmissionGrant.newBuilder()
                                            .setGrantId(n.path("id").asLong())
                                            .setRole(n.path("role").asText())
                                            .setStatus(n.path("status").asText());
                            if (!n.path("group").isNull()) g.setGroupId(n.path("group").asLong());
                            result.add(g.build());
                        });
        return result;
    }

    private String profileJson(AdmissionAdminProfile p, long version) {
        return encode(
                Map.of(
                        "assigned",
                        p.getAssigned(),
                        "mode",
                        p.getMode(),
                        "permissions",
                        p.getPermissionsList(),
                        "version",
                        version));
    }

    private static AdmissionGrant group(Long id) {
        var g = AdmissionGrant.newBuilder();
        if (id != null) g.setGroupId(id);
        return g.build();
    }

    private static PrepareIdentityOperationRequest parse(byte[] command) {
        try {
            return PrepareIdentityOperationRequest.parseFrom(command);
        } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
            throw new IllegalStateException("invalid stored operation");
        }
    }

    private String initial() {
        var s = new StringBuilder(12);
        for (int i = 0; i < 12; i++) s.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return s.toString();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static UUID uuid(String value) {
        try {
            var id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw invalid();
            return id;
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
    }

    private static void requireHash(ByteString value) {
        if (value.size() != 32) throw invalid();
    }

    private static String kind(IdentityOperationKind kind) {
        return kind.name().substring("IDENTITY_OPERATION_KIND_".length());
    }

    private static io.grpc.StatusRuntimeException invalid() {
        return error(Status.INVALID_ARGUMENT, "INVALID_IDENTITY_COMMAND");
    }

    private static io.grpc.StatusRuntimeException stale() {
        return error(Status.ABORTED, "IDENTITY_STATE_STALE");
    }

    static void requirePermission(AdminAccessProfile profile, IdentityOperationKind kind) {
        boolean allowed =
                switch (kind) {
                    case IDENTITY_OPERATION_KIND_ARCHIVE, IDENTITY_OPERATION_KIND_RESTORE ->
                            profile.allows("USERS_ARCHIVE");
                    case IDENTITY_OPERATION_KIND_HEADMAN_REPLACE ->
                            profile.allows("USERS_MANAGE") || profile.allows("GROUPS_MANAGE");
                    case IDENTITY_OPERATION_KIND_ADMIN_ACCESS -> profile.full();
                    default -> profile.allows("USERS_MANAGE");
                };
        if (!allowed) throw error(Status.PERMISSION_DENIED, "ADMIN_PERMISSION_DENIED");
    }

    static void requireCommandPermission(
            AdminAccessProfile profile,
            IdentityOperationKind kind,
            PrepareIdentityOperationRequest command) {
        requirePermission(profile, kind);
        if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE
                && command.getTargetsList().stream()
                        .anyMatch(IdentityTargetCommand::hasTelegramChange)
                && !(profile.allows("USERS_MANAGE") && profile.allows("GROUPS_MANAGE")))
            throw error(Status.PERMISSION_DENIED, "ADMIN_PERMISSION_DENIED");
    }

    private static void requireGrantChange(IdentityOperationKind kind, AdmissionGrant grant) {
        boolean allowed =
                switch (kind) {
                    case IDENTITY_OPERATION_KIND_CREATE -> true;
                    case IDENTITY_OPERATION_KIND_ROLE ->
                            Set.of("STUDENT", "TEACHER").contains(grant.getRole())
                                    || ("HEADMAN".equals(grant.getRole())
                                            && !"ACTIVE".equals(grant.getStatus()));
                    case IDENTITY_OPERATION_KIND_TRANSFER ->
                            "STUDENT".equals(grant.getRole())
                                    || ("HEADMAN".equals(grant.getRole())
                                            && !"ACTIVE".equals(grant.getStatus()));
                    case IDENTITY_OPERATION_KIND_HEADMAN_REPLACE ->
                            "HEADMAN".equals(grant.getRole());
                    case IDENTITY_OPERATION_KIND_ADMIN_ACCESS -> "ADMIN".equals(grant.getRole());
                    case IDENTITY_OPERATION_KIND_ARCHIVE -> "ARCHIVED".equals(grant.getStatus());
                    default -> false;
                };
        if (!allowed) throw error(Status.PERMISSION_DENIED, "IDENTITY_COMMAND_SCOPE_DENIED");
    }

    private void validateHeadmanAggregate(PrepareIdentityOperationRequest request) {
        var affected =
                request.getTargetsList().stream()
                        .map(IdentityTargetCommand::getUserId)
                        .filter(id -> id > 0)
                        .toList();
        Set<Long> groups = new HashSet<>();
        for (var target : request.getTargetsList())
            for (var headman : target.getDesiredGrantsList()) {
                if (!"HEADMAN".equals(headman.getRole()) || !"ACTIVE".equals(headman.getStatus()))
                    continue;
                if (!groups.add(headman.getGroupId())
                        || target.getDesiredGrantsList().stream()
                                .noneMatch(
                                        g ->
                                                "STUDENT".equals(g.getRole())
                                                        && "ACTIVE".equals(g.getStatus())
                                                        && g.hasGroupId()
                                                        && g.getGroupId() == headman.getGroupId()))
                    throw invalid();
                var existing =
                        jdbc.queryForList(
                                "SELECT user_id FROM user_role_grants WHERE role='headman' AND"
                                        + " status='active' AND group_id=?",
                                Long.class,
                                headman.getGroupId());
                if (existing.stream().anyMatch(id -> !affected.contains(id)))
                    throw error(Status.ABORTED, "HEADMAN_STATE_STALE");
            }
    }

    private static void validate(PrepareIdentityOperationRequest request) {
        uuid(request.getOperationId());
        requireHash(request.getCommandHash());
        if (!request.getUnknownFields().asMap().isEmpty()
                || request.getKind() == IdentityOperationKind.UNRECOGNIZED
                || request.getKind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_UNSPECIFIED
                || request.getTargetsCount() < 1
                || request.getTargetsCount() > 200) throw invalid();
        boolean create = request.getKind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE;
        AuthArchiveStepUpAdmission.validateEnvelope(request);
        if (create != request.hasCreateIdentity() || (create && request.getTargetsCount() != 1))
            throw invalid();
        if (create
                && ((!request.getCreateIdentity().getLoginPrefix().isEmpty()
                                && !request.getCreateIdentity()
                                        .getLoginPrefix()
                                        .matches("[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?"))
                        || (!request.getCreateIdentity().getLoginDigits().isEmpty()
                                && !request.getCreateIdentity()
                                        .getLoginDigits()
                                        .matches("[0-9]{4}"))
                        || request.getCreateIdentity().getTelegramId() < 0
                        || !request.getCreateIdentity().getUnknownFields().asMap().isEmpty()))
            throw invalid();
        long previous = -1;
        for (var target : request.getTargetsList()) {
            if (target.getUserId() <= previous
                    || (target.getUserId() == 0) != create
                    || !target.getUnknownFields().asMap().isEmpty()
                    || !target.hasDesiredAdminProfile()
                    || target.getExpectedAcademicRevision() < 0
                    || target.getExpectedRolesVersion() < 0
                    || target.getExpectedCredentialsVersion() < 0
                    || target.getExpectedTelegramBindingVersion() < 0
                    || (!create
                            && (target.getExpectedRolesVersion() == 0
                                    || target.getExpectedCredentialsVersion() == 0
                                    || target.getExpectedTelegramBindingVersion() == 0))
                    || (create
                            && (target.getExpectedRolesVersion() != 0
                                    || target.getExpectedCredentialsVersion() != 0
                                    || target.getExpectedTelegramBindingVersion() != 0))
                    || target.getDesiredGrantsCount() == 0
                    || target.getDesiredAccountState() == IdentityAccountState.UNRECOGNIZED
                    || target.getDesiredAccountState()
                            == IdentityAccountState.IDENTITY_ACCOUNT_STATE_UNSPECIFIED)
                throw invalid();
            previous = target.getUserId();
            String previousRole = "";
            for (var grant : target.getDesiredGrantsList()) {
                var statuses =
                        switch (grant.getRole()) {
                            case "STUDENT" ->
                                    Set.of(
                                            "ACTIVE",
                                            "EXPELLED",
                                            "GRADUATED",
                                            "SUSPENDED",
                                            "ARCHIVED");
                            case "TEACHER" ->
                                    Set.of("ACTIVE", "DISMISSED", "SUSPENDED", "ARCHIVED");
                            case "HEADMAN" -> Set.of("ACTIVE", "SUSPENDED", "ARCHIVED");
                            case "ADMIN" -> Set.of("ACTIVE", "ARCHIVED");
                            default -> throw invalid();
                        };
                if (grant.getRole().compareTo(previousRole) <= 0
                        || !statuses.contains(grant.getStatus())
                        || grant.getGrantId() < 0
                        || !grant.getUnknownFields().asMap().isEmpty()
                        || (grant.hasGroupId() && grant.getGroupId() <= 0)
                        || (Set.of("STUDENT", "HEADMAN").contains(grant.getRole())
                                != grant.hasGroupId())) throw invalid();
                previousRole = grant.getRole();
            }
            var p = target.getDesiredAdminProfile();
            if (!p.getUnknownFields().asMap().isEmpty() || p.getExpectedVersion() < 0)
                throw invalid();
            if (p.getAssigned()) {
                try {
                    AdminPermission.validate(p.getMode(), p.getPermissionsList());
                } catch (AdminAccessException bad) {
                    throw invalid();
                }
            } else if (!p.getMode().isEmpty() || p.getPermissionsCount() != 0) throw invalid();
            if (target.getDesiredAccountState()
                            == IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED
                    && (p.getAssigned()
                            || target.getDesiredGrantsList().stream()
                                    .anyMatch(g -> !"ARCHIVED".equals(g.getStatus()))))
                throw invalid();
            if (request.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE
                    && !target.getSupersedesFailedOperationId().isEmpty()) throw invalid();
            if (request.getKind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE
                    && target.getSupersedesFailedOperationId().isEmpty()) throw invalid();
            if (target.hasTelegramChange()
                    && (!target.getTelegramChange().getUnknownFields().asMap().isEmpty()
                            || (target.getTelegramChange().hasTelegramId()
                                    && target.getTelegramChange().getTelegramId() <= 0)))
                throw invalid();
        }
    }

    private record Identity(long rv, long credentials, long binding, UUID fence, String state) {}

    private record Target(
            long userId,
            long finalRv,
            long credentials,
            long binding,
            long academicRevision,
            String grants,
            String profile,
            String accountState,
            long finalBinding) {}

    private record Operation(
            UUID id,
            byte[] hash,
            byte[] command,
            String phase,
            long revision,
            String error,
            long actor,
            UUID actorSession,
            long actorSessionVersion,
            long actorCommitRv,
            long actorCredentials,
            long actorBinding,
            long actorProfileVersion,
            long catalogVersion,
            IdentityOperationKind authorizationKind) {}
}
