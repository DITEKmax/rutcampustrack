package ru.rutcampustrack.auth.session;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SessionLifecycleServiceTest {

    private static final long USER_ID = 7L;
    private static final Instant T0 = Instant.parse("2026-09-08T08:00:00Z");
    private static final Instant EXPIRY = T0.plusSeconds(3600);
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID INITIAL_JTI = UUID.fromString("00000000-0000-0000-0000-000000000017");

    @Test
    void createRejectsCallerSnapshotWhenAuthoritativeVersionOrGrantsChanged() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        port.setRoles(USER_ID, 2, List.of(grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L)));
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);

        SessionLifecycleService.CreateSessionRequest stale = request(
                SESSION_ID, INITIAL_JTI, 1,
                List.of(grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L),
                        grant(2, AuthRole.TEACHER, RoleStatus.ACTIVE, null)),
                "old-hash"
        );

        SessionStatePort.CreateSessionResult result = service.createSession(stale);

        assertThat(result.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        assertThat(port.sessions).isEmpty();
        assertThat(port.events).isEmpty();
    }

    @Test
    void createUsesStudentThenTeacherAndLeavesAdminHeadmanNeutral() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant teacher = grant(2, AuthRole.TEACHER, RoleStatus.ACTIVE, null);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(teacher, student));

        SessionStatePort.CreateSessionResult selected = service.createSession(request(
                SESSION_ID, INITIAL_JTI, 1, List.of(teacher, student), "old-hash"));

        assertThat(selected.succeeded()).isTrue();
        assertThat(selected.snapshot().activeRole()).isEqualTo(student);

        UUID neutralSession = UUID.fromString("00000000-0000-0000-0000-000000000008");
        port.setRoles(USER_ID, 2, List.of(
                grant(3, AuthRole.ADMIN, RoleStatus.ACTIVE, null),
                grant(4, AuthRole.HEADMAN, RoleStatus.ACTIVE, 10L)
        ));
        SessionStatePort.CreateSessionResult neutral = service.createSession(request(
                neutralSession, UUID.fromString("00000000-0000-0000-0000-000000000018"),
                2, port.roles.get(USER_ID), "old-hash"));

        assertThat(neutral.succeeded()).isTrue();
        assertThat(neutral.snapshot().activeRole()).isNull();
    }

    @Test
    void roleSwitchIsAtomicIdempotentAndVersionBound() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        RoleGrant teacher = grant(2, AuthRole.TEACHER, RoleStatus.ACTIVE, null);
        port.setRoles(USER_ID, 1, List.of(student, teacher));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student, teacher), "old-hash")).succeeded()).isTrue();

        SessionStatePort.RoleSelectionResult changed = service.selectRole(
                new SessionLifecycleService.SelectRoleRequest(
                        USER_ID, SESSION_ID, AuthRole.TEACHER, 1, T0,
                        AuthMethod.PASSWORD, null, null
                )
        );
        SessionStatePort.RoleSelectionResult same = service.selectRole(
                new SessionLifecycleService.SelectRoleRequest(
                        USER_ID, SESSION_ID, AuthRole.TEACHER, 2, T0.plusSeconds(1),
                        AuthMethod.PASSWORD, null, null
                )
        );
        SessionStatePort.RoleSelectionResult stale = service.selectRole(
                new SessionLifecycleService.SelectRoleRequest(
                        USER_ID, SESSION_ID, AuthRole.STUDENT, 1, T0.plusSeconds(2),
                        AuthMethod.PASSWORD, null, null
                )
        );

        assertThat(changed.snapshot().activeRole()).isEqualTo(teacher);
        assertThat(changed.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(same.snapshot().activeRole()).isEqualTo(teacher);
        assertThat(same.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(stale.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_VERSION_CONFLICT);
    }

    @Test
    void refreshHasOneWinnerPreviousIs409UnknownIs401AndExpiryStaysFixed() throws Exception {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(student));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student), "old-hash")).succeeded()).isTrue();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<SessionStatePort.RefreshResult> first = executor.submit(() -> {
                barrier.await();
                return service.refresh(new SessionLifecycleService.RefreshRequest(
                        USER_ID, SESSION_ID, INITIAL_JTI,
                        UUID.fromString("00000000-0000-0000-0000-000000000027"), T0.plusSeconds(10)
                ));
            });
            Future<SessionStatePort.RefreshResult> second = executor.submit(() -> {
                barrier.await();
                return service.refresh(new SessionLifecycleService.RefreshRequest(
                        USER_ID, SESSION_ID, INITIAL_JTI,
                        UUID.fromString("00000000-0000-0000-0000-000000000028"), T0.plusSeconds(10)
                ));
            });

            SessionStatePort.RefreshResult one = get(first);
            SessionStatePort.RefreshResult two = get(second);
            List<SessionStatePort.RefreshResult> results = List.of(one, two);
            assertThat(results.stream().filter(SessionStatePort.RefreshResult::succeeded)).hasSize(1);
            assertThat(results.stream().filter(result -> result.failureCode()
                    == SessionStatePort.FailureCode.REFRESH_ALREADY_ROTATED)).hasSize(1);
            assertThat(port.sessions.get(SESSION_ID).isRevoked()).isFalse();
            assertThat(one.succeeded() ? one.snapshot().refreshExpiresAt() : two.snapshot().refreshExpiresAt())
                    .isEqualTo(EXPIRY);

            UUID winnerJti = port.sessions.get(SESSION_ID).currentRefreshJti();
            SessionStatePort.RefreshResult previous = service.refresh(new SessionLifecycleService.RefreshRequest(
                    USER_ID, SESSION_ID, INITIAL_JTI,
                    UUID.fromString("00000000-0000-0000-0000-000000000029"), T0.plusSeconds(11)
            ));
            SessionStatePort.RefreshResult unknown = service.refresh(new SessionLifecycleService.RefreshRequest(
                    USER_ID, SESSION_ID, UUID.fromString("00000000-0000-0000-0000-000000009999"),
                    UUID.fromString("00000000-0000-0000-0000-000000000030"), T0.plusSeconds(11)
            ));
            assertThat(previous.failureCode()).isEqualTo(SessionStatePort.FailureCode.REFRESH_ALREADY_ROTATED);
            assertThat(unknown.failureCode()).isEqualTo(SessionStatePort.FailureCode.REFRESH_REJECTED);
            assertThat(port.sessions.get(SESSION_ID).currentRefreshJti()).isEqualTo(winnerJti);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void refreshClearsUnavailableActiveGrantWithoutFallback() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        RoleGrant teacher = grant(2, AuthRole.TEACHER, RoleStatus.ACTIVE, null);
        port.setRoles(USER_ID, 1, List.of(student, teacher));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student, teacher), "old-hash")).succeeded()).isTrue();
        port.setRoles(USER_ID, 2, List.of(
                grant(1, AuthRole.STUDENT, RoleStatus.SUSPENDED, 10L), teacher
        ));

        SessionStatePort.RefreshResult refreshed = service.refresh(new SessionLifecycleService.RefreshRequest(
                USER_ID, SESSION_ID, INITIAL_JTI,
                UUID.fromString("00000000-0000-0000-0000-000000000031"), T0.plusSeconds(12)
        ));

        assertThat(refreshed.succeeded()).isTrue();
        assertThat(refreshed.snapshot().activeRole()).isNull();
        assertThat(refreshed.snapshot().sessionVersion()).isEqualTo(2);
        assertThat(refreshed.snapshot().roles()).containsExactlyElementsOf(port.roles.get(USER_ID));
    }

    @Test
    void logoutAllIncludesCurrentAndPasswordFailureDoesNotPartiallyRevoke() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(student));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student), "old-hash")).succeeded()).isTrue();
        UUID secondSession = UUID.fromString("00000000-0000-0000-0000-000000000009");
        assertThat(service.createSession(request(
                secondSession, UUID.fromString("00000000-0000-0000-0000-000000000019"),
                1, List.of(student), "old-hash")).succeeded()).isTrue();

        CredentialSessionTransactionPort.ChangePasswordResult wrong = service.changePassword(
                passwordRequest("wrong-hash", "new-hash", "abcdefghij1!"));
        assertThat(wrong.failureCode()).isEqualTo(CredentialSessionTransactionPort.FailureCode.CURRENT_PASSWORD_INVALID);
        assertThat(port.sessions.values()).allMatch(state -> !state.isRevoked());

        CredentialSessionTransactionPort.ChangePasswordResult policyFailure = service.changePassword(
                passwordRequest("old-hash", "new-hash", "short1!"));
        assertThat(policyFailure.failureCode()).isEqualTo(
                CredentialSessionTransactionPort.FailureCode.PASSWORD_POLICY_VIOLATION);
        assertThat(credentials.changeCalls).isEqualTo(1);
        assertThat(port.sessions.values()).allMatch(state -> !state.isRevoked());

        SessionStatePort.RevokeAllResult logoutAll = service.revokeAll(
                new SessionLifecycleService.RevokeAllRequest(
                        USER_ID, SESSION_ID, T0.plusSeconds(20), AuthMethod.PASSWORD, null, null
                )
        );
        assertThat(logoutAll.succeeded()).isTrue();
        assertThat(logoutAll.revokedSessionCount()).isEqualTo(2);
        assertThat(port.sessions.values()).allMatch(SessionState::isRevoked);
    }

    @Test
    void currentLogoutIsDurableAndIdempotent() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(student));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student), "old-hash")).succeeded()).isTrue();

        SessionStatePort.RevokeResult first = service.revokeCurrent(
                new SessionLifecycleService.RevokeRequest(
                        USER_ID, SESSION_ID, T0.plusSeconds(1), AuthMethod.PASSWORD, null, null
                )
        );

        assertThat(first.succeeded()).isTrue();
        assertThat(first.alreadyRevoked()).isFalse();
        assertThat(first.snapshot().isRevoked()).isTrue();
        assertThat(port.events).hasSize(2);
        SessionState revoked = port.sessions.get(SESSION_ID);

        SessionStatePort.RevokeResult retry = service.revokeCurrent(
                new SessionLifecycleService.RevokeRequest(
                        USER_ID, SESSION_ID, T0.plusSeconds(2), AuthMethod.PASSWORD, null, null
                )
        );

        assertThat(retry.succeeded()).isTrue();
        assertThat(retry.alreadyRevoked()).isTrue();
        assertThat(retry.snapshot().isRevoked()).isTrue();
        assertThat(port.sessions.get(SESSION_ID)).isEqualTo(revoked);
        assertThat(port.events).hasSize(2);
    }

    @Test
    void passwordChangeSuccessRevokesEverySessionAndAuthorityFailureIsTyped() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(student));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student), "old-hash")).succeeded()).isTrue();
        UUID secondSession = UUID.fromString("00000000-0000-0000-0000-000000000010");
        assertThat(service.createSession(request(
                secondSession, UUID.fromString("00000000-0000-0000-0000-000000000020"),
                1, List.of(student), "old-hash")).succeeded()).isTrue();

        CredentialSessionTransactionPort.ChangePasswordResult changed = service.changePassword(
                passwordRequest("old-hash", "new-hash", "abcdefghij1!"));
        assertThat(changed.succeeded()).isTrue();
        assertThat(changed.revokedSessionCount()).isEqualTo(2);
        assertThat(credentials.hashes.get(USER_ID).value()).isEqualTo("new-hash");
        assertThat(port.sessions.values()).allMatch(SessionState::isRevoked);

        credentials.forcedFailure = CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE;
        int eventCount = port.events.size();
        CredentialSessionTransactionPort.ChangePasswordResult unavailable = service.changePassword(
                passwordRequest("new-hash", "newer-hash", "abcdefghij2!"));
        assertThat(unavailable.failureCode()).isEqualTo(
                CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(credentials.hashes.get(USER_ID).value()).isEqualTo("new-hash");
        assertThat(port.sessions.values()).allMatch(SessionState::isRevoked);
        assertThat(port.events).hasSize(eventCount);
    }

    @Test
    void expiredAndRevokedSessionsRejectSnapshotSelectionAndRefresh() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(student));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student), "old-hash")).succeeded()).isTrue();
        SessionState beforeExpiry = port.sessions.get(SESSION_ID);

        SessionStatePort.SnapshotResult expiredSnapshot = service.snapshot(
                new SessionLifecycleService.SnapshotRequest(USER_ID, SESSION_ID, EXPIRY)
        );
        SessionStatePort.RoleSelectionResult expiredSelection = service.selectRole(
                new SessionLifecycleService.SelectRoleRequest(
                        USER_ID, SESSION_ID, AuthRole.STUDENT, 1, EXPIRY,
                        AuthMethod.PASSWORD, null, null
                )
        );
        SessionStatePort.RefreshResult expiredRefresh = service.refresh(
                new SessionLifecycleService.RefreshRequest(
                        USER_ID, SESSION_ID, INITIAL_JTI,
                        UUID.fromString("00000000-0000-0000-0000-000000000032"), EXPIRY
                )
        );

        assertThat(expiredSnapshot.failureCode()).isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);
        assertThat(expiredSelection.failureCode()).isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);
        assertThat(expiredRefresh.failureCode()).isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);
        assertThat(port.sessions.get(SESSION_ID)).isEqualTo(beforeExpiry);
        assertThat(port.events).hasSize(1);

        SessionStatePort.RevokeResult revokedResult = service.revokeCurrent(
                new SessionLifecycleService.RevokeRequest(
                        USER_ID, SESSION_ID, T0.plusSeconds(1), AuthMethod.PASSWORD, null, null
                )
        );
        assertThat(revokedResult.succeeded()).isTrue();
        SessionState afterRevoke = port.sessions.get(SESSION_ID);
        int eventCount = port.events.size();

        SessionStatePort.SnapshotResult revokedSnapshot = service.snapshot(
                new SessionLifecycleService.SnapshotRequest(USER_ID, SESSION_ID, T0.plusSeconds(2))
        );
        SessionStatePort.RoleSelectionResult revokedSelection = service.selectRole(
                new SessionLifecycleService.SelectRoleRequest(
                        USER_ID, SESSION_ID, AuthRole.STUDENT, 1, T0.plusSeconds(2),
                        AuthMethod.PASSWORD, null, null
                )
        );
        SessionStatePort.RefreshResult revokedRefresh = service.refresh(
                new SessionLifecycleService.RefreshRequest(
                        USER_ID, SESSION_ID, INITIAL_JTI,
                        UUID.fromString("00000000-0000-0000-0000-000000000033"), T0.plusSeconds(2)
                )
        );

        assertThat(revokedSnapshot.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
        assertThat(revokedSelection.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
        assertThat(revokedRefresh.failureCode()).isEqualTo(SessionStatePort.FailureCode.SESSION_REVOKED);
        assertThat(port.sessions.get(SESSION_ID)).isEqualTo(afterRevoke);
        assertThat(port.events).hasSize(eventCount);
    }

    @Test
    void sessionAuthorityFailureDoesNotMutateStateOrEvents() {
        AtomicSessionDouble port = new AtomicSessionDouble();
        CredentialDouble credentials = new CredentialDouble(port);
        SessionLifecycleService service = new SessionLifecycleService(port, credentials);
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        port.setRoles(USER_ID, 1, List.of(student));
        assertThat(service.createSession(request(SESSION_ID, INITIAL_JTI, 1,
                List.of(student), "old-hash")).succeeded()).isTrue();
        SessionState before = port.sessions.get(SESSION_ID);
        int eventCount = port.events.size();
        port.forcedFailure = SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE;

        assertThat(service.createSession(request(
                UUID.fromString("00000000-0000-0000-0000-000000000034"),
                UUID.fromString("00000000-0000-0000-0000-000000000035"),
                1, List.of(student), "old-hash")).failureCode())
                .isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(service.selectRole(new SessionLifecycleService.SelectRoleRequest(
                USER_ID, SESSION_ID, AuthRole.STUDENT, 1, T0.plusSeconds(1),
                AuthMethod.PASSWORD, null, null
        )).failureCode()).isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(service.refresh(new SessionLifecycleService.RefreshRequest(
                USER_ID, SESSION_ID, INITIAL_JTI,
                UUID.fromString("00000000-0000-0000-0000-000000000036"), T0.plusSeconds(1)
        )).failureCode()).isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(service.revokeCurrent(new SessionLifecycleService.RevokeRequest(
                USER_ID, SESSION_ID, T0.plusSeconds(1), AuthMethod.PASSWORD, null, null
        )).failureCode()).isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);
        assertThat(service.revokeAll(new SessionLifecycleService.RevokeAllRequest(
                USER_ID, SESSION_ID, T0.plusSeconds(1), AuthMethod.PASSWORD, null, null
        )).failureCode()).isEqualTo(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE);

        assertThat(port.sessions).containsOnlyKeys(SESSION_ID);
        assertThat(port.sessions.get(SESSION_ID)).isEqualTo(before);
        assertThat(port.events).hasSize(eventCount);
    }

    @Test
    void snapshotBoundaryRejectsSameIdInconsistentRoleAndDuplicateGrants() {
        RoleGrant active = grant(1, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L);
        RoleGrant changedStatus = grant(1, AuthRole.STUDENT, RoleStatus.SUSPENDED, 10L);
        assertThatCode(() -> new SessionSnapshot(
                SESSION_ID, USER_ID, 1, 1, active, List.of(changedStatus),
                EXPIRY, T0, T0, null, null, AuthMethod.PASSWORD, null, null
        )).isInstanceOf(IllegalArgumentException.class);
        RoleGrant suspended = grant(2, AuthRole.STUDENT, RoleStatus.SUSPENDED, 10L);
        assertThatCode(() -> new SessionSnapshot(
                SESSION_ID, USER_ID, 1, 1, suspended, List.of(suspended),
                EXPIRY, T0, T0, null, null, AuthMethod.PASSWORD, null, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new SessionSnapshot(
                SESSION_ID, USER_ID, 1, 1, null, List.of(active, active),
                EXPIRY, T0, T0, null, null, AuthMethod.PASSWORD, null, null
        )).isInstanceOf(IllegalArgumentException.class);
        SessionSnapshot live = new SessionSnapshot(
                SESSION_ID, USER_ID, 1, 1, active, List.of(active),
                EXPIRY, T0, T0, null, null, AuthMethod.PASSWORD, null, null
        );
        assertThatCode(() -> SessionStatePort.RevokeResult.success(live, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> SessionStatePort.RevokeResult.success(live, true))
                .isInstanceOf(IllegalArgumentException.class);
        SessionState state = new SessionState(
                SESSION_ID, USER_ID, null, 1, INITIAL_JTI, null,
                EXPIRY, T0, T0, null, null, AuthMethod.PASSWORD, null, null
        );
        assertThat(state.toString()).doesNotContain(INITIAL_JTI.toString());
    }

    private static SessionLifecycleService.CreateSessionRequest request(
            UUID sessionId,
            UUID jti,
            long rolesVersion,
            List<RoleGrant> grants,
            String expectedHash
    ) {
        return new SessionLifecycleService.CreateSessionRequest(
                USER_ID, sessionId, jti, T0, EXPIRY, AuthMethod.PASSWORD,
                rolesVersion, grants, expectedHash, null, null
        );
    }

    private static SessionLifecycleService.ChangePasswordRequest passwordRequest(
            String expectedHash, String replacementHash, String newPassword
    ) {
        return new SessionLifecycleService.ChangePasswordRequest(
                USER_ID, SESSION_ID,
                new CredentialSessionTransactionPort.CredentialHash(expectedHash),
                new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                newPassword, T0.plusSeconds(30), AuthMethod.PASSWORD, null, null
        );
    }

    private static RoleGrant grant(long id, AuthRole role, RoleStatus status, Long groupId) {
        return new RoleGrant(id, USER_ID, role, status, groupId, T0, T0);
    }

    private static SessionStatePort.RefreshResult get(Future<SessionStatePort.RefreshResult> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static final class AtomicSessionDouble implements SessionStatePort {
        private final Object lock = new Object();
        private final Map<UUID, SessionState> sessions = new HashMap<>();
        private final Map<Long, List<RoleGrant>> roles = new HashMap<>();
        private final Map<Long, Long> rolesVersions = new HashMap<>();
        private final List<SecurityEvent> events = new ArrayList<>();
        private SessionStatePort.FailureCode forcedFailure;

        void setRoles(long userId, long version, List<RoleGrant> grants) {
            synchronized (lock) {
                roles.put(userId, List.copyOf(grants));
                rolesVersions.put(userId, version);
            }
        }

        @Override
        public CreateSessionResult createSession(CreateSessionCommand command) {
            synchronized (lock) {
                if (forcedFailure != null) {
                    return CreateSessionResult.failure(forcedFailure);
                }
                List<RoleGrant> authoritative = roles.get(command.state().userId());
                if (!Objects.equals(rolesVersions.get(command.state().userId()), command.rolesVersion())
                        || !Objects.equals(authoritative, command.grants())) {
                    return CreateSessionResult.failure(FailureCode.SESSION_STATE_STALE);
                }
                if (sessions.containsKey(command.state().sessionId())) {
                    return CreateSessionResult.failure(FailureCode.INVALID_ARGUMENT);
                }
                if (command.state().activeRoleGrantId() != null
                        && authoritative.stream().noneMatch(grant -> grant.grantId()
                        == command.state().activeRoleGrantId())) {
                    return CreateSessionResult.failure(FailureCode.SESSION_STATE_STALE);
                }
                sessions.put(command.state().sessionId(), command.state());
                events.add(command.loginEvent());
                return CreateSessionResult.success(snapshot(command.state(), authoritative));
            }
        }

        @Override
        public SnapshotResult snapshot(SnapshotCommand command) {
            synchronized (lock) {
                SessionState state = sessions.get(command.sessionId());
                FailureCode failure = liveFailure(state, command.userId(), command.now());
                if (failure != null) {
                    return SnapshotResult.failure(failure);
                }
                return SnapshotResult.success(snapshot(state, roles.get(command.userId())));
            }
        }

        @Override
        public RoleSelectionResult selectRole(SelectRoleCommand command) {
            synchronized (lock) {
                if (forcedFailure != null) {
                    return RoleSelectionResult.failure(forcedFailure);
                }
                SessionState state = sessions.get(command.sessionId());
                FailureCode failure = liveFailure(state, command.userId(), command.now());
                if (failure != null) {
                    return RoleSelectionResult.failure(failure);
                }
                if (state.sessionVersion() != command.expectedSessionVersion()) {
                    return RoleSelectionResult.failure(FailureCode.SESSION_VERSION_CONFLICT);
                }
                ActiveRolePolicy.Selection selected = new ActiveRolePolicy().select(
                        command.userId(), command.role(), roles.get(command.userId())
                );
                if (!selected.succeeded()) {
                    return RoleSelectionResult.failure(map(selected.code()));
                }
                if (Objects.equals(state.activeRoleGrantId(), selected.grant().grantId())) {
                    return RoleSelectionResult.success(snapshot(state, roles.get(command.userId())));
                }
                SessionState changed = state.withActiveRole(selected.grant().grantId(), state.sessionVersion() + 1);
                sessions.put(command.sessionId(), changed);
                events.add(command.roleChangedEvent());
                return RoleSelectionResult.success(snapshot(changed, roles.get(command.userId())));
            }
        }

        @Override
        public RefreshResult refresh(RefreshCommand command) {
            synchronized (lock) {
                if (forcedFailure != null) {
                    return RefreshResult.failure(forcedFailure);
                }
                SessionState state = sessions.get(command.sessionId());
                FailureCode failure = liveFailure(state, command.userId(), command.now());
                if (failure != null) {
                    return RefreshResult.failure(failure);
                }
                if (command.presentedJti().equals(state.previousRefreshJti())) {
                    return RefreshResult.failure(FailureCode.REFRESH_ALREADY_ROTATED);
                }
                if (!command.presentedJti().equals(state.currentRefreshJti())) {
                    return RefreshResult.failure(FailureCode.REFRESH_REJECTED);
                }
                SessionState rotated = state.withRefresh(command.replacementJti(), command.now());
                RoleGrant active = activeGrant(rotated, roles.get(command.userId()));
                if (active == null && rotated.activeRoleGrantId() != null) {
                    rotated = rotated.withActiveRole(null, rotated.sessionVersion() + 1);
                } else if (active != null && !active.isSelectable()) {
                    rotated = rotated.withActiveRole(null, rotated.sessionVersion() + 1);
                }
                sessions.put(command.sessionId(), rotated);
                return RefreshResult.success(snapshot(rotated, roles.get(command.userId())), command.replacementJti());
            }
        }

        @Override
        public RevokeResult revokeCurrent(RevokeCurrentCommand command) {
            synchronized (lock) {
                if (forcedFailure != null) {
                    return RevokeResult.failure(forcedFailure);
                }
                SessionState state = sessions.get(command.sessionId());
                if (state == null || state.userId() != command.userId()) {
                    return RevokeResult.failure(FailureCode.INVALID_SESSION);
                }
                if (state.isRevoked()) {
                    return RevokeResult.success(snapshot(state, roles.get(command.userId())), true);
                }
                SessionState revoked = state.revokedAt(command.now(),
                        ru.rutcampustrack.auth.session.model.SessionRevokeReason.CURRENT_LOGOUT);
                sessions.put(command.sessionId(), revoked);
                events.add(command.logoutEvent());
                return RevokeResult.success(snapshot(revoked, roles.get(command.userId())), false);
            }
        }

        @Override
        public RevokeResult revokeSelected(RevokeSelectedCommand command) {
            throw new UnsupportedOperationException("selected revocation is verified against real PostgreSQL");
        }

        @Override
        public RevokeAllResult revokeAll(RevokeAllCommand command) {
            synchronized (lock) {
                if (forcedFailure != null) {
                    return RevokeAllResult.failure(forcedFailure);
                }
                List<UUID> owned = sessions.entrySet().stream()
                        .filter(entry -> entry.getValue().userId() == command.userId())
                        .map(Map.Entry::getKey)
                        .toList();
                if (owned.stream().noneMatch(command.currentSessionId()::equals)) {
                    return RevokeAllResult.failure(FailureCode.INVALID_SESSION);
                }
                for (UUID sessionId : owned) {
                    SessionState state = sessions.get(sessionId);
                    if (!state.isRevoked()) {
                        sessions.put(sessionId, state.revokedAt(command.now(),
                                ru.rutcampustrack.auth.session.model.SessionRevokeReason.LOGOUT_ALL));
                    }
                }
                events.add(command.logoutAllEvent());
                return RevokeAllResult.success(owned.size());
            }
        }

        private SessionSnapshot snapshot(SessionState state, List<RoleGrant> authoritative) {
            RoleGrant active = activeGrant(state, authoritative);
            if (state.activeRoleGrantId() != null && (active == null || !active.isSelectable())) {
                SessionState cleared = state.withActiveRole(null, state.sessionVersion() + 1);
                sessions.put(state.sessionId(), cleared);
                state = cleared;
                active = null;
            }
            return new SessionSnapshot(
                    state.sessionId(), state.userId(), state.sessionVersion(),
                    rolesVersions.get(state.userId()), active, authoritative,
                    state.refreshExpiresAt(), state.createdAt(), state.lastSeenAt(),
                    state.revokedAt(), state.revokeReason(), state.authMethod(),
                    state.clientLabel(), state.locationLabel()
            );
        }

        private static RoleGrant activeGrant(SessionState state, List<RoleGrant> grants) {
            if (state.activeRoleGrantId() == null || grants == null) {
                return null;
            }
            return grants.stream()
                    .filter(grant -> grant.grantId() == state.activeRoleGrantId())
                    .findFirst()
                    .orElse(null);
        }

        private static FailureCode liveFailure(SessionState state, long userId, Instant now) {
            if (state == null || state.userId() != userId) {
                return FailureCode.INVALID_SESSION;
            }
            if (state.isRevoked()) {
                return FailureCode.SESSION_REVOKED;
            }
            if (state.isRefreshExpiredAt(now)) {
                return FailureCode.INVALID_SESSION;
            }
            return null;
        }

        private static FailureCode map(ActiveRolePolicy.Code code) {
            return switch (code) {
                case ROLE_NOT_GRANTED -> FailureCode.ROLE_NOT_GRANTED;
                case ROLE_NOT_SELECTABLE -> FailureCode.ROLE_NOT_SELECTABLE;
                case FOREIGN_GRANT, INVALID_GRANT -> FailureCode.INVALID_GRANT;
                case DUPLICATE_GRANT -> FailureCode.DUPLICATE_GRANT;
                case OK -> throw new IllegalArgumentException("successful selection cannot map to failure");
            };
        }
    }

    private static final class CredentialDouble implements CredentialSessionTransactionPort {
        private final AtomicSessionDouble sessionPort;
        private final Map<Long, CredentialHash> hashes = new HashMap<>();
        private FailureCode forcedFailure;
        private int changeCalls;

        private CredentialDouble(AtomicSessionDouble sessionPort) {
            this.sessionPort = sessionPort;
            hashes.put(USER_ID, new CredentialHash("old-hash"));
        }

        @Override
        public ChangePasswordResult changePassword(ChangePasswordCommand command) {
            synchronized (sessionPort.lock) {
                changeCalls++;
                if (forcedFailure != null) {
                    return ChangePasswordResult.failure(forcedFailure);
                }
                SessionState current = sessionPort.sessions.get(command.currentSessionId());
                if (current == null || current.userId() != command.userId()) {
                    return ChangePasswordResult.failure(FailureCode.INVALID_SESSION);
                }
                if (current.isRevoked()) {
                    return ChangePasswordResult.failure(FailureCode.SESSION_REVOKED);
                }
                CredentialHash actual = hashes.get(command.userId());
                if (actual == null || !actual.value().equals(command.expectedCurrentHash().value())) {
                    return ChangePasswordResult.failure(FailureCode.CURRENT_PASSWORD_INVALID);
                }
                hashes.put(command.userId(), command.replacementHash());
                int count = 0;
                for (Map.Entry<UUID, SessionState> entry : sessionPort.sessions.entrySet()) {
                    SessionState state = entry.getValue();
                    if (state.userId() == command.userId()) {
                        if (!state.isRevoked()) {
                            count++;
                            entry.setValue(state.revokedAt(command.now(),
                                    ru.rutcampustrack.auth.session.model.SessionRevokeReason.PASSWORD_CHANGED));
                        }
                    }
                }
                sessionPort.events.add(command.passwordChangedEvent());
                return ChangePasswordResult.success(count);
            }
        }

        @Override
        public boolean issuePasswordResetTicket(
                long userId,
                CredentialHash ticketHash,
                Instant expiresAt
        ) {
            throw new UnsupportedOperationException("password reset is outside this test double");
        }

        @Override
        public PasswordResetResult completePasswordReset(PasswordResetCommand command) {
            throw new UnsupportedOperationException("password reset is outside this test double");
        }
    }
}
