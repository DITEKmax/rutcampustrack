package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.AuthUserArchiveClient;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

class AcademicIdentityAdminConflictReadTest {
    @Test
    void fullRevokedAfterMachineSnapshotCannotDiscloseCurrentProfileInConflict() throws Exception {
        var fixture = new Fixture();
        when(fixture.auth.checkAdminAccess(eq(List.of()), eq(true), anyLong()))
                .thenAnswer(
                        call -> {
                            outside();
                            return fixture.actor;
                        })
                .thenAnswer(
                        call -> {
                            outside();
                            throw Status.PERMISSION_DENIED.asRuntimeException();
                        });
        assertThatThrownBy(
                        () ->
                                fixture.operations.adminAccess(
                                        8,
                                        new AdminAccessInput("LIMITED", List.of("USERS_VIEW")),
                                        1L,
                                        false))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e ->
                                assertThat(e.getStatus().getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
        verify(fixture.auth, times(2)).checkAdminAccess(eq(List.of()), eq(true), anyLong());
        verify(fixture.store, never()).rejectPreflight(anyString(), any());
        verifyNoInteractions(fixture.coordinator);
    }

    @Test
    void stillFullConflictPreservesProfileBodyAndOnlyThenReservesLocalNegative() throws Exception {
        var fixture = new Fixture();
        when(fixture.auth.checkAdminAccess(eq(List.of()), eq(true), anyLong()))
                .thenAnswer(
                        call -> {
                            outside();
                            return fixture.actor;
                        });
        when(fixture.store.rejectPreflight(anyString(), any())).thenReturn(true);
        assertThatThrownBy(
                        () ->
                                fixture.operations.adminAccess(
                                        8,
                                        new AdminAccessInput("LIMITED", List.of("USERS_VIEW")),
                                        1L,
                                        false))
                .isInstanceOfSatisfying(
                        AdminAccessException.class,
                        e -> {
                            assertThat(e.getStatusCode().value()).isEqualTo(409);
                            assertThat(e.getBody().getProperties().get("currentProfile"))
                                    .isInstanceOfSatisfying(
                                            AdminAccessProfile.class,
                                            p -> assertThat(p.version()).isEqualTo(2));
                        });
        var order = inOrder(fixture.auth, fixture.store);
        order.verify(fixture.auth).checkAdminAccess(eq(List.of()), eq(true), anyLong());
        order.verify(fixture.auth).readServiceIdentity(eq(List.of(8L)), anyLong());
        order.verify(fixture.auth).checkAdminAccess(eq(List.of()), eq(true), anyLong());
        order.verify(fixture.store).rejectPreflight(anyString(), any());
        verifyNoInteractions(fixture.coordinator);
    }

    private static void outside() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static final class Fixture {
        final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
        final JdbcAcademicIdentityOperationStore store =
                mock(JdbcAcademicIdentityOperationStore.class);
        final AcademicIdentityCoordinator coordinator = mock(AcademicIdentityCoordinator.class);
        final AcademicUserIdentityOperations operations;
        final CheckAdminAccessResponse actor =
                CheckAdminAccessResponse.newBuilder()
                        .setActorUserId(99)
                        .setRolesVersion(4)
                        .setSessionVersion(1)
                        .setProfile(
                                AuthorityAdminProfile.newBuilder()
                                        .setAssigned(true)
                                        .setMode("FULL")
                                        .setVersion(1))
                        .build();

        Fixture() throws Exception {
            var row = mock(ResultSet.class);
            when(row.getString("directory_role")).thenReturn("TEACHER");
            when(row.getString("directory_status")).thenReturn("ACTIVE");
            when(row.getString("last_name")).thenReturn("Петров");
            when(row.getString("first_name")).thenReturn("Пётр");
            when(row.getString("employee_number")).thenReturn("EMP-8");
            when(row.getLong("academic_revision")).thenReturn(2L);
            when(row.getLong("applied_auth_roles_version")).thenReturn(4L);
            var jdbc =
                    mock(
                            JdbcTemplate.class,
                            call -> {
                                if (call.getMethod().getName().equals("query")) {
                                    assertThat(
                                                    TransactionSynchronizationManager
                                                            .getCurrentTransactionIsolationLevel())
                                            .isEqualTo(
                                                    TransactionDefinition
                                                            .ISOLATION_REPEATABLE_READ);
                                    RowMapper<?> mapper = call.getArgument(1);
                                    return List.of(mapper.mapRow(row, 0));
                                }
                                return RETURNS_DEFAULTS.answer(call);
                            });
            var identity =
                    IdentityAuthoritySnapshot.newBuilder()
                            .setUserId(8)
                            .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                            .setRolesVersion(4)
                            .setCredentialsVersion(1)
                            .setTelegramBindingVersion(1)
                            .setAcademicRevision(2)
                            .setAdminProfile(
                                    AuthorityAdminProfile.newBuilder()
                                            .setAssigned(true)
                                            .setMode("FULL")
                                            .setVersion(2))
                            .build();
            when(auth.readServiceIdentity(eq(List.of(8L)), anyLong()))
                    .thenAnswer(
                            call -> {
                                outside();
                                return Map.of(8L, identity);
                            });
            var request = new MockHttpServletRequest();
            request.addHeader("Idempotency-Key", "00000000-0000-0000-0000-000000000001");
            var http =
                    new AcademicIdentityHttpOperations(
                            request, new MockHttpServletResponse(), store, coordinator);
            operations =
                    new AcademicUserIdentityOperations(
                            jdbc,
                            http,
                            new ObjectMapper().findAndRegisterModules(),
                            new LocalTransactions(),
                            auth,
                            mock(AcademicIdentityReadFence.class),
                            new RequestContext(),
                            mock(UserArchiveRepository.class),
                            mock(AuthUserArchiveClient.class));
        }
    }

    private static final class LocalTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
