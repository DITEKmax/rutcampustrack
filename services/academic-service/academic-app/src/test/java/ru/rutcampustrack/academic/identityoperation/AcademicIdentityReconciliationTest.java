package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.AuthUserArchiveClient;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Focused command/replay/receipt risks; real Auth/JDBC acceptance has its own IT. */
class AcademicIdentityReconciliationTest {
    private static final UUID FAILED = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID RETRY = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private final JdbcAcademicIdentityOperationStore store =
            mock(JdbcAcademicIdentityOperationStore.class);
    private final AcademicIdentityHttpOperations http = mock(AcademicIdentityHttpOperations.class);
    private final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
    private final AuthUserArchiveClient proof = mock(AuthUserArchiveClient.class);
    private final AttendanceUserImpactGrpcClient attendance =
            mock(AttendanceUserImpactGrpcClient.class);
    private final UserArchiveRepository archives = mock(UserArchiveRepository.class);
    private final RequestContext context = mock(RequestContext.class);
    private final AcademicIdentityReconciliationService service =
            new AcademicIdentityReconciliationService(
                    store,
                    http,
                    auth,
                    proof,
                    attendance,
                    archives,
                    context,
                    new ObjectMapper().findAndRegisterModules(),
                    mock(PlatformTransactionManager.class));
    private final AtomicReference<AcademicIdentityCoordinator.AcceptedCommand> built =
            new AtomicReference<>();
    private final InternalJwtClaims actor =
            new InternalJwtClaims(
                    100, UUID.randomUUID(), 2, 10, "ADMIN", "ACTIVE", null, false, false);

    @BeforeEach
    void currentActorAndBuilder() {
        when(context.getClaims()).thenReturn(actor);
        when(auth.checkAdminAccess(anyCollection(), anyBoolean(), anyLong()))
                .thenReturn(
                        CheckAdminAccessResponse.newBuilder()
                                .setActorUserId(100)
                                .setSessionVersion(2)
                                .setRolesVersion(10)
                                .setProfile(
                                        AuthorityAdminProfile.newBuilder()
                                                .setAssigned(true)
                                                .setMode("FULL")
                                                .setVersion(1))
                                .build());
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(Map.of(8L, identity(4)));
        when(http.execute(any(AcademicIdentityLocalCommand.ApiIntent.class), any()))
                .thenAnswer(
                        call -> {
                            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> factory =
                                    call.getArgument(1);
                            var command = factory.apply(RETRY);
                            built.set(command);
                            return new AcademicIdentityCoordinator.Operation(
                                    RETRY.toString(),
                                    AcademicIdentityCoordinator.Phase.SUCCEEDED,
                                    command.authCommand(),
                                    null,
                                    List.of());
                        });
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityOperationKind.class,
            names = {
                "IDENTITY_OPERATION_KIND_CREATE",
                "IDENTITY_OPERATION_KIND_ROLE",
                "IDENTITY_OPERATION_KIND_TRANSFER",
                "IDENTITY_OPERATION_KIND_TELEGRAM_BINDING"
            })
    void recoveryKeepsAllocatedIdentityDesiredIntentAndOriginalDatedDomainMaterial(
            IdentityOperationKind kind) throws Exception {
        var source = source(kind, null, false);
        prepareSource(source, null);
        if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE)
            when(auth.readServiceIdentity(anyCollection(), anyLong()))
                    .thenReturn(Map.of(8L, identity(1)));
        assertThat(service.reconcile(FAILED, null).status())
                .isEqualTo(IdentityOperationHttpModels.Status.SUCCEEDED);
        var command = built.get().authCommand();
        assertThat(command.getKind())
                .isEqualTo(IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE);
        assertThat(command.hasCreateIdentity()).isFalse();
        assertThat(command.getTargets(0).getUserId()).isEqualTo(8);
        assertThat(command.getTargets(0).getSupersedesFailedOperationId())
                .isEqualTo(FAILED.toString());
        assertThat(command.getTargets(0).getDesiredGrantsList())
                .isEqualTo(source.failed().prepared().getTargets(0).getGrantsList());
        assertThat(command.getTargets(0).getExpectedAcademicRevision())
                .isEqualTo(kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE ? 0 : 2);
        var local =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .readValue(
                                built.get().localPayload().toByteArray(),
                                AcademicIdentityLocalCommand.class);
        assertThat(local.profile().userId())
                .isEqualTo(kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE ? 0 : 8);
        assertThat(local.effectiveDate()).isEqualTo(source.originalLocal().effectiveDate());
        assertThat(local.reason()).isEqualTo(source.originalLocal().reason());
        assertThat(local.intent().action()).isEqualTo("RECONCILE");
        verifyNoInteractions(proof, attendance);
    }

    @Test
    void pendingSnapshotOfExactFailedCreateKeepsOriginalAssignedUidAndUnappliedRevisionZero() {
        var source = source(IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE, null, false);
        prepareSource(source, null);
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(
                        Map.of(
                                8L,
                                identity(1).toBuilder()
                                        .setAccountState(
                                                AuthorityAccountState
                                                        .AUTHORITY_ACCOUNT_STATE_PENDING)
                                        .build()));
        assertThat(service.reconcile(FAILED, null).status())
                .isEqualTo(IdentityOperationHttpModels.Status.SUCCEEDED);
        var target = built.get().authCommand().getTargets(0);
        assertThat(target.getUserId()).isEqualTo(8);
        assertThat(target.getExpectedRolesVersion()).isEqualTo(1);
        assertThat(target.getExpectedAcademicRevision()).isZero();
        assertThat(built.get().authCommand().hasCreateIdentity()).isFalse();
    }

    @Test
    void pendingIsNeverAcceptedForAnOrdinaryUnpublishedIdentityOrChangedCreateOrigin() {
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(
                        Map.of(
                                8L,
                                identity(1).toBuilder()
                                        .setAccountState(
                                                AuthorityAccountState
                                                        .AUTHORITY_ACCOUNT_STATE_PENDING)
                                        .build()));
        prepareSource(
                source(IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE, null, false), null);
        assertThatThrownBy(() -> service.reconcile(FAILED, null))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        var create = source(IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE, null, false);
        var changed =
                new JdbcAcademicIdentityOperationStore.RecoverySource(
                        create.failedId(),
                        create.originalId(),
                        create.kind(),
                        create.businessApplied(),
                        create.originalLocal()
                                .forReconciliation(
                                        new AcademicIdentityLocalCommand.ApiIntent(
                                                "PROFILE", 8, "0".repeat(64)),
                                        null),
                        create.originalLocalHash(),
                        create.failed(),
                        create.revisions());
        prepareSource(changed, null);
        assertThatThrownBy(() -> service.reconcile(FAILED, null))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        assertThat(built.get()).isNull();
        verifyNoInteractions(proof, attendance);
    }

    @Test
    void
            missingHeadmanOriginRequiresBothCapabilitiesAndKnownOriginIsNotInferredFromProfileEquality() {
        var source =
                source(IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE, null, false);
        prepareSource(source, null);
        service.reconcile(FAILED, null);
        verify(auth, atLeastOnce())
                .checkAdminAccess(
                        eq(List.of("GROUPS_MANAGE", "USERS_MANAGE")), eq(false), anyLong());
        clearInvocations(auth);
        prepareSource(source, "USERS_MANAGE");
        service.reconcile(FAILED, null);
        verify(auth, atLeastOnce())
                .checkAdminAccess(eq(List.of("USERS_MANAGE")), eq(false), anyLong());
        verify(auth, never())
                .checkAdminAccess(eq(List.of("GROUPS_MANAGE")), anyBoolean(), anyLong());
    }

    @Test
    void acceptedArchiveReplayDoesNotMintAnotherProofOrRequireExpiredPreviewMaterial() {
        var source = source(IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE, null, true);
        prepareSource(source, null);
        when(http.execute(any(AcademicIdentityLocalCommand.ApiIntent.class), any()))
                .thenReturn(
                        new AcademicIdentityCoordinator.Operation(
                                RETRY.toString(),
                                AcademicIdentityCoordinator.Phase.SUCCEEDED,
                                source.failed().command(),
                                null,
                                List.of()));
        assertThat(
                        service.reconcile(
                                        FAILED,
                                        new IdentityOperationHttpModels.ReconcileRequest(
                                                "a".repeat(64), null))
                                .status())
                .isEqualTo(IdentityOperationHttpModels.Status.SUCCEEDED);
        verify(store, never()).reconciliationSource(any());
        verifyNoInteractions(proof, attendance, archives);
    }

    @Test
    void archiveAdmissionBindsFreshRetryAndNeverStoresPasswordInEitherCommand() {
        var source = source(IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE, null, true);
        prepareSource(source, null);
        var observed =
                new UserArchiveRepository.Local(
                        8, "b".repeat(64), "archived", 1, 0, 0, true, Instant.now());
        when(store.observeReconciliationArchive(source)).thenReturn(observed);
        when(proof.confirm(eq(8L), eq(RETRY), eq("a".repeat(64)), anyString()))
                .thenReturn("00000000-0000-0000-0000-000000000010");
        service.reconcile(
                FAILED,
                new IdentityOperationHttpModels.ReconcileRequest(
                        "a".repeat(64), "synthetic-transient-step-up"));
        assertThat(built.get().authCommand().getArchiveStepUpProtocolVersion()).isEqualTo(1);
        assertThat(built.get().authCommand().getArchivePreviewDigest()).isEqualTo("a".repeat(64));
        assertThat(built.get().localPayload().toStringUtf8())
                .doesNotContain("synthetic-transient-step-up");
        assertThat(built.get().authCommand().toString())
                .doesNotContain("synthetic-transient-step-up");
        verify(proof).confirm(8, RETRY, "a".repeat(64), "synthetic-transient-step-up");
    }

    @Test
    void previewChecksAuthAgainAfterAttendanceAndRejectsChangedTargetBeforeSaving() {
        var source = source(IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE, null, true);
        prepareSource(source, null);
        when(store.observeReconciliationArchive(source))
                .thenReturn(
                        new UserArchiveRepository.Local(
                                8, "b".repeat(64), "archived", 1, 0, 0, true, Instant.now()));
        when(attendance.preview(8))
                .thenReturn(
                        ru.rutcampustrack.attendance.grpc.UserImpactSnapshot.newBuilder()
                                .setUserId(8)
                                .setSnapshotDigest("c".repeat(64))
                                .setObservedAtEpochMs(1)
                                .build());
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(Map.of(8L, identity(4)), Map.of(8L, identity(5)));
        assertThatThrownBy(() -> service.preview(FAILED))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        verify(archives, never()).savePreview(anyLong(), any(), anyString(), any());
        var order = inOrder(auth, attendance);
        order.verify(auth).readServiceIdentity(anyCollection(), anyLong());
        order.verify(attendance).preview(8);
        order.verify(auth).readServiceIdentity(anyCollection(), anyLong());
    }

    @Test
    void partialMismatchedOrReorderedReceiptsCannotClassifyStagedEffects() {
        var a =
                PreparedIdentityTarget.newBuilder()
                        .setUserId(8)
                        .setExpectedAcademicRevision(2)
                        .setFinalRolesVersion(4)
                        .build();
        var b = a.toBuilder().setUserId(9).build();
        var prepared = IdentityOperationResult.newBuilder().addTargets(a).addTargets(b).build();
        var ra =
                AcademicIdentityCommitReceipt.newBuilder()
                        .setUserId(8)
                        .setPreviousAcademicRevision(2)
                        .setCommittedAcademicRevision(3)
                        .setAppliedAuthRolesVersion(4)
                        .build();
        var rb = ra.toBuilder().setUserId(9).build();
        java.util.function.Function<
                        List<AcademicIdentityCommitReceipt>, AcademicIdentityCoordinator.Operation>
                op =
                        receipts ->
                                new AcademicIdentityCoordinator.Operation(
                                        FAILED.toString(),
                                        AcademicIdentityCoordinator.Phase.FAILED_SAFE,
                                        PrepareIdentityOperationRequest.getDefaultInstance(),
                                        prepared,
                                        receipts);
        JdbcAcademicIdentityOperationStore.requireCompleteReceipts(op.apply(List.of(ra, rb)));
        for (var wrong :
                List.of(
                        List.of(ra),
                        List.of(rb, ra),
                        List.of(ra, rb.toBuilder().setAppliedAuthRolesVersion(3).build()))) {
            assertThatThrownBy(
                            () ->
                                    JdbcAcademicIdentityOperationStore.requireCompleteReceipts(
                                            op.apply(wrong)))
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.ABORTED));
        }
    }

    private void prepareSource(
            JdbcAcademicIdentityOperationStore.RecoverySource source, String permission) {
        when(store.reconciliationSource(FAILED)).thenReturn(source);
        when(store.reconciliationAuthorization(FAILED))
                .thenReturn(
                        new JdbcAcademicIdentityOperationStore.RecoveryAuthorization(
                                source.kind(), permission, false));
    }

    private static IdentityAuthoritySnapshot identity(long rv) {
        return IdentityAuthoritySnapshot.newBuilder()
                .setUserId(8)
                .setRolesVersion(rv)
                .setCredentialsVersion(1)
                .setTelegramBindingVersion(1)
                .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_FENCED)
                .build();
    }

    private static JdbcAcademicIdentityOperationStore.RecoverySource source(
            IdentityOperationKind kind, String permission, boolean staged) {
        boolean create = kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE;
        boolean archive = kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE;
        var grant =
                AdmissionGrant.newBuilder()
                        .setGrantId(22)
                        .setRole("STUDENT")
                        .setStatus(archive ? "ARCHIVED" : "ACTIVE")
                        .setGroupId(7)
                        .build();
        var target =
                IdentityTargetCommand.newBuilder()
                        .setUserId(create ? 0 : 8)
                        .setExpectedAcademicRevision(create ? 0 : 2)
                        .addDesiredGrants(grant.toBuilder().setGrantId(create ? 0 : 22))
                        .setDesiredAdminProfile(AdmissionAdminProfile.getDefaultInstance())
                        .setDesiredAccountState(
                                archive
                                        ? IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED
                                        : IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                        .build();
        var command =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(FAILED.toString())
                        .setKind(kind)
                        .addTargets(target)
                        .build();
        var prepared =
                IdentityOperationResult.newBuilder()
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE)
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(8)
                                        .setExpectedAcademicRevision(create ? 0 : 2)
                                        .setFinalRolesVersion(create ? 1 : 4)
                                        .setCredentialsVersion(1)
                                        .setTelegramBindingVersion(1)
                                        .addGrants(grant))
                        .build();
        var local =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent(
                                create ? "CREATE" : archive ? "ARCHIVE" : "PROFILE",
                                create ? 0 : 8,
                                "0".repeat(64),
                                permission),
                        archive
                                ? null
                                : new AcademicIdentityLocalCommand.Profile(
                                        create ? 0 : 8,
                                        "Recovery",
                                        "Target",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                        LocalDate.of(2026, 4, 20),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "fixed-intent",
                        List.of(),
                        archive
                                ? new AcademicIdentityLocalCommand.ArchiveAdmission(
                                        "d".repeat(64), "e".repeat(64), true)
                                : null);
        var receipts =
                staged
                        ? List.of(
                                AcademicIdentityCommitReceipt.newBuilder()
                                        .setUserId(8)
                                        .setPreviousAcademicRevision(2)
                                        .setCommittedAcademicRevision(3)
                                        .setAppliedAuthRolesVersion(4)
                                        .build())
                        : List.<AcademicIdentityCommitReceipt>of();
        var operation =
                new AcademicIdentityCoordinator.Operation(
                        FAILED.toString(),
                        AcademicIdentityCoordinator.Phase.FAILED_SAFE,
                        command,
                        prepared,
                        receipts);
        return new JdbcAcademicIdentityOperationStore.RecoverySource(
                FAILED,
                FAILED,
                kind,
                staged,
                local,
                ByteString.copyFrom(new byte[32]),
                operation,
                Map.of(8L, create ? 0L : staged ? 3L : 2L));
    }
}
