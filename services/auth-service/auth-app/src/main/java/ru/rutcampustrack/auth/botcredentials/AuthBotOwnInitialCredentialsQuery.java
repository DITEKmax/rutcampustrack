package ru.rutcampustrack.auth.botcredentials;

import io.grpc.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.grpc.BotCredentialReadMode;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsRequest;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsResponse;
import ru.rutcampustrack.auth.authority.AuthAdminJdbcAuthority;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.util.List;
import java.util.UUID;

/**
 * Uncached own-account capability, separate from default-role activation and admin target access.
 */
@Service
public class AuthBotOwnInitialCredentialsQuery {

    private final JdbcTemplate jdbc;
    private final AuthAdminJdbcAuthority admins;

    @Autowired
    public AuthBotOwnInitialCredentialsQuery(JdbcTemplate jdbc) {
        this(jdbc, new AuthAdminJdbcAuthority(jdbc));
    }

    public AuthBotOwnInitialCredentialsQuery(JdbcTemplate jdbc, AuthAdminJdbcAuthority admins) {
        this.jdbc = jdbc;
        this.admins = admins;
    }

    @Transactional
    public BotOwnInitialCredentialsResponse read(BotOwnInitialCredentialsRequest request) {
        AuthBotCredentialServiceIdentityInterceptor.requireBot();
        validateRead(request);
        UserState user = lockUser(request.getExpectedUserId());
        if (user == null) return BotOwnInitialCredentialsResponse.getDefaultInstance();
        if (user.telegramId != request.getTelegramSenderId()) throw denied();
        if (request.getMode() == BotCredentialReadMode.BOT_CREDENTIAL_READ_MODE_RECHECK
                && (user.bindingVersion != request.getExpectedTelegramBindingVersion()
                        || user.credentialsVersion != request.getExpectedCredentialsVersion())) {
            throw stale();
        }
        if (!hasOwnAuthority(user)) return BotOwnInitialCredentialsResponse.getDefaultInstance();
        boolean eligible = user.eligible();
        var response =
                BotOwnInitialCredentialsResponse.newBuilder()
                        .setFound(true)
                        .setUserId(user.id)
                        .setTelegramBindingVersion(user.bindingVersion)
                        .setCredentialsVersion(user.credentialsVersion)
                        .setPasswordChanged(user.passwordChanged)
                        .setEligible(eligible);
        if (eligible) response.setLogin(user.login).setInitialPassword(user.initialPassword);
        return response.build();
    }

    /** Auth alone reads credentials after live admission; no network runs in this transaction. */
    UserState lockUser(long userId) {
        List<UserState> rows =
                jdbc.query(
                        """
SELECT id, telegram_id, account_state, login, initial_password, password_changed,
       credentials_version, telegram_binding_version, fence_operation_id
FROM auth_identities WHERE id = ? FOR UPDATE
""",
                        (rs, index) ->
                                new UserState(
                                        rs.getLong("id"),
                                        rs.getLong("telegram_id"),
                                        rs.getString("account_state"),
                                        rs.getString("login"),
                                        rs.getString("initial_password"),
                                        rs.getBoolean("password_changed"),
                                        rs.getLong("credentials_version"),
                                        rs.getLong("telegram_binding_version"),
                                        rs.getObject("fence_operation_id", UUID.class)),
                        userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    boolean hasOwnAuthority(UserState user) {
        if (user == null
                || user.status == null
                || !"ACTIVE".equals(user.status)
                || user.fenceOperationId != null
                || user.credentialsVersion <= 0
                || user.bindingVersion <= 0) return false;
        List<Grant> grants =
                jdbc.query(
                        """
SELECT role, status, group_id FROM user_role_grants WHERE user_id = ? ORDER BY id
""",
                        (rs, index) -> {
                            long group = rs.getLong("group_id");
                            boolean noGroup = rs.wasNull();
                            return new Grant(
                                    GrantRole.fromStored(rs.getString("role")),
                                    rs.getString("status"),
                                    noGroup ? null : group);
                        },
                        user.id);
        for (Grant grant : grants) {
            if (grant.role == null || !"active".equals(grant.status)) continue;
            switch (grant.role) {
                case STUDENT -> {
                    if (grant.groupId != null && grant.groupId > 0) return true;
                }
                case TEACHER -> {
                    return true;
                }
                case HEADMAN -> {
                    if (grant.groupId != null
                            && grants.stream()
                                    .anyMatch(
                                            base ->
                                                    base.role == GrantRole.STUDENT
                                                            && "active".equals(base.status)
                                                            && grant.groupId.equals(base.groupId)))
                        return true;
                }
                case ADMIN -> {
                    try {
                        if (admins.profile(user.id).assigned()) return true;
                    } catch (AdminAccessException error) {
                        if (error.getStatusCode().value() != 403) throw error;
                    }
                }
            }
        }
        return false;
    }

    private static void validateRead(BotOwnInitialCredentialsRequest request) {
        requireOwnIds(
                request.getTelegramSenderId(),
                request.getPrivateChatId(),
                request.getExpectedUserId());
        boolean snapshot =
                request.getMode() == BotCredentialReadMode.BOT_CREDENTIAL_READ_MODE_SNAPSHOT
                        && request.getExpectedTelegramBindingVersion() == 0
                        && request.getExpectedCredentialsVersion() == 0;
        boolean recheck =
                request.getMode() == BotCredentialReadMode.BOT_CREDENTIAL_READ_MODE_RECHECK
                        && request.getExpectedTelegramBindingVersion() > 0
                        && request.getExpectedCredentialsVersion() > 0;
        if (!snapshot && !recheck) throw invalid();
    }

    static void requireOwnIds(long sender, long chat, long user) {
        if (sender <= 0 || chat <= 0 || user <= 0) throw invalid();
        if (sender != chat) throw denied();
    }

    static io.grpc.StatusRuntimeException invalid() {
        return Status.INVALID_ARGUMENT
                .withDescription("valid bot credential metadata required")
                .asRuntimeException();
    }

    static io.grpc.StatusRuntimeException denied() {
        return Status.PERMISSION_DENIED
                .withDescription("own bot credential scope required")
                .asRuntimeException();
    }

    static io.grpc.StatusRuntimeException stale() {
        return Status.FAILED_PRECONDITION
                .withDescription("bot credential generation changed")
                .asRuntimeException();
    }

    private enum GrantRole {
        STUDENT("student"),
        TEACHER("teacher"),
        HEADMAN("headman"),
        ADMIN("admin");
        private final String code;

        GrantRole(String code) {
            this.code = code;
        }

        static GrantRole fromStored(String value) {
            for (GrantRole role : values()) if (role.code.equals(value)) return role;
            return null;
        }
    }

    private record Grant(GrantRole role, String status, Long groupId) {}

    /** Transient only; intentionally no record-generated credential-bearing toString. */
    static final class UserState {
        final long id;
        final long telegramId;
        final String status;
        final String login;
        final String initialPassword;
        final boolean passwordChanged;
        final long credentialsVersion;
        final long bindingVersion;
        final UUID fenceOperationId;

        UserState(
                long id,
                long telegramId,
                String status,
                String login,
                String initialPassword,
                boolean passwordChanged,
                long credentialsVersion,
                long bindingVersion,
                UUID fenceOperationId) {
            this.id = id;
            this.telegramId = telegramId;
            this.status = status;
            this.login = login;
            this.initialPassword = initialPassword;
            this.passwordChanged = passwordChanged;
            this.credentialsVersion = credentialsVersion;
            this.bindingVersion = bindingVersion;
            this.fenceOperationId = fenceOperationId;
        }

        boolean eligible() {
            return !passwordChanged
                    && initialPassword != null
                    && !initialPassword.isEmpty()
                    && login != null
                    && !login.isEmpty();
        }

        @Override
        public String toString() {
            return "BotCredentialUserState[redacted]";
        }
    }
}
