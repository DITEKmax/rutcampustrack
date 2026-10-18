package ru.rutcampustrack.academic.botcredentials;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsRequest;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsResponse;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;

import java.util.List;
import java.util.Set;

/**
 * Uncached own-account capability, separate from default-role activation and admin target access.
 */
@Service
public class BotOwnInitialCredentialsQuery {

    private static final Set<String> ACCOUNT_STATES =
            Set.of("active", "expelled", "suspended", "archived");
    private final JdbcTemplate jdbc;
    private final AdminAccessJdbcAuthority admins;

    public BotOwnInitialCredentialsQuery(JdbcTemplate jdbc, AdminAccessJdbcAuthority admins) {
        this.jdbc = jdbc;
        this.admins = admins;
    }

    /**
     * Retained internal signature; credentials are served only by the independently authenticated
     * Auth boundary.
     */
    public BotOwnInitialCredentialsResponse read(BotOwnInitialCredentialsRequest request) {
        BotCredentialServiceIdentityInterceptor.requireBot();
        throw Status.UNIMPLEMENTED
                .withDescription("own credentials require Auth service")
                .asRuntimeException();
    }

    /**
     * All tracker mutations lock user first, then the exact card; no network runs in this
     * transaction.
     */
    UserState lockUser(long userId) {
        List<UserState> rows =
                jdbc.query(
                        """
                        SELECT id, telegram_id, status, password_changed, credentials_version,
                               telegram_binding_version, eligible FROM auth_api.bot_user_state(?)
                        """,
                        (rs, index) ->
                                new UserState(
                                        rs.getLong("id"),
                                        rs.getLong("telegram_id"),
                                        rs.getString("status"),
                                        rs.getBoolean("eligible"),
                                        rs.getBoolean("password_changed"),
                                        rs.getLong("credentials_version"),
                                        rs.getLong("telegram_binding_version")),
                        userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    boolean hasOwnAuthority(UserState user) {
        if (user == null
                || user.status == null
                || !ACCOUNT_STATES.contains(user.status)
                || "archived".equals(user.status)
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
        final boolean credentialEligible;
        final boolean passwordChanged;
        final long credentialsVersion;
        final long bindingVersion;

        UserState(
                long id,
                long telegramId,
                String status,
                boolean credentialEligible,
                boolean passwordChanged,
                long credentialsVersion,
                long bindingVersion) {
            this.id = id;
            this.telegramId = telegramId;
            this.status = status;
            this.credentialEligible = credentialEligible;
            this.passwordChanged = passwordChanged;
            this.credentialsVersion = credentialsVersion;
            this.bindingVersion = bindingVersion;
        }

        boolean eligible() {
            return !passwordChanged && credentialEligible;
        }

        @Override
        public String toString() {
            return "BotCredentialUserState[redacted]";
        }
    }
}
