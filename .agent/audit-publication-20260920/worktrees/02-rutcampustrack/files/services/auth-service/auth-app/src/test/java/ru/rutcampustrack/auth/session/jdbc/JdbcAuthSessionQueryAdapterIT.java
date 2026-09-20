package ru.rutcampustrack.auth.session.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL proof for the read-only public session query adapter.
 *
 * <p>The fixture is fresh and non-reusable and runs the same V1..V24 migration
 * chain as the authority tests.  Queries are asserted against two users so a
 * caller supplied cursor or session id cannot cross the owner boundary.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAuthSessionQueryAdapterIT {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final PostgreSQLContainerFixture DATABASE =
            new PostgreSQLContainerFixture();

    private JdbcTemplate jdbc;
    private JdbcAuthSessionQueryAdapter adapter;
    private long groupId;
    private int userSequence;

    @BeforeAll
    void startDatabase() {
        DATABASE.start();
        Flyway.configure()
                .dataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password())
                .locations("filesystem:" + migrationDirectory().toAbsolutePath())
                .load()
                .migrate();

        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(DATABASE.jdbcUrl());
        source.setUsername(DATABASE.username());
        source.setPassword(DATABASE.password());
        source.setDriverClassName("org.postgresql.Driver");
        DataSource dataSource = source;
        jdbc = new JdbcTemplate(dataSource);
        adapter = new JdbcAuthSessionQueryAdapter(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        groupId = jdbc.queryForObject("SELECT id FROM groups ORDER BY id LIMIT 1", Long.class);
    }

    @AfterAll
    void stopDatabase() {
        DATABASE.stop();
    }

    @Test
    void loginAuthorityAndIdentityUseStoredRolesAndLabels() {
        UserFixture user = seedUser("Query", "Adapter", "Middle");

        AuthSessionQueryPort.LoginAuthority authority = adapter.findLoginAuthority(user.userId())
                .orElseThrow();
        assertThat(authority.userId()).isEqualTo(user.userId());
        assertThat(authority.rolesVersion()).isEqualTo(3L);
        assertThat(authority.displayName()).isEqualTo("Query Adapter Middle");
        assertThat(authority.grants()).hasSize(2);
        assertThat(authority.grants()).extracting(grant -> grant.role().name())
                .containsExactly("STUDENT", "TEACHER");
        assertThat(authority.grantContextLabels())
                .containsEntry(user.studentGrantId(), "ИВТ-211");
        assertThat(authority.groupLabels()).containsEntry(groupId, "ИВТ-211");

        AuthSessionQueryPort.UserIdentity identity = adapter.findUserIdentity(user.userId())
                .orElseThrow();
        assertThat(identity.displayName()).isEqualTo("Query Adapter Middle");
        assertThat(identity.groupLabels()).containsEntry(groupId, "ИВТ-211");
    }

    @Test
    void liveSessionsAreOwnAndUseCreatedAtSidKeyset() {
        UserFixture owner = seedUser("Live", "Owner", null);
        UserFixture foreign = seedUser("Foreign", "Owner", null);
        UUID newest = insertSession(owner, NOW.minusSeconds(10), NOW.plusSeconds(3600),
                null, null, "PASSWORD", "browser", "campus");
        UUID older = insertSession(owner, NOW.minusSeconds(20), NOW.plusSeconds(3600),
                null, null, "OTP", "phone", null);
        insertSession(owner, NOW.minusSeconds(30), NOW.plusSeconds(3600),
                NOW.minusSeconds(1), "CURRENT_LOGOUT", "TMA", "revoked", "campus");
        insertSession(owner, NOW.minusSeconds(40), NOW.minusSeconds(1),
                null, null, "PASSWORD", "expired", "campus");
        UUID foreignSession = insertSession(foreign, NOW.minusSeconds(5), NOW.plusSeconds(3600),
                null, null, "PASSWORD", "foreign", "campus");

        AuthSessionQueryPort.SessionPage first = adapter.findLiveSessions(
                owner.userId(), newest, null, 1);
        assertThat(first.items()).extracting(AuthSessionQueryPort.SessionView::sessionId)
                .containsExactly(newest);
        assertThat(first.nextCursor()).isNotBlank();

        AuthSessionQueryPort.SessionPage second = adapter.findLiveSessions(
                owner.userId(), newest, first.nextCursor(), 1);
        assertThat(second.items()).extracting(AuthSessionQueryPort.SessionView::sessionId)
                .containsExactly(older);
        assertThat(second.nextCursor()).isNull();
        assertThat(List.of(first.items(), second.items()).toString())
                .doesNotContain(foreignSession.toString());

        AuthSessionQueryPort.SessionMetadata metadata = adapter
                .findSessionMetadata(owner.userId(), newest)
                .orElseThrow();
        assertThat(metadata.authMethod()).isEqualTo("PASSWORD");
        assertThat(metadata.clientLabel()).isEqualTo("browser");
        assertThat(metadata.locationLabel()).isEqualTo("campus");
        assertThat(adapter.findSessionMetadata(foreign.userId(), newest)).isEmpty();
    }

    @Test
    void historyIsOwnKeysetAndMalformedCursorIsTyped() {
        UserFixture owner = seedUser("History", "Owner", null);
        UserFixture foreign = seedUser("History", "Foreign", null);
        UUID ownerSession = insertSession(owner, NOW.minusSeconds(100), NOW.plusSeconds(3600),
                null, null, "PASSWORD", "browser", "campus");
        UUID foreignSession = insertSession(foreign, NOW.minusSeconds(100), NOW.plusSeconds(3600),
                null, null, "PASSWORD", "foreign", "campus");
        insertEvent(owner.userId(), ownerSession, "LOGIN", NOW.minusSeconds(5),
                "PASSWORD", "browser", "campus");
        insertEvent(owner.userId(), ownerSession, "CURRENT_LOGOUT", NOW.minusSeconds(10),
                "PASSWORD", "browser", "campus");
        insertEvent(foreign.userId(), foreignSession, "LOGIN", NOW.minusSeconds(1),
                "PASSWORD", "foreign", "campus");

        AuthSessionQueryPort.HistoryPage first = adapter.findHistory(owner.userId(), null, 1);
        assertThat(first.items()).hasSize(1);
        assertThat(first.items().get(0).type()).isEqualTo("LOGIN");
        assertThat(first.items().get(0).authMethod()).isEqualTo("PASSWORD");
        assertThat(first.nextCursor()).isNotBlank();

        AuthSessionQueryPort.HistoryPage second = adapter.findHistory(
                owner.userId(), first.nextCursor(), 1);
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().get(0).type()).isEqualTo("CURRENT_LOGOUT");
        assertThat(second.nextCursor()).isNull();

        assertThatThrownBy(() -> adapter.findHistory(owner.userId(), "invalid cursor!", 20))
                .isInstanceOfSatisfying(AuthSessionException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(AuthSessionException.Code.INVALID_CURSOR));
        assertThat(List.of(first.items(), second.items()).toString())
                .doesNotContain(foreignSession.toString());
    }

    private UserFixture seedUser(String lastName, String firstName, String middleName) {
        String login = "query-it-" + (++userSequence);
        Instant created = NOW.minusSeconds(1000L + userSequence);
        long userId = jdbc.queryForObject(
                """
                        INSERT INTO users (
                            login, password_hash, last_name, first_name, middle_name,
                            role, status, is_headman, group_id,
                            initial_password, password_changed, created_at, updated_at
                        ) VALUES (?, 'query-hash', ?, ?, ?,
                                  CAST('student' AS user_role), CAST('active' AS account_status),
                                  FALSE, ?, 'initial', FALSE, ?, ?)
                        RETURNING id
                        """,
                Long.class, login, lastName, firstName, middleName, groupId,
                Timestamp.from(created), Timestamp.from(created));
        insertGrant(userId, "student", "active", groupId, created);
        insertGrant(userId, "teacher", "active", null, created.plusSeconds(1));
        long studentGrantId = jdbc.queryForObject(
                "SELECT id FROM user_role_grants WHERE user_id = ? AND role = 'student'",
                Long.class, userId);
        return new UserFixture(userId, studentGrantId);
    }

    private void insertGrant(
            long userId, String role, String status, Long grantGroupId, Instant timestamp) {
        jdbc.update(
                """
                        INSERT INTO user_role_grants (
                            user_id, role, status, group_id, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                userId, role, status, grantGroupId,
                Timestamp.from(timestamp), Timestamp.from(timestamp));
    }

    private UUID insertSession(
            UserFixture user,
            Instant createdAt,
            Instant refreshExpiresAt,
            Instant revokedAt,
            String revokeReason,
            String authMethod,
            String clientLabel,
            String locationLabel
    ) {
        UUID sid = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO auth_sessions (
                            sid, user_id, active_role_grant_id, session_version,
                            current_refresh_jti, previous_refresh_jti, refresh_expires_at,
                            created_at, last_seen_at, revoked_at, revoke_reason,
                            auth_method, client_label, location_label
                        ) VALUES (?, ?, ?, 1, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                sid, user.userId(), user.studentGrantId(), UUID.randomUUID(),
                Timestamp.from(refreshExpiresAt), Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(1)),
                revokedAt == null ? null : Timestamp.from(revokedAt), revokeReason,
                authMethod, clientLabel, locationLabel);
        return sid;
    }

    private void insertEvent(
            long userId,
            UUID sessionId,
            String type,
            Instant occurredAt,
            String authMethod,
            String clientLabel,
            String locationLabel
    ) {
        jdbc.update(
                """
                        INSERT INTO account_security_events (
                            user_id, sid, event_type, occurred_at,
                            auth_method, client_label, location_label
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userId, sessionId, type, Timestamp.from(occurredAt),
                authMethod, clientLabel, locationLabel);
    }

    private static Path migrationDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "services/academic-service/academic-app/src/main/resources/db/migration");
            if (Files.isRegularFile(candidate.resolve("V24__auth_session_authority.sql"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("academic migration directory is unavailable");
    }

    private record UserFixture(long userId, long studentGrantId) {
    }

    /** Small wrapper keeps the integration fixture setup readable at call sites. */
    private static final class PostgreSQLContainerFixture {
        private final org.testcontainers.containers.PostgreSQLContainer<?> delegate =
                new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("rct_auth_query")
                        .withUsername("rct_auth_query")
                        .withPassword("rct_auth_query_pass")
                        .withReuse(false);

        void start() {
            delegate.start();
        }

        void stop() {
            delegate.stop();
        }

        String jdbcUrl() {
            return delegate.getJdbcUrl();
        }

        String username() {
            return delegate.getUsername();
        }

        String password() {
            return delegate.getPassword();
        }
    }
}
