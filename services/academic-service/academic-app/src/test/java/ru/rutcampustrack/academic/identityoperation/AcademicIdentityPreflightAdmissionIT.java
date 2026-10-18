package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityCommand;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityDomainWriter;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityIntent;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Own-DB UUID admission race; does not substitute for the complete two-owner business tests. */
@Testcontainers
class AcademicIdentityPreflightAdmissionIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final RequestContext context = new RequestContext();
    private JdbcAcademicIdentityOperationStore store;
    private UUID id;
    private final AcademicIdentityLocalCommand.ApiIntent intent =
            new AcademicIdentityLocalCommand.ApiIntent("CREATE", 0, "0".repeat(64));

    @BeforeAll
    static void schema() {
        var source =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/academic-migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void setup() {
        id = UUID.randomUUID();
        context.setClaims(
                new InternalJwtClaims(
                        99, UUID.randomUUID(), 1, 4, "ADMIN", "ACTIVE", null, false, false));
        store =
                new JdbcAcademicIdentityOperationStore(
                        jdbc,
                        mapper,
                        context,
                        mock(AcademicIdentityDomainWriter.class),
                        mock(AcademicIdentityCacheInvalidator.class),
                        mock(UserArchiveRepository.class));
    }

    @Test
    void negativeWinnerBlocksAlreadyDelayedPositiveAcceptanceAndRemainsTerminal() throws Exception {
        var command = command();
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayed =
                    tx.execute(
                            status -> {
                                assertThat(store.rejectPreflight(id.toString(), intent)).isTrue();
                                var future =
                                        executor.submit(
                                                () -> {
                                                    started.countDown();
                                                    return tx.execute(
                                                            other -> store.accept(command));
                                                });
                                await(started);
                                assertThatThrownBy(() -> future.get(150, TimeUnit.MILLISECONDS))
                                        .isInstanceOf(TimeoutException.class);
                                return future;
                            });
            assertThatThrownBy(() -> delayed.get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            failure ->
                                    assertThat(Status.fromThrowable(failure.getCause()).getCode())
                                            .isEqualTo(Status.Code.FAILED_PRECONDITION));
        }
        assertThat(store.publicStatusForActor(id.toString()).status())
                .isEqualTo(IdentityOperationHttpModels.Status.REJECTED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_identity_operations WHERE"
                                        + " operation_id=?",
                                Long.class,
                                id))
                .isZero();
    }

    @Test
    void positiveWinnerBlocksStaleNegativeUntilCommitThenReportsRealAcceptedState()
            throws Exception {
        var command = command();
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayed =
                    tx.execute(
                            status -> {
                                assertThat(store.accept(command).phase())
                                        .isEqualTo(AcademicIdentityCoordinator.Phase.ACCEPTED);
                                var future =
                                        executor.submit(
                                                () -> {
                                                    started.countDown();
                                                    return tx.execute(
                                                            other ->
                                                                    store.rejectPreflight(
                                                                            id.toString(), intent));
                                                });
                                await(started);
                                assertThatThrownBy(() -> future.get(150, TimeUnit.MILLISECONDS))
                                        .isInstanceOf(TimeoutException.class);
                                return future;
                            });
            assertThat(delayed.get(3, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(store.publicStatusForActor(id.toString()).status())
                .isEqualTo(IdentityOperationHttpModels.Status.PENDING);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_identity_preflight_rejections WHERE"
                                        + " operation_id=?",
                                Long.class,
                                id))
                .isZero();
    }

    @Test
    void rejectedIntentCannotChangeOrBeReadByAnotherActorAndReceiptIsImmutable() {
        Boolean rejected = tx.execute(status -> store.rejectPreflight(id.toString(), intent));
        assertThat(rejected).isTrue();
        var changed = new AcademicIdentityLocalCommand.ApiIntent("CREATE", 0, "1".repeat(64));
        assertThatThrownBy(
                        () -> tx.execute(status -> store.rejectPreflight(id.toString(), changed)))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        failure ->
                                assertThat(failure.getStatus().getCode())
                                        .isEqualTo(Status.Code.ALREADY_EXISTS));
        context.setClaims(
                new InternalJwtClaims(
                        100, UUID.randomUUID(), 1, 4, "ADMIN", "ACTIVE", null, false, false));
        assertThatThrownBy(() -> store.publicStatusForActor(id.toString()))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        failure ->
                                assertThat(failure.getStatus().getCode())
                                        .isEqualTo(Status.Code.NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM academic_identity_preflight_rejections WHERE"
                                                + " operation_id=?",
                                        id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void groupNegativeWinnerBlocksDelayedGroupAcceptanceWithoutCreatingGuard() throws Exception {
        var groups = groupStore();
        var command = groupCommand();
        var groupIntent =
                GroupAvailabilityIntent.direct(
                        command.action(), command.scopeGroupId(), command.requestDigest());
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayed =
                    tx.execute(
                            status -> {
                                assertThat(store.rejectPreflight(id.toString(), groupIntent))
                                        .isTrue();
                                var future =
                                        executor.submit(
                                                () -> {
                                                    started.countDown();
                                                    return tx.execute(
                                                            other ->
                                                                    groups.accept(
                                                                            id,
                                                                            command,
                                                                            null,
                                                                            Map.of(),
                                                                            groupIntent));
                                                });
                                await(started);
                                assertThatThrownBy(() -> future.get(150, TimeUnit.MILLISECONDS))
                                        .isInstanceOf(TimeoutException.class);
                                return future;
                            });
            assertThatThrownBy(() -> delayed.get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(StatusRuntimeException.class);
        }
        assertThat(store.publicStatusForActor(id.toString()).status())
                .isEqualTo(IdentityOperationHttpModels.Status.REJECTED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_group_availability_operations WHERE"
                                        + " operation_id=?",
                                Long.class,
                                id))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_group_availability_guards WHERE"
                                        + " group_id=?",
                                Long.class,
                                command.scopeGroupId()))
                .isZero();
    }

    @Test
    void groupPositiveWinnerRemainsPendingAndGuardedInsteadOfReceivingFalseNegative()
            throws Exception {
        var groups = groupStore();
        var command = groupCommand();
        var groupIntent =
                GroupAvailabilityIntent.direct(
                        command.action(), command.scopeGroupId(), command.requestDigest());
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayed =
                    tx.execute(
                            status -> {
                                assertThat(
                                                groups.accept(
                                                                id,
                                                                command,
                                                                null,
                                                                Map.of(),
                                                                groupIntent)
                                                        .phase())
                                        .isEqualTo(GroupAvailabilityStore.Phase.ACCEPTED);
                                var future =
                                        executor.submit(
                                                () -> {
                                                    started.countDown();
                                                    return tx.execute(
                                                            other ->
                                                                    store.rejectPreflight(
                                                                            id.toString(),
                                                                            groupIntent));
                                                });
                                await(started);
                                assertThatThrownBy(() -> future.get(150, TimeUnit.MILLISECONDS))
                                        .isInstanceOf(TimeoutException.class);
                                return future;
                            });
            assertThat(delayed.get(3, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(groups.find(id).phase()).isEqualTo(GroupAvailabilityStore.Phase.ACCEPTED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT operation_id FROM academic_group_availability_guards WHERE"
                                        + " group_id=?",
                                UUID.class,
                                command.scopeGroupId()))
                .isEqualTo(id);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_identity_preflight_rejections WHERE"
                                        + " operation_id=?",
                                Long.class,
                                id))
                .isZero();
    }

    private GroupAvailabilityStore groupStore() {
        return new GroupAvailabilityStore(
                jdbc,
                mapper,
                context,
                mock(GroupAvailabilityDomainWriter.class),
                mock(AcademicIdentityCacheInvalidator.class));
    }

    private GroupAvailabilityCommand groupCommand() {
        long group =
                jdbc.queryForObject(
                        "INSERT INTO groups(name,is_active) VALUES(?,false) RETURNING id",
                        Long.class,
                        "ИУИТ-" + id.toString().substring(0, 8));
        return new GroupAvailabilityCommand(
                GroupAvailabilityCommand.Action.RESTORE,
                group,
                "0".repeat(64),
                null,
                null,
                null,
                List.of(new GroupAvailabilityCommand.GroupRevision(group, 1)));
    }

    private AcademicIdentityCoordinator.AcceptedCommand command() throws Exception {
        var auth =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE)
                        .setCreateIdentity(
                                CreateIdentityMaterial.newBuilder()
                                        .setLoginPrefix("ivanov")
                                        .setLoginDigits("1234"))
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setRole("TEACHER")
                                                        .setStatus("ACTIVE")));
        auth.setCommandHash(hash(auth.build().toByteArray()));
        byte[] local =
                mapper.writeValueAsBytes(
                        new AcademicIdentityLocalCommand(
                                intent,
                                new AcademicIdentityLocalCommand.Profile(
                                        0, "Иванов", "Иван", null, null, null, "EMP-123", null),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "initial-enrollment",
                                List.of()));
        return new AcademicIdentityCoordinator.AcceptedCommand(
                auth.build(), hash(local), ByteString.copyFrom(local));
    }

    private static ByteString hash(byte[] bytes) throws Exception {
        return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
