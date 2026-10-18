package ru.rutcampustrack.academic.botread;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

import ru.rutcampustrack.academic.grpc.GrpcUserAuthorityReader;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.authauthority.grpc.*;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

class AcademicBotHomeworkMetadataTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
    private final AcademicIdentityReadFence fences = mock(AcademicIdentityReadFence.class);
    private final GrpcUserAuthorityReader reader =
            new GrpcUserAuthorityReader(jdbc, auth, fences, new Transactions());

    @Test
    void freshMachineMetadataKeepsWireFieldsAndAuthCallsOutsideLocalTransaction() throws Exception {
        profile();
        when(auth.readServiceIdentity(eq(List.of(7L)), anyLong()))
                .thenAnswer(
                        ignored -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return Map.of(7L, identity());
                        });
        var result = reader.readUser(7);
        assertThat(result.getId()).isEqualTo(7);
        assertThat(result.getLogin()).isEqualTo("student7");
        assertThat(result.getDisplayName()).isEqualTo("Иванов Иван");
        assertThat(result.getRole()).isEqualTo("student");
        assertThat(result.getStatus()).isEqualTo("active");
        assertThat(result.getGroupId()).isEqualTo(12);
        assertThat(result.getTelegramId()).isEqualTo(42);
        assertThat(result.getRolesVersion()).isEqualTo(5);
        assertThat(result.getRoles(0).getGrantId()).isEqualTo(17);
        assertThat(result.getRoles(0).getRole()).isEqualTo("STUDENT");
    }

    @Test
    void changedAuthSnapshotReturnsNoProfile() throws Exception {
        profile();
        when(auth.readServiceIdentity(anyList(), anyLong()))
                .thenReturn(Map.of(7L, identity()))
                .thenReturn(Map.of(7L, identity().toBuilder().setTelegramId(43).build()));
        assertUnavailable();
    }

    @Test
    void mismatchBetweenAcademicAndAuthCommittedRevisionReturnsNoProfile() throws Exception {
        profile();
        when(auth.readServiceIdentity(anyList(), anyLong()))
                .thenReturn(Map.of(7L, identity().toBuilder().setAcademicRevision(9).build()));
        assertUnavailable();
    }

    @Test
    void pendingUserPublicationReturnsNoProfileOrAuthRead() {
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(fences).requireUserReadable(7);
        assertUnavailable();
        verifyNoInteractions(jdbc, auth);
    }

    @Test
    void unknownAcademicUserRetainsNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThatThrownBy(() -> reader.readUser(7))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.NOT_FOUND));
        verifyNoInteractions(auth);
    }

    @Test
    void pendingGroupPublicationReturnsNoProfile() throws Exception {
        profile();
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(fences).requireGroupReadable(12);
        assertUnavailable();
        verifyNoInteractions(auth);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void profile() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("academic_revision")).thenReturn(8L);
        when(rs.getLong("applied_auth_roles_version")).thenReturn(5L);
        when(rs.getString("last_name")).thenReturn("Иванов");
        when(rs.getString("first_name")).thenReturn("Иван");
        when(rs.getString("directory_role")).thenReturn("STUDENT");
        when(rs.getString("status")).thenReturn("active");
        when(rs.getObject("group_id", Long.class)).thenReturn(12L);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L)))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .getCurrentTransactionIsolationLevel())
                                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                            return List.of(((RowMapper) call.getArgument(1)).mapRow(rs, 0));
                        });
    }

    private static IdentityAuthoritySnapshot identity() {
        return IdentityAuthoritySnapshot.newBuilder()
                .setUserId(7)
                .setLogin("student7")
                .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                .setAcademicRevision(8)
                .setRolesVersion(5)
                .setTelegramBindingVersion(3)
                .setTelegramId(42)
                .addGrants(
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(17)
                                .setRole("STUDENT")
                                .setStatus("ACTIVE")
                                .setGroupId(12))
                .build();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> reader.readUser(7))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
    }

    private static class Transactions extends AbstractPlatformTransactionManager {
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
