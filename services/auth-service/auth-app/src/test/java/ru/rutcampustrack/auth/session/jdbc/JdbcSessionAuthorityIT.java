package ru.rutcampustrack.auth.session.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.auth.controller.AuthSessionController;
import ru.rutcampustrack.auth.controller.InternalWsSessionAdmissionController;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.exception.GlobalExceptionHandler;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.WsTicketService;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

/**
 * Real PostgreSQL proof for the two session authority ports.
 *
 * <p>The container is deliberately fresh and non-reusable. The academic migration directory is
 * loaded from this checkout so the test exercises the same V1..V24 schema chain as the owning
 * service, including the imported V24 source. No Spring application context or reused academic_db
 * fixture is involved.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSessionAuthorityIT {

    private static final Instant GRANT_TIME = Instant.parse("2026-09-09T00:00:00Z");
    private static final Instant SESSION_TIME = Instant.parse("2026-09-09T01:00:00Z");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("rct_student_auth")
                    .withUsername("rct_student_auth")
                    .withPassword("rct_student_auth_pass")
                    .withReuse(false);

    private JdbcTemplate jdbc;
    private DataSource dataSource;
    private DataSourceTransactionManager transactionManager;
    private JdbcSessionAuthority authority;
    private PasswordChangedOutbox passwordChangedOutbox;
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
        passwordChangedOutbox =
                new PasswordChangedOutbox(jdbc, new ObjectMapper().findAndRegisterModules());
        authority = new JdbcSessionAuthority(jdbc, transactionManager, passwordChangedOutbox);
    }

    @AfterAll
    void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void createAndSnapshotRecheckCredentialAndReturnSafeAuthoritativeState() {
        UserFixture user =
                seedUser(
                        GrantSeed.active(AuthRole.STUDENT, 1L),
                        GrantSeed.active(AuthRole.TEACHER, null));
        long studentGrant = grantId(user, AuthRole.STUDENT);
        CreatedSession created = create(user, AuthMethod.PASSWORD, studentGrant);

        SessionSnapshot snapshot =
                authority
                        .snapshot(
                                new SessionStatePort.SnapshotCommand(
                                        user.userId(),
                                        created.sessionId(),
                                        SESSION_TIME.plusSeconds(5)))
                        .snapshot();
        assertThat(snapshot.activeRole().role()).isEqualTo(AuthRole.STUDENT);
        assertThat(snapshot.roles())
                .extracting(RoleGrant::role)
                .containsExactly(AuthRole.STUDENT, AuthRole.TEACHER);
        assertThat(snapshot.toString()).doesNotContain(created.currentJti().toString());

        SessionStatePort.CreateSessionResult stale =
                authority.createSession(
                        command(
                                user,
                                AuthMethod.PASSWORD,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                studentGrant,
                                user.grants(),
                                user.rolesVersion(),
                                "different-hash"));
        assertThat(stale.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id = ?", user.userId()))
                .isEqualTo(1L);
    }

    @Test
    void foreignSessionAndGrantHintsCannotCrossOwner() {
        UserFixture owner = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        UserFixture foreign = seedUser(GrantSeed.active(AuthRole.TEACHER, null));
        CreatedSession created = create(owner, AuthMethod.OTP, grantId(owner, AuthRole.STUDENT));

        SessionStatePort.SnapshotResult foreignSnapshot =
                authority.snapshot(
                        new SessionStatePort.SnapshotCommand(
                                foreign.userId(), created.sessionId(), SESSION_TIME));
        assertThat(foreignSnapshot.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);

        SessionStatePort.RoleSelectionResult foreignSelection =
                authority.selectRole(
                        new SessionStatePort.SelectRoleCommand(
                                foreign.userId(),
                                created.sessionId(),
                                AuthRole.STUDENT,
                                1,
                                SESSION_TIME,
                                event(
                                        foreign.userId(),
                                        created.sessionId(),
                                        SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(foreignSelection.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);

        List<RoleGrant> foreignHints = new ArrayList<>(owner.grants());
        foreignHints.add(foreign.grants().get(0));
        SessionStatePort.CreateSessionResult staleGrant =
                authority.createSession(
                        command(
                                owner,
                                AuthMethod.OTP,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                grantId(owner, AuthRole.STUDENT),
                                foreignHints,
                                owner.rolesVersion(),
                                null));
        assertThat(staleGrant.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
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
                "suspended",
                Timestamp.from(GRANT_TIME.plusSeconds(1)),
                studentGrant,
                user.userId());

        SessionStatePort.CreateSessionResult stale =
                authority.createSession(
                        command(
                                user,
                                AuthMethod.OTP,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                studentGrant,
                                originalGrants,
                                originalRolesVersion,
                                null));

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
            Future<Integer> writer =
                    executor.submit(
                            () ->
                                    updateGrantStatusHoldingUserLock(
                                            user.userId(),
                                            studentGrant,
                                            writerLockHeld,
                                            releaseWriter));
            assertThat(writerLockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<SessionStatePort.SnapshotResult> snapshot =
                    executor.submit(
                            () ->
                                    authority.snapshot(
                                            new SessionStatePort.SnapshotCommand(
                                                    user.userId(),
                                                    current.sessionId(),
                                                    SESSION_TIME.plusSeconds(25))));
            assertThat(awaitUserLockContention()).isTrue();
            releaseWriter.countDown();

            assertThat(writer.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            SessionStatePort.SnapshotResult result = snapshot.get(15, TimeUnit.SECONDS);
            assertThat(result.succeeded()).isTrue();
            SessionSnapshot returned = result.snapshot();
            RoleGrant returnedStudent =
                    returned.roles().stream()
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
        UserFixture user =
                seedUser(
                        GrantSeed.active(AuthRole.STUDENT, 1L),
                        GrantSeed.active(AuthRole.TEACHER, null));
        CreatedSession created = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        long teacherGrant = grantId(user, AuthRole.TEACHER);

        SessionStatePort.RoleSelectionResult selected =
                authority.selectRole(
                        new SessionStatePort.SelectRoleCommand(
                                user.userId(),
                                created.sessionId(),
                                AuthRole.TEACHER,
                                1,
                                SESSION_TIME.plusSeconds(10),
                                event(
                                        user.userId(),
                                        created.sessionId(),
                                        SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(selected.succeeded()).isTrue();
        assertThat(selected.snapshot().activeRole().grantId()).isEqualTo(teacherGrant);
        assertThat(selected.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.ROLE_CHANGED)).isEqualTo(1L);

        SessionStatePort.RoleSelectionResult repeated =
                authority.selectRole(
                        new SessionStatePort.SelectRoleCommand(
                                user.userId(),
                                created.sessionId(),
                                AuthRole.TEACHER,
                                2,
                                SESSION_TIME.plusSeconds(11),
                                event(
                                        user.userId(),
                                        created.sessionId(),
                                        SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(repeated.succeeded()).isTrue();
        assertThat(repeated.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.ROLE_CHANGED)).isEqualTo(1L);

        SessionStatePort.RoleSelectionResult staleVersion =
                authority.selectRole(
                        new SessionStatePort.SelectRoleCommand(
                                user.userId(),
                                created.sessionId(),
                                AuthRole.STUDENT,
                                1,
                                SESSION_TIME.plusSeconds(12),
                                event(
                                        user.userId(),
                                        created.sessionId(),
                                        SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(staleVersion.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_VERSION_CONFLICT);
    }

    @Test
    void terminalRoleRemainsVisibleWithoutSelectionAndSuspendedActiveRoleIsClearedOnRefresh() {
        UserFixture terminal = seedUser(GrantSeed.of(AuthRole.STUDENT, RoleStatus.EXPELLED, 1L));
        CreatedSession terminalSession = create(terminal, AuthMethod.OTP, null);
        SessionSnapshot terminalSnapshot =
                authority
                        .snapshot(
                                new SessionStatePort.SnapshotCommand(
                                        terminal.userId(),
                                        terminalSession.sessionId(),
                                        SESSION_TIME))
                        .snapshot();
        assertThat(terminalSnapshot.activeRole()).isNull();
        assertThat(terminalSnapshot.roles())
                .singleElement()
                .satisfies(
                        grant -> {
                            assertThat(grant.isReadOnly()).isTrue();
                            assertThat(grant.isSelectable()).isFalse();
                        });
        SessionStatePort.RoleSelectionResult terminalSelection =
                authority.selectRole(
                        new SessionStatePort.SelectRoleCommand(
                                terminal.userId(),
                                terminalSession.sessionId(),
                                AuthRole.STUDENT,
                                1,
                                SESSION_TIME.plusSeconds(2),
                                event(
                                        terminal.userId(),
                                        terminalSession.sessionId(),
                                        SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(terminalSelection.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE);

        UserFixture suspended =
                seedUser(
                        GrantSeed.active(AuthRole.STUDENT, 1L),
                        GrantSeed.of(AuthRole.TEACHER, RoleStatus.SUSPENDED, null));
        CreatedSession suspendedSession =
                create(suspended, AuthMethod.OTP, grantId(suspended, AuthRole.STUDENT));
        SessionStatePort.RoleSelectionResult suspendedSelection =
                authority.selectRole(
                        new SessionStatePort.SelectRoleCommand(
                                suspended.userId(),
                                suspendedSession.sessionId(),
                                AuthRole.TEACHER,
                                1,
                                SESSION_TIME.plusSeconds(2),
                                event(
                                        suspended.userId(),
                                        suspendedSession.sessionId(),
                                        SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(suspendedSelection.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE);

        UserFixture selectable = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        long activeGrant = grantId(selectable, AuthRole.STUDENT);
        CreatedSession refreshSession = create(selectable, AuthMethod.OTP, activeGrant);
        jdbc.update(
                "UPDATE user_role_grants SET status = ?, updated_at = ? WHERE id = ?",
                "suspended",
                Timestamp.from(GRANT_TIME.plusSeconds(1)),
                activeGrant);

        SessionStatePort.RefreshResult refreshed =
                authority.refresh(
                        new SessionStatePort.RefreshCommand(
                                selectable.userId(),
                                refreshSession.sessionId(),
                                refreshSession.currentJti(),
                                UUID.randomUUID(),
                                SESSION_TIME.plusSeconds(20)));
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
            Future<SessionStatePort.RefreshResult> first =
                    executor.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return refresh(user, created, replacementA);
                            });
            Future<SessionStatePort.RefreshResult> second =
                    executor.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return refresh(user, created, replacementB);
                            });

            SessionStatePort.RefreshResult firstResult = first.get(15, TimeUnit.SECONDS);
            SessionStatePort.RefreshResult secondResult = second.get(15, TimeUnit.SECONDS);
            List<SessionStatePort.RefreshResult> results = List.of(firstResult, secondResult);
            assertThat(results.stream().filter(SessionStatePort.RefreshResult::succeeded).count())
                    .isEqualTo(1);
            assertThat(
                            results.stream()
                                    .filter(
                                            result ->
                                                    result.failureCode()
                                                            == SessionStatePort.FailureCode
                                                                    .REFRESH_ALREADY_ROTATED)
                                    .count())
                    .isEqualTo(1);
            assertThat(
                            results.stream()
                                    .filter(SessionStatePort.RefreshResult::succeeded)
                                    .findFirst()
                                    .orElseThrow()
                                    .snapshot()
                                    .refreshExpiresAt())
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

        SessionStatePort.RefreshResult older =
                authority.refresh(
                        new SessionStatePort.RefreshCommand(
                                user.userId(),
                                created.sessionId(),
                                created.currentJti(),
                                UUID.randomUUID(),
                                SESSION_TIME.plusSeconds(30)));
        assertThat(older.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.REFRESH_ALREADY_ROTATED);

        SessionStatePort.RefreshResult unknown =
                authority.refresh(
                        new SessionStatePort.RefreshCommand(
                                user.userId(),
                                created.sessionId(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                SESSION_TIME.plusSeconds(31)));
        assertThat(unknown.failureCode()).isEqualTo(SessionStatePort.FailureCode.REFRESH_REJECTED);
        assertThat(first.snapshot().refreshExpiresAt()).isEqualTo(created.expiresAt());
    }

    @Test
    void selectedRevokeHttpPreservesActorAndDeniesTargetAdmissionRefreshAndWs() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession actor = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession target = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        SessionPrincipal actorPrincipal = selectedPrincipal(user, actor);
        SessionPrincipal targetPrincipal = selectedPrincipal(user, target);
        AuthService service = selectedAuthService(authority);
        WsTicketService tickets = mock(WsTicketService.class);
        MockMvc mvc = selectedMvc(service, tickets);
        var authentication = new UsernamePasswordAuthenticationToken(actorPrincipal, null);

        mvc.perform(delete("/auth/sessions/{sid}", target.sessionId()).principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(delete("/auth/sessions/{sid}", target.sessionId()).principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(service.admit(actorPrincipal).sessionVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.admit(targetPrincipal))
                .isInstanceOf(AuthSessionException.class)
                .hasMessage("SESSION_REVOKED");
        assertThat(refresh(user, target, UUID.randomUUID()).failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
        assertThat(refresh(user, actor, UUID.randomUUID()).succeeded()).isTrue();
        InternalWsSessionAdmissionController ws =
                new InternalWsSessionAdmissionController(
                        service, new ru.rutcampustrack.auth.security.AuthAdminAccess(jdbc));
        assertThat(ws.admitWsSession(wsRequest(targetPrincipal)).getStatusCode().value())
                .isEqualTo(401);
        assertThat(ws.admitWsSession(wsRequest(actorPrincipal)).getStatusCode().value())
                .isEqualTo(204);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sid FROM account_security_events WHERE user_id=? AND event_type='CURRENT_LOGOUT'",
                                UUID.class,
                                user.userId()))
                .isEqualTo(target.sessionId());
        verifyNoInteractions(tickets);
    }

    @Test
    void selectedRevokeHttpHidesForeignAndMissingAndClearsOnlyCurrentCookie() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        UserFixture foreign = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession actor = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession foreignTarget =
                create(foreign, AuthMethod.OTP, grantId(foreign, AuthRole.STUDENT));
        var authentication =
                new UsernamePasswordAuthenticationToken(selectedPrincipal(user, actor), null);
        MockMvc mvc = selectedMvc(selectedAuthService(authority), mock(WsTicketService.class));
        for (UUID target : List.of(foreignTarget.sessionId(), UUID.randomUUID())) {
            mvc.perform(delete("/auth/sessions/{sid}", target).principal(authentication))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.extras.code").value("SESSION_NOT_FOUND"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        mvc.perform(delete("/auth/sessions/not-a-uuid").principal(authentication))
                .andExpect(status().isBadRequest());
        assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isZero();
        assertSessionLive(foreign, foreignTarget);
        MockMvc failed =
                selectedMvc(
                        selectedAuthService(failingAuthority("revoke-selected.event")),
                        mock(WsTicketService.class));
        failed.perform(delete("/auth/sessions/{sid}", actor.sessionId()).principal(authentication))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.extras.code").value("AUTHORITY_UNAVAILABLE"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertSessionLive(user, actor);
        mvc.perform(delete("/auth/sessions/{sid}", actor.sessionId()).principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        header().string(
                                        "Set-Cookie",
                                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(delete("/auth/sessions/{sid}", actor.sessionId()).principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void selectedRevokeAcceptsNeutralActorAndPreservesRevokedObsoleteTargetVersions()
            throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession actor = create(user, AuthMethod.OTP, null);
        CreatedSession target = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE id=?",
                grantId(user, AuthRole.STUDENT));
        long currentRolesVersion =
                jdbc.queryForObject(
                        "SELECT roles_version FROM users WHERE id=?", Long.class, user.userId());
        SessionPrincipal neutral =
                new SessionPrincipal(
                        user.userId(),
                        actor.sessionId(),
                        1,
                        currentRolesVersion,
                        null,
                        null,
                        null,
                        false,
                        false);
        var authentication = new UsernamePasswordAuthenticationToken(neutral, null);
        MockMvc mvc = selectedMvc(selectedAuthService(authority), mock(WsTicketService.class));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(
                            delete("/auth/sessions/{sid}", target.sessionId())
                                    .principal(authentication))
                    .andExpect(status().isNoContent())
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        mvc.perform(delete("/auth/sessions/not-a-uuid").principal(authentication))
                .andExpect(status().isBadRequest());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT session_version FROM auth_sessions WHERE sid=?",
                                Long.class,
                                target.sessionId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT session_version FROM auth_sessions WHERE sid=?",
                                Long.class,
                                actor.sessionId()))
                .isEqualTo(1);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isEqualTo(1);
    }

    @Test
    void selectedRevokeRollsBackStateAndEventTogether() {
        for (String seam : List.of("revoke.selected", "revoke-selected.event")) {
            UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
            CreatedSession actor = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
            CreatedSession target = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
            assertThat(
                            failingAuthority(seam)
                                    .revokeSelected(
                                            selectedCommand(user, actor, target.sessionId()))
                                    .failureCode())
                    .isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
            assertNoRevocationOrEvent(user, target, SecurityEvent.Type.CURRENT_LOGOUT);
            assertSessionLive(user, actor);
        }
    }

    @Test
    void selectedRevokeFencesActorVersionsRevocationAndHeldGrantWriter() throws Exception {
        UserFixture user =
                seedUser(
                        GrantSeed.active(AuthRole.STUDENT, 1L),
                        GrantSeed.active(AuthRole.TEACHER, null));
        CreatedSession actor = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession target = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        SessionStatePort.RevokeSelectedCommand command =
                selectedCommand(user, actor, target.sessionId());
        authority.selectRole(
                new SessionStatePort.SelectRoleCommand(
                        user.userId(),
                        actor.sessionId(),
                        AuthRole.TEACHER,
                        1,
                        SESSION_TIME.plusSeconds(1),
                        event(user.userId(), actor.sessionId(), SecurityEvent.Type.ROLE_CHANGED)));
        assertThat(authority.revokeSelected(command).failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        assertSessionLive(user, target);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> writer =
                    executor.submit(
                            () ->
                                    updateGrantStatusHoldingUserLock(
                                            user.userId(),
                                            grantId(user, AuthRole.TEACHER),
                                            held,
                                            release));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            Future<SessionStatePort.RevokeResult> revoke =
                    executor.submit(
                            () ->
                                    authority.revokeSelected(
                                            new SessionStatePort.RevokeSelectedCommand(
                                                    user.userId(),
                                                    actor.sessionId(),
                                                    target.sessionId(),
                                                    2,
                                                    user.rolesVersion(),
                                                    SESSION_TIME.plusSeconds(25))));
            assertThat(awaitUserLockContention()).isTrue();
            release.countDown();
            assertThat(writer.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(revoke.get(15, TimeUnit.SECONDS).failureCode())
                    .isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
            assertSessionLive(user, target);
            assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isZero();
            authority.revokeCurrent(
                    new SessionStatePort.RevokeCurrentCommand(
                            user.userId(),
                            actor.sessionId(),
                            SESSION_TIME.plusSeconds(30),
                            event(
                                    user.userId(),
                                    actor.sessionId(),
                                    SecurityEvent.Type.CURRENT_LOGOUT)));
            assertThat(authority.revokeSelected(command).failureCode())
                    .isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
            assertSessionLive(user, target);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void selectedRevokeConcurrentRetryCommitsOneEvent() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession actor = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession target = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<SessionStatePort.RevokeResult> action =
                    () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return authority.revokeSelected(
                                selectedCommand(user, actor, target.sessionId()));
                    };
            Future<SessionStatePort.RevokeResult> first = executor.submit(action);
            Future<SessionStatePort.RevokeResult> second = executor.submit(action);
            var results =
                    List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).allMatch(SessionStatePort.RevokeResult::succeeded);
            assertThat(
                            results.stream()
                                    .filter(SessionStatePort.RevokeResult::alreadyRevoked)
                                    .count())
                    .isEqualTo(1);
            assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isEqualTo(1);
            assertSessionLive(user, actor);
        } finally {
            executor.shutdownNow();
        }
    }

    private SessionStatePort.RevokeSelectedCommand selectedCommand(
            UserFixture user, CreatedSession actor, UUID target) {
        return new SessionStatePort.RevokeSelectedCommand(
                user.userId(),
                actor.sessionId(),
                target,
                1,
                user.rolesVersion(),
                SESSION_TIME.plusSeconds(25));
    }

    private SessionPrincipal selectedPrincipal(UserFixture user, CreatedSession session) {
        RoleGrant grant = user.grants().get(0);
        return new SessionPrincipal(
                user.userId(),
                session.sessionId(),
                1,
                user.rolesVersion(),
                grant.role(),
                grant.status(),
                grant.groupId(),
                grant.role() == AuthRole.HEADMAN,
                grant.isReadOnly());
    }

    private WsSessionAdmissionRequest wsRequest(SessionPrincipal principal) {
        return new WsSessionAdmissionRequest(
                principal.userId(),
                principal.sessionId().toString(),
                principal.sessionVersion(),
                principal.rolesVersion(),
                principal.selectedRole().name(),
                principal.selectedStatus().name(),
                principal.groupId(),
                principal.headman(),
                principal.readOnly());
    }

    @Test
    void dashboardAdmissionReadsCurrentCapabilityAndRejectsRevokedStaleAndNonadminIdentities() {
        UserFixture admin = seedUser();
        jdbc.update(
                "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) "
                        + "VALUES (?,TRUE,'LIMITED',ARRAY['DASHBOARD_VIEW'],1)",
                admin.userId());
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                        + "VALUES (?,'admin','active',NULL,?,?)",
                admin.userId(),
                Timestamp.from(GRANT_TIME),
                Timestamp.from(GRANT_TIME));
        admin =
                loadUser(
                        admin.userId(),
                        admin.passwordHash()); // explicit profile precedes the ADMIN grant
        CreatedSession session = create(admin, AuthMethod.OTP, grantId(admin, AuthRole.ADMIN));
        jdbc.update(
                "UPDATE auth_sessions SET refresh_expires_at=NOW()+INTERVAL '1 day' WHERE sid=?",
                session.sessionId());
        var controller =
                new InternalWsSessionAdmissionController(
                        selectedAuthService(authority),
                        new ru.rutcampustrack.auth.security.AuthAdminAccess(jdbc));
        SessionPrincipal bound = selectedPrincipal(admin, session);
        assertThat(controller.admitDashboardSession(wsRequest(bound)).getStatusCode().value())
                .isEqualTo(204);

        jdbc.update(
                "UPDATE admin_access_profiles SET permissions=ARRAY['USERS_VIEW'],version=version+1 WHERE user_id=?",
                admin.userId());
        assertThat(controller.admitDashboardSession(wsRequest(bound)).getStatusCode().value())
                .isEqualTo(401);
        SessionPrincipal current =
                selectedPrincipal(loadUser(admin.userId(), admin.passwordHash()), session);
        assertThat(controller.admitDashboardSession(wsRequest(current)).getStatusCode().value())
                .isEqualTo(403);
        jdbc.update(
                "UPDATE admin_access_profiles SET permissions=ARRAY['DASHBOARD_VIEW'],version=version+1 WHERE user_id=?",
                admin.userId());
        current = selectedPrincipal(loadUser(admin.userId(), admin.passwordHash()), session);
        assertThat(controller.admitDashboardSession(wsRequest(current)).getStatusCode().value())
                .isEqualTo(204);
        jdbc.update(
                "UPDATE auth_sessions SET revoked_at=NOW(),revoke_reason='CURRENT_LOGOUT' WHERE sid=?",
                session.sessionId());
        assertThat(controller.admitDashboardSession(wsRequest(current)).getStatusCode().value())
                .isEqualTo(401);

        UserFixture student = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession studentSession =
                create(student, AuthMethod.OTP, grantId(student, AuthRole.STUDENT));
        assertThat(
                        controller
                                .admitDashboardSession(
                                        wsRequest(selectedPrincipal(student, studentSession)))
                                .getStatusCode()
                                .value())
                .isEqualTo(403);
        AuthService unavailable = mock(AuthService.class);
        org.mockito.Mockito.when(unavailable.admit(org.mockito.ArgumentMatchers.any()))
                .thenThrow(
                        new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        assertThat(
                        new InternalWsSessionAdmissionController(
                                        unavailable,
                                        new ru.rutcampustrack.auth.security.AuthAdminAccess(jdbc))
                                .admitDashboardSession(wsRequest(bound))
                                .getStatusCode()
                                .value())
                .isEqualTo(503);
    }

    private AuthService selectedAuthService(JdbcSessionAuthority adapter) {
        return new AuthService(
                mock(ru.rutcampustrack.auth.repository.UserRepository.class),
                mock(ru.rutcampustrack.auth.service.JwtService.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(ru.rutcampustrack.auth.config.JwtProperties.class),
                mock(ru.rutcampustrack.auth.service.LoginRateLimiter.class),
                mock(ru.rutcampustrack.auth.service.BcryptConcurrencyGuard.class),
                mock(ru.rutcampustrack.shared.observability.BusinessMetrics.class),
                mock(AuthSessionQueryPort.class),
                new SessionLifecycleService(adapter, adapter),
                java.time.Clock.fixed(SESSION_TIME.plusSeconds(25), java.time.ZoneOffset.UTC));
    }

    private MockMvc selectedMvc(AuthService service, WsTicketService tickets) {
        return MockMvcBuilders.standaloneSetup(
                        new AuthSessionController(
                                service, mock(AuthSessionQueryPort.class), tickets))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void revokeCurrentIsDurableIdempotentAndDeniesLaterSnapshot() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession created = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));

        SessionStatePort.RevokeResult revoked =
                authority.revokeCurrent(
                        new SessionStatePort.RevokeCurrentCommand(
                                user.userId(),
                                created.sessionId(),
                                SESSION_TIME.plusSeconds(40),
                                event(
                                        user.userId(),
                                        created.sessionId(),
                                        SecurityEvent.Type.CURRENT_LOGOUT)));
        assertThat(revoked.succeeded()).isTrue();
        assertThat(revoked.alreadyRevoked()).isFalse();
        assertThat(revoked.snapshot().isRevoked()).isTrue();

        SessionStatePort.SnapshotResult afterCommit =
                authority.snapshot(
                        new SessionStatePort.SnapshotCommand(
                                user.userId(), created.sessionId(), SESSION_TIME.plusSeconds(41)));
        assertThat(afterCommit.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);

        SessionStatePort.RevokeResult repeated =
                authority.revokeCurrent(
                        new SessionStatePort.RevokeCurrentCommand(
                                user.userId(),
                                created.sessionId(),
                                SESSION_TIME.plusSeconds(42),
                                event(
                                        user.userId(),
                                        created.sessionId(),
                                        SecurityEvent.Type.CURRENT_LOGOUT)));
        assertThat(repeated.succeeded()).isTrue();
        assertThat(repeated.alreadyRevoked()).isTrue();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.CURRENT_LOGOUT)).isEqualTo(1L);
    }

    @Test
    void revokeAllCoversEveryOwnSessionAndAppendsOneEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.TMA, grantId(user, AuthRole.STUDENT));

        SessionStatePort.RevokeAllResult result =
                authority.revokeAll(
                        new SessionStatePort.RevokeAllCommand(
                                user.userId(),
                                current.sessionId(),
                                SESSION_TIME.plusSeconds(50),
                                event(
                                        user.userId(),
                                        current.sessionId(),
                                        SecurityEvent.Type.LOGOUT_ALL)));
        assertThat(result.succeeded()).isTrue();
        assertThat(result.revokedSessionCount()).isEqualTo(2);
        assertThat(revokedSessionCount(user.userId())).isEqualTo(2L);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.LOGOUT_ALL)).isEqualTo(1L);
        assertThat(
                        authority
                                .snapshot(
                                        new SessionStatePort.SnapshotCommand(
                                                user.userId(),
                                                other.sessionId(),
                                                SESSION_TIME.plusSeconds(51)))
                                .failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
    }

    @Test
    void injectedFailureAfterCurrentRevokeEventRollsBackRevokeAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        JdbcSessionAuthority failingAuthority = failingAuthority("revoke.event");

        SessionStatePort.RevokeResult result =
                failingAuthority.revokeCurrent(
                        new SessionStatePort.RevokeCurrentCommand(
                                user.userId(),
                                current.sessionId(),
                                SESSION_TIME.plusSeconds(52),
                                event(
                                        user.userId(),
                                        current.sessionId(),
                                        SecurityEvent.Type.CURRENT_LOGOUT)));

        assertThat(result.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertNoRevocationOrEvent(user, current, SecurityEvent.Type.CURRENT_LOGOUT);
    }

    @Test
    void injectedFailureAfterRevokeAllEventRollsBackEveryRevokeAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.TMA, grantId(user, AuthRole.STUDENT));
        JdbcSessionAuthority failingAuthority = failingAuthority("revoke-all.event");

        SessionStatePort.RevokeAllResult result =
                failingAuthority.revokeAll(
                        new SessionStatePort.RevokeAllCommand(
                                user.userId(),
                                current.sessionId(),
                                SESSION_TIME.plusSeconds(53),
                                event(
                                        user.userId(),
                                        current.sessionId(),
                                        SecurityEvent.Type.LOGOUT_ALL)));

        assertThat(result.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.LOGOUT_ALL)).isZero();
        assertSessionLive(user, current);
        assertSessionLive(user, other);
    }

    @Test
    void passwordChangeRechecksHashUpdatesFlagsRevokesAllAndAuditsAtomically() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        jdbc.update(
                "UPDATE users SET telegram_id = ? WHERE id = ?",
                700000000L + user.userId(),
                user.userId());
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        String replacementHash = "replacement-hash-" + user.userId();

        CredentialSessionTransactionPort.ChangePasswordResult result =
                authority.changePassword(
                        new CredentialSessionTransactionPort.ChangePasswordCommand(
                                user.userId(),
                                current.sessionId(),
                                new CredentialSessionTransactionPort.CredentialHash(
                                        user.passwordHash()),
                                new CredentialSessionTransactionPort.CredentialHash(
                                        replacementHash),
                                SESSION_TIME.plusSeconds(60),
                                event(
                                        user.userId(),
                                        current.sessionId(),
                                        SecurityEvent.Type.PASSWORD_CHANGED,
                                        AuthMethod.PASSWORD)));
        assertThat(result.succeeded()).isTrue();
        assertThat(result.revokedSessionCount()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isEqualTo(replacementHash);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_changed FROM users WHERE id = ?",
                                Boolean.class,
                                user.userId()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isNull();
        assertThat(revokedSessionCount(user.userId())).isEqualTo(2L);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isEqualTo(1L);
        assertThat(notificationCount(user.userId())).isEqualTo(1L);
        assertThat(
                        authority
                                .snapshot(
                                        new SessionStatePort.SnapshotCommand(
                                                user.userId(),
                                                other.sessionId(),
                                                SESSION_TIME.plusSeconds(61)))
                                .failureCode())
                .isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
    }

    @Test
    void injectedFailureAfterPasswordEventRollsBackCredentialFlagsSessionsAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        CreatedSession other = create(user, AuthMethod.OTP, grantId(user, AuthRole.STUDENT));
        String replacementHash = "event-failure-hash-" + user.userId();
        JdbcSessionAuthority failingAuthority = failingAuthority("password.event");

        CredentialSessionTransactionPort.ChangePasswordResult result =
                failingAuthority.changePassword(passwordCommand(user, current, replacementHash));

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

        CredentialSessionTransactionPort.ChangePasswordResult result =
                failingAuthority.changePassword(passwordCommand(user, current, replacementHash));

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

        CredentialSessionTransactionPort.ChangePasswordResult result =
                authority.changePassword(
                        new CredentialSessionTransactionPort.ChangePasswordCommand(
                                user.userId(),
                                current.sessionId(),
                                new CredentialSessionTransactionPort.CredentialHash(
                                        user.passwordHash()),
                                new CredentialSessionTransactionPort.CredentialHash(
                                        replacementHash),
                                current.expiresAt(),
                                event(
                                        user.userId(),
                                        current.sessionId(),
                                        SecurityEvent.Type.PASSWORD_CHANGED,
                                        AuthMethod.PASSWORD)));
        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.SESSION_REVOKED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isEqualTo(user.passwordHash());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_changed FROM users WHERE id = ?",
                                Boolean.class,
                                user.userId()))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isEqualTo("initial");
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
    }

    @Test
    void injectedFailureRollsBackPasswordStateSessionRevocationAndEvent() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        JdbcSessionAuthority failingAuthority =
                new JdbcSessionAuthority(
                        jdbc,
                        transactionManager,
                        passwordChangedOutbox,
                        operation -> {
                            if (operation.equals("password.credential")) {
                                throw new IllegalStateException("intentional transaction failure");
                            }
                        });

        CredentialSessionTransactionPort.ChangePasswordResult result =
                failingAuthority.changePassword(
                        new CredentialSessionTransactionPort.ChangePasswordCommand(
                                user.userId(),
                                current.sessionId(),
                                new CredentialSessionTransactionPort.CredentialHash(
                                        user.passwordHash()),
                                new CredentialSessionTransactionPort.CredentialHash("new-hash"),
                                SESSION_TIME.plusSeconds(70),
                                event(
                                        user.userId(),
                                        current.sessionId(),
                                        SecurityEvent.Type.PASSWORD_CHANGED,
                                        AuthMethod.PASSWORD)));
        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isEqualTo(user.passwordHash());
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
    }

    private JdbcSessionAuthority failingAuthority(String failedOperation) {
        return new JdbcSessionAuthority(
                jdbc,
                transactionManager,
                passwordChangedOutbox,
                operation -> {
                    if (failedOperation.equals(operation)) {
                        throw new IllegalStateException("intentional transaction failure");
                    }
                });
    }

    @Test
    void notificationIntentRollsBackWithPasswordSessionsAndOneUseResetTicket() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        jdbc.update(
                "UPDATE users SET telegram_id = ? WHERE id = ?",
                700000000L + user.userId(),
                user.userId());
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));

        var failedChange =
                failingAuthority("password.outbox")
                        .changePassword(passwordCommand(user, current, "replacement-hash"));
        assertThat(failedChange.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertPasswordStateUnchanged(user);
        assertSessionLive(user, current);
        assertThat(notificationCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();

        String ticketHash = "reset-ticket-hash-" + user.userId();
        jdbc.update(
                "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                user.userId(),
                ticketHash,
                Timestamp.from(SESSION_TIME.plusSeconds(300)));
        var failedReset =
                failingAuthority("password-reset.outbox")
                        .completePasswordReset(
                                new CredentialSessionTransactionPort.PasswordResetCommand(
                                        new CredentialSessionTransactionPort.CredentialHash(
                                                ticketHash),
                                        new CredentialSessionTransactionPort.CredentialHash(
                                                "reset-replacement-hash"),
                                        SESSION_TIME.plusSeconds(60)));
        assertThat(failedReset.failureCode())
                .isEqualTo(
                        CredentialSessionTransactionPort.PasswordResetFailureCode
                                .AUTHORITY_UNAVAILABLE);
        assertPasswordStateUnchanged(user);
        assertSessionLive(user, current);
        assertThat(notificationCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT used_at FROM password_reset_tokens WHERE token_hash = ?",
                                Timestamp.class,
                                ticketHash))
                .isNull();
    }

    @Test
    void adminResetLinkHasOneHourTtlIndependentOfOtpConfiguration() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        AuthService auth = mock(AuthService.class);
        var users = mock(ru.rutcampustrack.auth.repository.UserRepository.class);
        var access = mock(ru.rutcampustrack.auth.security.AuthAdminAccess.class);
        SessionPrincipal principal =
                new SessionPrincipal(
                        1,
                        UUID.randomUUID(),
                        1,
                        1,
                        AuthRole.ADMIN,
                        RoleStatus.ACTIVE,
                        null,
                        false,
                        false);
        SessionSnapshot admin = mock(SessionSnapshot.class);
        org.mockito.Mockito.when(admin.activeRole())
                .thenReturn(
                        new RoleGrant(
                                1,
                                1,
                                AuthRole.ADMIN,
                                RoleStatus.ACTIVE,
                                null,
                                GRANT_TIME,
                                GRANT_TIME));
        org.mockito.Mockito.when(auth.admit(principal)).thenReturn(admin);
        org.mockito.Mockito.when(users.existsById(user.userId())).thenReturn(true);
        var service =
                new ru.rutcampustrack.auth.service.AdminPasswordResetService(
                        auth,
                        users,
                        new SessionLifecycleService(authority, authority),
                        "http://localhost/app/password-reset");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adminAccess", access);
        Instant before = Instant.now();
        var response = service.issueLink(principal, user.userId());
        Instant after = Instant.now();
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        assertThat(response.expiresAt())
                .isBetween(before.plusSeconds(3600), after.plusSeconds(3600));
        Instant stored =
                jdbc.queryForObject(
                                "SELECT expires_at FROM password_reset_tokens WHERE user_id=? AND used_at IS NULL",
                                Timestamp.class,
                                user.userId())
                        .toInstant();
        assertThat(stored)
                .isBetween(response.expiresAt().minusMillis(1), response.expiresAt().plusMillis(1));
        org.mockito.Mockito.verify(access)
                .authorize(principal, user.userId(), "USER_CREDENTIALS_MANAGE");
    }

    @Test
    void resetTicketSupersedesOldAndLatestExpiresAndIsSingleUse() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        create(user, AuthMethod.TMA, grantId(user, AuthRole.STUDENT));
        String old = "old-reset-hash-" + user.userId();
        String latest = "latest-reset-hash-" + user.userId();
        assertThat(
                        authority.issuePasswordResetTicket(
                                user.userId(), resetHash(old), SESSION_TIME.plusSeconds(600)))
                .isTrue();
        assertThat(
                        authority.issuePasswordResetTicket(
                                user.userId(), resetHash(latest), SESSION_TIME.plusSeconds(60)))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT expires_at FROM password_reset_tokens WHERE token_hash=?",
                                Timestamp.class,
                                latest))
                .isEqualTo(Timestamp.from(SESSION_TIME.plusSeconds(60)));
        assertThat(
                        authority
                                .completePasswordReset(reset(old, SESSION_TIME.plusSeconds(1)))
                                .failureCode())
                .isEqualTo(
                        CredentialSessionTransactionPort.PasswordResetFailureCode
                                .RESET_TICKET_INVALID);
        assertThat(
                        authority
                                .completePasswordReset(reset(latest, SESSION_TIME.plusSeconds(60)))
                                .failureCode())
                .isEqualTo(
                        CredentialSessionTransactionPort.PasswordResetFailureCode
                                .RESET_TICKET_INVALID);
        assertPasswordStateUnchanged(user);
        assertThat(revokedSessionCount(user.userId())).isZero();
        var completed =
                authority.completePasswordReset(reset(latest, SESSION_TIME.plusSeconds(59)));
        assertThat(completed.succeeded()).isTrue();
        assertThat(completed.revokedSessionCount()).isEqualTo(2);
        assertThat(
                        authority
                                .completePasswordReset(reset(latest, SESSION_TIME.plusSeconds(59)))
                                .failureCode())
                .isEqualTo(
                        CredentialSessionTransactionPort.PasswordResetFailureCode
                                .RESET_TICKET_INVALID);
        assertThat(eventCount(user.userId(), SecurityEvent.Type.PASSWORD_CHANGED)).isEqualTo(1);
    }

    @Test
    void failedReplacementInsertRollsBackSupersedeAndKeepsPreviousTicketUsable() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        String existing = "duplicate-reset-hash-" + user.userId();
        assertThat(
                        authority.issuePasswordResetTicket(
                                user.userId(), resetHash(existing), SESSION_TIME.plusSeconds(600)))
                .isTrue();
        // Real unique violation occurs after UPDATE, proving both statements roll back together.
        assertThat(
                        authority.issuePasswordResetTicket(
                                user.userId(), resetHash(existing), SESSION_TIME.plusSeconds(60)))
                .isFalse();
        assertThat(
                        count(
                                "SELECT count(*) FROM password_reset_tokens WHERE user_id=? AND used_at IS NULL",
                                user.userId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT expires_at FROM password_reset_tokens WHERE token_hash=?",
                                Timestamp.class,
                                existing))
                .isEqualTo(Timestamp.from(SESSION_TIME.plusSeconds(600)));
        assertThat(
                        authority
                                .completePasswordReset(reset(existing, SESSION_TIME.plusSeconds(1)))
                                .succeeded())
                .isTrue();
    }

    @Test
    void concurrentResetIssueAndCompletionSerializeOnUserAndRejectSupersededTicket()
            throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        String old = "concurrent-old-reset-hash-" + user.userId();
        String replacement = "concurrent-new-reset-hash-" + user.userId();
        assertThat(
                        authority.issuePasswordResetTicket(
                                user.userId(), resetHash(old), SESSION_TIME.plusSeconds(600)))
                .isTrue();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var waiting =
                    new java.util.concurrent.atomic.AtomicReference<
                            Future<CredentialSessionTransactionPort.PasswordResetResult>>();
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                            status -> {
                                jdbc.queryForObject(
                                        "SELECT id FROM users WHERE id=? FOR UPDATE",
                                        Long.class,
                                        user.userId());
                                waiting.set(
                                        pool.submit(
                                                () ->
                                                        authority.completePasswordReset(
                                                                reset(
                                                                        old,
                                                                        SESSION_TIME.plusSeconds(
                                                                                1)))));
                                // An unbound autocommit observer must not mistake the holding
                                // transaction for itself.
                                try (var observerConnection = dataSource.getConnection()) {
                                    var observer =
                                            new JdbcTemplate(
                                                    new org.springframework.jdbc.datasource
                                                            .SingleConnectionDataSource(
                                                            observerConnection, true));
                                    assertThat(awaitUserLockContention(observer)).isTrue();
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(interrupted);
                                } catch (SQLException failure) {
                                    throw new IllegalStateException(failure);
                                }
                                assertThat(
                                                authority.issuePasswordResetTicket(
                                                        user.userId(),
                                                        resetHash(replacement),
                                                        SESSION_TIME.plusSeconds(600)))
                                        .isTrue();
                            });
            assertThat(waiting.get().get(10, TimeUnit.SECONDS).failureCode())
                    .isEqualTo(
                            CredentialSessionTransactionPort.PasswordResetFailureCode
                                    .RESET_TICKET_INVALID);
            assertPasswordStateUnchanged(user);
            String first = "race-first-reset-hash-" + user.userId();
            String second = "race-second-reset-hash-" + user.userId();
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Boolean> one =
                    pool.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return authority.issuePasswordResetTicket(
                                        user.userId(),
                                        resetHash(first),
                                        SESSION_TIME.plusSeconds(600));
                            });
            Future<Boolean> two =
                    pool.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return authority.issuePasswordResetTicket(
                                        user.userId(),
                                        resetHash(second),
                                        SESSION_TIME.plusSeconds(600));
                            });
            assertThat(one.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(two.get(10, TimeUnit.SECONDS)).isTrue();
            String latest =
                    jdbc.queryForObject(
                            "SELECT token_hash FROM password_reset_tokens WHERE user_id=? AND used_at IS NULL",
                            String.class,
                            user.userId());
            String superseded = latest.equals(first) ? second : first;
            assertThat(
                            authority
                                    .completePasswordReset(
                                            reset(superseded, SESSION_TIME.plusSeconds(1)))
                                    .failureCode())
                    .isEqualTo(
                            CredentialSessionTransactionPort.PasswordResetFailureCode
                                    .RESET_TICKET_INVALID);
            assertThat(
                            authority
                                    .completePasswordReset(
                                            reset(latest, SESSION_TIME.plusSeconds(1)))
                                    .succeeded())
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    private static CredentialSessionTransactionPort.CredentialHash resetHash(String value) {
        return new CredentialSessionTransactionPort.CredentialHash(value);
    }

    private static CredentialSessionTransactionPort.PasswordResetCommand reset(
            String hash, Instant now) {
        return new CredentialSessionTransactionPort.PasswordResetCommand(
                resetHash(hash), resetHash("synthetic-reset-password-hash"), now);
    }

    private long notificationCount(long userId) {
        return count(
                "SELECT count(*) FROM auth_outbox WHERE payload -> 'payload' ->> 'telegram_id' = ?",
                Long.toString(700000000L + userId));
    }

    @Test
    void np01UnlinkedChangeKeepsOriginalUidAndPostGenerationCleanupIntent() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        long card = np01Card(user);
        jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", user.userId());

        assertThat(
                        authority
                                .changePassword(passwordCommand(user, current, "np01-change-hash"))
                                .succeeded())
                .isTrue();
        np01AssertCleanupEvent(user);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_state FROM bot_credential_messages WHERE record_id=?",
                                String.class,
                                card))
                .isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password IS NULL FROM users WHERE id=?",
                                Boolean.class,
                                user.userId()))
                .isTrue();
        assertThat(revokedSessionCount(user.userId())).isEqualTo(1);
    }

    @Test
    void np01UnlinkedResetKeepsCleanupIntentWithoutRedirectedRecipient() throws Exception {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        np01Card(user);
        jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", user.userId());
        String ticketHash = "np01-ticket-" + user.userId();
        jdbc.update(
                "INSERT INTO password_reset_tokens(user_id,token_hash,expires_at) VALUES(?,?,?)",
                user.userId(),
                ticketHash,
                Timestamp.from(SESSION_TIME.plusSeconds(300)));
        assertThat(
                        authority
                                .completePasswordReset(
                                        reset(ticketHash, SESSION_TIME.plusSeconds(1)))
                                .succeeded())
                .isTrue();
        np01AssertCleanupEvent(user);
    }

    @Test
    void np01OutboxFailureRollsBackCredentialGenerationCardInvalidationAndSessionsTogether() {
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession current = create(user, AuthMethod.PASSWORD, grantId(user, AuthRole.STUDENT));
        long card = np01Card(user);
        long generation =
                jdbc.queryForObject(
                        "SELECT credentials_version FROM users WHERE id=?",
                        Long.class,
                        user.userId());
        long revision =
                jdbc.queryForObject(
                        "SELECT record_revision FROM bot_credential_messages WHERE record_id=?",
                        Long.class,
                        card);
        var result =
                failingAuthority("password.outbox")
                        .changePassword(passwordCommand(user, current, "np01-rollback-hash"));
        assertThat(result.failureCode())
                .isEqualTo(CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT credentials_version FROM users WHERE id=?",
                                Long.class,
                                user.userId()))
                .isEqualTo(generation);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT record_revision FROM bot_credential_messages WHERE record_id=?",
                                Long.class,
                                card))
                .isEqualTo(revision);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_state FROM bot_credential_messages WHERE record_id=?",
                                String.class,
                                card))
                .isEqualTo("NOT_REQUIRED");
        assertThat(notificationCount(user.userId())).isZero();
        assertSessionLive(user, current);
    }

    private long np01Card(UserFixture user) {
        long chat = 700000000L + user.userId();
        jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", chat, user.userId());
        return jdbc.queryForObject(
                """
                INSERT INTO bot_credential_messages(user_id,private_chat_id,message_id,telegram_binding_version,credentials_version)
                SELECT id,telegram_id,9001,telegram_binding_version,credentials_version FROM users WHERE id=? RETURNING record_id
                """,
                Long.class,
                user.userId());
    }

    private void np01AssertCleanupEvent(UserFixture user) throws Exception {
        String json =
                jdbc.queryForObject(
                        "SELECT payload::text FROM auth_outbox WHERE payload->'payload'->>'user_id'=?",
                        String.class,
                        Long.toString(user.userId()));
        var event = new ObjectMapper().readTree(json);
        var payload = event.path("payload");
        assertThat(event.path("event_version").asInt()).isEqualTo(2);
        assertThat(payload.path("user_id").asLong()).isEqualTo(user.userId());
        assertThat(payload.path("credentials_version").asLong())
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT credentials_version FROM users WHERE id=?",
                                Long.class,
                                user.userId()));
        assertThat(payload.path("telegram_binding_version").asLong())
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT telegram_binding_version FROM users WHERE id=?",
                                Long.class,
                                user.userId()));
        assertThat(payload.has("telegram_id")).isFalse();
        assertThat(payload.size()).isEqualTo(3);
    }

    private CredentialSessionTransactionPort.ChangePasswordCommand passwordCommand(
            UserFixture user, CreatedSession current, String replacementHash) {
        return new CredentialSessionTransactionPort.ChangePasswordCommand(
                user.userId(),
                current.sessionId(),
                new CredentialSessionTransactionPort.CredentialHash(user.passwordHash()),
                new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                SESSION_TIME.plusSeconds(60),
                event(
                        user.userId(),
                        current.sessionId(),
                        SecurityEvent.Type.PASSWORD_CHANGED,
                        AuthMethod.PASSWORD));
    }

    private void assertPasswordStateUnchanged(UserFixture user) {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isEqualTo(user.passwordHash());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_changed FROM users WHERE id = ?",
                                Boolean.class,
                                user.userId()))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password FROM users WHERE id = ?",
                                String.class,
                                user.userId()))
                .isEqualTo("initial");
    }

    private void assertNoRevocationOrEvent(
            UserFixture user, CreatedSession current, SecurityEvent.Type eventType) {
        assertThat(revokedSessionCount(user.userId())).isZero();
        assertThat(eventCount(user.userId(), eventType)).isZero();
        assertSessionLive(user, current);
    }

    private void assertSessionLive(UserFixture user, CreatedSession session) {
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_sessions "
                                        + "WHERE sid = ? AND user_id = ? AND revoked_at IS NULL",
                                session.sessionId(),
                                user.userId()))
                .isEqualTo(1L);
    }

    private Integer updateGrantStatusHoldingUserLock(
            long userId,
            long grantId,
            CountDownLatch writerLockHeld,
            CountDownLatch releaseWriter) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            jdbc.queryForObject(
                                    "SELECT id FROM users WHERE id = ? FOR UPDATE",
                                    Long.class,
                                    userId);
                            writerLockHeld.countDown();
                            try {
                                if (!releaseWriter.await(15, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "grant writer release was not signalled");
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "grant writer was interrupted", interrupted);
                            }
                            return jdbc.update(
                                    "UPDATE user_role_grants SET status = ?, updated_at = ? "
                                            + "WHERE id = ? AND user_id = ?",
                                    "suspended",
                                    Timestamp.from(GRANT_TIME.plusSeconds(2)),
                                    grantId,
                                    userId);
                        });
    }

    private boolean awaitUserLockContention() throws InterruptedException {
        return awaitUserLockContention(jdbc);
    }

    private boolean awaitUserLockContention(JdbcTemplate observer) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long blocked =
                    observer.queryForObject(
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
                            Long.class);
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

        SessionStatePort.RefreshResult rejected =
                authority.refresh(
                        new SessionStatePort.RefreshCommand(
                                user.userId(),
                                created.sessionId(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                SESSION_TIME.plusSeconds(80)));
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
        SessionStatePort.CreateSessionCommand command =
                command(
                        user,
                        method,
                        sessionId,
                        currentJti,
                        activeGrantId,
                        user.grants(),
                        user.rolesVersion(),
                        method == AuthMethod.PASSWORD ? user.passwordHash() : null);
        SessionStatePort.CreateSessionResult result = authority.createSession(command);
        assertThat(result.succeeded()).isTrue();
        return new CreatedSession(
                user, sessionId, currentJti, SESSION_TIME.plusSeconds(24 * 60 * 60), command);
    }

    private SessionStatePort.RefreshResult refresh(
            UserFixture user, CreatedSession session, UUID replacementJti) {
        return authority.refresh(
                new SessionStatePort.RefreshCommand(
                        user.userId(),
                        session.sessionId(),
                        session.currentJti(),
                        replacementJti,
                        SESSION_TIME.plusSeconds(20)));
    }

    private SessionStatePort.CreateSessionCommand command(
            UserFixture user,
            AuthMethod method,
            UUID sessionId,
            UUID currentJti,
            Long activeGrantId,
            List<RoleGrant> grants,
            long rolesVersion,
            String expectedHash) {
        SessionState state =
                new SessionState(
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
                        "it-location");
        CredentialProofAndEvent credential =
                new CredentialProofAndEvent(
                        method == AuthMethod.PASSWORD
                                ? new SessionStatePort.CredentialProof(expectedHash)
                                : null,
                        event(user.userId(), sessionId, SecurityEvent.Type.LOGIN, method));
        return new SessionStatePort.CreateSessionCommand(
                state, rolesVersion, grants, credential.proof(), credential.event());
    }

    private SecurityEvent event(long userId, UUID sessionId, SecurityEvent.Type type) {
        return event(userId, sessionId, type, AuthMethod.OTP);
    }

    private SecurityEvent event(
            long userId, UUID sessionId, SecurityEvent.Type type, AuthMethod authMethod) {
        return new SecurityEvent(
                userId, sessionId, type, SESSION_TIME, authMethod, "it-client", "it-location");
    }

    private UserFixture seedUser(GrantSeed... seeds) {
        String login = "jdbc-it-" + (++userSequence);
        String hash = "credential-hash-" + login;
        long userId =
                jdbc.queryForObject(
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
                        Timestamp.from(GRANT_TIME));
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
                    Timestamp.from(GRANT_TIME));
        }
        return loadUser(userId, hash);
    }

    private UserFixture loadUser(long userId, String passwordHash) {
        long rolesVersion =
                jdbc.queryForObject(
                        "SELECT roles_version FROM users WHERE id = ?", Long.class, userId);
        List<RoleGrant> grants =
                jdbc.query(
                        """
                        SELECT id, user_id, role, status, group_id, created_at, updated_at
                        FROM user_role_grants
                        WHERE user_id = ?
                        ORDER BY id
                        """,
                        JdbcSessionAuthorityIT::mapGrant,
                        userId);
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
                type.name());
    }

    private long revokedSessionCount(long userId) {
        return count(
                "SELECT count(*) FROM auth_sessions WHERE user_id = ? AND revoked_at IS NOT NULL",
                userId);
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
                instant(rs, "updated_at"));
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
            Path candidate =
                    current.resolve(
                            "services/academic-service/academic-app/src/main/resources/db/migration");
            if (Files.isRegularFile(candidate.resolve("V24__auth_session_authority.sql"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("academic migration directory is unavailable");
    }

    private record UserFixture(
            long userId, String passwordHash, long rolesVersion, List<RoleGrant> grants) {
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
            SessionStatePort.CreateSessionCommand command) {}

    private record CredentialProofAndEvent(
            SessionStatePort.CredentialProof proof, SecurityEvent event) {}
}
