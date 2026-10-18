package ru.rutcampustrack.shared.security.authdb;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fixed Auth-owned commands. Uses the caller's existing, transaction-bound JDBC connection. */
public final class AuthDataCommands {
    private final JdbcTemplate jdbc;

    public AuthDataCommands(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize(
            InternalJwtClaims actor,
            long target,
            String login,
            String hash,
            String initial,
            String role,
            String status,
            Long telegramId,
            String telegramUsername) {
        execute(
                "SELECT auth_api.initialize_identity(?,?,?,?,?,?,?,?,?,?,?,?)",
                actor,
                target,
                login,
                hash,
                initial,
                role,
                status,
                telegramId,
                telegramUsername);
    }

    public void synchronize(
            InternalJwtClaims actor,
            long target,
            String role,
            String status,
            Long telegramId,
            String telegramUsername,
            boolean headman,
            boolean archive) {
        execute(
                "SELECT auth_api.synchronize_identity(?,?,?,?,?,?,?,?,?,?,?)",
                actor,
                target,
                role,
                status,
                telegramId,
                telegramUsername,
                headman,
                archive);
    }

    public void project(
            InternalJwtClaims actor,
            long target,
            String role,
            String status,
            Long telegramId,
            String telegramUsername) {
        execute(
                "SELECT auth_api.project_identity(?,?,?,?,?,?,?,?,?)",
                actor,
                target,
                role,
                status,
                telegramId,
                telegramUsername);
    }

    public void grant(
            InternalJwtClaims actor, long target, String role, String status, Long group) {
        execute("SELECT auth_api.write_grant(?,?,?,?,?,?,?,?)", actor, target, role, status, group);
    }

    public void headman(InternalJwtClaims actor, long target, boolean active) {
        execute("SELECT auth_api.write_headman(?,?,?,?,?,?)", actor, target, active);
    }

    public void replaceHeadman(
            InternalJwtClaims actor,
            Long previous,
            long candidate,
            long group,
            boolean legacy,
            Long telegram,
            String status) {
        execute(
                "SELECT auth_api.replace_headman(?,?,?,?,?,?,?,?,?,?)",
                actor,
                previous,
                candidate,
                group,
                legacy,
                telegram,
                status);
    }

    public void transfer(
            InternalJwtClaims actor,
            long target,
            String role,
            String status,
            Long telegram,
            String telegramName,
            boolean headman,
            String studentStatus,
            long group) {
        execute(
                "SELECT auth_api.transfer_identity(?,?,?,?,?,?,?,?,?,?,?,?)",
                actor,
                target,
                role,
                status,
                telegram,
                telegramName,
                headman,
                studentStatus,
                group);
    }

    public void restore(InternalJwtClaims actor, long target) {
        execute("SELECT auth_api.restore_identity(?,?,?,?,?)", actor, target);
    }

    public void adminAccess(
            InternalJwtClaims actor,
            long target,
            String mode,
            List<String> permissions,
            long expectedVersion,
            boolean revoke) {
        var values = parameters(actor, target, mode, null, expectedVersion, revoke);
        try {
            jdbc.query(
                    connection -> {
                        var statement =
                                connection.prepareStatement(
                                        "SELECT auth_api.write_admin_access(?,?,?,?,?,?,?,?,?)");
                        for (int i = 0; i < values.length; i++) {
                            if (i == 6)
                                statement.setArray(
                                        i + 1,
                                        connection.createArrayOf(
                                                "text", permissions.toArray(String[]::new)));
                            else statement.setObject(i + 1, values[i]);
                        }
                        return statement;
                    },
                    rs -> {});
        } catch (DataAccessException failure) {
            throw closedFailure(failure);
        }
    }

    /** Never cached or attached to a JPA entity. Authorization is rechecked in the function. */
    public String initialPassword(InternalJwtClaims actor, long target) {
        try {
            return jdbc.queryForObject(
                    "SELECT auth_api.read_initial_password(?,?,?,?,?)",
                    String.class,
                    parameters(actor, target));
        } catch (DataAccessException failure) {
            throw closedFailure(failure);
        }
    }

    private void execute(String sql, InternalJwtClaims actor, Object... arguments) {
        try {
            jdbc.query(sql, rs -> {}, parameters(actor, arguments));
        } catch (DataAccessException failure) {
            throw closedFailure(failure);
        }
    }

    // PostgreSQL diagnostics can contain statement context. Return only this closed vocabulary.
    private static RuntimeException closedFailure(DataAccessException failure) {
        if (failure instanceof DataIntegrityViolationException
                && failure.getMostSpecificCause() instanceof SQLException sql
                && "23505".equals(sql.getSQLState())) return failure;
        if (failure.getMostSpecificCause() instanceof SQLException sql) {
            String state = sql.getSQLState();
            String text = sql.getMessage();
            if ("42501".equals(state)) {
                if (text != null && text.contains("ADMIN_ACCESS_SELF_CHANGE_DENIED"))
                    return new AdminAccessException(
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            "ADMIN_ACCESS_SELF_CHANGE_DENIED");
                if (text != null && text.contains("ADMIN_FULL_REQUIRED"))
                    return AdminAccessException.fullRequired();
                return AdminAccessException.denied();
            }
            if ("23514".equals(state) && text != null && text.contains("LAST_FULL_ADMIN"))
                return new AdminAccessException(
                        org.springframework.http.HttpStatus.CONFLICT, "LAST_FULL_ADMIN");
            if ("22023".equals(state)) return AdminAccessException.invalid();
        }
        return AdminAccessException.unavailable();
    }

    private static Object[] parameters(InternalJwtClaims actor, Object... arguments) {
        if (actor == null
                || actor.sessionId() == null
                || actor.userId() <= 0
                || actor.rolesVersion() <= 0
                || actor.sessionVersion() <= 0
                || !"ADMIN".equals(actor.role())
                || !"ACTIVE".equals(actor.status())
                || actor.readOnly()) throw AdminAccessException.denied();
        List<Object> result =
                new ArrayList<>(
                        List.of(
                                actor.userId(),
                                actor.rolesVersion(),
                                actor.sessionId(),
                                actor.sessionVersion()));
        result.addAll(Arrays.asList(arguments));
        return result.toArray();
    }
}
