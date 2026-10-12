package ru.rutcampustrack.academic.integration;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

/** Signs the test's specified actor with current durable versions; it grants no authority. */
final class AcademicTestAdminRequest {
    private AcademicTestAdminRequest() {}

    static RequestPostProcessor signed(JdbcTemplate jdbc, InternalJwtTestFactory signer) {
        return request -> {
            if (request.getHeader("X-Internal-Token") != null) return request;
            long userId = Long.parseLong(request.getHeader("X-User-Id"));
            long sessionVersion = 1L; // Each request selects a fresh durable session below.
            long rolesVersion =
                    jdbc.queryForObject(
                            "SELECT roles_version FROM users WHERE id=?", Long.class, userId);
            request.addHeader(
                    "X-Internal-Token",
                    signer.validToken(
                            userId,
                            selectedSession(jdbc, userId, sessionVersion),
                            sessionVersion,
                            rolesVersion,
                            "ADMIN",
                            "ACTIVE",
                            null,
                            false,
                            false));
            return request;
        };
    }

    static UUID selectedSession(JdbcTemplate jdbc, long userId, long sessionVersion) {
        UUID session = UUID.randomUUID();
        // Selecting an existing ADMIN grant does not create or upgrade one.
        jdbc.update(
                """
                INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,
                    current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)
                SELECT ?,user_id,id,?,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD'
                FROM user_role_grants WHERE user_id=? AND role='admin' AND status='active'
                """,
                session,
                sessionVersion,
                UUID.randomUUID(),
                userId);
        return session;
    }
}
