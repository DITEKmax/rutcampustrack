package ru.rutcampustrack.academic.groupavailability;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authoperations.grpc.*;

import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** The fixed group lifecycle protocol. Each RPC lies between independent local transactions. */
@Service
public class GroupAvailabilityCoordinator {
    private final GroupAvailabilityStore store;
    private final AuthCredentialReaderInvalidationClient auth;
    private final IdentityCatalogSynchronizer catalog;
    private final RequestContext context;

    public GroupAvailabilityCoordinator(
            GroupAvailabilityStore store,
            AuthCredentialReaderInvalidationClient auth,
            IdentityCatalogSynchronizer catalog,
            RequestContext context) {
        this.store = store;
        this.auth = auth;
        this.catalog = catalog;
        this.context = context;
    }

    public GroupAvailabilityStore.Operation execute(GroupAvailabilityStore.Operation operation) {
        outside();
        if (operation.phase() == GroupAvailabilityStore.Phase.ACCEPTED) {
            if (operation.rejection() != null) return reject(operation);
            if (operation.actor() != null) {
                var claims = context.getClaims();
                var accepted = operation.actor();
                if (claims == null
                        || claims.userId() != accepted.getUserId()
                        || !claims.sessionId().toString().equals(accepted.getSessionId())
                        || claims.sessionVersion() != accepted.getSessionVersion()
                        || claims.rolesVersion() != accepted.getRolesVersion())
                    throw Status.FAILED_PRECONDITION
                            .withDescription("original accepted actor session required for Prepare")
                            .asRuntimeException();
            }
            try {
                operation = adopt(operation, auth.prepare(operation.auth()));
            } catch (StatusRuntimeException failure) {
                var reason =
                        switch (failure.getStatus().getCode()) {
                            case UNAUTHENTICATED, PERMISSION_DENIED ->
                                    IdentityOperationRejectionReason
                                            .IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED;
                            case INVALID_ARGUMENT, FAILED_PRECONDITION, ALREADY_EXISTS ->
                                    IdentityOperationRejectionReason
                                            .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT;
                            default -> null;
                        };
                if (reason == null) throw failure;
                operation = store.rejectionIntent(operation, reason);
                return operation.phase() == GroupAvailabilityStore.Phase.ACCEPTED
                        ? reject(operation)
                        : advance(operation);
            }
        }
        return advance(operation);
    }

    public GroupAvailabilityStore.Operation recover(UUID id) {
        outside();
        var operation = store.find(id);
        if (operation.phase() == GroupAvailabilityStore.Phase.ACCEPTED) {
            if (operation.rejection() != null) return reject(operation);
            // NOT_FOUND never reserves absence; retain the guard and original accepted intent.
            operation =
                    adopt(
                            operation,
                            auth.get(
                                    GetCredentialReaderInvalidationRequest.newBuilder()
                                            .setOperationId(id.toString())
                                            .setCommandHash(operation.auth().getCommandHash())
                                            .build()));
        }
        return advance(operation);
    }

    private GroupAvailabilityStore.Operation reject(GroupAvailabilityStore.Operation operation) {
        var request =
                RejectUnpreparedCredentialReaderInvalidationRequest.newBuilder()
                        .setCanonicalPrepare(operation.auth())
                        .setReason(operation.rejection());
        if (operation.actor() != null) request.setActor(operation.actor());
        var result = auth.reject(request.build());
        validate(operation, result);
        if (result.getPhase()
                == CredentialReaderInvalidationPhase.CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED)
            return store.rejected(operation, result);
        // The Auth operation lock may have found a delayed positive Prepare instead.
        return advance(adopt(operation, result));
    }

    private GroupAvailabilityStore.Operation adopt(
            GroupAvailabilityStore.Operation operation, CredentialReaderInvalidationResult result) {
        validate(operation, result);
        return switch (result.getPhase()) {
            case CREDENTIAL_READER_INVALIDATION_PHASE_INVALIDATED ->
                    store.invalidated(operation, result);
            case CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE ->
                    store.failed(operation, result);
            case CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED -> store.rejected(operation, result);
            default -> throw AuthCredentialReaderInvalidationClient.unavailable();
        };
    }

    private GroupAvailabilityStore.Operation advance(GroupAvailabilityStore.Operation operation) {
        if (operation.phase() == GroupAvailabilityStore.Phase.AUTH_INVALIDATED) {
            if (!operation.localRejected()) {
                try {
                    operation = store.apply(operation);
                } catch (StatusRuntimeException failure) {
                    if (failure.getStatus().getCode() != Status.Code.ABORTED) throw failure;
                    operation = store.stopApply(operation);
                }
            }
            if (operation.phase() == GroupAvailabilityStore.Phase.AUTH_INVALIDATED
                    && operation.localRejected()) {
                var failed =
                        auth.fail(
                                FailCredentialReaderInvalidationRequest.newBuilder()
                                        .setOperationId(operation.id().toString())
                                        .setCommandHash(operation.auth().getCommandHash())
                                        .setExpectedOperationRevision(
                                                operation.result().getOperationRevision())
                                        .build());
                validate(operation, failed);
                if (failed.getPhase()
                        != CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE)
                    throw AuthCredentialReaderInvalidationClient.unavailable();
                return store.failed(operation, failed);
            }
        }
        if (operation.phase() == GroupAvailabilityStore.Phase.LOCAL_COMMITTED) {
            validateReceipts(operation);
            var request =
                    operation.receipts().toBuilder()
                            .setOperationId(operation.id().toString())
                            .setCommandHash(operation.auth().getCommandHash())
                            .setExpectedOperationRevision(operation.result().getOperationRevision())
                            .build();
            var result = auth.commit(request);
            validate(operation, result);
            if (result.getPhase()
                    == CredentialReaderInvalidationPhase
                            .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE)
                return store.failed(operation, result);
            if (result.getPhase()
                            != CredentialReaderInvalidationPhase
                                    .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED
                    || !result.getHeadmenList().equals(operation.result().getHeadmenList())
                    || result.getCommittedCatalogVersion() < 0
                    || result.getHeadmenCount() > 0 && result.getCommittedCatalogVersion() == 0)
                throw AuthCredentialReaderInvalidationClient.unavailable();
            catalog.await(
                    result.getCommittedCatalogVersion(),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(3));
            return store.succeeded(operation, result);
        }
        return operation;
    }

    private static void validate(
            GroupAvailabilityStore.Operation operation, CredentialReaderInvalidationResult result) {
        if (!result.getOperationId().equals(operation.id().toString())
                || !result.getCommandHash().equals(operation.auth().getCommandHash())
                || result.getOperationRevision() <= 0)
            throw AuthCredentialReaderInvalidationClient.unavailable();
        if (result.getPhase()
                == CredentialReaderInvalidationPhase
                        .CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED) {
            if (result.getHeadmenCount() != 0)
                throw AuthCredentialReaderInvalidationClient.unavailable();
            return;
        }
        var expected = new TreeMap<Long, CredentialReaderGroup>();
        for (var group : operation.auth().getGroupsList())
            if (group.getHeadmanUserId() > 0) {
                var old = expected.putIfAbsent(group.getHeadmanUserId(), group);
                if (old != null
                        && (old.getExpectedHeadmanRolesVersion()
                                        != group.getExpectedHeadmanRolesVersion()
                                || old.getExpectedHeadmanAcademicRevision()
                                        != group.getExpectedHeadmanAcademicRevision()))
                    throw AuthCredentialReaderInvalidationClient.unavailable();
            }
        if (result.getHeadmenCount() != expected.size())
            throw AuthCredentialReaderInvalidationClient.unavailable();
        int index = 0;
        for (var entry : expected.entrySet()) {
            var target = result.getHeadmen(index++);
            var group = entry.getValue();
            if (target.getUserId() != entry.getKey()
                    || target.getExpectedAcademicRevision()
                            != group.getExpectedHeadmanAcademicRevision()
                    || target.getFinalRolesVersion() != group.getExpectedHeadmanRolesVersion() + 1)
                throw AuthCredentialReaderInvalidationClient.unavailable();
        }
    }

    private static void validateReceipts(GroupAvailabilityStore.Operation operation) {
        var receipts = operation.receipts();
        if (receipts == null
                || receipts.getGroupsCount() != operation.command().groups().size()
                || receipts.getHeadmenCount() != operation.result().getHeadmenCount())
            throw AuthCredentialReaderInvalidationClient.unavailable();
        for (int n = 0; n < receipts.getGroupsCount(); n++) {
            var actual = receipts.getGroups(n);
            var expected = operation.command().groups().get(n);
            if (actual.getGroupId() != expected.id()
                    || actual.getPreviousGroupRevision() != expected.revision()
                    || actual.getCommittedGroupRevision() != expected.revision() + 1)
                throw AuthCredentialReaderInvalidationClient.unavailable();
        }
        for (int n = 0; n < receipts.getHeadmenCount(); n++) {
            var actual = receipts.getHeadmen(n);
            var expected = operation.result().getHeadmen(n);
            if (actual.getUserId() != expected.getUserId()
                    || actual.getPreviousAcademicRevision()
                            != expected.getExpectedAcademicRevision()
                    || actual.getCommittedAcademicRevision()
                            != expected.getExpectedAcademicRevision() + 1
                    || actual.getAppliedAuthRolesVersion() != expected.getFinalRolesVersion())
                throw AuthCredentialReaderInvalidationClient.unavailable();
        }
    }

    private static void outside() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw AuthCredentialReaderInvalidationClient.unavailable();
    }
}
