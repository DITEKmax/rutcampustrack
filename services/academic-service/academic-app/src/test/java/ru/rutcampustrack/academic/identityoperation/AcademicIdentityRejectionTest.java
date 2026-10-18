package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.util.List;

class AcademicIdentityRejectionTest {
    private final AcademicIdentityCoordinator.Store store =
            mock(AcademicIdentityCoordinator.Store.class);
    private final AcademicIdentityCoordinator.Auth auth =
            mock(AcademicIdentityCoordinator.Auth.class);
    private final AcademicIdentityCoordinator coordinator =
            new AcademicIdentityCoordinator(store, auth, mock(IdentityCatalogSynchronizer.class));

    @Test
    void definiteDenialIsTerminalOnlyAfterExactAuthNegativeReservation() throws Exception {
        var accepted = command();
        var operation = operation(accepted, AcademicIdentityCoordinator.Phase.ACCEPTED);
        var request =
                RejectUnpreparedIdentityOperationRequest.newBuilder()
                        .setCanonicalPrepare(accepted.authCommand())
                        .setReason(
                                IdentityOperationRejectionReason
                                        .IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED)
                        .build();
        var rejected =
                IdentityOperationResult.newBuilder()
                        .setOperationId(operation.id())
                        .setCommandHash(operation.command().getCommandHash())
                        .setKind(operation.command().getKind())
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED)
                        .setOperationRevision(1)
                        .build();
        var terminal = operation(accepted, AcademicIdentityCoordinator.Phase.REJECTED);
        when(store.accept(accepted)).thenReturn(operation);
        when(auth.prepare(operation.command()))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        when(store.prepareRejection(operation, request.getReason())).thenReturn(request);
        when(auth.reject(request)).thenReturn(rejected);
        when(store.rejected(operation, rejected)).thenReturn(terminal);
        assertThat(coordinator.execute(accepted)).isSameAs(terminal);
        var ordered = inOrder(store, auth);
        ordered.verify(store).prepareRejection(operation, request.getReason());
        ordered.verify(auth).reject(request);
        ordered.verify(store).rejected(operation, rejected);
        verify(store, never()).apply(any());
        verify(auth, never()).commit(any());
    }

    @Test
    void unknownPrepareOutcomeDoesNotReserveRejectionOrDropPendingCommand() throws Exception {
        var accepted = command();
        var operation = operation(accepted, AcademicIdentityCoordinator.Phase.ACCEPTED);
        when(store.accept(accepted)).thenReturn(operation);
        when(auth.prepare(operation.command())).thenThrow(Status.ABORTED.asRuntimeException());
        assertThatThrownBy(() -> coordinator.execute(accepted))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        verify(store, never()).prepareRejection(any(), any());
        verify(store, never()).rejected(any(), any());
        verify(auth, never()).reject(any());
    }

    @Test
    void lostNegativeResponseIsRecoveredFromDurableIntentWithoutFreshPrepare() throws Exception {
        var accepted = command();
        var operation = operation(accepted, AcademicIdentityCoordinator.Phase.ACCEPTED);
        var request =
                RejectUnpreparedIdentityOperationRequest.newBuilder()
                        .setCanonicalPrepare(operation.command())
                        .setReason(
                                IdentityOperationRejectionReason
                                        .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT)
                        .build();
        var rejected =
                IdentityOperationResult.newBuilder()
                        .setOperationId(operation.id())
                        .setCommandHash(operation.command().getCommandHash())
                        .setKind(operation.command().getKind())
                        .setOperationRevision(1)
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED)
                        .build();
        when(store.find(operation.id())).thenReturn(operation);
        when(store.pendingRejection(operation)).thenReturn(request);
        when(auth.reject(request)).thenReturn(rejected);
        var terminal = operation(accepted, AcademicIdentityCoordinator.Phase.REJECTED);
        when(store.rejected(operation, rejected)).thenReturn(terminal);
        assertThat(coordinator.recover(operation.id())).isSameAs(terminal);
        verify(auth, never()).get(any());
        verify(auth, never()).prepare(any());
        verify(store, never()).apply(any());
    }

    private static AcademicIdentityCoordinator.AcceptedCommand command() throws Exception {
        var builder =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId("3ada6f52-35fa-4cbd-a2e8-7f562e108dc9")
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE);
        builder.setCommandHash(hash(builder.build().toByteArray()));
        var payload = ByteString.copyFromUtf8("{}");
        return new AcademicIdentityCoordinator.AcceptedCommand(
                builder.build(), hash(payload.toByteArray()), payload);
    }

    private static ByteString hash(byte[] value) throws Exception {
        return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static AcademicIdentityCoordinator.Operation operation(
            AcademicIdentityCoordinator.AcceptedCommand command,
            AcademicIdentityCoordinator.Phase phase) {
        return new AcademicIdentityCoordinator.Operation(
                command.authCommand().getOperationId(),
                phase,
                command.authCommand(),
                null,
                List.of());
    }
}
