package ru.rutcampustrack.academic.studentprojection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authauthority.grpc.AuthorityRoleGrant;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;
import ru.rutcampustrack.authsecurity.grpc.LiveSessionAuthority;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OwnDB equivalents of the former combined-schema cases: parameter binding; current Auth grants
 * versus directory status; stable local RR plus stale-authority denial after a concurrent transfer.
 * Auth snapshots are synthetic; current directed RPC runtime remains a separate CI acceptance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class StudentProjectionQueryAdapterIT {
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("rct_student_projection")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;

    @BeforeAll
    void startContainer() {
        POSTGRES.start();
        var dataSource =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/academic-migration")
                .load()
                .migrate();
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void findAssignmentsBindsSemesterGroupsAndBoundsInDeclaredOrder() {
        reset();
        long groupA = insertGroup("A");
        long groupB = insertGroup("B");
        insertProfile(1L, "TEACHER", "ACTIVE");
        long semester = insertSemester();
        long subjectA = insertSubject("A subject", groupA);
        long subjectB = insertSubject("B subject", groupB);
        long assignmentA = insertAssignment(1L, subjectA, groupA, semester);
        long assignmentB = insertAssignment(1L, subjectB, groupB, semester);
        var adapter = adapter(new AtomicReference<>(Map.of()));
        var assignments =
                adapter.findAssignments(
                        semester,
                        Set.of(groupB, groupA),
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1));
        assertThat(assignments)
                .extracting(StudentProjectionQuery.AssignmentSnapshot::id)
                .containsExactly(assignmentA, assignmentB);
        assertThat(assignments)
                .extracting(StudentProjectionQuery.AssignmentSnapshot::groupId)
                .containsExactly(groupA, groupB);
    }

    @Test
    void authorityAndRosterUseCurrentStudentGrantWithoutLegacyStatusFallback() {
        reset();
        long group = insertGroup("A");
        long semester = insertSemester();
        insertProfile(100L, "STUDENT", "ARCHIVED");
        insertMembership(100L, group);
        var state = new AtomicReference<>(Map.of(100L, identity(100L, 7, group)));
        var adapter = adapter(state);
        var authority = adapter.findAuthority(100L).orElseThrow();
        assertThat(authority.accountStatus()).isEqualTo("ACTIVE");
        assertThat(authority.grants())
                .singleElement()
                .extracting(StudentProjectionQuery.RoleGrantSnapshot::status)
                .isEqualTo("ACTIVE");
        var roster =
                adapter.readSnapshot(
                        authority,
                        () ->
                                adapter.findActiveStudentIds(
                                        group,
                                        semester,
                                        LocalDate.of(2026, 9, 1),
                                        LocalDate.of(2026, 10, 1)));
        assertThat(roster).containsExactly(100L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_regclass('users') IS NULL AND"
                                        + " to_regclass('user_role_grants') IS NULL AND"
                                        + " to_regclass('auth_sessions') IS NULL",
                                Boolean.class))
                .isTrue();
        var scope =
                new StudentProjectionScopeService(adapter, clock())
                        .resolve(semester, claims(group));
        assertThat(scope.activeRosterUserIds()).containsExactly(100L);
        state.set(Map.of(100L, identity(100L, 8, group)));
        assertThatThrownBy(() -> adapter.verifyCurrent(authority, scope))
                .isInstanceOfSatisfying(
                        StudentProjectionException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(
                                                StudentProjectionException.Code.INVALID_SESSION));
    }

    @Test
    void rosterRequiresCurrentAuthGroupAndExactLocallyAppliedVersion() {
        reset();
        long groupA = insertGroup("Cohort A");
        long groupB = insertGroup("Cohort B");
        long semester = insertSemester();
        insertProfile(100L, "STUDENT", "ACTIVE");
        insertMembership(100L, groupA);
        insertProfile(200L, "STUDENT", "ACTIVE");
        insertMembership(200L, groupA);
        var state =
                new AtomicReference<>(
                        Map.of(100L, identity(100L, 7, groupA), 200L, identity(200L, 7, groupB)));
        var resolver = new StudentProjectionScopeService(adapter(state), clock());
        assertThatThrownBy(() -> resolver.resolve(semester, claims(groupA)))
                .isInstanceOfSatisfying(
                        StudentProjectionException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(
                                                StudentProjectionException.Code
                                                        .DEPENDENCY_UNAVAILABLE));
        state.set(Map.of(100L, identity(100L, 7, groupA), 200L, identity(200L, 8, groupA)));
        assertThatThrownBy(() -> resolver.resolve(semester, claims(groupA)))
                .isInstanceOfSatisfying(
                        StudentProjectionException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(
                                                StudentProjectionException.Code
                                                        .DEPENDENCY_UNAVAILABLE));
        state.set(Map.of(100L, identity(100L, 7, groupA), 200L, identity(200L, 7, groupA)));
        assertThat(resolver.resolve(semester, claims(groupA)).activeRosterUserIds())
                .containsExactly(100L, 200L);
    }

    @Test
    void localRepeatableReadRemainsStableButConcurrentAuthorityChangeDeniesDisclosure()
            throws Exception {
        reset();
        long groupA = insertGroup("Snapshot A");
        long groupB = insertGroup("Snapshot B");
        long semester = insertSemester();
        insertProfile(100L, "STUDENT", "ACTIVE");
        insertMembership(100L, groupA);
        var state = new AtomicReference<>(Map.of(100L, identity(100L, 7, groupA)));
        var adapter = adapter(state);
        var admitted = adapter.findAuthority(100L).orElseThrow();
        var snapshotRead = new CountDownLatch(1);
        var committed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<StudentProjectionQuery.GroupHistorySnapshot>> history =
                new AtomicReference<>();
        Thread reader =
                new Thread(
                        () -> {
                            try {
                                history.set(
                                        adapter.readSnapshot(
                                                admitted,
                                                () -> {
                                                    var before = adapter.findGroupHistory(100L);
                                                    snapshotRead.countDown();
                                                    try {
                                                        assertThat(
                                                                        committed.await(
                                                                                10,
                                                                                TimeUnit.SECONDS))
                                                                .isTrue();
                                                    } catch (InterruptedException error) {
                                                        Thread.currentThread().interrupt();
                                                        throw new AssertionError(error);
                                                    }
                                                    assertThat(adapter.findGroupHistory(100L))
                                                            .isEqualTo(before);
                                                    return before;
                                                }));
                            } catch (Throwable error) {
                                failure.set(error);
                            }
                        });
        reader.start();
        assertThat(snapshotRead.await(10, TimeUnit.SECONDS)).isTrue();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        ignored -> {
                            jdbc.update(
                                    "UPDATE academic_profiles SET applied_auth_roles_version=8, "
                                            + "academic_revision=2 WHERE id=100");
                            jdbc.update(
                                    "UPDATE academic_role_eligibility SET group_id=? WHERE"
                                            + " user_id=100",
                                    groupB);
                            jdbc.update(
                                    "UPDATE student_group_history SET left_at=DATE '2026-09-10' "
                                            + "WHERE user_id=100");
                            jdbc.update(
                                    "INSERT INTO student_group_history(user_id,group_id,joined_at) "
                                            + "VALUES(100,?,DATE '2026-09-10')",
                                    groupB);
                        });
        state.set(
                Map.of(100L, identity(100L, 8, groupB).toBuilder().setAcademicRevision(2).build()));
        committed.countDown();
        reader.join(10_000);
        assertThat(reader.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(history.get())
                .singleElement()
                .extracting(StudentProjectionQuery.GroupHistorySnapshot::groupId)
                .isEqualTo(groupA);
        assertThatThrownBy(
                        () ->
                                new StudentProjectionScopeService(adapter, clock())
                                        .resolve(semester, claims(groupA)))
                .isInstanceOfSatisfying(
                        StudentProjectionException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(
                                                StudentProjectionException.Code.INVALID_SESSION));
    }

    private JdbcStudentProjectionQueryAdapter adapter(
            AtomicReference<Map<Long, IdentityAuthoritySnapshot>> state) {
        var identities = mock(AuthIdentityDataClient.class);
        when(identities.readServiceIdentity(anyCollection(), anyLong()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            Collection<Long> selected = call.getArgument(0);
                            var result = new LinkedHashMap<Long, IdentityAuthoritySnapshot>();
                            selected.forEach(
                                    id -> {
                                        if (state.get().containsKey(id))
                                            result.put(id, state.get().get(id));
                                    });
                            return result;
                        });
        var sessions = mock(AcademicLiveSessionClient.class);
        when(sessions.admitProjection(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            InternalJwtClaims actor = call.getArgument(0);
                            var identity =
                                    StoredSessionIdentity.newBuilder()
                                            .setUserId(actor.userId())
                                            .setSessionId(actor.sessionId().toString())
                                            .setSessionVersion(actor.sessionVersion())
                                            .setRolesVersion(actor.rolesVersion())
                                            .setRole(actor.role())
                                            .setStatus(actor.status())
                                            .setGroupId(actor.groupId())
                                            .setReadOnly(actor.readOnly())
                                            .setIsHeadman(actor.isHeadman());
                            return LiveSessionAuthority.newBuilder()
                                    .setIdentity(identity)
                                    .setActiveRoleGrantId(11)
                                    .setRefreshExpiresAt("2030-01-01T00:00:00Z")
                                    .build();
                        });
        return new JdbcStudentProjectionQueryAdapter(
                jdbc, identities, sessions, new AcademicIdentityReadFence(jdbc), transactions);
    }

    private void reset() {
        jdbc.execute("TRUNCATE academic_profiles,groups,semesters RESTART IDENTITY CASCADE");
    }

    private static Clock clock() {
        return Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneId.of("Europe/Moscow"));
    }

    private static InternalJwtClaims claims(long group) {
        return new InternalJwtClaims(
                100L, SESSION_ID, 1L, 7L, "STUDENT", "ACTIVE", group, false, false);
    }

    private static IdentityAuthoritySnapshot identity(long user, long version, long group) {
        return IdentityAuthoritySnapshot.newBuilder()
                .setUserId(user)
                .setRolesVersion(version)
                .setAcademicRevision(1)
                .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                .addGrants(
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(11)
                                .setRole("STUDENT")
                                .setStatus("ACTIVE")
                                .setGroupId(group))
                .build();
    }

    private void insertProfile(long id, String role, String status) {
        jdbc.update(
                "INSERT INTO"
                    + " academic_profiles(id,directory_role,directory_status,last_name,first_name,academic_revision,applied_auth_roles_version,created_at,updated_at)"
                    + " VALUES(?,?,?,'Projection','Fixture',1,7,NOW(),NOW())",
                id,
                role,
                status);
    }

    private void insertMembership(long user, long group) {
        jdbc.update(
                "INSERT INTO academic_role_eligibility(user_id,role,status,group_id,updated_at) "
                        + "VALUES(?,'STUDENT','ACTIVE',?,NOW())",
                user,
                group);
        jdbc.update(
                "INSERT INTO student_group_history(user_id,group_id,joined_at) "
                        + "VALUES(?,?,DATE '2026-01-01')",
                user,
                group);
    }

    private long insertGroup(String suffix) {
        return jdbc.queryForObject(
                "INSERT INTO groups(name) VALUES(?) RETURNING id",
                Long.class,
                "Projection " + suffix);
    }

    private long insertSemester() {
        return jdbc.queryForObject(
                "INSERT INTO semesters(name,date_from,date_to,is_active) VALUES('Projection"
                        + " semester',DATE '2026-09-01',DATE '2026-09-30',FALSE) RETURNING id",
                Long.class);
    }

    private long insertSubject(String name, long group) {
        return jdbc.queryForObject(
                "WITH inserted AS (INSERT INTO subjects(name,type,group_id) VALUES(?,'lecture',?)"
                        + " RETURNING id) INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                        + " SELECT id,'lecture' FROM inserted RETURNING subject_id",
                Long.class,
                name,
                group);
    }

    private long insertAssignment(long teacher, long subject, long group, long semester) {
        return jdbc.queryForObject(
                "INSERT INTO"
                    + " assignments(teacher_id,subject_id,group_id,semester_id,lesson_type,valid_from)"
                    + " VALUES(?,?,?,?,'lecture',DATE '2026-09-01') RETURNING id",
                Long.class,
                teacher,
                subject,
                group,
                semester);
    }
}
