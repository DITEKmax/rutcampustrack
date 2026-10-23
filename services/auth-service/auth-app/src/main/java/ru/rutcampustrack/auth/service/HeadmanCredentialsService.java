package ru.rutcampustrack.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView.Origin;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView.State;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;

/**
 * Own-group credential operations. All authority, effects and disclosure audit share one PG
 * transaction.
 */
@Service
public class HeadmanCredentialsService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final GeneratedPasswordMutation mutation;
    private final SessionLifecycleService sessions;
    private final String recoveryUrl;
    private final ru.rutcampustrack.auth.authority.AcademicIdentityPresentationClient academic;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public HeadmanCredentialsService(
            JdbcTemplate jdbc,
            AuthService auth,
            GeneratedPasswordMutation mutation,
            SessionLifecycleService sessions,
            ru.rutcampustrack.auth.authority.AcademicIdentityPresentationClient academic,
            org.springframework.transaction.PlatformTransactionManager manager,
            @Value("${auth.password-reset-url:}") String recoveryUrl) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.mutation = mutation;
        this.sessions = sessions;
        this.academic = academic;
        this.tx = new org.springframework.transaction.support.TransactionTemplate(manager);
        this.recoveryUrl = recoveryUrl;
    }

    public HeadmanCredentialView read(SessionPrincipal actor, long studentId) {
        var eligibility = preflight(actor, studentId);
        return tx.execute(status -> readLocal(actor, studentId, eligibility));
    }

    private HeadmanCredentialView readLocal(
            SessionPrincipal actor,
            long studentId,
            ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility eligibility) {
        try {
            lockAndAuthorize(actor, studentId, eligibility);
            HeadmanCredentialView result = currentView(studentId);
            audit(actor.userId(), studentId, "HEADMAN_PASSWORD_SHOWN", result);
            return result;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    public HeadmanCredentialView generate(
            SessionPrincipal actor, long studentId, String expectedVersion) {
        var eligibility = preflight(actor, studentId);
        return tx.execute(status -> generateLocal(actor, studentId, expectedVersion, eligibility));
    }

    private HeadmanCredentialView generateLocal(
            SessionPrincipal actor,
            long studentId,
            String expectedVersion,
            ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility eligibility) {
        long expected = positiveVersion(expectedVersion);
        try {
            lockAndAuthorize(actor, studentId, eligibility);
            HeadmanCredentialView before = currentView(studentId);
            if (!Long.toString(expected).equals(before.credentialsVersion())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Версия доступа изменилась");
            }
            if (!mutation.generate(studentId, expected, Origin.HEADMAN_RESET))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Версия доступа изменилась");
            HeadmanCredentialView result = currentView(studentId);
            audit(actor.userId(), studentId, "HEADMAN_PASSWORD_RESET", result);
            return result;
        } catch (DataAccessException exception) {
            // JDBC failures can retain SQL bind values. Never attach a credential-bearing cause.
            throw unavailable();
        }
    }

    public AdminPasswordResetLinkResponse issueLink(SessionPrincipal actor, long studentId) {
        var eligibility = preflight(actor, studentId);
        return tx.execute(status -> issueLinkLocal(actor, studentId, eligibility));
    }

    private AdminPasswordResetLinkResponse issueLinkLocal(
            SessionPrincipal actor,
            long studentId,
            ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility eligibility) {
        try {
            lockAndAuthorize(actor, studentId, eligibility);
            PasswordResetLinkMaterial material =
                    PasswordResetLinkMaterial.create(recoveryUrl, Instant.now());
            if (!sessions.issuePasswordResetTicket(
                    studentId,
                    new CredentialSessionTransactionPort.CredentialHash(material.ticketHash()),
                    material.response().expiresAt())) {
                throw unavailable();
            }
            audit(actor.userId(), studentId, "HEADMAN_RESET_LINK_ISSUED", currentView(studentId));
            return material.response();
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility preflight(
            SessionPrincipal actor, long studentId) {
        if (actor == null
                || studentId <= 0
                || actor.selectedRole() != AuthRole.HEADMAN
                || actor.selectedStatus() != RoleStatus.ACTIVE
                || !actor.headman()
                || actor.readOnly()
                || actor.groupId() == null) throw denied();
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) throw unavailable();
        var versions =
                jdbc.queryForList(
                        "SELECT roles_version FROM auth_identities WHERE id=? AND"
                                + " account_state='ACTIVE' AND fence_operation_id IS NULL",
                        Long.class,
                        studentId);
        if (versions.size() != 1) throw denied();
        var eligibility =
                academic.headmanEligibility(
                        ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibilityRequest
                                .newBuilder()
                                .setActorUserId(actor.userId())
                                .setExpectedActorRolesVersion(actor.rolesVersion())
                                .setStudentUserId(studentId)
                                .setExpectedStudentRolesVersion(versions.getFirst())
                                .build());
        if (eligibility.getGroupId() <= 0
                || eligibility.getActorAcademicRevision() <= 0
                || eligibility.getStudentAcademicRevision() <= 0) throw denied();
        return eligibility;
    }

    private void lockAndAuthorize(
            SessionPrincipal actor,
            long studentId,
            ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility eligibility) {
        if (actor == null)
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        if (studentId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный участник");
        if (actor.selectedRole() != AuthRole.HEADMAN
                || actor.selectedStatus() != RoleStatus.ACTIVE
                || !actor.headman()
                || actor.readOnly()
                || actor.groupId() == null) throw denied();
        // Canonical order: all users ascending, then group, then sessions. Scope is reread under
        // locks.
        List<Long> users =
                jdbc.query(
                        "SELECT id FROM auth_identities WHERE id IN (?, ?) AND"
                                + " account_state='ACTIVE' AND fence_operation_id IS NULL ORDER BY id"
                                + " FOR UPDATE",
                        (row, index) -> row.getLong(1),
                        actor.userId(),
                        studentId);
        if (!users.contains(actor.userId()) || !users.contains(studentId)) throw denied();
        Long actorRv =
                jdbc.queryForObject(
                        "SELECT roles_version FROM auth_identities WHERE id=?",
                        Long.class,
                        actor.userId());
        Long targetRv =
                jdbc.queryForObject(
                        "SELECT roles_version FROM auth_identities WHERE id=?",
                        Long.class,
                        studentId);
        if (eligibility.getActorRolesVersion() != actor.rolesVersion()
                || !Objects.equals(actorRv, eligibility.getActorRolesVersion())
                || !Objects.equals(targetRv, eligibility.getStudentRolesVersion())
                || !Objects.equals(actor.groupId(), eligibility.getGroupId())) throw denied();
        jdbc.query(
                "SELECT sid FROM auth_sessions WHERE (user_id = ? AND sid = ?) OR user_id = ? ORDER"
                        + " BY sid FOR UPDATE",
                (row, index) -> row.getObject(1),
                actor.userId(),
                actor.sessionId(),
                studentId);
        var current = auth.admit(actor);
        if (current.activeRole() == null
                || current.activeRole().role() != AuthRole.HEADMAN
                || current.activeRole().status() != RoleStatus.ACTIVE
                || current.isReadOnly()
                || !Objects.equals(current.activeRole().groupId(), actor.groupId())) throw denied();
        List<Long> headmen =
                jdbc.query(
                        """
SELECT g.user_id FROM user_role_grants g JOIN auth_identities u ON u.id = g.user_id
WHERE g.role = 'headman' AND g.status = 'active' AND g.group_id = ? AND u.account_state='ACTIVE' AND u.fence_operation_id IS NULL
ORDER BY g.user_id
""",
                        (row, index) -> row.getLong(1),
                        actor.groupId());
        if (headmen.size() != 1 || headmen.getFirst() != actor.userId()) throw denied();
        if (!activeStudent(actor.userId(), actor.groupId())
                || !activeStudent(studentId, actor.groupId())) throw denied();
        Boolean admin =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id = ? AND role = 'admin' AND status = 'active')
""",
                        Boolean.class,
                        studentId);
        if (Boolean.TRUE.equals(admin)) throw denied();
    }

    private boolean activeStudent(long userId, long groupId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM user_role_grants g JOIN auth_identities u ON u.id = g.user_id
    WHERE g.user_id = ? AND g.role = 'student' AND g.status = 'active'
    AND g.group_id = ? AND u.account_state='ACTIVE' AND u.fence_operation_id IS NULL)
""",
                        Boolean.class,
                        userId,
                        groupId));
    }

    private HeadmanCredentialView currentView(long userId) {
        return jdbc.queryForObject(
                """
SELECT credentials_version, password_changed, initial_password, generated_password_origin, password_hash IS NOT NULL AS has_hash
FROM auth_identities WHERE id = ?
""",
                (row, index) -> {
                    String originCode = row.getString("generated_password_origin");
                    String password = row.getString("initial_password");
                    boolean changed = row.getBoolean("password_changed");
                    Origin origin =
                            "initial".equals(originCode)
                                    ? Origin.INITIAL
                                    : "headman_reset".equals(originCode)
                                            ? Origin.HEADMAN_RESET
                                            : "admin_reset".equals(originCode)
                                                    ? Origin.ADMIN_RESET
                                                    : null;
                    State state =
                            changed
                                    ? State.STUDENT_CHANGED
                                    : password != null
                                                    && !password.isBlank()
                                                    && origin != null
                                                    && row.getBoolean("has_hash")
                                            ? State.GENERATED
                                            : State.UNAVAILABLE;
                    return new HeadmanCredentialView(
                            Long.toString(userId),
                            Long.toString(row.getLong("credentials_version")),
                            state,
                            state == State.GENERATED ? origin : null,
                            state == State.GENERATED ? password : null);
                },
                userId);
    }

    private void audit(long actorId, long studentId, String action, HeadmanCredentialView view) {
        // Fixed metadata fields only. Any failure aborts credential mutation/disclosure.
        jdbc.update(
                """
INSERT INTO audit_log(user_id, action, target_type, target_id, extras, succeeded)
VALUES (?, ?, 'user', ?, jsonb_build_object('credentialsVersion', ?::text, 'state', ?::text, 'origin', ?::text), TRUE)
""",
                actorId,
                action,
                studentId,
                view.credentialsVersion(),
                view.state().name(),
                view.origin() == null ? null : view.origin().code());
    }

    private static long positiveVersion(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}"))
                throw new NumberFormatException();
            long result = Long.parseLong(value);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Некорректная версия доступа");
        }
    }

    private static AuthSessionException denied() {
        return new AuthSessionException(AuthSessionException.Code.ROLE_NOT_GRANTED);
    }

    private static AuthSessionException unavailable() {
        return new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
    }
}
