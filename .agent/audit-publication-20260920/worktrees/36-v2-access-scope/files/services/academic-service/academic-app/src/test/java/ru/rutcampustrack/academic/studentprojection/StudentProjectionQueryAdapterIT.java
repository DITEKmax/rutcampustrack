package ru.rutcampustrack.academic.studentprojection;

import org.flywaydb.core.Flyway;
import org.springframework.aop.framework.ProxyFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL evidence for the projection read adapter.
 *
 * <p>The assignment case deliberately uses two groups because the SQL has a
 * semester parameter, a dynamic group list, and two date parameters.  The
 * authority/roster case leaves the legacy user status archived while keeping
 * an active current STUDENT grant; the grant is the authoritative status for
 * this reader.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class StudentProjectionQueryAdapterIT {

    private static final UUID SESSION_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("rct_student_projection")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    private JdbcTemplate adminJdbc;
    private DataSource adminDataSource;

    @BeforeAll
    void startContainer() {
        POSTGRES.start();
        adminDataSource = dataSourceForSchema(null);
        adminJdbc = new JdbcTemplate(adminDataSource);
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void findAssignmentsBindsSemesterGroupsAndBoundsInDeclaredOrder() {
        String schema = newSchema();
        JdbcTemplate jdbc = jdbcFor(schema);
        flyway(schema).migrate();

        long groupA = insertGroup(jdbc, "A");
        long groupB = insertGroup(jdbc, "B");
        long teacherId = insertUser(jdbc, "projection_teacher", "teacher", "active", null);
        long semesterId = insertSemester(jdbc, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        long subjectA = insertSubject(jdbc, "A subject", groupA);
        long subjectB = insertSubject(jdbc, "B subject", groupB);
        long assignmentA = insertAssignment(jdbc, teacherId, subjectA, groupA, semesterId);
        long assignmentB = insertAssignment(jdbc, teacherId, subjectB, groupB, semesterId);

        JdbcStudentProjectionQueryAdapter adapter = new JdbcStudentProjectionQueryAdapter(jdbc);
        List<StudentProjectionQuery.AssignmentSnapshot> assignments = adapter.findAssignments(
                semesterId,
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
        String schema = newSchema();
        JdbcTemplate jdbc = jdbcFor(schema);
        flyway(schema).migrate();

        long groupId = insertGroup(jdbc, "A");
        long semesterId = insertSemester(jdbc, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        long studentId = insertUser(jdbc, "projection_archived_legacy", "student", "archived", groupId);
        jdbc.update("""
                INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at)
                VALUES (?, 'student', 'active', ?, NOW(), NOW())
                """, studentId, groupId);
        jdbc.update("""
                INSERT INTO student_group_history (user_id, group_id, joined_at, left_at)
                VALUES (?, ?, DATE '2026-01-01', NULL)
                """, studentId, groupId);

        JdbcStudentProjectionQueryAdapter adapter = new JdbcStudentProjectionQueryAdapter(jdbc);
        StudentProjectionQuery.AuthoritySnapshot authority = adapter.findAuthority(studentId).orElseThrow();
        List<Long> roster = adapter.findActiveStudentIds(
                groupId, semesterId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

        assertThat(authority.accountStatus()).isEqualTo("active");
        assertThat(authority.grants()).singleElement()
                .extracting(StudentProjectionQuery.RoleGrantSnapshot::status)
                .isEqualTo("active");
        assertThat(roster).containsExactly(studentId);
    }

    @Test
    void resolverUsesOneRepeatableReadSnapshotAcrossCommittedAuthorityAndHistoryChange()
        throws Exception {
        String schema = newSchema();
        DataSource resolverDataSource = dataSourceForSchema(schema);
        JdbcTemplate jdbc = new JdbcTemplate(resolverDataSource);
        flyway(schema).migrate();

        long groupA = insertGroup(jdbc, "Snapshot A");
        long groupB = insertGroup(jdbc, "Snapshot B");
        long studentId = insertUser(jdbc, "projection_snapshot_student", "student", "active", groupA);
        long semesterId = insertSemester(jdbc, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        jdbc.update("""
                INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at)
                VALUES (?, 'student', 'active', ?, NOW(), NOW())
                """, studentId, groupA);
        long rolesVersion = jdbc.queryForObject(
                "SELECT roles_version FROM users WHERE id = ?", Long.class, studentId);
        jdbc.update("""
                INSERT INTO student_group_history (user_id, group_id, joined_at, left_at)
                VALUES (?, ?, DATE '2026-01-01', NULL)
                """, studentId, groupA);
        long studentGrantId = jdbc.queryForObject(
                "SELECT id FROM user_role_grants WHERE user_id = ? AND role = 'student'",
                Long.class, studentId);
        insertSession(jdbc, studentId, studentGrantId, SESSION_ID);

        JdbcStudentProjectionQueryAdapter adapter =
                new JdbcStudentProjectionQueryAdapter(jdbc);
        PausingProjectionQuery query = new PausingProjectionQuery(
                adapter, new CountDownLatch(1), new CountDownLatch(1));
        StudentProjectionScopeService resolver = new StudentProjectionScopeService(
                query,
                Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"),
                        java.time.ZoneId.of("Europe/Moscow")));
        TransactionInterceptor resolverTransaction = new TransactionInterceptor(
                new DataSourceTransactionManager(resolverDataSource),
                new AnnotationTransactionAttributeSource());
        ProxyFactory resolverProxyFactory = new ProxyFactory(resolver);
        resolverProxyFactory.setProxyTargetClass(true);
        resolverProxyFactory.addAdvice(resolverTransaction);
        StudentProjectionScopeService proxiedResolver =
                (StudentProjectionScopeService) resolverProxyFactory.getProxy();
        InternalJwtClaims claims = new InternalJwtClaims(
                studentId,
                SESSION_ID,
                1L,
                rolesVersion,
                "STUDENT",
                "ACTIVE",
                groupA,
                false,
                false);
        AtomicReference<StudentProjectionScope> resolved = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread resolverThread = new Thread(() -> {
            try {
                resolved.set(proxiedResolver.resolve(semesterId, claims));
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "projection-repeatable-read");
        resolverThread.start();
        assertThat(query.authorityRead.await(10, TimeUnit.SECONDS)).isTrue();

        DataSource writerDataSource = dataSourceForSchema(schema);
        JdbcTemplate writerJdbc = new JdbcTemplate(writerDataSource);
        TransactionTemplate writerTransaction = new TransactionTemplate(
                new DataSourceTransactionManager(writerDataSource));
        writerTransaction.executeWithoutResult(ignored -> {
            writerJdbc.update("""
                    UPDATE user_role_grants
                    SET group_id = ?, updated_at = NOW()
                    WHERE user_id = ? AND role = 'student'
                    """, groupB, studentId);
            writerJdbc.update("""
                    UPDATE student_group_history
                    SET left_at = DATE '2026-09-10'
                    WHERE user_id = ? AND left_at IS NULL
                    """, studentId);
            writerJdbc.update("""
                    INSERT INTO student_group_history (user_id, group_id, joined_at, left_at)
                    VALUES (?, ?, DATE '2026-09-10', NULL)
                    """, studentId, groupB);
        });
        query.writerCommitted.countDown();
        resolverThread.join(10_000L);

        assertThat(resolverThread.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(resolved.get()).isNotNull();
        assertThat(resolved.get().rankGroupId()).isEqualTo(groupA);
        assertThat(resolved.get().rankEligible()).isTrue();
    }

    private String newSchema() {
        String schema = "rct_projection_" + SCHEMA_SEQUENCE.incrementAndGet();
        adminJdbc.execute("CREATE SCHEMA \"" + schema + "\"");
        return schema;
    }

    private JdbcTemplate jdbcFor(String schema) {
        return new JdbcTemplate(dataSourceForSchema(schema));
    }

    private DriverManagerDataSource dataSourceForSchema(String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String schemaSeparator = jdbcUrl.contains("?") ? "&" : "?";
        dataSource.setUrl(schema == null
                ? jdbcUrl
                : jdbcUrl + schemaSeparator + "currentSchema=" + schema);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(dataSourceForSchema(schema))
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(true)
                .load();
    }

    private static long insertGroup(JdbcTemplate jdbc, String suffix) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name) VALUES (?) RETURNING id",
                Long.class, "Projection " + suffix);
    }

    private static long insertUser(
            JdbcTemplate jdbc,
            String login,
            String role,
            String status,
            Long groupId) {
        return jdbc.queryForObject("""
                INSERT INTO users (
                    login, password_hash, last_name, first_name, role, status,
                    is_headman, group_id, password_changed, created_at, updated_at)
                VALUES (?, 'hash', 'Projection', 'Fixture', CAST(? AS user_role), CAST(? AS account_status),
                        FALSE, ?, FALSE, NOW(), NOW())
                RETURNING id
                """, Long.class, login, role, status, groupId);
    }

    private static long insertSemester(JdbcTemplate jdbc, LocalDate dateFrom, LocalDate dateTo) {
        return jdbc.queryForObject("""
                INSERT INTO semesters (name, date_from, date_to, is_active)
                VALUES ('Projection semester', ?, ?, FALSE)
                RETURNING id
                """, Long.class, dateFrom, dateTo);
    }

    private static long insertSubject(JdbcTemplate jdbc, String name, long groupId) {
        return jdbc.queryForObject("""
                WITH inserted AS (
                    INSERT INTO subjects (name, type, group_id)
                    VALUES (?, 'lecture', ?)
                    RETURNING id
                )
                INSERT INTO subject_lesson_types (subject_id, lesson_type)
                SELECT id, 'lecture' FROM inserted
                RETURNING subject_id
                """, Long.class, name, groupId);
    }

    private static long insertAssignment(
            JdbcTemplate jdbc,
            long teacherId,
            long subjectId,
            long groupId,
            long semesterId) {
        return jdbc.queryForObject("""
                INSERT INTO assignments (
                    teacher_id, subject_id, group_id, semester_id, lesson_type, valid_from)
                VALUES (?, ?, ?, ?, 'lecture', DATE '2026-09-01')
                RETURNING id
                """, Long.class, teacherId, subjectId, groupId, semesterId);
    }

    private static UUID insertSession(
            JdbcTemplate jdbc,
            long userId,
            long activeRoleGrantId,
            UUID sessionId) {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("""
                INSERT INTO auth_sessions (
                    sid, user_id, active_role_grant_id, session_version,
                    current_refresh_jti, refresh_expires_at, created_at, last_seen_at,
                    auth_method)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, 'PASSWORD')
                """,
                sessionId,
                userId,
                activeRoleGrantId,
                UUID.randomUUID(),
                Timestamp.from(Instant.parse("2030-01-01T00:00:00Z")),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return sessionId;
    }

    private static final class PausingProjectionQuery implements StudentProjectionQuery {
        private final StudentProjectionQuery delegate;
        private final CountDownLatch authorityRead;
        private final CountDownLatch writerCommitted;

        private PausingProjectionQuery(
                StudentProjectionQuery delegate,
                CountDownLatch authorityRead,
                CountDownLatch writerCommitted) {
            this.delegate = delegate;
            this.authorityRead = authorityRead;
            this.writerCommitted = writerCommitted;
        }

        @Override
        public Optional<AuthoritySnapshot> findAuthority(long userId) {
            Optional<AuthoritySnapshot> authority = delegate.findAuthority(userId);
            authorityRead.countDown();
            try {
                if (!writerCommitted.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("writer did not commit the concurrent update");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("resolver wait was interrupted", interrupted);
            }
            return authority;
        }

        @Override
        public Optional<SessionSnapshot> findSession(long userId, UUID sessionId) {
            return delegate.findSession(userId, sessionId);
        }

        @Override
        public Optional<SemesterSnapshot> findSemester(long semesterId) {
            return delegate.findSemester(semesterId);
        }

        @Override
        public List<GroupHistorySnapshot> findGroupHistory(long userId) {
            return delegate.findGroupHistory(userId);
        }

        @Override
        public List<AssignmentSnapshot> findAssignments(
                long semesterId,
                java.util.Collection<Long> groupIds,
                LocalDate dateFrom,
                LocalDate dateUntilExclusive) {
            return delegate.findAssignments(semesterId, groupIds, dateFrom, dateUntilExclusive);
        }

        @Override
        public java.util.Map<Long, SubjectSnapshot> findSubjectsByIds(
                java.util.Collection<Long> subjectIds) {
            return delegate.findSubjectsByIds(subjectIds);
        }

        @Override
        public List<Long> findActiveStudentIds(
                long groupId,
                long semesterId,
                LocalDate dateFrom,
                LocalDate dateUntilExclusive) {
            return delegate.findActiveStudentIds(groupId, semesterId, dateFrom, dateUntilExclusive);
        }
    }
}
