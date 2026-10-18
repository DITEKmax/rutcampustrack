package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import jakarta.persistence.EntityManager;

import org.flywaydb.core.Flyway;
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
import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.exportjobs.ExportJobService;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityCoordinator;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReconciliationService;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.identityoperation.JdbcAcademicIdentityOperationStore;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.user.UserAssembler;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadFormat;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
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
    @Autowired private ExportJobService exportJobs;
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
        repeatOwnMigrationsPreservingNonemptyState(student, admin);
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

    private void repeatOwnMigrationsPreservingNonemptyState(
            UserCreatedResponse student, UserCreatedResponse createdAdmin) {
        AcademicIdentityIntegrationFixture.SelectedActor selectedStudent;
        try (var ignored = identityFixture.request(admin)) {
            selectedStudent = identityFixture.select(student.getId(), AuthRole.STUDENT);
        }
        assertThat(selectedStudent.userId()).isEqualTo(student.getId());
        assertThat(selectedStudent.sessionId()).isNotNull();
        var selector =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.ADMIN_USERS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.AdminUsersParameters(
                                "migration-preservation",
                                null,
                                null,
                                null,
                                null,
                                ReportDownloadFormat.HTML),
                        null);
        UUID job;
        try (var ignored = identityFixture.request(admin)) {
            job =
                    exportJobs
                            .enqueue(
                                    admin.claims(),
                                    new AcademicExportJobModels.Create(UUID.randomUUID(), selector))
                            .id();
        }
        assertThat(
                        administrator.queryForObject(
                                "SELECT state='queued' AND attempts=0 AND user_id=? AND"
                                        + " session_id=? FROM academic_export_jobs WHERE job_id=?",
                                Boolean.class,
                                admin.userId(),
                                admin.sessionId(),
                                job))
                .isTrue();
        assertOwnDatabaseRoles();
        var authOwner = identityFixture.fixtureOwnerAuthJdbc();
        var authTables =
                List.of(
                        "auth_identities",
                        "user_role_grants",
                        "admin_access_profiles",
                        "auth_sessions",
                        "auth_identity_operations",
                        "auth_identity_operation_targets");
        var academicTables =
                List.of(
                        "academic_profiles",
                        "academic_role_eligibility",
                        "student_group_history",
                        "academic_identity_operations",
                        "academic_identity_operation_targets",
                        "academic_export_jobs");
        var authBefore = preservationSnapshot(authOwner, authTables);
        var academicBefore = preservationSnapshot(administrator, academicTables);
        // New Flyway instances on already migrated, nonempty own databases.
        // Repeating migrations is not a process restart, restore or rollback experiment.
        repeatMigrations(authOwner.getDataSource(), "classpath:db/auth-migration");
        repeatMigrations(administrator.getDataSource(), "classpath:db/academic-migration");
        assertThat(authBefore.equals(preservationSnapshot(authOwner, authTables)))
                .as(
                        "Auth repeat preserves nonempty identity, credential, grant, session and"
                                + " ledger state")
                .isTrue();
        assertThat(academicBefore.equals(preservationSnapshot(administrator, academicTables)))
                .as(
                        "Academic repeat preserves nonempty profile, membership, history, job and"
                                + " ledger state")
                .isTrue();
        assertOwnDatabaseRoles();
        try (var ignored = identityFixture.request(admin)) {
            assertInitialCredential(student);
            assertInitialCredential(createdAdmin);
            assertThat(exportJobs.status(admin.claims(), job).state()).isEqualTo("queued");
        }
    }

    private static void repeatMigrations(DataSource source, String location) {
        var flyway = Flyway.configure().dataSource(source).locations(location).load();
        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isZero();
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
    }

    private static List<String> preservationSnapshot(JdbcTemplate owner, List<String> tables) {
        var result = new ArrayList<String>();
        for (String table : tables) {
            // Every input is a fixed table name above. Hash private row JSON inside this method;
            // failure output never contains initial material, stored credential hashes or sessions.
            result.add(
                    privateRowsDigest(
                            owner,
                            "SELECT to_jsonb(t)::text FROM "
                                    + table
                                    + " t ORDER BY to_jsonb(t)::text"));
        }
        result.add(
                privateRowsDigest(
                        owner,
                        "SELECT to_jsonb(t)::text FROM flyway_schema_history t ORDER BY"
                                + " installed_rank"));
        result.add(
                privateRowsDigest(
                        owner,
                        "SELECT jsonb_build_array(c.oid,c.relowner,c.relacl)::text FROM pg_class c"
                                + " JOIN pg_namespace n ON n.oid=c.relnamespace WHERE"
                                + " n.nspname='public' ORDER BY c.oid"));
        result.add(
                privateRowsDigest(
                        owner,
                        "SELECT jsonb_build_array(p.oid,p.proowner,p.proacl)::text FROM pg_proc p"
                                + " JOIN pg_namespace n ON n.oid=p.pronamespace WHERE"
                                + " n.nspname='public' ORDER BY p.oid"));
        result.add(
                privateRowsDigest(
                        owner,
                        "SELECT jsonb_build_array(datname,datdba,datacl)::text FROM pg_database"
                                + " WHERE datname IN ('auth_db','academic_db') ORDER BY datname"));
        result.add(
                privateRowsDigest(
                        owner,
                        "SELECT jsonb_build_array(nspname,nspowner,nspacl)::text FROM pg_namespace"
                                + " WHERE nspname='public'"));
        return result;
    }

    private static String privateRowsDigest(JdbcTemplate owner, String query) {
        var rows = owner.queryForList(query, String.class);
        assertThat(rows.isEmpty()).as("preservation requires nonempty owner rows").isFalse();
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String row : rows) {
                digest.update(row.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 required");
        }
    }

    private void assertOwnDatabaseRoles() {
        assertOwnDatabaseRole(
                identityFixture.fixtureOwnerAuthJdbc(), "auth_db", "rct_auth_migrator", true);
        assertOwnDatabaseRole(administrator, "academic_db", "rct_migrator", true);
        assertOwnDatabaseRole(identityFixture.authJdbc(), "auth_db", "rct_auth_runtime", false);
        assertOwnDatabaseRole(jdbc, "academic_db", "rct_academic_runtime", false);
    }

    private static void assertOwnDatabaseRole(
            JdbcTemplate database, String name, String principal, boolean migrator) {
        assertThat(
                        database.queryForObject(
                                "SELECT current_database()=? AND current_user=? AND"
                                    + " session_user=current_user AND NOT rolsuper AND NOT"
                                    + " rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole AND"
                                    + " NOT rolreplication AND NOT"
                                    + " has_database_privilege(current_user,?,'CONNECT') AND"
                                    + " has_database_privilege(current_user,current_database(),'CREATE')=?"
                                    + " AND has_schema_privilege(current_user,'public','CREATE')=?"
                                    + " AND has_table_privilege(current_user,'public.flyway_schema_history',"
                                    + " 'SELECT,INSERT,UPDATE,DELETE')=? FROM pg_roles WHERE"
                                    + " rolname=current_user",
                                Boolean.class,
                                name,
                                principal,
                                name.equals("auth_db") ? "academic_db" : "auth_db",
                                migrator,
                                migrator,
                                migrator))
                .isTrue();
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
