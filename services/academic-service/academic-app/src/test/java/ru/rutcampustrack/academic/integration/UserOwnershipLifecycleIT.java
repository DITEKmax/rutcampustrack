package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityCoordinator;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReconciliationService;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.identityoperation.JdbcAcademicIdentityOperationStore;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.user.UserAssembler;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

/**
 * Actual split Auth identity and Academic JPA/JDBC ownership, including safe failed CREATE
 * recovery.
 */
class UserOwnershipLifecycleIT extends AbstractAcademicIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserService users;
    @Autowired private UserAssembler assembler;
    @Autowired private GroupService groups;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AcademicUserIdentityOperations identityOperations;
    @Autowired private JdbcAcademicIdentityOperationStore operations;
    @Autowired private AcademicIdentityReconciliationService reconciliation;
    @Autowired private AuthIdentityDataClient identityData;
    @MockitoSpyBean private StudentGroupHistoryRepository history;
    private static final AtomicLong TELEGRAM = new AtomicLong(9_100_000_000L);
    private JdbcTemplate administrator;
    private LocalDate joinedAt;
    private AcademicIdentityIntegrationFixture.SelectedActor admin;
    private AcademicIdentityIntegrationFixture.RequestScope actorRequest;

    @BeforeEach
    void ownedSeedAndLiveActor() {
        administrator = identityFixture.fixtureOwnerAcademicJdbc();
        administrator.update("UPDATE semesters SET is_active=false WHERE is_active");
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        joinedAt = today.minusDays(7);
        administrator.update(
                "INSERT INTO semesters(name,date_from,date_to,is_active) VALUES (?,?,?,true)",
                "Owned JPA " + UUID.randomUUID(),
                joinedAt,
                today.plusDays(7));
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        actorRequest = identityFixture.request(admin, null);
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("rct_academic_runtime");
        assertThat(identityFixture.authJdbc().queryForObject("SELECT current_user", String.class))
                .isEqualTo("rct_auth_runtime");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            assertThat(
                                            entityManager
                                                    .createNativeQuery(
                                                            "SELECT current_user", String.class)
                                                    .getSingleResult())
                                    .isEqualTo("rct_academic_runtime");
                            var connection =
                                    org.springframework.jdbc.datasource.DataSourceUtils
                                            .getConnection(dataSource);
                            try {
                                assertThat(
                                                org.springframework.jdbc.datasource.DataSourceUtils
                                                        .isConnectionTransactional(
                                                                connection, dataSource))
                                        .isTrue();
                            } finally {
                                org.springframework.jdbc.datasource.DataSourceUtils
                                        .releaseConnection(connection, dataSource);
                            }
                        });
    }

    @AfterEach
    void closeOwnedActorRequest() {
        if (actorRequest != null) actorRequest.close();
    }

    @Test
    void realStudentAndAdminCreationPreserveInitialCredentialGrantsAndHistory() {
        long group = managedGroup();
        var student = create(student(group));
        assertThat(student).isNotNull();
        assertInitialCredential(student);
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=? AND"
                                        + " group_id=? AND joined_at=?",
                                Long.class,
                                student.getId(),
                                group,
                                joinedAt))
                .isOne();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=? AND"
                                            + " role='student' AND status='active' AND group_id=?",
                                        Long.class,
                                        student.getId(),
                                        group))
                .isOne();
        var admin =
                create(
                        new CreateUserRequest(
                                "Synthetic",
                                "Administrator",
                                null,
                                UserRole.ADMIN,
                                null,
                                null,
                                null,
                                new AdminAccessInput("FULL", List.of())));
        assertThat(admin).isNotNull();
        assertInitialCredential(admin);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT p.assigned AND p.mode='FULL' AND p.version=1 AND"
                                            + " g.status='active' FROM admin_access_profiles p JOIN"
                                            + " user_role_grants g ON g.user_id=p.user_id AND"
                                            + " g.role='admin' WHERE p.user_id=?",
                                        Boolean.class,
                                        admin.getId()))
                .isTrue();
    }

    @Test
    void failureAfterRealHistoryFlushKeepsAuthFencedAndReconcilesTheSameIdentity() {
        long group = managedGroup();
        long outboxBefore =
                administrator.queryForObject("SELECT count(*) FROM academic_outbox", Long.class);
        AtomicLong attemptedUser = new AtomicLong();
        org.mockito.stubbing.Answer<?> repository =
                mockingDetails(history).getMockCreationSettings().getDefaultAnswer();
        assertThat(repository).isNotNull();
        doAnswer(
                        call -> {
                            @SuppressWarnings("unchecked")
                            var found =
                                    (java.util.Optional<
                                                    ru.rutcampustrack.academic.entity
                                                            .StudentGroupHistory>)
                                            repository.answer(call);
                            var row = found.orElseThrow();
                            attemptedUser.set(row.getUserId());
                            entityManager.flush();
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT count(*) FROM student_group_history"
                                                            + " WHERE id=?",
                                                    Long.class,
                                                    row.getId()))
                                    .isOne();
                            throw Status.ABORTED
                                    .withDescription("owned post-flush rollback")
                                    .asRuntimeException();
                        })
                .when(history)
                .findById(anyLong());
        UUID failedId = UUID.randomUUID();
        try (var ignored = identityFixture.request(admin, failedId)) {
            assertThatThrownBy(() -> identityOperations.create(student(group)))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.FAILED_PRECONDITION));
        }
        assertThat(attemptedUser.get()).isPositive();
        long userId = attemptedUser.get();
        var failed = operations.find(failedId.toString());
        assertThat(failed.phase()).isEqualTo(AcademicIdentityCoordinator.Phase.FAILED_SAFE);
        assertThat(failed.prepared().getTargets(0).getUserId()).isEqualTo(userId);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT phase='FAILED_SAFE' FROM auth_identity_operations"
                                                + " WHERE operation_id=?",
                                        Boolean.class,
                                        failedId))
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT account_state='PENDING' AND fence_operation_id=?"
                                                + " AND credentials_version=1 AND"
                                                + " telegram_binding_version=1 FROM auth_identities"
                                                + " WHERE id=?",
                                        Boolean.class,
                                        failedId,
                                        userId))
                .isTrue();
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM academic_profiles WHERE id=?",
                                Long.class,
                                userId))
                .isZero();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        userId))
                .isZero();
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=?",
                                Long.class,
                                userId))
                .isZero();
        assertThat(administrator.queryForObject("SELECT count(*) FROM academic_outbox", Long.class))
                .isEqualTo(outboxBefore);
        assertThatThrownBy(
                        () ->
                                identityData.initialCredentials(
                                        List.of(userId),
                                        System.nanoTime() + TimeUnit.SECONDS.toNanos(3)))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isIn(
                                                Status.Code.PERMISSION_DENIED,
                                                Status.Code.FAILED_PRECONDITION));
        String reservedHash =
                identityFixture
                        .fixtureOwnerAuthJdbc()
                        .queryForObject(
                                "SELECT password_hash FROM auth_identities WHERE id=?",
                                String.class,
                                userId);
        doAnswer(repository).when(history).findById(anyLong());
        var fresh = identityFixture.select(admin.userId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(fresh, UUID.randomUUID())) {
            var reconciled = reconciliation.reconcile(failedId, null);
            assertThat(reconciled.status()).isEqualTo(IdentityOperationHttpModels.Status.SUCCEEDED);
            var committed = operations.find(reconciled.operationId().toString());
            assertThat(committed.phase()).isEqualTo(AcademicIdentityCoordinator.Phase.SUCCEEDED);
            assertThat(committed.prepared().getTargets(0).getUserId()).isEqualTo(userId);
        }
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM academic_profiles WHERE id=?",
                                Long.class,
                                userId))
                .isOne();
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=? AND"
                                        + " group_id=? AND joined_at=?",
                                Long.class,
                                userId,
                                group,
                                joinedAt))
                .isOne();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=? AND"
                                            + " role='student' AND status='active' AND group_id=?",
                                        Long.class,
                                        userId,
                                        group))
                .isOne();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT account_state='ACTIVE' AND fence_operation_id IS"
                                                + " NULL AND credentials_version=1 AND"
                                                + " telegram_binding_version=1 FROM auth_identities"
                                                + " WHERE id=?",
                                        Boolean.class,
                                        userId))
                .isTrue();
        String finalHash =
                identityFixture
                        .fixtureOwnerAuthJdbc()
                        .queryForObject(
                                "SELECT password_hash FROM auth_identities WHERE id=?",
                                String.class,
                                userId);
        assertThat(reservedHash.equals(finalHash))
                .as("reconcile preserves the reserved credential")
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM auth_identities WHERE"
                                                + " provision_operation_id=?",
                                        Long.class,
                                        failedId))
                .isOne();
        assertThat(
                        identityData
                                .initialCredentials(
                                        List.of(userId),
                                        System.nanoTime() + TimeUnit.SECONDS.toNanos(3))
                                .getCredentialsCount())
                .isOne();
    }

    private UserCreatedResponse create(CreateUserRequest request) {
        try (var ignored = identityFixture.request(admin)) {
            return users.createUser(request).getContent();
        }
    }

    private void assertInitialCredential(UserCreatedResponse created) {
        String plain = created.getInitialPassword();
        assertThat(plain != null && !plain.isBlank()).isTrue();
        String hash =
                identityFixture
                        .fixtureOwnerAuthJdbc()
                        .queryForObject(
                                "SELECT password_hash FROM auth_identities WHERE id=?",
                                String.class,
                                created.getId());
        assertThat(passwords.matches(plain, hash)).isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT credentials_version=1 AND"
                                                + " telegram_binding_version=1 AND"
                                                + " generated_password_origin='initial' FROM"
                                                + " auth_identities WHERE id=?",
                                        Boolean.class,
                                        created.getId()))
                .isTrue();
        var entity = users.findUserById(created.getId());
        assertThat(entity.getPasswordHash()).isNull();
        assertThat(entity.getInitialPassword()).isNull();
        assertThat(plain.equals(assembler.toAdminModel(entity).getContent().getInitialPassword()))
                .isTrue();
    }

    private long managedGroup() {
        for (int number = 110; number <= 119; number++) {
            String name = "ФД-" + number;
            if (administrator.queryForObject(
                            "SELECT count(*) FROM groups WHERE name=?", Long.class, name)
                    == 0) return groups.createGroup(new CreateGroupRequest(name)).getId();
        }
        throw new IllegalStateException("Owned group fixture namespace exhausted");
    }

    private static CreateUserRequest student(long group) {
        return new CreateUserRequest(
                "Synthetic",
                "Student",
                null,
                UserRole.STUDENT,
                group,
                null,
                TELEGRAM.incrementAndGet());
    }
}
