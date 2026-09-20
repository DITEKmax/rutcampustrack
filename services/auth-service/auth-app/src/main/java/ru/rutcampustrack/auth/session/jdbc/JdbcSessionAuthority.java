package ru.rutcampustrack.auth.session.jdbc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionRevokeReason;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL authority for the session ports.
 *
 * <p>Every public port call is one transaction.  Mutations first lock the
 * authoritative user row and then the own session row(s), which is also the
 * lock order used by role-grant triggers.  No refresh or credential value is
 * included in a returned domain result or an exception message.
 */
@Repository
public class JdbcSessionAuthority implements SessionStatePort, CredentialSessionTransactionPort {

    private static final String LOCK_USER_SQL = """
            SELECT id, password_hash, roles_version
            FROM users
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String SESSION_SQL = """
            SELECT sid, user_id, active_role_grant_id, session_version,
                   current_refresh_jti, previous_refresh_jti,
                   refresh_expires_at, created_at, last_seen_at,
                   revoked_at, revoke_reason, auth_method,
                   client_label, location_label
            FROM auth_sessions
            WHERE sid = ? AND user_id = ?
            """;

    private static final String LOCK_SESSION_SQL = SESSION_SQL + " FOR UPDATE";

    private static final String ALL_SESSIONS_FOR_UPDATE_SQL = """
            SELECT sid, user_id, active_role_grant_id, session_version,
                   current_refresh_jti, previous_refresh_jti,
                   refresh_expires_at, created_at, last_seen_at,
                   revoked_at, revoke_reason, auth_method,
                   client_label, location_label
            FROM auth_sessions
            WHERE user_id = ?
            ORDER BY sid
            FOR UPDATE
            """;

    private static final String GRANTS_SQL = """
            SELECT id, user_id, role, status, group_id, created_at, updated_at
            FROM user_role_grants
            WHERE user_id = ?
            ORDER BY id
            """;

    private static final String INSERT_SESSION_SQL = """
            INSERT INTO auth_sessions (
                sid, user_id, active_role_grant_id, session_version,
                current_refresh_jti, previous_refresh_jti,
                refresh_expires_at, created_at, last_seen_at,
                revoked_at, revoke_reason, auth_method,
                client_label, location_label
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO account_security_events (
                user_id, sid, event_type, occurred_at, auth_method,
                client_label, location_label
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String CLEAR_ACTIVE_ROLE_SQL = """
            UPDATE auth_sessions
            SET active_role_grant_id = NULL,
                session_version = session_version + 1
            WHERE sid = ? AND user_id = ?
            """;

    private static final String SELECT_ROLE_SQL = """
            UPDATE auth_sessions
            SET active_role_grant_id = ?,
                session_version = session_version + 1,
                last_seen_at = ?
            WHERE sid = ? AND user_id = ?
              AND session_version = ?
              AND revoked_at IS NULL
              AND refresh_expires_at > ?
            """;

    private static final String REFRESH_SQL = """
            UPDATE auth_sessions
            SET current_refresh_jti = ?,
                previous_refresh_jti = ?,
                last_seen_at = ?,
                active_role_grant_id = ?,
                session_version = ?
            WHERE sid = ? AND user_id = ?
              AND current_refresh_jti = ?
              AND revoked_at IS NULL
              AND refresh_expires_at > ?
            """;

    private static final String REVOKE_CURRENT_SQL = """
            UPDATE auth_sessions
            SET revoked_at = ?, revoke_reason = ?
            WHERE sid = ? AND user_id = ? AND revoked_at IS NULL
            """;

    private static final String REVOKE_ALL_SQL = """
            UPDATE auth_sessions
            SET revoked_at = ?, revoke_reason = ?
            WHERE user_id = ? AND revoked_at IS NULL
            """;

    private static final String UPDATE_PASSWORD_SQL = """
            UPDATE users
            SET password_hash = ?, password_changed = TRUE, initial_password = NULL
            WHERE id = ? AND password_hash = ?
            """;

    private static final String REVOKE_PASSWORD_SESSIONS_SQL = """
            UPDATE auth_sessions
            SET revoked_at = ?, revoke_reason = ?
            WHERE user_id = ? AND revoked_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final FailureInjector failureInjector;

    @Autowired
    public JdbcSessionAuthority(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this(jdbcTemplate, transactionManager, operation -> {
        });
    }

    /**
     * Package-private test seam.  The integration test injects a failure after
     * a real PostgreSQL write to verify that the surrounding transaction rolls
     * back both the state change and its event.
     */
    JdbcSessionAuthority(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            FailureInjector failureInjector
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager")
        );
        this.failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
    }

    @Override
    public CreateSessionResult createSession(CreateSessionCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> createSessionInTransaction(command),
                () -> CreateSessionResult.failure(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
        );
    }

    @Override
    public SnapshotResult snapshot(SnapshotCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> snapshotInTransaction(command),
                () -> SnapshotResult.failure(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
        );
    }

    @Override
    public RoleSelectionResult selectRole(SelectRoleCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> selectRoleInTransaction(command),
                () -> RoleSelectionResult.failure(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
        );
    }

    @Override
    public RefreshResult refresh(RefreshCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> refreshInTransaction(command),
                () -> RefreshResult.failure(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
        );
    }

    @Override
    public RevokeResult revokeCurrent(RevokeCurrentCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> revokeCurrentInTransaction(command),
                () -> RevokeResult.failure(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
        );
    }

    @Override
    public RevokeAllResult revokeAll(RevokeAllCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> revokeAllInTransaction(command),
                () -> RevokeAllResult.failure(SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
        );
    }

    @Override
    public ChangePasswordResult changePassword(ChangePasswordCommand command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                () -> changePasswordInTransaction(command),
                () -> ChangePasswordResult.failure(
                        CredentialSessionTransactionPort.FailureCode.AUTHORITY_UNAVAILABLE
                )
        );
    }

    private CreateSessionResult createSessionInTransaction(CreateSessionCommand command) {
        SessionState state = command.state();
        if (state.sessionVersion() != 1) {
            return CreateSessionResult.failure(SessionStatePort.FailureCode.INVALID_ARGUMENT);
        }

        UserRow user = findUser(state.userId(), true);
        if (user == null) {
            return CreateSessionResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        if (state.authMethod() == AuthMethod.PASSWORD
                && !Objects.equals(
                user.passwordHash(), command.credentialProof().expectedCredentialHash()
        )) {
            return CreateSessionResult.failure(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        }

        List<RoleGrant> grants = findGrants(state.userId());
        if (user.rolesVersion() != command.rolesVersion()
                || !sameGrantSnapshot(command.grants(), grants)) {
            return CreateSessionResult.failure(SessionStatePort.FailureCode.SESSION_STATE_STALE);
        }

        if (state.activeRoleGrantId() != null) {
            RoleGrant active = findGrant(state.activeRoleGrantId(), grants);
            if (active == null) {
                return CreateSessionResult.failure(SessionStatePort.FailureCode.INVALID_GRANT);
            }
            if (!active.isSelectable()) {
                return CreateSessionResult.failure(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE);
            }
        }

        jdbcTemplate.update(
                INSERT_SESSION_SQL,
                state.sessionId(),
                state.userId(),
                state.activeRoleGrantId(),
                state.sessionVersion(),
                state.currentRefreshJti(),
                state.previousRefreshJti(),
                timestamp(state.refreshExpiresAt()),
                timestamp(state.createdAt()),
                timestamp(state.lastSeenAt()),
                timestamp(state.revokedAt()),
                state.revokeReason() == null ? null : state.revokeReason().name(),
                state.authMethod().name(),
                state.clientLabel(),
                state.locationLabel()
        );
        failureInjector.after("create.session");
        insertEvent(command.loginEvent());
        failureInjector.after("create.event");

        SessionRow inserted = requireSession(
                findSession(state.userId(), state.sessionId(), true)
        );
        return CreateSessionResult.success(toSnapshot(inserted, user.rolesVersion(), grants));
    }

    private SnapshotResult snapshotInTransaction(SnapshotCommand command) {
        UserRow user = findUser(command.userId(), true);
        if (user == null) {
            return SnapshotResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        SessionRow session = findSession(command.userId(), command.sessionId(), true);
        if (session == null) {
            return SnapshotResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        if (!isLive(session, command.now())) {
            return SnapshotResult.failure(SessionStatePort.FailureCode.SESSION_REVOKED);
        }

        List<RoleGrant> grants = findGrants(command.userId());
        session = clearUnselectableActive(session, grants);
        return SnapshotResult.success(toSnapshot(session, user.rolesVersion(), grants));
    }

    private RoleSelectionResult selectRoleInTransaction(SelectRoleCommand command) {
        UserRow user = findUser(command.userId(), true);
        if (user == null) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        SessionRow session = findSession(command.userId(), command.sessionId(), true);
        if (session == null) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        if (!isLive(session, command.now())) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.SESSION_REVOKED);
        }
        if (session.sessionVersion() != command.expectedSessionVersion()) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.SESSION_VERSION_CONFLICT);
        }

        List<RoleGrant> grants = findGrants(command.userId());
        RoleGrant requested = grants.stream()
                .filter(grant -> grant.role() == command.role())
                .findFirst()
                .orElse(null);
        if (requested == null) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.ROLE_NOT_GRANTED);
        }
        if (!requested.isSelectable()) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE);
        }

        if (Objects.equals(session.activeRoleGrantId(), requested.grantId())) {
            return RoleSelectionResult.success(toSnapshot(session, user.rolesVersion(), grants));
        }

        int updated = jdbcTemplate.update(
                SELECT_ROLE_SQL,
                requested.grantId(),
                timestamp(command.now()),
                command.sessionId(),
                command.userId(),
                command.expectedSessionVersion(),
                timestamp(command.now())
        );
        if (updated != 1) {
            return RoleSelectionResult.failure(SessionStatePort.FailureCode.SESSION_VERSION_CONFLICT);
        }
        failureInjector.after("select.role");
        insertEvent(command.roleChangedEvent());
        failureInjector.after("select.event");

        SessionRow selected = requireSession(
                findSession(command.userId(), command.sessionId(), true)
        );
        return RoleSelectionResult.success(toSnapshot(selected, user.rolesVersion(), grants));
    }

    private RefreshResult refreshInTransaction(RefreshCommand command) {
        UserRow user = findUser(command.userId(), true);
        if (user == null) {
            return RefreshResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        SessionRow session = findSession(command.userId(), command.sessionId(), true);
        if (session == null) {
            return RefreshResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        if (!isLive(session, command.now())) {
            return RefreshResult.failure(SessionStatePort.FailureCode.SESSION_REVOKED);
        }
        if (command.presentedJti().equals(session.previousRefreshJti())) {
            return RefreshResult.failure(SessionStatePort.FailureCode.REFRESH_ALREADY_ROTATED);
        }
        if (!command.presentedJti().equals(session.currentRefreshJti())) {
            return RefreshResult.failure(SessionStatePort.FailureCode.REFRESH_REJECTED);
        }

        List<RoleGrant> grants = findGrants(command.userId());
        RoleGrant active = session.activeRoleGrantId() == null
                ? null
                : findGrant(session.activeRoleGrantId(), grants);
        if (session.activeRoleGrantId() != null && active == null) {
            throw new IllegalStateException("active grant disappeared");
        }
        boolean clearActive = active != null && !active.isSelectable();
        long nextVersion = clearActive
                ? Math.addExact(session.sessionVersion(), 1)
                : session.sessionVersion();
        Long nextActiveGrantId = clearActive ? null : session.activeRoleGrantId();

        int updated = jdbcTemplate.update(
                REFRESH_SQL,
                command.replacementJti(),
                session.currentRefreshJti(),
                timestamp(command.now()),
                nextActiveGrantId,
                nextVersion,
                command.sessionId(),
                command.userId(),
                command.presentedJti(),
                timestamp(command.now())
        );
        if (updated != 1) {
            return RefreshResult.failure(SessionStatePort.FailureCode.REFRESH_REJECTED);
        }
        failureInjector.after("refresh.rotate");

        SessionRow refreshed = requireSession(
                findSession(command.userId(), command.sessionId(), true)
        );
        return RefreshResult.success(
                toSnapshot(refreshed, user.rolesVersion(), grants),
                command.replacementJti()
        );
    }

    private RevokeResult revokeCurrentInTransaction(RevokeCurrentCommand command) {
        UserRow user = findUser(command.userId(), true);
        if (user == null) {
            return RevokeResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        SessionRow session = findSession(command.userId(), command.sessionId(), true);
        if (session == null) {
            return RevokeResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }

        List<RoleGrant> grants = findGrants(command.userId());
        session = clearUnselectableActive(session, grants);
        if (session.revokedAt() != null) {
            return RevokeResult.success(
                    toSnapshot(session, user.rolesVersion(), grants),
                    true
            );
        }

        int updated = jdbcTemplate.update(
                REVOKE_CURRENT_SQL,
                timestamp(command.now()),
                SessionRevokeReason.CURRENT_LOGOUT.name(),
                command.sessionId(),
                command.userId()
        );
        if (updated != 1) {
            throw new IllegalStateException("current session revoke did not update one row");
        }
        failureInjector.after("revoke.current");
        insertEvent(command.logoutEvent());
        failureInjector.after("revoke.event");

        SessionRow revoked = requireSession(
                findSession(command.userId(), command.sessionId(), true)
        );
        return RevokeResult.success(toSnapshot(revoked, user.rolesVersion(), grants), false);
    }

    private RevokeAllResult revokeAllInTransaction(RevokeAllCommand command) {
        UserRow user = findUser(command.userId(), true);
        if (user == null) {
            return RevokeAllResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }
        List<SessionRow> sessions = findAllSessions(command.userId());
        if (sessions.stream().noneMatch(
                session -> session.sessionId().equals(command.currentSessionId())
        )) {
            return RevokeAllResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);
        }

        int newlyRevoked = jdbcTemplate.update(
                REVOKE_ALL_SQL,
                timestamp(command.now()),
                SessionRevokeReason.LOGOUT_ALL.name(),
                command.userId()
        );
        failureInjector.after("revoke.all");
        insertEvent(command.logoutAllEvent());
        failureInjector.after("revoke-all.event");

        int reportedCount = newlyRevoked == 0 ? sessions.size() : newlyRevoked;
        return RevokeAllResult.success(reportedCount);
    }

    private ChangePasswordResult changePasswordInTransaction(ChangePasswordCommand command) {
        UserRow user = findUser(command.userId(), true);
        if (user == null) {
            return ChangePasswordResult.failure(
                    CredentialSessionTransactionPort.FailureCode.INVALID_SESSION
            );
        }
        SessionRow current = findSession(command.userId(), command.currentSessionId(), true);
        if (current == null) {
            return ChangePasswordResult.failure(
                    CredentialSessionTransactionPort.FailureCode.INVALID_SESSION
            );
        }
        if (!isLive(current, command.now())) {
            return ChangePasswordResult.failure(
                    CredentialSessionTransactionPort.FailureCode.SESSION_REVOKED
            );
        }
        if (!Objects.equals(
                user.passwordHash(), command.expectedCurrentHash().value()
        )) {
            return ChangePasswordResult.failure(
                    CredentialSessionTransactionPort.FailureCode.CURRENT_PASSWORD_INVALID
            );
        }

        int passwordUpdated = jdbcTemplate.update(
                UPDATE_PASSWORD_SQL,
                command.replacementHash().value(),
                command.userId(),
                command.expectedCurrentHash().value()
        );
        if (passwordUpdated != 1) {
            return ChangePasswordResult.failure(
                    CredentialSessionTransactionPort.FailureCode.CURRENT_PASSWORD_INVALID
            );
        }
        failureInjector.after("password.credential");

        int revoked = jdbcTemplate.update(
                REVOKE_PASSWORD_SESSIONS_SQL,
                timestamp(command.now()),
                SessionRevokeReason.PASSWORD_CHANGED.name(),
                command.userId()
        );
        if (revoked == 0) {
            throw new IllegalStateException("password change revoked no session");
        }
        failureInjector.after("password.sessions");
        insertEvent(command.passwordChangedEvent());
        failureInjector.after("password.event");
        return ChangePasswordResult.success(revoked);
    }

    private SessionRow clearUnselectableActive(SessionRow session, List<RoleGrant> grants) {
        if (session.activeRoleGrantId() == null) {
            return session;
        }
        RoleGrant active = findGrant(session.activeRoleGrantId(), grants);
        if (active == null) {
            throw new IllegalStateException("active grant disappeared");
        }
        if (active.isSelectable()) {
            return session;
        }

        int updated = jdbcTemplate.update(
                CLEAR_ACTIVE_ROLE_SQL,
                session.sessionId(),
                session.userId()
        );
        if (updated != 1) {
            throw new IllegalStateException("active role clear did not update one row");
        }
        failureInjector.after("session.clear-active-role");
        return requireSession(findSession(session.userId(), session.sessionId(), true));
    }

    private void insertEvent(SecurityEvent event) {
        jdbcTemplate.update(
                INSERT_EVENT_SQL,
                event.userId(),
                event.sessionId(),
                event.type().name(),
                timestamp(event.occurredAt()),
                event.authMethod() == null ? null : event.authMethod().name(),
                event.clientLabel(),
                event.locationLabel()
        );
    }

    private UserRow findUser(long userId, boolean lock) {
        List<UserRow> rows = jdbcTemplate.query(
                LOCK_USER_SQL,
                JdbcSessionAuthority::mapUser,
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SessionRow findSession(long userId, UUID sessionId, boolean lock) {
        List<SessionRow> rows = jdbcTemplate.query(
                lock ? LOCK_SESSION_SQL : SESSION_SQL,
                JdbcSessionAuthority::mapSession,
                sessionId,
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<SessionRow> findAllSessions(long userId) {
        return jdbcTemplate.query(
                ALL_SESSIONS_FOR_UPDATE_SQL,
                JdbcSessionAuthority::mapSession,
                userId
        );
    }

    private List<RoleGrant> findGrants(long userId) {
        return jdbcTemplate.query(GRANTS_SQL, JdbcSessionAuthority::mapGrant, userId);
    }

    private static boolean isLive(SessionRow session, Instant now) {
        return session.revokedAt() == null && now.isBefore(session.refreshExpiresAt());
    }

    private static boolean sameGrantSnapshot(List<RoleGrant> expected, List<RoleGrant> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        List<RoleGrant> remaining = new ArrayList<>(actual);
        for (RoleGrant candidate : expected) {
            if (!remaining.remove(candidate)) {
                return false;
            }
        }
        return remaining.isEmpty();
    }

    private static RoleGrant findGrant(long grantId, List<RoleGrant> grants) {
        return grants.stream()
                .filter(grant -> grant.grantId() == grantId)
                .findFirst()
                .orElse(null);
    }

    private static SessionRow requireSession(SessionRow session) {
        if (session == null) {
            throw new IllegalStateException("session disappeared");
        }
        return session;
    }

    private static SessionSnapshot toSnapshot(
            SessionRow session,
            long rolesVersion,
            List<RoleGrant> grants
    ) {
        RoleGrant active = session.activeRoleGrantId() == null
                ? null
                : findGrant(session.activeRoleGrantId(), grants);
        if (session.activeRoleGrantId() != null && active == null) {
            throw new IllegalStateException("active grant missing from snapshot");
        }
        return new SessionSnapshot(
                session.sessionId(),
                session.userId(),
                session.sessionVersion(),
                rolesVersion,
                active,
                grants,
                session.refreshExpiresAt(),
                session.createdAt(),
                session.lastSeenAt(),
                session.revokedAt(),
                session.revokeReason(),
                session.authMethod(),
                session.clientLabel(),
                session.locationLabel()
        );
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static UserRow mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserRow(
                rs.getLong("id"),
                rs.getString("password_hash"),
                rs.getLong("roles_version")
        );
    }

    private static SessionRow mapSession(ResultSet rs, int rowNum) throws SQLException {
        long activeGrant = rs.getLong("active_role_grant_id");
        boolean activeGrantWasNull = rs.wasNull();
        return new SessionRow(
                rs.getObject("sid", UUID.class),
                rs.getLong("user_id"),
                activeGrantWasNull ? null : activeGrant,
                rs.getLong("session_version"),
                rs.getObject("current_refresh_jti", UUID.class),
                rs.getObject("previous_refresh_jti", UUID.class),
                instant(rs, "refresh_expires_at"),
                instant(rs, "created_at"),
                instant(rs, "last_seen_at"),
                instant(rs, "revoked_at"),
                enumOrNull(rs.getString("revoke_reason"), SessionRevokeReason.class),
                AuthMethod.valueOf(rs.getString("auth_method")),
                rs.getString("client_label"),
                rs.getString("location_label")
        );
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

    private static <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private <T> T inTransaction(Supplier<T> work, Supplier<T> unavailable) {
        try {
            T result = transactionTemplate.execute(status -> work.get());
            return result == null ? unavailable.get() : result;
        } catch (RuntimeException ignored) {
            // SQL, transaction, and invariant failures are deliberately
            // collapsed to the typed unavailable outcome; no driver message
            // can disclose a credential, JTI, or SQL detail to the caller.
            return unavailable.get();
        }
    }

    @FunctionalInterface
    interface FailureInjector {
        void after(String operation);
    }

    private record UserRow(long userId, String passwordHash, long rolesVersion) {
    }

    private record SessionRow(
            UUID sessionId,
            long userId,
            Long activeRoleGrantId,
            long sessionVersion,
            UUID currentRefreshJti,
            UUID previousRefreshJti,
            Instant refreshExpiresAt,
            Instant createdAt,
            Instant lastSeenAt,
            Instant revokedAt,
            SessionRevokeReason revokeReason,
            AuthMethod authMethod,
            String clientLabel,
            String locationLabel
    ) {
    }
}
