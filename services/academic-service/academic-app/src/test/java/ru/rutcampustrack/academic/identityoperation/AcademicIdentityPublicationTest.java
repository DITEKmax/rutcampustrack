package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.authoperations.grpc.*;

import java.util.List;

class AcademicIdentityPublicationTest {
    @Test
    void authCommitWithoutAppliedCatalogKeepsLocalEffectsFencedForRecovery() {
        var store = mock(AcademicIdentityCoordinator.Store.class);
        var auth = mock(AcademicIdentityCoordinator.Auth.class);
        var catalog = mock(IdentityCatalogSynchronizer.class);
        var command =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId("op")
                        .setCommandHash(ByteString.copyFromUtf8("hash"))
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(7)
                                        .setExpectedRolesVersion(4)
                                        .setExpectedAcademicRevision(2))
                        .build();
        var prepared =
                IdentityOperationResult.newBuilder()
                        .setOperationId("op")
                        .setCommandHash(command.getCommandHash())
                        .setKind(command.getKind())
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED)
                        .setOperationRevision(1)
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(7)
                                        .setExpectedAcademicRevision(2)
                                        .setFinalRolesVersion(5)
                                        .setCredentialsVersion(1)
                                        .setTelegramBindingVersion(1))
                        .build();
        var operation =
                new AcademicIdentityCoordinator.Operation(
                        "op",
                        AcademicIdentityCoordinator.Phase.LOCAL_COMMITTED,
                        command,
                        prepared,
                        List.of(
                                AcademicIdentityCommitReceipt.newBuilder()
                                        .setUserId(7)
                                        .setPreviousAcademicRevision(2)
                                        .setCommittedAcademicRevision(3)
                                        .setAppliedAuthRolesVersion(5)
                                        .build()));
        var committed =
                prepared.toBuilder()
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED)
                        .setCommittedCatalogVersion(19)
                        .build();
        when(store.find("op")).thenReturn(operation);
        when(auth.commit(any())).thenReturn(committed);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(catalog).await(eq(19L), anyLong());
        assertThat(new AcademicIdentityCoordinator(store, auth, catalog).recover("op"))
                .isSameAs(operation);
        verify(store, never()).succeeded(any(), any());
        verify(store, never()).apply(any());
        verify(auth, never()).prepare(any());
    }
}
