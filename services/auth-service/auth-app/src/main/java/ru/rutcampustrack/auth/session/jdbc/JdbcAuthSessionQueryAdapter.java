package ru.rutcampustrack.auth.session.jdbc;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.auth.authority.AcademicIdentityPresentationClient;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import ru.rutcampustrack.authauthority.grpc.IdentityPresentationRequest;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL implementation of the read-only public session queries. */
@Repository
public class JdbcAuthSessionQueryAdapter implements AuthSessionQueryPort {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private static final String LOGIN_AUTHORITY_SQL =
            """
            SELECT u.id, u.roles_version,
                   g.id AS grant_id, g.role, g.status, g.group_id,
                   g.created_at AS grant_created_at, g.updated_at AS grant_updated_at
            FROM auth_identities u
            LEFT JOIN user_role_grants g ON g.user_id = u.id
            WHERE u.id = ? AND u.account_state = 'ACTIVE' AND u.fence_operation_id IS NULL
            ORDER BY g.id
            """;

    private static final String LIVE_SESSIONS_SQL =
            """
            SELECT sid, auth_method, client_label, location_label,
                   created_at, last_seen_at
            FROM auth_sessions
            WHERE user_id = ?
              AND revoked_at IS NULL
              AND refresh_expires_at > ?
            ORDER BY created_at DESC, sid DESC
            LIMIT ?
            """;

    private static final String SESSION_METADATA_SQL =
            """
            SELECT sid, auth_method, client_label, location_label,
                   created_at, last_seen_at, refresh_expires_at, revoked_at
            FROM auth_sessions
            WHERE user_id = ? AND sid = ?
            """;

    private static final String LIVE_SESSIONS_AFTER_SQL =
            """
            SELECT sid, auth_method, client_label, location_label,
                   created_at, last_seen_at
            FROM auth_sessions
            WHERE user_id = ?
              AND revoked_at IS NULL
              AND refresh_expires_at > ?
              AND (created_at, sid) < (?, ?)
            ORDER BY created_at DESC, sid DESC
            LIMIT ?
            """;

    private static final String HISTORY_SQL =
            """
            SELECT id, event_type, occurred_at, auth_method,
                   client_label, location_label
            FROM account_security_events
            WHERE user_id = ?
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;

    private static final String HISTORY_AFTER_SQL =
            """
            SELECT id, event_type, occurred_at, auth_method,
                   client_label, location_label
            FROM account_security_events
            WHERE user_id = ?
              AND (occurred_at, id) < (?, ?)
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final AcademicIdentityPresentationClient presentation;

    @Autowired
    public JdbcAuthSessionQueryAdapter(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<AcademicIdentityPresentationClient> presentation) {
        this(jdbcTemplate, Clock.systemUTC(), presentation.getIfAvailable());
    }

    public JdbcAuthSessionQueryAdapter(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC(), null);
    }

    public JdbcAuthSessionQueryAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this(jdbcTemplate, clock, null);
    }

    public JdbcAuthSessionQueryAdapter(
            JdbcTemplate jdbcTemplate,
            Clock clock,
            AcademicIdentityPresentationClient presentation) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.presentation = presentation;
    }

    @Override
    public Optional<LoginAuthority> findLoginAuthority(long userId) {
        requirePositiveUserId(userId);
        try {
            List<LoginRow> rows =
                    jdbcTemplate.query(
                            LOGIN_AUTHORITY_SQL, JdbcAuthSessionQueryAdapter::mapLoginRow, userId);
            if (rows.isEmpty()) return Optional.empty();
            LoginRow first = rows.getFirst();
            List<RoleGrant> grants =
                    rows.stream()
                            .filter(row -> row.grantId() != null)
                            .map(LoginRow::toGrant)
                            .toList();
            // QR exchange calls this method inside its own transaction: authority
            // never depends on presentation or a recursive live Academic call.
            return Optional.of(new LoginAuthority(first.userId(), first.rolesVersion(), grants));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<UserIdentity> findUserIdentity(long userId) {
        Optional<LoginAuthority> local = findLoginAuthority(userId);
        if (local.isEmpty()) return Optional.empty();
        var fallback = new UserIdentity(userId, "", Map.of());
        // Names are optional. A caller that is still inside an Auth transaction
        // receives blank labels; no RPC may run while an Auth DB lock is held.
        if (presentation == null || TransactionSynchronizationManager.isActualTransactionActive())
            return Optional.of(fallback);
        var groupIds =
                local.get().grants().stream()
                        .map(RoleGrant::groupId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        try {
            var labels =
                    presentation.resolve(
                            IdentityPresentationRequest.newBuilder()
                                    .addUserIds(userId)
                                    .addAllGroupIds(groupIds)
                                    .build());
            String name =
                    labels.getUsersList().stream()
                            .filter(user -> user.getUserId() == userId)
                            .map(user -> user.getDisplayName())
                            .findFirst()
                            .orElse("");
            Map<Long, String> groups = new HashMap<>();
            labels.getGroupsList().stream()
                    .filter(group -> groupIds.contains(group.getGroupId()))
                    .forEach(group -> groups.put(group.getGroupId(), group.getLabel()));
            return Optional.of(new UserIdentity(userId, name, groups));
        } catch (RuntimeException unavailablePresentation) {
            return Optional.of(fallback);
        }
    }

    @Override
    public Optional<SessionMetadata> findSessionMetadata(long userId, UUID sessionId) {
        requirePositiveUserId(userId);
        Objects.requireNonNull(sessionId, "sessionId");
        try {
            List<SessionMetadata> rows =
                    jdbcTemplate.query(
                            SESSION_METADATA_SQL,
                            (rs, rowNum) ->
                                    new SessionMetadata(
                                            rs.getObject("sid", UUID.class),
                                            rs.getString("auth_method").toUpperCase(Locale.ROOT),
                                            rs.getString("client_label"),
                                            rs.getString("location_label"),
                                            instant(rs, "created_at"),
                                            instant(rs, "last_seen_at"),
                                            instant(rs, "refresh_expires_at"),
                                            instant(rs, "revoked_at")),
                            userId,
                            sessionId);
            return rows.stream().findFirst();
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public SessionPage findLiveSessions(
            long userId, UUID currentSessionId, String cursor, int limit) {
        requirePositiveUserId(userId);
        int boundedLimit = boundedLimit(limit);
        SessionCursor parsedCursor = parseSessionCursor(cursor);
        Instant now = clock.instant();
        try {
            List<SessionView> rows;
            if (parsedCursor == null) {
                rows =
                        jdbcTemplate.query(
                                LIVE_SESSIONS_SQL,
                                JdbcAuthSessionQueryAdapter::mapSession,
                                userId,
                                Timestamp.from(now),
                                boundedLimit + 1);
            } else {
                rows =
                        jdbcTemplate.query(
                                LIVE_SESSIONS_AFTER_SQL,
                                JdbcAuthSessionQueryAdapter::mapSession,
                                userId,
                                Timestamp.from(now),
                                Timestamp.from(parsedCursor.createdAt()),
                                parsedCursor.sessionId(),
                                boundedLimit + 1);
            }
            boolean hasMore = rows.size() > boundedLimit;
            List<SessionView> items =
                    hasMore ? List.copyOf(rows.subList(0, boundedLimit)) : List.copyOf(rows);
            String nextCursor = hasMore ? encodeSessionCursor(items.get(items.size() - 1)) : null;
            return new SessionPage(items, nextCursor);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public HistoryPage findHistory(long userId, String cursor, int limit) {
        requirePositiveUserId(userId);
        int boundedLimit = boundedLimit(limit);
        HistoryCursor parsedCursor = parseHistoryCursor(cursor);
        try {
            List<HistoryView> rows;
            if (parsedCursor == null) {
                rows =
                        jdbcTemplate.query(
                                HISTORY_SQL,
                                JdbcAuthSessionQueryAdapter::mapHistory,
                                userId,
                                boundedLimit + 1);
            } else {
                rows =
                        jdbcTemplate.query(
                                HISTORY_AFTER_SQL,
                                JdbcAuthSessionQueryAdapter::mapHistory,
                                userId,
                                Timestamp.from(parsedCursor.occurredAt()),
                                parsedCursor.id(),
                                boundedLimit + 1);
            }
            boolean hasMore = rows.size() > boundedLimit;
            List<HistoryView> items =
                    hasMore ? List.copyOf(rows.subList(0, boundedLimit)) : List.copyOf(rows);
            String nextCursor = hasMore ? encodeHistoryCursor(items.get(items.size() - 1)) : null;
            return new HistoryPage(items, nextCursor);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** Defaults are part of the public contract; a zero limit means default. */
    public static int boundedLimit(int requested) {
        if (requested == 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, Math.max(1, requested));
    }

    private static SessionCursor parseSessionCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = decodeCursor(cursor);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor shape");
            }
            Instant createdAt = Instant.parse(parts[0]);
            UUID sessionId = UUID.fromString(parts[1]);
            if (!sessionId.toString().equals(parts[1])) {
                throw new IllegalArgumentException("cursor UUID");
            }
            return new SessionCursor(createdAt, sessionId);
        } catch (RuntimeException exception) {
            throw invalidCursor(exception);
        }
    }

    private static HistoryCursor parseHistoryCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = decodeCursor(cursor);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor shape");
            }
            Instant occurredAt = Instant.parse(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new IllegalArgumentException("cursor id");
            }
            return new HistoryCursor(occurredAt, id);
        } catch (RuntimeException exception) {
            throw invalidCursor(exception);
        }
    }

    private static String decodeCursor(String cursor) {
        if (!cursor.matches("[A-Za-z0-9_-]{1,512}")) {
            throw new IllegalArgumentException("cursor encoding");
        }
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    private static String encodeSessionCursor(SessionView view) {
        return encodeCursor(view.createdAt() + "|" + view.sessionId());
    }

    private static String encodeHistoryCursor(HistoryView view) {
        return encodeCursor(view.occurredAt() + "|" + view.id());
    }

    private static String encodeCursor(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SessionView mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new SessionView(
                rs.getObject("sid", UUID.class),
                rs.getString("auth_method").toUpperCase(Locale.ROOT),
                rs.getString("client_label"),
                rs.getString("location_label"),
                instant(rs, "created_at"),
                instant(rs, "last_seen_at"));
    }

    private static HistoryView mapHistory(ResultSet rs, int rowNum) throws SQLException {
        String authMethod = rs.getString("auth_method");
        return new HistoryView(
                rs.getLong("id"),
                rs.getString("event_type").toUpperCase(Locale.ROOT),
                instant(rs, "occurred_at"),
                authMethod == null ? null : authMethod.toUpperCase(Locale.ROOT),
                rs.getString("client_label"),
                rs.getString("location_label"));
    }

    private static LoginRow mapLoginRow(ResultSet rs, int rowNum) throws SQLException {
        long grantId = rs.getLong("grant_id");
        Long nullableGrantId = rs.wasNull() ? null : grantId;
        Long groupId = nullableLong(rs, "group_id");
        return new LoginRow(
                rs.getLong("id"),
                rs.getLong("roles_version"),
                nullableGrantId,
                rs.getString("role"),
                rs.getString("status"),
                groupId,
                instant(rs, "grant_created_at"),
                instant(rs, "grant_updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static void requirePositiveUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private static AuthSessionException invalidCursor(Throwable cause) {
        return new AuthSessionException(AuthSessionException.Code.INVALID_CURSOR, cause);
    }

    private static AuthSessionException unavailable(Throwable cause) {
        return new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, cause);
    }

    private record LoginRow(
            long userId,
            long rolesVersion,
            Long grantId,
            String role,
            String status,
            Long groupId,
            Instant grantCreatedAt,
            Instant grantUpdatedAt) {
        private RoleGrant toGrant() {
            return new RoleGrant(
                    grantId,
                    userId,
                    AuthRole.valueOf(role.toUpperCase(Locale.ROOT)),
                    RoleStatus.valueOf(status.toUpperCase(Locale.ROOT)),
                    groupId,
                    grantCreatedAt,
                    grantUpdatedAt);
        }
    }

    private record SessionCursor(Instant createdAt, UUID sessionId) {}

    private record HistoryCursor(Instant occurredAt, long id) {}
}
