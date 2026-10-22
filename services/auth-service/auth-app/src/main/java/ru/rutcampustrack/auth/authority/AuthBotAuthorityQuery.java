package ru.rutcampustrack.auth.authority;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.authauthority.grpc.BotActiveGrant;
import ru.rutcampustrack.authauthority.grpc.BotReadCapability;
import ru.rutcampustrack.authauthority.grpc.CheckBotOwnAuthorityRequest;
import ru.rutcampustrack.authauthority.grpc.CheckBotOwnAuthorityResponse;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;

/** One uncached Auth-only snapshot. Presentation and network calls occur after this transaction. */
@Repository
public class AuthBotAuthorityQuery {
    private final JdbcTemplate jdbc;

    public AuthBotAuthorityQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot find(long sender, long chat) {
        validateOwnChat(sender, chat);
        var identities =
                jdbc.query(
                        """
                        SELECT id,login,password_changed,roles_version,telegram_binding_version
                        FROM auth_identities WHERE telegram_id=? AND account_state='ACTIVE'
                          AND fence_operation_id IS NULL
                        """,
                        (rs, n) ->
                                new Identity(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getBoolean(3),
                                        rs.getLong(4),
                                        rs.getLong(5)),
                        sender);
        if (identities.isEmpty()) return null;
        Identity user = identities.getFirst();
        var grants =
                jdbc.query(
                        "SELECT role,status,group_id FROM user_role_grants WHERE user_id=? ORDER BY"
                                + " id",
                        (rs, n) ->
                                new Grant(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getObject(3, Long.class)),
                        user.id());
        var active =
                grants.stream()
                        .filter(g -> "active".equals(g.status()))
                        .filter(g -> valid(g, grants, user.id()))
                        .map(
                                g ->
                                        BotActiveGrant.newBuilder()
                                                .setRole(
                                                        g.role().toUpperCase(java.util.Locale.ROOT))
                                                .setStatus("ACTIVE")
                                                .setGroupId(g.groupId() == null ? 0 : g.groupId())
                                                .build())
                        .toList();
        if (active.isEmpty()) return null;
        return new Snapshot(
                user.id(),
                user.login(),
                user.passwordChanged(),
                user.rolesVersion(),
                user.bindingVersion(),
                active);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CheckBotOwnAuthorityResponse check(CheckBotOwnAuthorityRequest request) {
        if (request.getExpectedUserId() <= 0
                || request.getExpectedRolesVersion() <= 0
                || request.getExpectedTelegramBindingVersion() <= 0
                || request.getRequiredCapability()
                        == BotReadCapability.BOT_READ_CAPABILITY_UNSPECIFIED
                || request.getRequiredCapability() == BotReadCapability.UNRECOGNIZED)
            throw invalid();
        Snapshot snapshot = find(request.getTelegramSenderId(), request.getPrivateChatId());
        if (snapshot == null || snapshot.userId() != request.getExpectedUserId()) throw denied();
        if (snapshot.bindingVersion() != request.getExpectedTelegramBindingVersion()
                || snapshot.rolesVersion() != request.getExpectedRolesVersion()) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("bot authority changed")
                    .asRuntimeException();
        }
        String role =
                switch (request.getRequiredCapability()) {
                    case BOT_READ_CAPABILITY_HOMEWORK_READ -> "STUDENT";
                    case BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ -> "HEADMAN";
                    case BOT_READ_CAPABILITY_TEACHER_TODAY_READ -> "TEACHER";
                    default -> throw invalid();
                };
        var grants = snapshot.grants().stream().filter(g -> role.equals(g.getRole())).toList();
        if (grants.isEmpty()) throw denied();
        var result =
                CheckBotOwnAuthorityResponse.newBuilder()
                        .setUserId(snapshot.userId())
                        .setTelegramBindingVersion(snapshot.bindingVersion())
                        .setRolesVersion(snapshot.rolesVersion())
                        .setCapability(request.getRequiredCapability());
        grants.stream()
                .map(BotActiveGrant::getGroupId)
                .filter(id -> id > 0)
                .distinct()
                .forEach(result::addAuthorizedGroupIds);
        return result.build();
    }

    private boolean valid(Grant grant, List<Grant> all, long userId) {
        return switch (grant.role()) {
            case "student" -> grant.groupId() != null && grant.groupId() > 0;
            case "headman" ->
                    grant.groupId() != null
                            && grant.groupId() > 0
                            && all.stream()
                                    .anyMatch(
                                            g ->
                                                    "student".equals(g.role())
                                                            && "active".equals(g.status())
                                                            && grant.groupId().equals(g.groupId()));
            case "teacher" -> grant.groupId() == null;
            case "admin" -> grant.groupId() == null && assignedAdmin(userId);
            default -> false;
        };
    }

    private boolean assignedAdmin(long userId) {
        var profiles =
                jdbc.query(
                        "SELECT assigned,mode,permissions FROM admin_access_profiles WHERE"
                                + " user_id=?",
                        (rs, n) -> {
                            if (!rs.getBoolean(1)) return false;
                            Array array = rs.getArray(3);
                            List<String> permissions =
                                    array == null
                                            ? List.of()
                                            : Arrays.asList((String[]) array.getArray());
                            try {
                                AdminPermission.validate(rs.getString(2), permissions);
                                return true;
                            } catch (AdminAccessException invalid) {
                                return false;
                            }
                        },
                        userId);
        return profiles.size() == 1 && profiles.getFirst();
    }

    private static void validateOwnChat(long sender, long chat) {
        if (sender <= 0 || chat <= 0) throw invalid();
        if (sender != chat) throw denied();
    }

    static io.grpc.StatusRuntimeException invalid() {
        return Status.INVALID_ARGUMENT
                .withDescription("valid bot own identity required")
                .asRuntimeException();
    }

    static io.grpc.StatusRuntimeException denied() {
        return Status.PERMISSION_DENIED
                .withDescription("bot own authority required")
                .asRuntimeException();
    }

    private record Identity(
            long id,
            String login,
            boolean passwordChanged,
            long rolesVersion,
            long bindingVersion) {}

    private record Grant(String role, String status, Long groupId) {}

    public record Snapshot(
            long userId,
            String login,
            boolean passwordChanged,
            long rolesVersion,
            long bindingVersion,
            List<BotActiveGrant> grants) {}
}
