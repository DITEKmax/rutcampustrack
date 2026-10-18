package ru.rutcampustrack.academic.identityoperation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.academic.contract.api.IdentityOperationApi;
import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.UUID;

@RestController
public class AcademicIdentityOperationController implements IdentityOperationApi {
    private final JdbcAcademicIdentityOperationStore store;
    private final ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore groupStore;
    private final ru.rutcampustrack.academic.groupavailability.GroupAvailabilityService groups;

    @org.springframework.beans.factory.annotation.Autowired
    private AcademicIdentityReconciliationService users;

    public AcademicIdentityOperationController(
            JdbcAcademicIdentityOperationStore store,
            ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore groupStore,
            ru.rutcampustrack.academic.groupavailability.GroupAvailabilityService groups) {
        this.store = store;
        this.groupStore = groupStore;
        this.groups = groups;
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN},
            adminShared = true)
    public ResponseEntity<IdentityOperationHttpModels.Operation> getIdentityOperation(
            UUID operationId) {
        var operation =
                groupStore.exists(operationId)
                        ? groups.statusForCurrentAdmin(operationId)
                        : store.isGroupPreflight(operationId)
                                ? groups.rejectedGroupStatusForCurrentAdmin(operationId)
                                : store.publicStatusForActor(operationId.toString());
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Operation-Id", operationId.toString())
                .body(operation);
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN},
            adminShared = true)
    public ResponseEntity<IdentityOperationHttpModels.Operation> reconcileGroupOperation(
            UUID operationId, IdentityOperationHttpModels.ReconcileRequest request) {
        boolean group = groupStore.exists(operationId);
        if (group
                && request != null
                && (request.archivePreviewDigest() != null || request.password() != null))
            throw io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("group recovery has no body")
                    .asRuntimeException();
        var result = group ? groups.reconcile(operationId) : users.reconcile(operationId, request);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Operation-Id", result.operationId().toString())
                .header("Location", AcademicIdentityHttpOperations.location(result.operationId()))
                .body(result);
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN},
            admin = {"USERS_ARCHIVE"})
    public ResponseEntity<ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview>
            getArchiveReconciliationPreview(UUID operationId) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(users.preview(operationId));
    }
}
