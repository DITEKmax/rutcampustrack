package ru.rutcampustrack.academic.identityoperation;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.AuthUserArchiveClient;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authauthority.grpc.CheckAdminAccessResponse;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;
import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** One fixed-intent retry of a failed identity action; this never reconstructs an actor bearer. */
@Service
public class AcademicIdentityReconciliationService {
    private final JdbcAcademicIdentityOperationStore store;
    private final AcademicIdentityHttpOperations http;
    private final AuthIdentityDataClient identities;
    private final AuthUserArchiveClient proof;
    private final AttendanceUserImpactGrpcClient attendance;
    private final UserArchiveRepository archives;
    private final RequestContext context;
    private final ObjectMapper canonical;
    private final TransactionTemplate writes;

    public AcademicIdentityReconciliationService(
            JdbcAcademicIdentityOperationStore store,
            AcademicIdentityHttpOperations http,
            AuthIdentityDataClient identities,
            AuthUserArchiveClient proof,
            AttendanceUserImpactGrpcClient attendance,
            UserArchiveRepository archives,
            RequestContext context,
            ObjectMapper mapper,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.http = http;
        this.identities = identities;
        this.proof = proof;
        this.attendance = attendance;
        this.archives = archives;
        this.context = context;
        canonical =
                mapper.copy()
                        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        writes = new TransactionTemplate(transactions);
    }

    public IdentityOperationHttpModels.Operation reconcile(
            UUID failedId, IdentityOperationHttpModels.ReconcileRequest request) {
        outsideTransaction();
        var authorization = store.reconciliationAuthorization(failedId);
        authorize(authorization, deadline());
        var intent =
                new AcademicIdentityLocalCommand.ApiIntent(
                        "RECONCILE",
                        0,
                        UserArchiveRepository.digest(
                                "identity-reconcile-v1:"
                                        + failedId
                                        + ":"
                                        + (request == null
                                                ? ""
                                                : java.util.Objects.toString(
                                                        request.archivePreviewDigest(), ""))));
        // Existing HTTP execution checks saved bytes first: a lost positive reply must not
        // require a second preview/password nor discard an already admitted proof on expiry.
        var completed =
                http.execute(intent, id -> build(failedId, id, intent, request, authorization));
        return AcademicIdentityHttpOperations.publicStatus(completed);
    }

    private AcademicIdentityCoordinator.AcceptedCommand build(
            UUID failedId,
            UUID id,
            AcademicIdentityLocalCommand.ApiIntent intent,
            IdentityOperationHttpModels.ReconcileRequest request,
            JdbcAcademicIdentityOperationStore.RecoveryAuthorization authorization) {
        var source =
                AcademicIdentityHttpOperations.localPreflight(
                        () -> store.reconciliationSource(failedId));
        boolean archive = source.kind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE;
        AcademicIdentityHttpOperations.localPreflight(
                () -> {
                    if (archive
                            ? request == null
                                    || request.archivePreviewDigest() == null
                                    || !request.archivePreviewDigest().matches("[0-9a-f]{64}")
                            : request != null
                                    && (request.archivePreviewDigest() != null
                                            || request.password() != null))
                        throw Status.INVALID_ARGUMENT
                                .withDescription("archive recovery requires its new preview only")
                                .asRuntimeException();
                    return true;
                });
        long deadline = deadline();
        var actor = authorize(authorization, deadline);
        var current = identities.readServiceIdentity(source.revisions().keySet(), deadline);
        var command =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE);
        for (int index = 0; index < source.failed().prepared().getTargetsCount(); index++) {
            var target = source.failed().prepared().getTargets(index);
            var identity = current.get(target.getUserId());
            requireIdentity(identity, target.getUserId(), source);
            command.addTargets(
                    source.failed().command().getTargets(index).toBuilder()
                            .setUserId(target.getUserId())
                            .setExpectedRolesVersion(identity.getRolesVersion())
                            .setExpectedCredentialsVersion(identity.getCredentialsVersion())
                            .setExpectedTelegramBindingVersion(identity.getTelegramBindingVersion())
                            .setExpectedAcademicRevision(source.revisions().get(target.getUserId()))
                            .clearDesiredGrants()
                            .addAllDesiredGrants(target.getGrantsList())
                            .setDesiredAdminProfile(
                                    source
                                            .failed()
                                            .command()
                                            .getTargets(index)
                                            .getDesiredAdminProfile()
                                            .toBuilder()
                                            .setExpectedVersion(
                                                    identity.getAdminProfile().getVersion()))
                            .setSupersedesFailedOperationId(failedId.toString()));
        }
        AcademicIdentityLocalCommand.ArchiveAdmission admission = null;
        if (archive) {
            var observed =
                    AcademicIdentityHttpOperations.localPreflight(
                            () -> store.observeReconciliationArchive(source));
            AcademicIdentityHttpOperations.localPreflight(
                    () -> {
                        archives.checkArchiveSafeguards(
                                context.getClaims().userId(), observed.userId());
                        archives.validatePreview(
                                context.getClaims().userId(),
                                observed.userId(),
                                request.archivePreviewDigest(),
                                observed);
                        return true;
                    });
            String receipt =
                    observed.requiresPassword()
                            ? proof.confirm(
                                    observed.userId(),
                                    id,
                                    request.archivePreviewDigest(),
                                    request.password())
                            : "";
            command.setArchiveStepUpProtocolVersion(1)
                    .setArchivePreviewDigest(request.archivePreviewDigest())
                    .setPasswordStepUpReceiptId(receipt);
            admission =
                    new AcademicIdentityLocalCommand.ArchiveAdmission(
                            request.archivePreviewDigest(),
                            observed.digest(),
                            observed.requiresPassword());
        }
        if (!current.equals(identities.readServiceIdentity(source.revisions().keySet(), deadline))
                || !actor.equals(authorize(authorization, deadline))) throw unavailable();
        var local = source.originalLocal().forReconciliation(intent, admission);
        try {
            byte[] bytes = canonical.writeValueAsBytes(local);
            command.setCommandHash(hash(command.build().toByteArray()));
            return new AcademicIdentityCoordinator.AcceptedCommand(
                    command.build(), hash(bytes), ByteString.copyFrom(bytes));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("fixed local recovery command cannot be serialized");
        }
    }

    public UserArchiveModels.Preview preview(UUID failedId) {
        outsideTransaction();
        var authorization = store.reconciliationAuthorization(failedId);
        if (authorization.kind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE)
            throw Status.FAILED_PRECONDITION
                    .withDescription("archive recovery required")
                    .asRuntimeException();
        long deadline = deadline();
        var actor = authorize(authorization, deadline);
        var source = store.reconciliationSource(failedId);
        var identitiesBefore =
                identities.readServiceIdentity(source.revisions().keySet(), deadline);
        source.revisions()
                .keySet()
                .forEach(user -> requireIdentity(identitiesBefore.get(user), user, source));
        var before = store.observeReconciliationArchive(source);
        archives.checkArchiveSafeguards(context.getClaims().userId(), before.userId());
        var remote = attendance.preview(before.userId());
        if (remote.getUserId() != before.userId()
                || !remote.getSnapshotDigest().matches("[0-9a-f]{64}")) throw unavailable();
        var after = store.observeReconciliationArchive(source);
        var identitiesAfter = identities.readServiceIdentity(source.revisions().keySet(), deadline);
        if (!before.digest().equals(after.digest())
                || before.requiresPassword() != after.requiresPassword()
                || !identitiesBefore.equals(identitiesAfter)
                || !actor.equals(authorize(authorization, deadline))) throw unavailable();
        Instant expires = Instant.now().plusSeconds(600);
        String digest =
                UserArchiveRepository.digest(
                        "identity-reconcile-preview-v1:"
                                + UUID.randomUUID()
                                + ":"
                                + failedId
                                + ":"
                                + actor.getActorUserId()
                                + ":"
                                + before.digest()
                                + ":"
                                + remote.getSnapshotDigest()
                                + ":"
                                + remote.getObservedAtEpochMs());
        writes.executeWithoutResult(
                status -> archives.savePreview(actor.getActorUserId(), before, digest, expires));
        return new UserArchiveModels.Preview(
                before.userId(),
                before.groupCount(),
                remote.getAttendanceMarksCount(),
                before.headmanCount(),
                before.soleTeacherCount(),
                before.requiresPassword(),
                before.observedAt(),
                Instant.ofEpochMilli(remote.getObservedAtEpochMs()),
                remote.getSnapshotDigest(),
                digest,
                expires);
    }

    private CheckAdminAccessResponse authorize(
            JdbcAcademicIdentityOperationStore.RecoveryAuthorization authorization, long deadline) {
        boolean full =
                authorization.kind() == IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS;
        List<String> permissions =
                switch (authorization.kind()) {
                    case IDENTITY_OPERATION_KIND_ADMIN_ACCESS -> List.of();
                    case IDENTITY_OPERATION_KIND_ARCHIVE, IDENTITY_OPERATION_KIND_RESTORE ->
                            List.of("USERS_ARCHIVE");
                    case IDENTITY_OPERATION_KIND_HEADMAN_REPLACE ->
                            authorization.telegramChange()
                                            || authorization.originatingPermission() == null
                                    ? List.of("GROUPS_MANAGE", "USERS_MANAGE")
                                    : List.of(authorization.originatingPermission());
                    case IDENTITY_OPERATION_KIND_CREATE,
                            IDENTITY_OPERATION_KIND_ROLE,
                            IDENTITY_OPERATION_KIND_TRANSFER,
                            IDENTITY_OPERATION_KIND_TELEGRAM_BINDING ->
                            List.of("USERS_MANAGE");
                    default -> throw unavailable();
                };
        var result = identities.checkAdminAccess(permissions, full, deadline);
        var actor = context.getClaims();
        if (actor == null
                || result.getActorUserId() != actor.userId()
                || result.getSessionVersion() != actor.sessionVersion()
                || result.getRolesVersion() != actor.rolesVersion()
                || !result.getProfile().getAssigned()
                || result.getProfile().getVersion() <= 0) throw unavailable();
        return result;
    }

    private static void requireIdentity(
            IdentityAuthoritySnapshot identity,
            long user,
            JdbcAcademicIdentityOperationStore.RecoverySource source) {
        if (identity == null
                || identity.getUserId() != user
                || identity.getRolesVersion() <= 0
                || identity.getCredentialsVersion() <= 0
                || identity.getTelegramBindingVersion() <= 0
                || identity.getAdminProfile().getVersion() < 0) throw unavailable();
        switch (identity.getAccountState()) {
            case AUTHORITY_ACCOUNT_STATE_FENCED,
                    AUTHORITY_ACCOUNT_STATE_ACTIVE,
                    AUTHORITY_ACCOUNT_STATE_ARCHIVED -> {}
            case AUTHORITY_ACCOUNT_STATE_PENDING -> {
                // Auth deliberately preserves PENDING for a provisioned, still-fenced CREATE.
                // This is expected-version material only. Auth PREPARE still checks the exact
                // failed reservation and current authority before it can reserve a new RV.
                if (source.kind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE
                        || !"CREATE".equals(source.originalLocal().intent().action())
                        || source.originalLocal().intent().targetId() != 0
                        || source.originalLocal().profile() == null
                        || source.originalLocal().profile().userId() != 0
                        || source.failed().phase() != AcademicIdentityCoordinator.Phase.FAILED_SAFE
                        || !source.failed().id().equals(source.failedId().toString())
                        || source.failed().prepared() == null
                        || source.failed().prepared().getPhase()
                                != IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE
                        || source.failed().prepared().getTargetsCount() != 1
                        || source.failed().prepared().getTargets(0).getUserId() != user
                        || source.failed().command().getTargetsCount() != 1
                        || !(source.failed().command().getKind()
                                                == IdentityOperationKind
                                                        .IDENTITY_OPERATION_KIND_CREATE
                                        && source.failed().command().getTargets(0).getUserId() == 0
                                || source.failed().command().getKind()
                                                == IdentityOperationKind
                                                        .IDENTITY_OPERATION_KIND_RECONCILE
                                        && source.failed().command().getTargets(0).getUserId()
                                                == user)
                        || source.revisions().size() != 1
                        || !source.revisions().containsKey(user)) throw unavailable();
            }
            default -> throw unavailable();
        }
    }

    private static ByteString hash(byte[] bytes) {
        try {
            return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("identity recovery snapshot unavailable")
                .asRuntimeException();
    }
}
