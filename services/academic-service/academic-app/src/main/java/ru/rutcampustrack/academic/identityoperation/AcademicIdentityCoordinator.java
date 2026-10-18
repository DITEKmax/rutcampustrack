package ru.rutcampustrack.academic.identityoperation;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.authoperations.grpc.AcademicIdentityCommitReceipt;
import ru.rutcampustrack.authoperations.grpc.CommitIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.FailIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.GetIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationKind;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationPhase;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationRejectionReason;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationResult;
import ru.rutcampustrack.authoperations.grpc.PrepareIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.RejectUnpreparedIdentityOperationRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Its store owns each separate local transaction; domain APPLY persists
 * profile/eligibility/history/outbox and the exact receipts with LOCAL_COMMITTED.
 */
public final class AcademicIdentityCoordinator {
    private final Store store;
    private final Auth auth;
    private final IdentityCatalogSynchronizer catalog;

    public AcademicIdentityCoordinator(
            Store store, Auth auth, IdentityCatalogSynchronizer catalog) {
        this.store = store;
        this.auth = auth;
        this.catalog = catalog;
    }

    /** Accepted state and both canonical hashes are durable before the first Auth request. */
    public Operation execute(AcceptedCommand command) {
        outsideTransaction();
        Operation operation = store.accept(command);
        if (operation.phase() == Phase.ACCEPTED) {
            outsideTransaction();
            var rejection = store.pendingRejection(operation);
            IdentityOperationResult prepared;
            if (rejection != null) prepared = auth.reject(rejection);
            else {
                try {
                    // Only this request path carries the original live actor transport context.
                    prepared = auth.prepare(operation.command());
                } catch (io.grpc.StatusRuntimeException denied) {
                    var reason = definiteRejection(denied.getStatus().getCode());
                    if (reason == null) throw denied;
                    String code = confirmedConflictCode(denied);
                    rejection =
                            code == null
                                    ? store.prepareRejection(operation, reason)
                                    : store.prepareRejection(operation, reason, code);
                    if (rejection == null) return advance(store.find(operation.id()));
                    outsideTransaction();
                    prepared = auth.reject(rejection);
                }
            }
            operation = observePrepared(operation, prepared);
        }
        return advance(operation);
    }

    /** Recovery never mints a JWT or reconstructs live authority from the stored actor tuple. */
    public Operation recover(String operationId) {
        outsideTransaction();
        var operation = store.find(operationId);
        if (operation.phase() == Phase.ACCEPTED) {
            outsideTransaction();
            // An unknown Auth operation requires a fresh admitted request, not worker Prepare.
            var rejection = store.pendingRejection(operation);
            var prepared =
                    rejection != null
                            ? auth.reject(rejection)
                            : auth.get(
                                    GetIdentityOperationRequest.newBuilder()
                                            .setOperationId(operationId)
                                            .setCommandHash(operation.command().getCommandHash())
                                            .build());
            operation = observePrepared(operation, prepared);
        }
        return advance(operation);
    }

    private Operation observePrepared(Operation operation, IdentityOperationResult result) {
        if (result.getPhase() == IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP) {
            validateNoop(operation, result);
            return store.nooped(operation, result);
        }
        if (result.getPhase() == IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED) {
            if (!result.getOperationId().equals(operation.id())
                    || !result.getCommandHash().equals(operation.command().getCommandHash())
                    || result.getKind() != operation.command().getKind()
                    || result.getOperationRevision() <= 0
                    || result.getTargetsCount() != 0) throw unavailable();
            return store.rejected(operation, result);
        }
        if (result.getPhase() == IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE) {
            validate(
                    result, operation, IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
            return store.failedSafe(operation, result);
        }
        if (result.getPhase() == IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED) {
            // A competing runner can have advanced the same durable local operation.
            var current = store.find(operation.id());
            if (current.phase() != Phase.LOCAL_COMMITTED && current.phase() != Phase.SUCCEEDED)
                throw unavailable();
            return current;
        }
        validate(
                result, operation, IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED);
        return store.prepared(operation, result);
    }

    static void validateNoop(Operation operation, IdentityOperationResult result) {
        var command = operation.command();
        if (command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS
                || result.getKind() != command.getKind()
                || !result.getOperationId().equals(operation.id())
                || !result.getCommandHash().equals(command.getCommandHash())
                || result.getPhase() != IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP
                || result.getOperationRevision() != 1
                || result.getCommittedCatalogVersion() != 0
                || !result.getErrorCode().isEmpty()
                || command.getTargetsCount() != 1
                || result.getTargetsCount() != 1) throw unavailable();
        var requested = command.getTargets(0);
        var target = result.getTargets(0);
        if (requested.getUserId() <= 0
                || target.getUserId() != requested.getUserId()
                || target.getFinalRolesVersion() != requested.getExpectedRolesVersion()
                || target.getFinalRolesVersion() <= 0
                || target.getExpectedAcademicRevision() != requested.getExpectedAcademicRevision()
                || target.getExpectedAcademicRevision() <= 0
                || target.getCredentialsVersion() != requested.getExpectedCredentialsVersion()
                || target.getCredentialsVersion() <= 0
                || target.getTelegramBindingVersion()
                        != requested.getExpectedTelegramBindingVersion()
                || target.getTelegramBindingVersion() <= 0
                || target.getFinalAdminProfileVersion()
                        != requested.getDesiredAdminProfile().getExpectedVersion()
                || target.getFinalAdminProfileVersion() < 0
                || requested.hasTelegramChange()
                || !target.getGrantsList().equals(requested.getDesiredGrantsList())
                || target.getGrantsList().stream().anyMatch(g -> g.getGrantId() <= 0))
            throw unavailable();
    }

    private static String confirmedConflictCode(io.grpc.StatusRuntimeException failure) {
        if (failure.getStatus().getCode() != Status.Code.ALREADY_EXISTS
                || failure.getTrailers() == null) return null;
        String code =
                failure.getTrailers()
                        .get(
                                io.grpc.Metadata.Key.of(
                                        "x-rct-error-code",
                                        io.grpc.Metadata.ASCII_STRING_MARSHALLER));
        return "TELEGRAM_ID_CONFLICT".equals(code) ? code : null;
    }

    private static IdentityOperationRejectionReason definiteRejection(Status.Code code) {
        return switch (code) {
            case PERMISSION_DENIED, UNAUTHENTICATED ->
                    IdentityOperationRejectionReason
                            .IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED;
            case ALREADY_EXISTS, INVALID_ARGUMENT, FAILED_PRECONDITION ->
                    IdentityOperationRejectionReason
                            .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT;
            default -> null; // ABORTED, transport and timeout never prove absence or rollback.
        };
    }

    private Operation advance(Operation operation) {
        if (operation.phase() == Phase.AUTH_PREPARED) {
            // A thrown/unknown outcome is not a rollback proof. Leave the operation fenced;
            // the next recovery reads its durable phase before doing anything else.
            try {
                operation = store.apply(operation);
            } catch (io.grpc.StatusRuntimeException rejected) {
                if (rejected.getStatus().getCode() != Status.Code.ABORTED) throw rejected;
                // The store's transactional proxy has finished a definite business rollback.
                // An unknown SQL/commit failure never enters this branch.
                operation = store.rejectedBeforeCommit(operation);
            }
        }
        if (operation.phase() == Phase.FAILED_SAFE
                && operation.receipts().isEmpty()
                && operation.prepared() != null
                && operation.prepared().getPhase()
                        == IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED) {
            outsideTransaction();
            var failed =
                    auth.fail(
                            FailIdentityOperationRequest.newBuilder()
                                    .setOperationId(operation.id())
                                    .setCommandHash(operation.command().getCommandHash())
                                    .setExpectedOperationRevision(
                                            operation.prepared().getOperationRevision())
                                    .setErrorCode("ACADEMIC_CONFLICT")
                                    .build());
            validate(
                    failed, operation, IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
            return store.failedSafe(operation, failed);
        }
        if (operation.phase() == Phase.LOCAL_COMMITTED) {
            validateReceipts(operation);
            outsideTransaction();
            var committed =
                    auth.commit(
                            CommitIdentityOperationRequest.newBuilder()
                                    .setOperationId(operation.id())
                                    .setCommandHash(operation.command().getCommandHash())
                                    .setExpectedOperationRevision(
                                            operation.prepared().getOperationRevision())
                                    .addAllReceipts(operation.receipts())
                                    .build());
            if (committed.getPhase()
                    == IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE) {
                validate(
                        committed,
                        operation,
                        IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
                // Keep already committed local effects and both fences. Reconciliation
                // requires a fresh authorized operation; old privileges never reactivate.
                return store.failedSafe(operation, committed);
            }
            validate(
                    committed,
                    operation,
                    IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
            if (!committed.getTargetsList().equals(operation.prepared().getTargetsList())) {
                throw unavailable();
            }
            if (committed.getCommittedCatalogVersion() <= 0) throw unavailable();
            try {
                catalog.await(
                        committed.getCommittedCatalogVersion(),
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
            } catch (RuntimeException pendingCatalog) {
                // Auth has committed, but local lists cannot publish an incomplete projection.
                // Recovery repeats this idempotent commit and catch-up without releasing fences.
                return operation;
            }
            operation = store.succeeded(operation, committed);
        }
        return operation;
    }

    private static void validate(
            IdentityOperationResult result, Operation operation, IdentityOperationPhase phase) {
        if (!result.getOperationId().equals(operation.id())
                || !result.getCommandHash().equals(operation.command().getCommandHash())
                || result.getKind() != operation.command().getKind()
                || result.getOperationRevision() <= 0
                || result.getPhase() != phase
                || result.getTargetsCount() != operation.command().getTargetsCount())
            throw unavailable();
        long previous = 0;
        for (int n = 0; n < result.getTargetsCount(); n++) {
            var target = result.getTargets(n);
            var requested = operation.command().getTargets(n);
            if (target.getUserId() <= previous
                    || target.getFinalRolesVersion() <= 0
                    || target.getExpectedAcademicRevision()
                            != requested.getExpectedAcademicRevision()
                    || requested.getUserId() != 0 && requested.getUserId() != target.getUserId()
                    || target.getFinalRolesVersion() != requested.getExpectedRolesVersion() + 1)
                throw unavailable();
            previous = target.getUserId();
            if (target.getCredentialsVersion() <= 0
                    || target.getTelegramBindingVersion() <= 0
                    || target.getGrantsCount() != requested.getDesiredGrantsCount())
                throw unavailable();
            for (int grantIndex = 0; grantIndex < target.getGrantsCount(); grantIndex++) {
                var actual = target.getGrants(grantIndex);
                var desired = requested.getDesiredGrants(grantIndex);
                if (actual.getGrantId() <= 0
                        || desired.getGrantId() != 0 && actual.getGrantId() != desired.getGrantId()
                        || !actual.getRole().equals(desired.getRole())
                        || !actual.getStatus().equals(desired.getStatus())
                        || actual.hasGroupId() != desired.hasGroupId()
                        || actual.hasGroupId() && actual.getGroupId() != desired.getGroupId())
                    throw unavailable();
            }
        }
    }

    private static void validateReceipts(Operation operation) {
        if (operation.prepared() == null
                || operation.receipts().size() != operation.prepared().getTargetsCount())
            throw unavailable();
        for (int n = 0; n < operation.receipts().size(); n++) {
            var receipt = operation.receipts().get(n);
            var target = operation.prepared().getTargets(n);
            if (receipt.getUserId() != target.getUserId()
                    || receipt.getPreviousAcademicRevision() != target.getExpectedAcademicRevision()
                    || receipt.getCommittedAcademicRevision()
                            != receipt.getPreviousAcademicRevision() + 1
                    || receipt.getAppliedAuthRolesVersion() != target.getFinalRolesVersion())
                throw unavailable();
        }
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("identity operation requires recovery")
                .asRuntimeException();
    }

    public enum Phase {
        ACCEPTED,
        AUTH_PREPARED,
        LOCAL_COMMITTED,
        SUCCEEDED,
        REJECTED,
        FAILED_SAFE
    }

    /**
     * Payload is the fixed canonical nonsecret domain command, never HTTP headers or credentials.
     */
    public record AcceptedCommand(
            PrepareIdentityOperationRequest authCommand,
            ByteString localCommandHash,
            ByteString localPayload) {
        public AcceptedCommand {
            if (authCommand == null
                    || localPayload == null
                    || localPayload.isEmpty()
                    || localPayload.size() > 262144
                    || localCommandHash == null
                    || !hash(localPayload.toByteArray()).equals(localCommandHash)
                    || !hash(authCommand.toBuilder().clearCommandHash().build().toByteArray())
                            .equals(authCommand.getCommandHash())) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("canonical identity command required")
                        .asRuntimeException();
            }
        }
    }

    private static ByteString hash(byte[] payload) {
        try {
            return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public record Operation(
            String id,
            Phase phase,
            PrepareIdentityOperationRequest command,
            IdentityOperationResult prepared,
            List<AcademicIdentityCommitReceipt> receipts,
            String rejectionCode) {
        public Operation(
                String id,
                Phase phase,
                PrepareIdentityOperationRequest command,
                IdentityOperationResult prepared,
                List<AcademicIdentityCommitReceipt> receipts) {
            this(id, phase, command, prepared, receipts, null);
        }

        public Operation {
            receipts = List.copyOf(receipts);
        }
    }

    public interface Store {
        Operation accept(AcceptedCommand command);

        Operation find(String operationId);

        Operation prepared(Operation expected, IdentityOperationResult prepared);

        Operation nooped(Operation expected, IdentityOperationResult nooped);

        Operation apply(Operation expected);

        Operation succeeded(Operation expected, IdentityOperationResult committed);

        Operation failedSafe(Operation expected, IdentityOperationResult failed);

        Operation rejectedBeforeCommit(Operation expected);

        RejectUnpreparedIdentityOperationRequest prepareRejection(
                Operation expected, IdentityOperationRejectionReason reason);

        default RejectUnpreparedIdentityOperationRequest prepareRejection(
                Operation expected, IdentityOperationRejectionReason reason, String code) {
            return prepareRejection(expected, reason);
        }

        RejectUnpreparedIdentityOperationRequest pendingRejection(Operation expected);

        Operation rejected(Operation expected, IdentityOperationResult rejected);
    }

    public interface Auth {
        IdentityOperationResult prepare(PrepareIdentityOperationRequest request);

        IdentityOperationResult get(GetIdentityOperationRequest request);

        IdentityOperationResult commit(CommitIdentityOperationRequest request);

        IdentityOperationResult fail(FailIdentityOperationRequest request);

        IdentityOperationResult reject(RejectUnpreparedIdentityOperationRequest request);
    }
}
