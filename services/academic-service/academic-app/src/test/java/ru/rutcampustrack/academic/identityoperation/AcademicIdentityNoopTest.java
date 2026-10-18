package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.util.List;

class AcademicIdentityNoopTest {
    private final AcademicIdentityCoordinator.Store store =
            mock(AcademicIdentityCoordinator.Store.class);
    private final AcademicIdentityCoordinator.Auth auth =
            mock(AcademicIdentityCoordinator.Auth.class);
    private final IdentityCatalogSynchronizer catalog = mock(IdentityCatalogSynchronizer.class);
    private final AcademicIdentityCoordinator coordinator =
            new AcademicIdentityCoordinator(store, auth, catalog);

    @Test
    void authoritativeNoopClosesAcceptedWithoutApplyCommitOrCatalogMutation() throws Exception {
        var command = command();
        var accepted = operation(command, AcademicIdentityCoordinator.Phase.ACCEPTED, null);
        var noop = noop(command);
        var terminal = operation(command, AcademicIdentityCoordinator.Phase.SUCCEEDED, noop);
        when(store.accept(command)).thenReturn(accepted);
        when(auth.prepare(command.authCommand())).thenReturn(noop);
        when(store.nooped(accepted, noop)).thenReturn(terminal);
        assertThat(coordinator.execute(command)).isSameAs(terminal);
        verify(store, never()).prepared(any(), any());
        verify(store, never()).apply(any());
        verify(auth, never()).commit(any());
        verifyNoInteractions(catalog);
    }

    @Test
    void lostNoopReplyRecoversExactTerminalWithoutPreparingWithNewActor() throws Exception {
        var command = command();
        var accepted = operation(command, AcademicIdentityCoordinator.Phase.ACCEPTED, null);
        var noop = noop(command);
        var terminal = operation(command, AcademicIdentityCoordinator.Phase.SUCCEEDED, noop);
        when(store.find(accepted.id())).thenReturn(accepted);
        when(auth.get(any())).thenReturn(noop);
        when(store.nooped(accepted, noop)).thenReturn(terminal);
        assertThat(coordinator.recover(accepted.id())).isSameAs(terminal);
        verify(auth, never()).prepare(any());
        verify(store, never()).apply(any());
        verify(auth, never()).commit(any());
        verifyNoInteractions(catalog);
    }

    @Test
    void changedNoopTupleCannotCloseOrErasePositiveWork() throws Exception {
        var command = command();
        var accepted = operation(command, AcademicIdentityCoordinator.Phase.ACCEPTED, null);
        var changed =
                noop(command).toBuilder()
                        .setTargets(
                                0, noop(command).getTargets(0).toBuilder().setFinalRolesVersion(5))
                        .build();
        when(store.accept(command)).thenReturn(accepted);
        when(auth.prepare(command.authCommand())).thenReturn(changed);
        assertThatThrownBy(() -> coordinator.execute(command))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e ->
                                assertThat(e.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        verify(store, never()).nooped(any(), any());
        verify(store, never()).apply(any());
        verify(auth, never()).commit(any());
    }

    @Test
    void ordinaryRoleCommandCannotMasqueradeAsAdminNoop() throws Exception {
        var original = command();
        var builder =
                original.authCommand().toBuilder()
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE)
                        .clearCommandHash();
        builder.setCommandHash(hash(builder.build().toByteArray()));
        var command =
                new AcademicIdentityCoordinator.AcceptedCommand(
                        builder.build(), original.localCommandHash(), original.localPayload());
        var accepted = operation(command, AcademicIdentityCoordinator.Phase.ACCEPTED, null);
        when(store.accept(command)).thenReturn(accepted);
        when(auth.prepare(command.authCommand())).thenReturn(noop(command));
        assertThatThrownBy(() -> coordinator.execute(command))
                .isInstanceOf(StatusRuntimeException.class);
        verify(store, never()).nooped(any(), any());
        verify(store, never()).apply(any());
    }

    private static AcademicIdentityCoordinator.AcceptedCommand command() throws Exception {
        var builder =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId("00000000-0000-0000-0000-000000000001")
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(8)
                                        .setExpectedRolesVersion(4)
                                        .setExpectedCredentialsVersion(1)
                                        .setExpectedTelegramBindingVersion(1)
                                        .setExpectedAcademicRevision(2)
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.getDefaultInstance())
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(12)
                                                        .setRole("TEACHER")
                                                        .setStatus("ACTIVE")));
        builder.setCommandHash(hash(builder.build().toByteArray()));
        var local = ByteString.copyFromUtf8("{}");
        return new AcademicIdentityCoordinator.AcceptedCommand(
                builder.build(), hash(local.toByteArray()), local);
    }

    private static IdentityOperationResult noop(
            AcademicIdentityCoordinator.AcceptedCommand command) {
        var target = command.authCommand().getTargets(0);
        return IdentityOperationResult.newBuilder()
                .setOperationId(command.authCommand().getOperationId())
                .setCommandHash(command.authCommand().getCommandHash())
                .setKind(command.authCommand().getKind())
                .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP)
                .setOperationRevision(1)
                .addTargets(
                        PreparedIdentityTarget.newBuilder()
                                .setUserId(8)
                                .setLogin("teacher-1234")
                                .setFinalRolesVersion(4)
                                .setCredentialsVersion(1)
                                .setTelegramBindingVersion(1)
                                .setExpectedAcademicRevision(2)
                                .addAllGrants(target.getDesiredGrantsList()))
                .build();
    }

    private static AcademicIdentityCoordinator.Operation operation(
            AcademicIdentityCoordinator.AcceptedCommand command,
            AcademicIdentityCoordinator.Phase phase,
            IdentityOperationResult result) {
        return new AcademicIdentityCoordinator.Operation(
                command.authCommand().getOperationId(),
                phase,
                command.authCommand(),
                result,
                List.of());
    }

    private static ByteString hash(byte[] bytes) throws Exception {
        return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
