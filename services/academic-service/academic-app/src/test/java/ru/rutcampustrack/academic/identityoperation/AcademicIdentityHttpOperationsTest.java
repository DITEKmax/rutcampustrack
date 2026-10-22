package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

class AcademicIdentityHttpOperationsTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final JdbcAcademicIdentityOperationStore store =
            mock(JdbcAcademicIdentityOperationStore.class);
    private final AcademicIdentityCoordinator coordinator = mock(AcademicIdentityCoordinator.class);
    private final AcademicIdentityHttpOperations http =
            new AcademicIdentityHttpOperations(request, response, store, coordinator);
    private final AcademicIdentityLocalCommand.ApiIntent intent =
            new AcademicIdentityLocalCommand.ApiIntent("CREATE", 0, "0".repeat(64));

    @Test
    void unknownPrepareOutcomeReturnsDurablePendingAndOperationHeaders() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var accepted = accepted();
        when(coordinator.execute(accepted)).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        when(store.findForActor(ID))
                .thenReturn(operation(accepted, AcademicIdentityCoordinator.Phase.ACCEPTED));
        assertThatThrownBy(() -> http.execute(intent, id -> accepted))
                .isInstanceOfSatisfying(
                        AcademicIdentityPendingException.class,
                        pending -> {
                            assertThat(pending.operation().operationId())
                                    .isEqualTo(UUID.fromString(ID));
                            assertThat(pending.operation().status())
                                    .isEqualTo(IdentityOperationHttpModels.Status.PENDING);
                        });
        verify(response).setHeader("X-Operation-Id", ID);
        verify(response).setHeader("Location", "/academic/identity-operations/" + ID);
    }

    @Test
    void unknownOutcomeWithoutLocalLedgerPreservesOriginalFailure() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var accepted = accepted();
        var original =
                Status.UNAVAILABLE.withDescription("prepare unavailable").asRuntimeException();
        when(coordinator.execute(accepted)).thenThrow(original);
        when(store.findForActor(ID)).thenThrow(Status.NOT_FOUND.asRuntimeException());
        assertThatThrownBy(() -> http.execute(intent, id -> accepted)).isSameAs(original);
    }

    @Test
    void sameKeyRaceReplaysWinnerAndHandlesItsUnknownOutcome() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var accepted = accepted();
        when(store.acceptedForActor(ID, intent)).thenReturn(null, accepted);
        when(coordinator.execute(accepted))
                .thenThrow(
                        Status.ALREADY_EXISTS.asRuntimeException(),
                        Status.DEADLINE_EXCEEDED.asRuntimeException());
        when(store.findForActor(ID))
                .thenReturn(operation(accepted, AcademicIdentityCoordinator.Phase.LOCAL_COMMITTED));
        assertThatThrownBy(() -> http.execute(intent, id -> accepted))
                .isInstanceOf(AcademicIdentityPendingException.class);
        verify(coordinator, times(2)).execute(accepted);
    }

    @Test
    void definiteLocalConflictBecomesDurableNegativeBeforeReturningOriginalFieldError() {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var denied =
                new ru.rutcampustrack.academic.exception.ConflictException(
                        "groupId", 7L, "headman changed");
        when(store.rejectPreflight(ID, intent)).thenReturn(true);
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        intent,
                                        id -> {
                                            throw denied;
                                        }))
                .isSameAs(denied);
        verify(store).rejectPreflight(ID, intent);
        verifyNoInteractions(coordinator);
    }

    @Test
    void definiteLocalSnapshotCasConflictIsNegativeButUnknownRemoteAbortedIsNot() {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var stale = Status.ABORTED.withDescription("headman changed").asRuntimeException();
        when(store.rejectPreflight(ID, intent)).thenReturn(true);
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        intent,
                                        id ->
                                                AcademicIdentityHttpOperations.localPreflight(
                                                        () -> {
                                                            throw stale;
                                                        })))
                .isSameAs(stale);
        verify(store).rejectPreflight(ID, intent);
        clearInvocations(store);
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        intent,
                                        id -> {
                                            throw stale;
                                        }))
                .isSameAs(stale);
        verify(store, never()).rejectPreflight(anyString(), any());
    }

    @Test
    void concurrentPositiveWinnerReturnsItsActualPendingStateInsteadOfFalseRejection()
            throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var accepted = accepted();
        when(store.acceptedForActor(ID, intent)).thenReturn(null, accepted);
        when(store.rejectPreflight(ID, intent)).thenReturn(false);
        when(coordinator.execute(accepted))
                .thenReturn(operation(accepted, AcademicIdentityCoordinator.Phase.AUTH_PREPARED));
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        intent,
                                        id -> {
                                            throw new ru.rutcampustrack.academic.exception
                                                    .BadRequestException("stale preflight");
                                        }))
                .isInstanceOfSatisfying(
                        AcademicIdentityPendingException.class,
                        pending ->
                                assertThat(pending.operation().status())
                                        .isEqualTo(IdentityOperationHttpModels.Status.PENDING));
        verify(coordinator).execute(accepted);
    }

    @Test
    void unknownPreflightDatabaseOutcomeNeverCreatesNegativeReceipt() {
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        var unknown =
                new org.springframework.dao.DataAccessResourceFailureException("connection lost");
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        intent,
                                        id -> {
                                            throw unknown;
                                        }))
                .isSameAs(unknown);
        verify(store, never()).rejectPreflight(anyString(), any());
        verifyNoInteractions(coordinator);
    }

    @Test
    void blockedGroupAttemptBecomesRejectedBeforeReturningFailedOperationSelector() {
        var intent = new AcademicIdentityLocalCommand.ApiIntent("GROUP_RESTORE", 7, "0".repeat(64));
        var blocked =
                new ru.rutcampustrack.academic.exception.ConflictException(
                        "group recovery required",
                        java.util.Map.of("operationId", "00000000-0000-0000-0000-000000000002"));
        when(store.rejectPreflight(ID, intent)).thenReturn(true);
        assertThatThrownBy(
                        () ->
                                http.groupPreflight(
                                        UUID.fromString(ID),
                                        intent,
                                        () -> {
                                            throw blocked;
                                        },
                                        () -> {
                                            throw new AssertionError("no positive winner");
                                        }))
                .isSameAs(blocked);
        verify(store).rejectPreflight(ID, intent);
        assertThat(blocked.getExtras())
                .containsEntry("operationId", "00000000-0000-0000-0000-000000000002");
    }

    @Test
    void acceptedGroupWinnerIsReturnedInsteadOfFalsePreflightRejection() {
        var intent = new AcademicIdentityLocalCommand.ApiIntent("GROUP_RESTORE", 7, "0".repeat(64));
        var winner =
                mock(
                        ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore
                                .Operation.class);
        when(store.rejectPreflight(ID, intent)).thenReturn(false);
        assertThat(
                        http.groupPreflight(
                                UUID.fromString(ID),
                                intent,
                                () -> {
                                    throw Status.ABORTED.asRuntimeException();
                                },
                                () -> winner))
                .isSameAs(winner);
    }

    @Test
    void unknownGroupPreflightDatabaseOutcomeDoesNotMintNegativeReceipt() {
        var intent = new AcademicIdentityLocalCommand.ApiIntent("GROUP_RESTORE", 7, "0".repeat(64));
        var unknown =
                new org.springframework.dao.DataAccessResourceFailureException("connection lost");
        assertThatThrownBy(
                        () ->
                                http.groupPreflight(
                                        UUID.fromString(ID),
                                        intent,
                                        () -> {
                                            throw unknown;
                                        },
                                        () -> {
                                            throw new AssertionError();
                                        }))
                .isSameAs(unknown);
        verify(store, never()).rejectPreflight(anyString(), any());
    }

    @Test
    void definiteLocalAdminConflictKeepsCurrentProfileAndDurableNegativeReceipt() {
        var adminIntent =
                new AcademicIdentityLocalCommand.ApiIntent("ADMIN_ACCESS", 8, "0".repeat(64));
        var current =
                new ru.rutcampustrack.shared.security.admin.AdminAccessProfile(
                        8, true, "LIMITED", List.of("USERS_VIEW"), 3);
        var conflict =
                new ru.rutcampustrack.shared.security.admin.AdminAccessException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "ADMIN_ACCESS_CONFLICT",
                        current);
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        when(store.rejectPreflight(ID, adminIntent)).thenReturn(true);
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        adminIntent,
                                        id ->
                                                AcademicIdentityHttpOperations.localPreflight(
                                                        () -> {
                                                            throw conflict;
                                                        })))
                .isSameAs(conflict);
        assertThat(conflict.getBody().getProperties()).containsEntry("currentProfile", current);
        verify(store).rejectPreflight(ID, adminIntent);
        verifyNoInteractions(coordinator);
    }

    @Test
    void localAdminAuthorityUnavailableCannotBecomeNegativeReceipt() {
        var adminIntent =
                new AcademicIdentityLocalCommand.ApiIntent("ADMIN_ACCESS", 8, "0".repeat(64));
        var unavailable =
                ru.rutcampustrack.shared.security.admin.AdminAccessException.unavailable();
        when(request.getHeader("Idempotency-Key")).thenReturn(ID);
        assertThatThrownBy(
                        () ->
                                http.execute(
                                        adminIntent,
                                        id ->
                                                AcademicIdentityHttpOperations.localPreflight(
                                                        () -> {
                                                            throw unavailable;
                                                        })))
                .isSameAs(unavailable);
        verify(store, never()).rejectPreflight(anyString(), any());
        verifyNoInteractions(coordinator);
    }

    private static AcademicIdentityCoordinator.AcceptedCommand accepted() throws Exception {
        var builder =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(ID)
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE);
        builder.setCommandHash(
                ByteString.copyFrom(
                        MessageDigest.getInstance("SHA-256")
                                .digest(builder.build().toByteArray())));
        byte[] local = {1};
        return new AcademicIdentityCoordinator.AcceptedCommand(
                builder.build(),
                ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(local)),
                ByteString.copyFrom(local));
    }

    private static AcademicIdentityCoordinator.Operation operation(
            AcademicIdentityCoordinator.AcceptedCommand accepted,
            AcademicIdentityCoordinator.Phase phase) {
        return new AcademicIdentityCoordinator.Operation(
                ID, phase, accepted.authCommand(), null, List.of());
    }
}
