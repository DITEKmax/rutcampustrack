package ru.rutcampustrack.auth.session.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL proof for the two session authority ports.
 *
 * <p>The container is deliberately fresh and non-reusable.  The academic
 * migration directory is loaded from this checkout so the test exercises the
 * same V1..V24 schema chain as the owning service, including the imported
 * V24 source.  No Spring application context or reused academic_db fixture is
 * involved.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSessionAuthorityIT {

    private static final Instant GRANT_TIME = Instant.parse("2026-09-09T00:00:00Z");
    private static final Instant SESSION_TIME = Instant.parse("2026-09-09T01:00:00Z");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rct_student_auth")
            .withUsername("rct_student_auth")
            .withPassword("rct_student_auth_pass")
            .withReuse(false);

    private JdbcTemplate jdbc;
    private DataSource dataSource;
    private DataSourceTransactionManager transactionManager;
    private JdbcSessionAuthority authority;
    private int userSequence;

    @BeforeAll
    void startDatabase() {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:" + migrationDirectory().toAbsolutePath())
                .load()
                .migrate();

        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        source.setDriverClassName("org.postgresql.Driver");
        dataSource = source;
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        authority = new JdbcSessionAuthority(jdbc, transactionManager);
    }

    @AfterAll
    void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void createAndSnapshotRecheckCredentialAndReturnSafeAuthoritativeState() {
        UserFixture user = seedUser(
                GrantSeed.active(AuthRole.STUDENT, 1L),
                GrantSeed.active(AuthRole.TEACHER, null)
        );
        long studentGrant = grantId(user, AuthRole.STUDENT);
        CreatedSession created = create(user, AuthMethod.PASSWORD, studentGrant);

        SessionSnapshot snapshot = authority.snapshot(new SessionStatePort.SnapshotCommand(
                user.userId(), created.sessionId(), SESSION_TIME.plusSeconds(5)
        )).snapshot();
        assertThat(snapshot.activeRole().role()).isEqualTo(AuthRole.STUDENT);
        assertThat(snapshot.roles()).extracting(RoleGrant::role)
                .containsExactly(AuthRole.STUDENT, AuthRole.TEACHER);
        assertThat(snapshot.toString()).doesNotContain(created.currentJti().toString());

        SessionStatePort.CreateSessionResult stale = authority.createSession(
                command(user, AuthMethod.PASSWORD, UUID.randomUUID(), UUID.randomUUID(),
                        studentGrant, user.grants(), user.rolesVersion(), "different-hash")
        );
        assertThat(stale.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id = ?", user.userId()))
                .isEqualTo(1L);
    }

    @Test
    void foreignSessionAndGrantHintsCannotCrossOwner() {
        UserFixture owner = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        UserFixture foreign = seedUser(GrantSeed.active(AuthRole.TEACHER, null));
        CreatedSession created = create(owner, AuthMethod.OTP, grantId(owner, AuthRole.STUDENT));

        SessionStatePort.SnapshotResult foreignSnapshot = authority.snapshot(
                new SessionStatePort.SnapshotCommand(foreign.userId(), created.sessionId(), SESSION_TIME)
        );
        assertThat(foreignSnapshot.failureCode()).isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);

        SessionStatePort.RoleSelectionResult foreignSelection = authority.selectRole(
                new SessionStatePort.SelectRoleCommand(
                        foreign.userId(), created.sessionId(), AuthRole.STUDENT, 1,
                        SESSION_TIME, event(foreign.userId(), created.sessionId(),
                                SecurityEvent.Type.ROLE_CHANGED)
                )
        );
        assertThat(foreignSelection.failureCode()).isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);

        List<RoleGrant> foreignHints = new ArrayList<>(owner.grants());
        foreignHints.add(foreign.grants().get(0));
        SessionStatePort.CreateSessionResult staleGrant = authority.createSession(
                command(owner, AuthMethod.OTP, UUID.randomUUID(), UUID.randomUUID(),
                        grantId(owner, AuthRole.STUDENT), foreignHints, owner.rolesVersion(), null)
        );
        assertThat(staleGrant.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id = ?", owner.userId()))
                .isEqualTo(1L);
    }

    @Test
    void staleAuthoritativeGrantVersionRejectsSessionAndLoginEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        long studentGrant = grantId(user, AuthRole.STUDENT);
        long originalRolesVersion = user.rolesVersion();
        List<RoleGrant> originalGrants = user.grants();

        jdbc.update(
                "UPDATE user_role_grants SET status = ?, updated_at = ? WHERE id = ? AND user_id = ?",
                "suspended", Timestamp.from(GRANT_TIME.plusSeconds(1)), studentGrant, user.userId()
        );

        SessionStatePort.CreateSessionResult stale = authority.createSession(
                command(user, AuthMethod.OTP, UUID.randomUUID(), UUID.randomUUID(),
                        studentGrant, originalGrants, originalRolesVersion, null)
        );

        assertThat(stale.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id = ?", user.userId()))
                .isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.LOGIN)).isZero();
    }

    @Test
    void snapshotWaitsForHeldGrantWriterLockAndReturnsWhollyNewTuple() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        long studentGrant = grantId(user, AuthRole.STUDENT);
        CreatedSession current = create(user, AuthMethod.OTP, studentGrant);
        long originalRolesVersion = user.rolesVersion();
        long originalSessionVersion = current.command().state().sessionVersion();
        CountDownLatch writerLockHeld = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> writer = executor.submit(() ->
                    updateGrantStatusHoldingUserLock(
                            user.userId(), studentGrant, writerLockHeld, releaseWriter
                    )
            );
            assertThat(writerLockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<SessionStatePort.SnapshotResult> snapshot = executor.submit(() ->
                    authority.snapshot(new SessionStatePort.SnapshotCommand(
                            user.userId(), current.sessionId(), SESSION_TIME.plusSeconds(25)
                    ))
            );
            assertThat(awaitUserLockContention()).isTrue();
            releaseWriter.countDown();

            assertThat(writer.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            SessionStatePort.SnapshotResult result = snapshot.get(15, TimeUnit.SECONDS);
            assertThat(result.succeeded()).isTrue();
            SessionSnapshot returned = result.snapshot();
            RoleGrant returnedStudent = returned.roles().stream()
                    .filter(grant -> grant.grantId() == studentGrant)
                    .findFirst()
                    .orElseThrow();
            assertThat(returned.rolesVersion()).isEqualTo(originalRolesVersion + 1);
            assertThat(returned.sessionVersion()).isEqualTo(originalSessionVersion + 1);
            assertThat(returned.activeRole()).isNull();
            assertThat(returnedStudent.status()).isEqualTo(RoleStatus.SUSPENDED);
        } finally {
            releaseWriter.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void roleSelectionIsOwnVersionedAndSameRoleIsIdempotentWithoutEvent() {
        UserFixture user = seedUser(
                GrantSeed.active(AuthRole.STUDENT, 1L),
                GrantSeed.active(AuthRole.TEACHER, null)
        );
        CreatedSession created = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        long teacherGrant = grantId(user, AuthRole.TEACHER);

        SessionStatePort.RoleSelectionResult selected = authority.selectRole(
                new SessionStatePort.SelectRoleCommand(
                        user.userId(), created.sessionId(), AuthRole.TEACHER, 1,
                        SESSION_TIME.plusSeconds(10),
                        event(user.userId(), created.sessionId(), SecurityEvent.Type.ROLE_CHANGED)
                )
        );
        assertThat(selected.succeeded()).isTrue();
        assertThat(selected.snapshot().activeRole().grantId()).isEqualTo(teacherGrant);
        assertThat(selected.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.ROLE_CHANGED)).isEqualTo(1L);

        SessionStatePort.RoleSelectionResult repeated = authority.selectRole(
                new SessionStatePort.SelectRoleCommand(
                        user.userId(), created.sessionId(), AuthRole.TEACHER, 2,
                        SESSION_TIME.plusSeconds(11),
                        event(user.userId(), created.sessionId(), SecurityEvent.Type.ROLE_CHANGED)
                )
        );
        assertThat(repeated.succeeded()).isTrue();
        assertThat(repeated.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.ROLE_CHANGED)).isEqualTo(1L);

        SessionStatePort.RoleSelectionResult staleVersion = authority.selectRole(
                new SessionStatePort.SelectRoleCommand(
                        user.userId(), created.sessionId(), AuthRole.STUDENT, 1,
                        SESSION_TIME.plusSeconds(12),
                        event(user.userId(), created.sessionId(), SecurityEvent.Type.ROLE_CHANGED)
                )
        );
        assertThat(staleVersion.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_VERSION_CONFLICT);
    }

    @Test
    void terminalRoleIsReadOnlyAndSuspendedActiveRoleIsClearedOnRefresh() {
        UserFixture terminal = seedUser(GrantSeed.of(AuthRole.STUDENT, RoleStatus.EXPELLED, 1L));
        CreatedSession terminalSession = create(
                terminal, AuthMethod.OTP, grantId(terminal, AuthRole.STUDENT)
        );
        SessionSnapshot terminalSnapshot = authority.snapshot(
                new SessionStatePort.SnapshotCommand(
                        terminal.userId(), terminalSession.sessionId(), SESSION_TIME
                )
        ).snapshot();
        assertThat(terminalSnapshot.activeRole().isReadOnly()).isTrue();
        assertThat(terminalSnapshot.isReadOnly()).isTrue();

        UserFixture suspended = seedUser(
                GrantSeed.active(AuthRole.STUDENT, 1L),
                GrantSeed.of(AuthRole.TEACHER, RoleStatus.SUSPENDED, null)
        );
        CreatedSession suspendedSession = create(
                suspended, AuthMethod.OTP, grantId(suspended, AuthRole.STUDENT)
        );
        SessionStatePort.RoleSelectionResult suspendedSelection = authority.selectRole(
                new SessionStatePort.SelectRoleCommand(
                        suspended.userId(), suspendedSession.sessionId(), AuthRole.TEACHER, 1,
                        SESSION_TIME.plusSeconds(2),
                        event(suspended.userId(), suspendedSession.sessionId(),
                                SecurityEvent.Type.ROLE_CHANGED)
                )
        );
        assertThat(suspendedSelection.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE);

        UserFixture selectable = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        long activeGrant = grantId(selectable, AuthRole.STUDENT);
        CreatedSession refreshSession = create(selectable, AuthMethod.OTP, activeGrant);
        jdbc.update(
                "UPDATE user_role_grants SET status = ?, updated_at = ? WHERE id = ?",
                "suspended", Timestamp.from(GRANT_TIME.plusSeconds(1)), activeGrant
        );

        SessionStatePort.RefreshResult refreshed = authority.refresh(
                new SessionStatePort.RefreshCommand(
                        selectable.userId(), refreshSession.sessionId(),
                        refreshSession.currentJti(), UUID.randomUUID(), SESSION_TIME.plusSeconds(20)
                )
        );
        assertThat(refreshed.succeeded()).isTrue();
        assertThat(refreshed.snapshot().activeRole()).isNull();
        assertThat(refreshed.snapshot().sessionVersion()).isEqualTo(2);
    }

    @Test
    void concurrentRefreshHasOneWinnerAndImmediatePreviousLoser() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession created = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        UUID replacementA = UUID.randomUUID();
        UUID replacementB = UUID.randomUUID();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SessionStatePort.RefreshResult> first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return refresh(user, created, replacementA);
            });
            Future<SessionStatePort.RefreshResult> second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return refresh(user, created, replacementB);
            });

            SessionStatePort.RefreshResult firstResult = first.get(15, TimeUnit.SECONDS);
            SessionStatePort.RefreshResult secondResult = second.get(15, TimeUnit.SECONDS);
            List<SessionStatePort.RefreshResult> results = List.of(firstResult, secondResult);
            assertThat(results.stream().filter(SessionStatePort.RefreshResult::succeeded).count())
                    .isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> result.failureCode()
                            == SessionStatePort.FailureCode.REFRESH_ALREADY_ROTATED)
                    .count()).isEqualTo(1);
            assertThat(results.stream().filter(SessionStatePort.RefreshResult::succeeded)
                    .findFirst().orElseThrow().snapshot().refreshExpiresAt())
                    .isEqualTo(created.expiresAt());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refreshRejectsOlderJtiAndNeverExtendsAbsoluteExpiry() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession created = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        UUID replacement = UUID.randomUUID();
        SessionStatePort.RefreshResult first = refresh(user, created, replacement);
        assertThat(first.succeeded()).isTrue();

        SessionStatePort.RefreshResult older = authority.refresh(
                new SessionStatePort.RefreshCommand(
                        user.userId(), created.sessionId(), created.currentJti(),
                        UUID.randomUUID(), SESSION_TIME.plusSeconds(30)
                )
        );
        assertThat(older.failureCode()).isEqualTo(SessionStatePort.FailureCode.REFRESH_ALREADY_ROTATED);

        SessionStatePort.RefreshResult unknown = authority.refresh(
                new SessionStatePort.RefreshCommand(
                        user.userId(), created.sessionId(), UUID.randomUUID(),
                        UUID.randomUUID(), SESSION_TIME.plusSeconds(31)
                )
        );
        assertThat(unknown.failureCode()).isEqualTo(SessionStatePort.FailureCode.REFRESH_REJECTED);
        assertThat(first.snapshot().refreshExpiresAt()).isEqualTo(created.expiresAt());
    }

    @Test
    void revokeCurrentIsDurableIdempotentAndDeniesLaterSnapshot() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession created = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));

        SessionStatePort.RevokeResult revoked = authority.revokeCurrent(
                new SessionStatePort.RevokeCurrentCommand(
                        user.userId(), created.sessionId(), SESSION_TIME.plusSeconds(40),
                        event(user.userId(), created.sessionId(), SecurityEvent.Type.CURRENT_LOGOUT)
                )
        );
        assertThat(revoked.succeeded()).isTrue();
        assertThat(revoked.alreadyRevoked()).isFalse();
        assertThat(revoked.snapshot().isRevoked()).isTrue();

        SessionStatePort.SnapshotResult afterCommit = authority.snapshot(
                new SessionStatePort.SnapshotCommand(
                        user.userId(), created.sessionId(), SESSION_TIME.plusSeconds(41)
                )
        );
        assertThat(afterCommit.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);

        SessionStatePort.RevokeResult repeated = authority.revokeCurrent(
                new SessionStatePort.RevokeCurrentCommand(
                        user.userId(), created.sessionId(), SESSION_TIME.plusSeconds(42),
                        event(user.userId(), created.sessionId(), SecurityEvent.Type.CURRENT_LOGOUT)
                )
        );
        assertThat(repeated.succeeded()).isTrue();
        assertThat(repeated.alreadyRevoked()).isTrue();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isEqualTo(1L);
    }

    @Test
    void revokeAllCoversEveryOwnSessionAndAppendsOneEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.TMA, grantId(user, AuthRole.STUDENT));

        SessionStatePort.RevokeAllResult result = authority.revokeAll(
                new SessionStatePort.RevokeAllCommand(
                        user.userId(), current.sessionId(), SESSION_TIME.plusSeconds(50),
                        event(user.userId(), current.sessionId(), SecurityEvent.Type.LOGOUT_ALL)
                )
        );
        assertThat(result.succeeded()).isTrue();
        assertThat(result.revokedSessionCount()).isEqualTo(2);
        assertThat(revokedSessionCount(user.userId())).isEqualTo(2L);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.LOGOUT_ALL)).isEqualTo(1L);
        assertThat(authority.snapshot(new SessionStatePort.SnapshotCommand(
                user.userId(), other.sessionId(), SESSION_TIME.plusSeconds(51)
        )).failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
    }

    @Test
    void injectedFailureAfterCurrentRevokeEventRollsBackRevokeAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        JdbcSessionAuthority failingAuthority = failingAuthority("revoke.event");

        SessionStatePort.RevokeResult result = failingAuthority.revokeCurrent(
                new SessionStatePort.RevokeCurrentCommand(
                        user.userId(), current.sessionId(), SESSION_TIME.plusSeconds(52),
                        event(user.userId(), current.sessionId(), SecurityEvent.Type.CURRENT_LOGOUT)
                )
        );

        assertThat(result.failureCode()).isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertNoRevocationOrEvent(user, current, SecurityEvent.Type.CURRENT_LOGOUT);
    }

    @Test
    void injectedFailureAfterRevokeAllEventRollsBackEveryRevokeAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.TMA, grantId(user, AuthRole.STUDENT));
        JdbcSessionAuthority failingAuthority = failingAuthority("revoke-all.event");

        SessionStatePort.RevokeAllResult result = failingAuthority.revokeAll(
                new SessionStatePort.RevokeAllCommand(
                        user.userId(), current.sessionId(), SESSION_TIME.plusSeconds(53),
                        event(user.userId(), current.sessionId(), SecurityEvent.Type.LOGOUT_ALL)
                )
        );

        assertThat(result.failureCode()).isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.LOGOUT_ALL)).isZero();
        assertSessionLive(user, current);
        assertSessionLive(user, other);
    }

    @Test
    void passwordChangeRechecksHashUpdatesFlagsRevokesAllAndAuditsAtomically() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        String replacementHash = "replacement-hash-" + user.userId();

        CredentialSessionTransactionPort.ChangePasswordResult result = authority.changePassword(
                new CredentialSessionTransactionPort.ChangePasswordCommand(
                        user.userId(), current.sessionId(),
                        new CredentialSessionTransactionPort.CredentialHash(user.passwordHash()),
                        new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                        SESSION_TIME.plusSeconds(60),
                    event(user.userId(), current.sessionId(), SecurityEvent.Type.PASSWORD_CHANGED,
                            AuthMethod.PASSWORD)
                )
        );
        assertThat(result.succeeded()).isTrue();
        assertThat(result.revokedSessionCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.userId()
        )).isEqualTo(replacementHash);
        assertThat(jdbc.queryForObject(
                "SELECT password_changed FROM users WHERE id = ?", Boolean.class, user.userId()
        )).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT initial_password FROM users WHERE id = ?", String.class, user.userId()
        )).isNull();
        assertThat(revokedSessionCount(user.userId())).isEqualTo(2L);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isEqualTo(1L);
        assertThat(authority.snapshot(new SessionStatePort.SnapshotCommand(
                user.userId(), other.sessionId(), SESSION_TIME.plusSeconds(61)
        )).failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
    }

    @Test
    void injectedFailureAfterPasswordEventRollsBackCredentialFlagsSessionsAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        String replacementHash = "event-failure-hash-" + user.userId();
        JdbcSessionAuthority failingAuthority = failingAuthority("password.event");

        CredentialSessionTransactionPort.ChangePasswordResult result = failingAuthority.changePassword(
                passwordCommand(user, current, replacementHash)
        );

        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertPasswordStateUnchanged(user);
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
        assertSessionLive(user, current);
        assertSessionLive(user, other);
    }

    @Test
    void injectedFailureAfterPasswordSessionRevokeRollsBackCredentialAndSessions() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        String replacementHash = "session-failure-hash-" + user.userId();
        JdbcSessionAuthority failingAuthority = failingAuthority("password.sessions");

        CredentialSessionTransactionPort.ChangePasswordResult result = failingAuthority.changePassword(
                passwordCommand(user, current, replacementHash)
        );

        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertPasswordStateUnchanged(user);
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
        assertSessionLive(user, current);
        assertSessionLive(user, other);
    }

    @Test
    void expiredCurrentSessionCannotChangePasswordOrAppendSecurityEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        String replacementHash = "expired-replacement-hash";

        CredentialSessionTransactionPort.ChangePasswordResult result = authority.changePassword(
                new CredentialSessionTransactionPort.ChangePasswordCommand(
                        user.userId(), current.sessionId(),
                        new CredentialSessionTransactionPort.CredentialHash(user.passwordHash()),
                        new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                        current.expiresAt(),
                        event(user.userId(), current.sessionId(), SecurityEvent.Type.PASSWORD_CHANGED,
                                AuthMethod.PASSWORD)
                )
        );
        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.SESSION_REVOKED);
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.userId()
        )).isEqualTo(user.passwordHash());
        assertThat(jdbc.queryForObject(
                "SELECT password_changed FROM users WHERE id = ?", Boolean.class, user.userId()
        )).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT initial_password FROM users WHERE id = ?", String.class, user.userId()
        )).isEqualTo("initial");
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
    }

    @Test
    void injectedFailureRollsBackPasswordStateSessionRevocationAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        JdbcSessionAuthority failingAuthority = new JdbcSessionAuthority(
                jdbc,
                transactionManager,
                operation -> {
                    if (operation.equals("password.credential")) {
                        throw new IllegalStateException("intentional transaction failure");
                    }
                }
        );

        CredentialSessionTransactionPort.ChangePasswordResult result = failingAuthority.changePassword(
                new CredentialSessionTransactionPort.ChangePasswordCommand(
                        user.userId(), current.sessionId(),
                        new CredentialSessionTransactionPort.CredentialHash(user.passwordHash()),
                        new CredentialSessionTransactionPort.CredentialHash("new-hash"),
                        SESSION_TIME.plusSeconds(70),
                    event(user.userId(), current.sessionId(), SecurityEvent.Type.PASSWORD_CHANGED,
                            AuthMethod.PASSWORD)
                )
        );
        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.userId()
        )).isEqualTo(user.passwordHash());
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
    }

    private JdbcSessionAuthority failingAuthority(String failedOperation) {
        return new JdbcSessionAuthority(
                jdbc,
                transactionManager,
                operation -> {
                    if (failedOperation.equals(operation)) {
                        throw new IllegalStateException("intentional transaction failure");
                    }
                }
        );
    }

    private CredentialSessionTransactionPort.ChangePasswordCommand passwordCommand(
            UserFixture user,
            CreatedSession current,
            String replacementHash
    ) {
        return new CredentialSessionTransactionPort.ChangePasswordCommand(
                user.userId(), current.sessionId(),
                new CredentialSessionTransactionPort.CredentialHash(user.passwordHash()),
                new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                SESSION_TIME.plusSeconds(60),
                event(user.userId(), current.sessionId(), SecurityEvent.Type.PASSWORD_CHANGED,
                        AuthMethod.PASSWORD)
        );
    }

    private void assertPasswordStateUnchanged(UserFixture user) {
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.userId()
        )).isEqualTo(user.passwordHash());
        assertThat(jdbc.queryForObject(
                "SELECT password_changed FROM users WHERE id = ?", Boolean.class, user.userId()
        )).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT initial_password FROM users WHERE id = ?", String.class, user.userId()
        )).isEqualTo("initial");
    }

    private void assertNoRevocationOrEvent(
            UserFixture user,
            CreatedSession current,
            SecurityEvent.Type eventType
    ) {
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), eventType)).isZero();
        assertSessionLive(user, current);
    }

    private void assertSessionLive(UserFixture user, CreatedSession session) {
        assertThat(count(
                "SELECT count(*) FROM auth_sessions "
                        + "WHERE sid = ? AND user_id = ? AND revoked_at IS NULL",
                session.sessionId(),
                user.userId()
        )).isEqualTo(1L);
    }

    private Integer updateGrantStatusHoldingUserLock(
            long userId,
            long grantId,
            CountDownLatch writerLockHeld,
            CountDownLatch releaseWriter
    ) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.queryForObject(
                    "SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId
            );
            writerLockHeld.countDown();
            try {
                if (!releaseWriter.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("grant writer release was not signalled");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("grant writer was interrupted", interrupted);
            }
            return jdbc.update(
                    "UPDATE user_role_grants SET status = ?, updated_at = ? "
                            + "WHERE id = ? AND user_id = ?",
                    "suspended", Timestamp.from(GRANT_TIME.plusSeconds(2)), grantId, userId
            );
        });
    }

    private boolean awaitUserLockContention() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long blocked = jdbc.queryForObject(
                    """
                            SELECT count(*)
                            FROM pg_stat_activity blocked
                            WHERE blocked.pid <> pg_backend_pid()
                              AND blocked.state = 'active'
                              AND blocked.wait_event_type = 'Lock'
                              AND blocked.query ~* 'from[[:space:]]+users'
                              AND blocked.query ~* 'where[[:space:]]+id[[:space:]]*='
                              AND blocked.query ~* 'for[[:space:]]+update'
                              AND EXISTS (
                                  SELECT 1
                                  FROM unnest(pg_blocking_pids(blocked.pid)) AS blocker(pid)
                                  WHERE blocker.pid <> pg_backend_pid()
                              )
                            """,
                    Long.class
            );
            if (blocked != null && blocked > 0) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return false;
    }

    @Test
    void failuresAndCommandTextDoNotExposeCredentialOrRefreshValues() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession created = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        String secretHash = user.passwordHash();

        SessionStatePort.RefreshResult rejected = authority.refresh(
                new SessionStatePort.RefreshCommand(
                        user.userId(), created.sessionId(), UUID.randomUUID(),
                        UUID.randomUUID(), SESSION_TIME.plusSeconds(80)
                )
        );
        assertThat(rejected.failureCode()).isEqualTo(SessionStatePort.FailureCode.REFRESH_REJECTED);
        assertThat(rejected.toString()).doesNotContain(created.currentJti().toString());

        SessionStatePort.CredentialProof proof = new SessionStatePort.CredentialProof(secretHash);
        CredentialSessionTransactionPort.CredentialHash hash =
                new CredentialSessionTransactionPort.CredentialHash(secretHash);
        assertThat(proof.toString()).doesNotContain(secretHash);
        assertThat(hash.toString()).doesNotContain(secretHash);
        assertThat(created.command().toString()).doesNotContain(secretHash);
        assertThat(created.command().toString()).doesNotContain(created.currentJti().toString());
    }

    private CreatedSession create(UserFixture user, AuthMethod method, Long activeGrantId) {
        UUID sessionId = UUID.randomUUID();
        UUID currentJti = UUID.randomUUID();
        SessionStatePort.CreateSessionCommand command = command(
                user, method, sessionId, currentJti, activeGrantId,
                user.grants(), user.rolesVersion(),
                method == AuthMethod.PASSWORD ? user.passwordHash() : null
        );
        SessionStatePort.CreateSessionResult result = authority.createSession(command);
        assertThat(result.succeeded()).isTrue();
        return new CreatedSession(
                user,
                sessionId,
                currentJti,
                SESSION_TIME.plusSeconds(24 * 60 * 60),
                command
        );
    }

    private SessionStatePort.RefreshResult refresh(
            UserFixture user,
            CreatedSession session,
            UUID replacementJti
    ) {
        return authority.refresh(new SessionStatePort.RefreshCommand(
                user.userId(), session.sessionId(), session.currentJti(),
                replacementJti, SESSION_TIME.plusSeconds(20)
        ));
    }

    private SessionStatePort.CreateSessionCommand command(
            UserFixture user,
            AuthMethod method,
            UUID sessionId,
            UUID currentJti,
            Long activeGrantId,
            List<RoleGrant> grants,
            long rolesVersion,
            String expectedHash
    ) {
        SessionState state = new SessionState(
                sessionId,
                user.userId(),
                activeGrantId,
                1,
                currentJti,
                null,
                SESSION_TIME.plusSeconds(24 * 60 * 60),
                SESSION_TIME,
                SESSION_TIME,
                null,
                null,
                method,
                "it-client",
                "it-location"
        );
        CredentialProofAndEvent credential = new CredentialProofAndEvent(
                method == AuthMethod.PASSWORD
                        ? new SessionStatePort.CredentialProof(expectedHash)
                        : null,
            event(user.userId(), sessionId, SecurityEvent.Type.LOGIN, method)
        );
        return new SessionStatePort.CreateSessionCommand(
                state, rolesVersion, grants, credential.proof(), credential.event()
        );
    }

    private SecurityEvent event(long userId, UUID sessionId, SecurityEvent.Type type) {
        return event(userId, sessionId, type, AuthMethod.OTP);
    }

    private SecurityEvent event(
            long userId,
            UUID sessionId,
            SecurityEvent.Type type,
            AuthMethod authMethod
    ) {
        return new SecurityEvent(
                userId,
                sessionId,
                type,
                SESSION_TIME,
                authMethod,
                "it-client",
                "it-location"
        );
    }

    private UserFixture seedUser(GrantSeed... seeds) {
        String login = "jdbc-it-" + (++userSequence);
        String hash = "credential-hash-" + login;
        long userId = jdbc.queryForObject(
                """
                        INSERT INTO users (
                            login, password_hash, last_name, first_name,
                            role, status, is_headman, group_id,
                            initial_password, password_changed, created_at, updated_at
                        ) VALUES (?, ?, 'Jdbc', 'Authority',
                                  CAST('student' AS user_role), CAST('active' AS account_status),
                                  FALSE, 1, 'initial', FALSE, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                login,
                hash,
                Timestamp.from(GRANT_TIME),
                Timestamp.from(GRANT_TIME)
        );
        for (GrantSeed seed : seeds) {
            jdbc.queryForObject(
                    """
                            INSERT INTO user_role_grants (
                                user_id, role, status, group_id, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            RETURNING id
                            """,
                    Long.class,
                    userId,
                    seed.role().name().toLowerCase(Locale.ROOT),
                    seed.status().name().toLowerCase(Locale.ROOT),
                    seed.groupId(),
                    Timestamp.from(GRANT_TIME),
                    Timestamp.from(GRANT_TIME)
            );
        }
        return loadUser(userId, hash);
    }

    private UserFixture loadUser(long userId, String passwordHash) {
        long rolesVersion = jdbc.queryForObject(
                "SELECT roles_version FROM users WHERE id = ?", Long.class, userId
        );
        List<RoleGrant> grants = jdbc.query(
                """
                        SELECT id, user_id, role, status, group_id, created_at, updated_at
                        FROM user_role_grants
                        WHERE user_id = ?
                        ORDER BY id
                        """,
                JdbcSessionAuthorityIT::mapGrant,
                userId
        );
        return new UserFixture(userId, passwordHash, rolesVersion, grants);
    }

    private long grantId(UserFixture user, AuthRole role) {
        return user.grants().stream()
                .filter(grant -> grant.role() == role)
                .findFirst()
                .orElseThrow()
                .grantId();
    }

    private long eventCount(long userId, SecurityEvent.Type type) {
        return count(
                "SELECT count(*) FROM account_security_events WHERE user_id = ? AND event_type = ?",
                userId,
                type.name()
        );
    }

    private long revokedSessionCount(long userId) {
        return count(
                "SELECT count(*) FROM auth_sessions WHERE user_id = ? AND revoked_at IS NOT NULL",
                userId
        );
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static RoleGrant mapGrant(ResultSet rs, int rowNum) throws SQLException {
        return new RoleGrant(
                rs.getLong("id"),
                rs.getLong("user_id"),
                AuthRole.valueOf(rs.getString("role").toUpperCase(Locale.ROOT)),
                RoleStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                nullableLong(rs, "group_id"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Path migrationDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "services/academic-service/academic-app/src/main/resources/db/migration"
            );
            if (Files.isRegularFile(candidate.resolve("V24__auth_session_authority.sql"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("academic migration directory is unavailable");
    }

    private record UserFixture(
            long userId,
            String passwordHash,
            long rolesVersion,
            List<RoleGrant> grants
    ) {
        private UserFixture {
            grants = List.copyOf(grants);
        }
    }

    private record GrantSeed(AuthRole role, RoleStatus status, Long groupId) {
        private static GrantSeed active(AuthRole role, Long groupId) {
            return new GrantSeed(role, RoleStatus.ACTIVE, groupId);
        }

        private static GrantSeed of(AuthRole role, RoleStatus status, Long groupId) {
            return new GrantSeed(role, status, groupId);
        }
    }

    private record CreatedSession(
            UserFixture user,
            UUID sessionId,
            UUID currentJti,
            Instant expiresAt,
            SessionStatePort.CreateSessionCommand command
    ) {
    }

    private record CredentialProofAndEvent(
            SessionStatePort.CredentialProof proof,
            SecurityEvent event
    ) {
    }
}
