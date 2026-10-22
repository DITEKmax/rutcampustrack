package ru.rutcampustrack.academic.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Composition risks only; actual signed Auth/TLS/JDBC acceptance uses the common fixture. */
class AssistantPermissionReadServiceTest {
    private final AcademicLiveSessionClient auth = mock(AcademicLiveSessionClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AcademicIdentityReadFence fences = mock(AcademicIdentityReadFence.class);
    private final LocalTransactions transactions = new LocalTransactions();
    private final AssistantPermissionReadService service =
            new AssistantPermissionReadService(auth, jdbc, fences, transactions);
    private final InternalJwtClaims student = actor("STUDENT");
    private final AtomicInteger snapshots = new AtomicInteger();
    private boolean helper = true;
    private boolean headman;
    private long appliedRv = 4;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void currentAuthority() throws Exception {
        when(auth.admit(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return null; // Profile is irrelevant to this non-admin authority.
                        });
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            snapshots.incrementAndGet();
                            var row = mock(ResultSet.class);
                            when(row.getLong("academic_revision")).thenReturn(2L);
                            when(row.getLong("group_revision")).thenReturn(3L);
                            when(row.getLong("applied_auth_roles_version")).thenReturn(appliedRv);
                            when(row.getBoolean("visible")).thenReturn(true);
                            when(row.getBoolean("student")).thenReturn(true);
                            when(row.getBoolean("headman")).thenReturn(headman);
                            when(row.getBoolean("helper")).thenReturn(helper);
                            return List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0));
                        });
    }

    @Test
    void liveAdmissionSurroundsCoherentOwnPermissionReads() {
        assertThat(service.allows(student, 8, " manage_homework ")).isTrue();
        verify(auth, times(2)).admit(student);
        verify(fences, times(2)).requireUserReadable(7);
        verify(fences, times(2)).requireGroupReadable(8);
        assertThat(snapshots).hasValue(2);
        assertThat(transactions.reads).isEqualTo(2);
    }

    @Test
    void currentRevocationBeforeOrDuringTheReadNeverGrants() {
        when(auth.admit(student)).thenThrow(Status.UNAUTHENTICATED.asRuntimeException());
        assertThat(service.allows(student, 8, "MANAGE_HOMEWORK")).isFalse();
        assertThat(snapshots).hasValue(0);
        reset(auth);
        when(auth.admit(student))
                .thenReturn(null)
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        assertThat(service.allows(student, 8, "MANAGE_HOMEWORK")).isFalse();
        assertThat(snapshots).hasValue(1);
    }

    @Test
    void authOutageAndLocalPendingFenceRemainUnavailableRatherThanDeny() {
        when(auth.admit(student)).thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());
        unavailable(() -> service.allows(student, 8, "MANAGE_HOMEWORK"));
        reset(auth);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(fences).requireGroupReadable(8);
        unavailable(() -> service.allows(student, 8, "MANAGE_HOMEWORK"));
        assertThat(snapshots).hasValue(0);
    }

    @Test
    void localRevokeBetweenAuthAdmissionsCannotPublishTheEarlierPermission() {
        when(auth.admit(student))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            if (snapshots.get() == 1) helper = false;
                            return null;
                        });
        unavailable(() -> service.allows(student, 8, "MANAGE_HOMEWORK"));
        assertThat(snapshots).hasValue(2);
    }

    @Test
    void authDenyCombinedWithPendingPublicationRemainsUnavailable() {
        when(auth.admit(student)).thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(fences).requireUserReadable(7);
        unavailable(() -> service.allows(student, 8, "MANAGE_HOMEWORK"));
        assertThat(snapshots).hasValue(0);
        verifyNoInteractions(jdbc);
    }

    @Test
    void staleAppliedRolesVersionCannotTurnAnAuthAdmissionIntoLocalAuthority() {
        appliedRv = 3;
        unavailable(() -> service.allows(student, 8, "MANAGE_HOMEWORK"));
    }

    @Test
    void absentHelperPermissionDeniesButPairedLocalHeadmanDoesNotNeedAHelperRow() {
        helper = false;
        assertThat(service.allows(student, 8, "MANAGE_HOMEWORK")).isFalse();
        assertThat(service.allows(actor("HEADMAN"), 8, "MANAGE_HOMEWORK")).isFalse();
        headman = true;
        assertThat(service.allows(actor("HEADMAN"), 8, "MANAGE_HOMEWORK")).isTrue();
    }

    @Test
    void foreignGroupAndUnknownPermissionCannotSelectAnotherAuthorityScope() {
        assertThat(service.allows(student, 9, "MANAGE_HOMEWORK")).isFalse();
        assertThat(service.allows(student, 8, "UNKNOWN")).isFalse();
        verifyNoInteractions(auth, jdbc, fences);
    }

    @Test
    void ambientLocalTransactionIsRejectedBeforeAnyAuthCall() {
        new TransactionTemplate(new LocalTransactions(false))
                .executeWithoutResult(
                        status -> unavailable(() -> service.allows(student, 8, "MANAGE_HOMEWORK")));
        verifyNoInteractions(auth, jdbc, fences);
    }

    private static InternalJwtClaims actor(String role) {
        return new InternalJwtClaims(
                7,
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                1,
                4,
                role,
                "ACTIVE",
                8L,
                role.equals("HEADMAN"),
                false);
    }

    private static void unavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
    }

    private static final class LocalTransactions extends AbstractPlatformTransactionManager {
        private final boolean checkRead;
        private int reads;

        private LocalTransactions() {
            this(true);
        }

        private LocalTransactions(boolean checkRead) {
            this.checkRead = checkRead;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            if (checkRead) {
                assertThat(definition.isReadOnly()).isTrue();
                assertThat(definition.getIsolationLevel())
                        .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                reads++;
            }
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
