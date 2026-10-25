package ru.rutcampustrack.auth.service;

import java.util.Set;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.auth.authority.AuthInitialCredentialReader;
import ru.rutcampustrack.auth.dto.AdminCredentialView;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView.Origin;
import ru.rutcampustrack.auth.security.AuthAdminAccess;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

/** Live ADMIN authorization, secret disclosure/audit and credential effects share one commit. */
@Service
public class AdminGeneratedPasswordService {
    private final JdbcTemplate jdbc;
    private final AuthAdminAccess access;
    private final AuthService auth;
    private final GeneratedPasswordMutation mutation;
    private final AuthInitialCredentialReader reader;
    private final TransactionTemplate tx;

    public AdminGeneratedPasswordService(
            JdbcTemplate jdbc,
            AuthAdminAccess access,
            AuthService auth,
            GeneratedPasswordMutation mutation,
            AuthInitialCredentialReader reader,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.access = access;
        this.auth = auth;
        this.mutation = mutation;
        this.reader = reader;
        this.tx = new TransactionTemplate(manager);
    }

    public AdminCredentialView read(SessionPrincipal actor, long target) {
        return transaction(
                () -> {
                    authorize(actor, target);
                    AdminCredentialView view = current(target);
                    audit(actor.userId(), target, "ADMIN_PASSWORD_SHOWN", view);
                    return view;
                });
    }

    public AdminCredentialView generate(
            SessionPrincipal actor, long target, String expectedVersion) {
        long expected = positiveVersion(expectedVersion);
        return transaction(
                () -> {
                    authorize(actor, target);
                    AdminCredentialView before = current(target);
                    if (!Long.toString(expected).equals(before.credentialsVersion())
                            || !mutation.generate(target, expected, Origin.ADMIN_RESET))
                        throw new AdminGeneratedPasswordException(
                                AdminGeneratedPasswordException.Code.CREDENTIAL_VERSION_CONFLICT);
                    AdminCredentialView view = current(target);
                    audit(actor.userId(), target, "ADMIN_PASSWORD_RESET", view);
                    return view;
                });
    }

    private void authorize(SessionPrincipal actor, long target) {
        if (target <= 0)
            throw new AdminGeneratedPasswordException(
                    AdminGeneratedPasswordException.Code.INVALID_REQUEST);
        access.authorize(actor, target, "USER_CREDENTIALS_MANAGE");
        var targets =
                jdbc.query(
                        "SELECT account_state,fence_operation_id IS NULL FROM auth_identities WHERE id=?",
                        (rs, n) -> "ACTIVE".equals(rs.getString(1)) && rs.getBoolean(2),
                        target);
        if (targets.isEmpty())
            throw new AdminGeneratedPasswordException(
                    AdminGeneratedPasswordException.Code.CREDENTIAL_TARGET_NOT_FOUND);
        if (!targets.getFirst())
            throw new AdminGeneratedPasswordException(
                    AdminGeneratedPasswordException.Code.CREDENTIAL_TARGET_UNAVAILABLE);
        // Global -> users ascending (authorize) -> session rows. Recheck live bearer inside this
        // transaction.
        jdbc.queryForList(
                "SELECT sid FROM auth_sessions WHERE (user_id=? AND sid=?) OR user_id=? ORDER BY sid FOR UPDATE",
                java.util.UUID.class,
                actor.userId(),
                actor.sessionId(),
                target);
        auth.admit(actor);
    }

    private AdminCredentialView current(long target) {
        var snapshot = reader.active(target);
        if (snapshot == null)
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        String origin = snapshot.getGeneratedPasswordOrigin();
        boolean generated =
                !snapshot.getPasswordChanged()
                        && !snapshot.getInitialPassword().isBlank()
                        && Set.of("initial", "headman_reset", "admin_reset").contains(origin);
        AdminCredentialView.State state =
                snapshot.getPasswordChanged()
                        ? AdminCredentialView.State.USER_CHANGED
                        : generated
                                ? AdminCredentialView.State.GENERATED
                                : AdminCredentialView.State.UNAVAILABLE;
        return new AdminCredentialView(
                Long.toString(target),
                Long.toString(snapshot.getCredentialsVersion()),
                state,
                generated ? origin : null,
                generated ? snapshot.getInitialPassword() : null);
    }

    private void audit(long actor, long target, String action, AdminCredentialView view) {
        jdbc.update(
                """
                INSERT INTO audit_log(user_id,action,target_type,target_id,extras,succeeded)
                VALUES (?,?,'user',?,jsonb_build_object('credentialsVersion',?::text,'state',?::text,'origin',?::text),TRUE)
                """,
                actor,
                action,
                target,
                view.credentialsVersion(),
                view.state().name(),
                view.origin());
    }

    private static long positiveVersion(String version) {
        try {
            if (version == null || !version.matches("[1-9][0-9]{0,18}"))
                throw new NumberFormatException();
            long parsed = Long.parseLong(version);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new AdminGeneratedPasswordException(
                    AdminGeneratedPasswordException.Code.INVALID_REQUEST);
        }
    }

    private <T> T transaction(Supplier<T> action) {
        try {
            return tx.execute(status -> action.get());
        } catch (AdminGeneratedPasswordException
                | AdminAccessException
                | AuthSessionException safe) {
            throw safe;
        } catch (RuntimeException unsafe) {
            // Commit/JDBC errors may contain secret bind values; retain neither cause nor message.
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }
}
