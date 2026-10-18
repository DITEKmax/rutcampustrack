package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserAssembler;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

/** Real JpaTransactionManager + JdbcTemplate under the deployed Academic column grants. */
@Import(UserOwnershipLifecycleIT.RuntimePrincipals.class)
@TestExecutionListeners(
        inheritListeners = false,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class UserOwnershipLifecycleIT extends AbstractAcademicIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserService users;
    @Autowired private UserAssembler assembler;
    @Autowired private GroupService groups;
    @Autowired private PasswordEncoder passwords;
    @Autowired private RequestContext context;
    @MockitoSpyBean private StudentGroupHistoryRepository history;
    private static final AtomicLong TELEGRAM = new AtomicLong(9_100_000_000L);
    private JdbcTemplate administrator;
    private long semester;
    private LocalDate joinedAt;
    private List<Long> previousActive;

    @BeforeEach
    void ownedSeedAndLiveActor() {
        // Administrator fixture access never enters a product bean or the tested transaction.
        administrator =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                RuntimePrincipals.jdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        previousActive =
                administrator.queryForList(
                        "SELECT id FROM semesters WHERE is_active ORDER BY id", Long.class);
        administrator.update("UPDATE semesters SET is_active=false WHERE is_active");
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        var writable =
                administrator.queryForList(
                        "SELECT id FROM semesters WHERE date_from<=? AND date_to>=? AND NOT"
                                + " is_archived AND archive_transition='NONE' AND NOT"
                                + " archive_release_pending ORDER BY id LIMIT 1",
                        Long.class,
                        today,
                        today);
        semester =
                writable.isEmpty()
                        ? administrator.queryForObject(
                                "INSERT INTO semesters(name,date_from,date_to,is_active) VALUES"
                                        + " (?,?,?,true) RETURNING id",
                                Long.class,
                                "Owned JPA " + UUID.randomUUID(),
                                today.minusDays(7),
                                today.plusDays(7))
                        : writable.getFirst();
        administrator.update("UPDATE semesters SET is_active=true WHERE id=?", semester);
        joinedAt =
                administrator.queryForObject(
                        "SELECT date_from FROM semesters WHERE id=?", LocalDate.class, semester);
        long actor =
                administrator.queryForObject(
                        "INSERT INTO users(login,last_name,first_name,role,password_hash) VALUES"
                                + " (?,'Synthetic','JPA','admin',?) RETURNING id",
                        Long.class,
                        "jpa_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                        passwords.encode("synthetic-actor-password"));
        administrator.update(
                "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)"
                        + " VALUES (?,true,'FULL','{}',1)",
                actor);
        long grant =
                administrator.queryForObject(
                        "INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at)"
                                + " VALUES (?,'admin','active',NOW(),NOW()) RETURNING id",
                        Long.class,
                        actor);
        UUID sid = UUID.randomUUID();
        administrator.update(
                "INSERT INTO"
                    + " auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)"
                    + " VALUES (?,?,?,1,?,NOW()+INTERVAL '1 hour',NOW(),NOW(),'PASSWORD')",
                sid,
                actor,
                grant,
                UUID.randomUUID());
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        context.setUserId(actor);
        context.setRole(UserRole.ADMIN);
        context.setClaims(
                new InternalJwtClaims(
                        actor,
                        sid,
                        1,
                        administrator.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?", Long.class, actor),
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("rct_academic_runtime");
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
    void restoreOnlyOwnedFixtureSelection() {
        RequestContextHolder.resetRequestAttributes();
        if (administrator != null && previousActive != null) {
            administrator.update("UPDATE semesters SET is_active=false WHERE id=?", semester);
            for (Long id : previousActive)
                administrator.update("UPDATE semesters SET is_active=true WHERE id=?", id);
        }
        // Unique rows stay in this explicitly owned, non-reused container until teardown/reset by
        // its existing owner.
    }

    @Test
    void realStudentAndAdminCreationPreserveInitialCredentialGrantsAndHistory() {
        long group = managedGroup();
        var student = users.createUser(student(group)).getContent();
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
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_role_grants WHERE user_id=? AND"
                                        + " role='student' AND status='active' AND group_id=?",
                                Long.class,
                                student.getId(),
                                group))
                .isOne();
        var admin =
                users.createUser(
                                new CreateUserRequest(
                                        "Synthetic",
                                        "Administrator",
                                        null,
                                        UserRole.ADMIN,
                                        null,
                                        null,
                                        null,
                                        new AdminAccessInput("FULL", List.of())))
                        .getContent();
        assertThat(admin).isNotNull();
        assertInitialCredential(admin);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT p.assigned AND p.mode='FULL' AND p.version=1 AND"
                                        + " g.status='active' FROM admin_access_profiles p JOIN"
                                        + " user_role_grants g ON g.user_id=p.user_id AND"
                                        + " g.role='admin' WHERE p.user_id=?",
                                Boolean.class,
                                admin.getId()))
                .isTrue();
    }

    @Test
    void failureAfterRealHistoryFlushRollsBackProfileIdentityGrantHistoryAndOutbox() {
        long group = managedGroup();
        long outboxBefore =
                administrator.queryForObject("SELECT count(*) FROM academic_outbox", Long.class);
        AtomicLong attemptedUser = new AtomicLong();
        // Spring Data repository methods are interface methods. Delegate to Spring Test's
        // original proxy, as HistoricalMembershipIT does, instead of callRealMethod().
        org.mockito.stubbing.Answer<?> repository =
                mockingDetails(history).getMockCreationSettings().getDefaultAnswer();
        assertThat(repository).isNotNull();
        doAnswer(
                        call -> {
                            ru.rutcampustrack.academic.entity.StudentGroupHistory row =
                                    call.getArgument(0);
                            attemptedUser.set(row.getUserId());
                            return repository.answer(call);
                        })
                .when(history)
                .save(any(ru.rutcampustrack.academic.entity.StudentGroupHistory.class));
        doAnswer(
                        call -> {
                            repository.answer(call);
                            throw new IllegalStateException("owned post-flush rollback");
                        })
                .when(history)
                .flush();
        assertThatThrownBy(() -> users.createUser(student(group)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("owned post-flush rollback");
        assertThat(attemptedUser.get()).isPositive();
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM users WHERE id=?",
                                Long.class,
                                attemptedUser.get()))
                .isZero();
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                Long.class,
                                attemptedUser.get()))
                .isZero();
        assertThat(
                        administrator.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=?",
                                Long.class,
                                attemptedUser.get()))
                .isZero();
        assertThat(administrator.queryForObject("SELECT count(*) FROM academic_outbox", Long.class))
                .isEqualTo(outboxBefore);
    }

    private void assertInitialCredential(UserCreatedResponse created) {
        String plain = created.getInitialPassword();
        assertThat(plain != null && !plain.isBlank()).isTrue();
        String hash =
                administrator.queryForObject(
                        "SELECT password_hash FROM users WHERE id=?",
                        String.class,
                        created.getId());
        assertThat(passwords.matches(plain, hash)).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT credentials_version=1 AND telegram_binding_version=1 AND"
                                    + " generated_password_origin='initial' FROM users WHERE id=?",
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
        for (int number = 100; number <= 599; number++) {
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RuntimePrincipals {
        private static final String DATABASE =
                "academic_ownership_" + UUID.randomUUID().toString().replace("-", "");
        private static boolean prepared;

        RuntimePrincipals() {
            prepareOwnedDatabase();
        }

        private static synchronized void prepareOwnedDatabase() {
            if (prepared) return;
            if (!POSTGRES.isRunning() || POSTGRES.isShouldBeReused())
                throw new IllegalStateException(
                        "ORM proof requires this owned non-reused PostgreSQL");
            new JdbcTemplate(
                            new DriverManagerDataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword()))
                    .execute("CREATE DATABASE \"" + DATABASE + "\" OWNER rct_migrator");
            prepared = true;
        }

        static String jdbcUrl() {
            String owned =
                    POSTGRES.getJdbcUrl()
                            .replaceFirst(
                                    "/"
                                            + java.util.regex.Pattern.quote(
                                                    POSTGRES.getDatabaseName())
                                            + "(?=\\?|$)",
                                    "/" + DATABASE);
            if (owned.equals(POSTGRES.getJdbcUrl()))
                throw new IllegalStateException("Owned ORM database URL was not selected");
            return owned;
        }

        @Bean
        @Primary
        DataSource runtimeDataSource() {
            return new DriverManagerDataSource(
                    jdbcUrl(),
                    "rct_academic_runtime",
                    ru.rutcampustrack.shared.testcontainers.MigrationTestUtils
                            .ACADEMIC_AUTH_FIXTURE_PASSWORD);
        }

        @Bean
        @FlywayDataSource
        DataSource migrationDataSource() {
            return new DriverManagerDataSource(
                    jdbcUrl(),
                    "rct_migrator",
                    ru.rutcampustrack.shared.testcontainers.MigrationTestUtils
                            .ACADEMIC_AUTH_FIXTURE_PASSWORD);
        }
    }
}
