package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Actual RC guard/aggregate seam; no claim that a repeated RR query refreshes its snapshot. */
class AcademicIdentityRecoveryArchiveGuardIT extends AbstractAcademicIntegrationTest {
    @Autowired private UserService users;
    @MockitoSpyBean private JdbcAcademicIdentityOperationStore store;
    @MockitoSpyBean private UserArchiveRepository archives;

    @Test
    void actualForeignPreparedGuardWinsAtRcFinalReadEvenWhenArchiveDigestDoesNotChange()
            throws Exception {
        var canonical = identityFixture.canonical();
        var admin = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        long foreign =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Guard",
                                        "Student",
                                        null,
                                        UserRole.STUDENT,
                                        canonical.groupId(),
                                        null,
                                        900000000778L),
                                admin)
                        .userId();
        UUID foreignOperation = UUID.randomUUID();
        doAnswer(
                        call -> {
                            AcademicIdentityCoordinator.Operation operation = call.getArgument(0);
                            if (operation.id().equals(foreignOperation.toString()))
                                throw Status.UNAVAILABLE
                                        .withDescription("synthetic stop after actual prepare")
                                        .asRuntimeException();
                            return call.callRealMethod();
                        })
                .when(store)
                .apply(any());
        var observed = new AtomicReference<UserArchiveRepository.Local>();
        var interleave = new AtomicBoolean(true);
        var worker = Executors.newSingleThreadExecutor();
        try {
            doAnswer(
                            call -> {
                                var before = (UserArchiveRepository.Local) call.callRealMethod();
                                if (interleave.compareAndSet(true, false)) {
                                    assertThat(
                                                    TransactionSynchronizationManager
                                                            .isActualTransactionActive())
                                            .isTrue();
                                    assertThat(
                                                    identityFixture
                                                            .academicJdbc()
                                                            .queryForObject(
                                                                    "SHOW transaction_isolation",
                                                                    String.class))
                                            .isEqualTo("read committed");
                                    observed.set(before);
                                    worker.submit(
                                                    () -> {
                                                        try (var scope =
                                                                identityFixture.request(
                                                                        admin, foreignOperation)) {
                                                            assertThatThrownBy(
                                                                            () ->
                                                                                    users
                                                                                            .updateRoleGrant(
                                                                                                    foreign,
                                                                                                    "STUDENT",
                                                                                                    new RoleGrantUpdateRequest(
                                                                                                            RoleGrantStatus
                                                                                                                    .SUSPENDED,
                                                                                                            null,
                                                                                                            null,
                                                                                                            null)))
                                                                    .isInstanceOf(
                                                                            AcademicIdentityPendingException
                                                                                    .class);
                                                        }
                                                    })
                                            .get(10, TimeUnit.SECONDS);
                                }
                                return before;
                            })
                    .when(archives)
                    .observe(canonical.teacherId());
            var tx = identityFixture.academicTransactions();
            tx.setReadOnly(true);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            assertThatThrownBy(
                            () ->
                                    tx.execute(
                                            status ->
                                                    store.observeArchiveWithinRecoveryGuard(
                                                            UUID.randomUUID(),
                                                            canonical.teacherId())))
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.UNAVAILABLE));
            assertThat(
                            identityFixture
                                    .academicJdbc()
                                    .queryForObject(
                                            "SELECT phase FROM academic_identity_operations WHERE"
                                                + " operation_id=?",
                                            String.class,
                                            foreignOperation))
                    .isEqualTo("AUTH_PREPARED");
            assertThat(
                            identityFixture
                                    .authJdbc()
                                    .queryForObject(
                                            "SELECT fence_operation_id FROM auth_identities WHERE"
                                                + " id=?",
                                            UUID.class,
                                            foreign))
                    .isEqualTo(foreignOperation);
            assertThat(archives.observe(canonical.teacherId()).digest())
                    .isEqualTo(observed.get().digest());
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
