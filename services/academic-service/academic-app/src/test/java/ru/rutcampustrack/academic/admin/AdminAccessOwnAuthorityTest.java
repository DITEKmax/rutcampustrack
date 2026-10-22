package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityPendingException;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.shared.security.admin.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class AdminAccessOwnAuthorityTest {
    private final AcademicAdminAccess access = mock(AcademicAdminAccess.class);
    private final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
    private final AcademicUserIdentityOperations operations =
            mock(AcademicUserIdentityOperations.class);
    private AdminAccessService service;
    private final CheckAdminAccessResponse actor =
            CheckAdminAccessResponse.newBuilder()
                    .setActorUserId(99)
                    .setSessionVersion(1)
                    .setRolesVersion(4)
                    .setProfile(
                            AuthorityAdminProfile.newBuilder()
                                    .setAssigned(true)
                                    .setMode("FULL")
                                    .setVersion(1))
                    .build();

    @BeforeEach
    void setup() {
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        call -> {
                            if (call.getMethod().getName().equals("queryForObject"))
                                return Boolean.TRUE;
                            return RETURNS_DEFAULTS.answer(call);
                        });
        service =
                new AdminAccessService(
                        jdbc,
                        mock(AdminAccessJdbcAuthority.class),
                        access,
                        new RequestContext(),
                        null,
                        null,
                        null);
        ReflectionTestUtils.setField(service, "identities", auth);
        ReflectionTestUtils.setField(service, "operations", operations);
    }

    @Test
    void profileReadRequiresFreshFullAuthorityBeforeAndAfterTargetSnapshot() {
        when(auth.checkAdminAccess(eq(List.of()), eq(true), anyLong())).thenReturn(actor);
        when(auth.readServiceIdentity(eq(List.of(8L)), anyLong()))
                .thenReturn(Map.of(8L, identity()));
        assertThat(service.get(8))
                .isEqualTo(new AdminAccessProfile(8, true, "LIMITED", List.of("USERS_VIEW"), 3));
        var order = inOrder(auth);
        order.verify(auth).checkAdminAccess(eq(List.of()), eq(true), anyLong());
        order.verify(auth).readServiceIdentity(eq(List.of(8L)), anyLong());
        order.verify(auth).checkAdminAccess(eq(List.of()), eq(true), anyLong());
    }

    @Test
    void actorRevokedDuringReadCannotReceiveProfile() {
        when(auth.checkAdminAccess(eq(List.of()), eq(true), anyLong()))
                .thenReturn(actor)
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        when(auth.readServiceIdentity(eq(List.of(8L)), anyLong()))
                .thenReturn(Map.of(8L, identity()));
        assertThatThrownBy(() -> service.get(8))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e ->
                                assertThat(e.getStatus().getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    @Test
    void fencedTargetSnapshotNeverDisclosesSavedProfile() {
        when(auth.checkAdminAccess(eq(List.of()), eq(true), anyLong())).thenReturn(actor);
        when(auth.readServiceIdentity(eq(List.of(8L)), anyLong()))
                .thenReturn(
                        Map.of(
                                8L,
                                identity().toBuilder()
                                        .setAccountState(
                                                AuthorityAccountState
                                                        .AUTHORITY_ACCOUNT_STATE_FENCED)
                                        .build()));
        assertThatThrownBy(() -> service.get(8))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e ->
                                assertThat(e.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
    }

    @Test
    void pendingMutationKeepsItsOperationAndDoesNotReadSuccessProfile() {
        var input = new AdminAccessInput("LIMITED", List.of("USERS_VIEW"));
        var pending =
                new AcademicIdentityPendingException(
                        new IdentityOperationHttpModels.Operation(
                                UUID.randomUUID(), IdentityOperationHttpModels.Status.PENDING, 2));
        when(operations.adminAccess(8, input, 2L, false)).thenThrow(pending);
        assertThatThrownBy(() -> service.put(8, input, 2L)).isSameAs(pending);
        verifyNoInteractions(auth);
    }

    @Test
    void completedMutationResponseReadsCurrentAuthProfileIncludingNoopReplay() {
        when(auth.checkAdminAccess(eq(List.of()), eq(true), anyLong())).thenReturn(actor);
        when(auth.readServiceIdentity(eq(List.of(8L)), anyLong()))
                .thenReturn(Map.of(8L, identity()));
        var input = new AdminAccessInput("LIMITED", List.of("USERS_VIEW"));
        assertThat(service.put(8, input, 2L).version()).isEqualTo(3);
        verify(operations).adminAccess(8, input, 2L, false);
        verify(auth).readServiceIdentity(eq(List.of(8L)), anyLong());
    }

    @Test
    void malformedOrAbsentAssignmentCannotDefaultToFull() {
        assertThat(
                        AdminAccessService.profile(8, AuthorityAdminProfile.getDefaultInstance())
                                .assigned())
                .isFalse();
        assertThatThrownBy(
                        () ->
                                AdminAccessService.profile(
                                        8,
                                        AuthorityAdminProfile.newBuilder()
                                                .setAssigned(true)
                                                .setVersion(1)
                                                .build()))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e ->
                                assertThat(e.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        assertThatThrownBy(
                        () ->
                                AdminAccessService.profile(
                                        8,
                                        AuthorityAdminProfile.newBuilder()
                                                .setAssigned(true)
                                                .setMode("LIMITED")
                                                .setVersion(1)
                                                .addPermissions("UNKNOWN")
                                                .build()))
                .isInstanceOf(StatusRuntimeException.class);
    }

    private static IdentityAuthoritySnapshot identity() {
        return IdentityAuthoritySnapshot.newBuilder()
                .setUserId(8)
                .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                .addGrants(
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(12)
                                .setRole("ADMIN")
                                .setStatus("ACTIVE"))
                .setAdminProfile(
                        AuthorityAdminProfile.newBuilder()
                                .setAssigned(true)
                                .setMode("LIMITED")
                                .addPermissions("USERS_VIEW")
                                .setVersion(3))
                .build();
    }
}
