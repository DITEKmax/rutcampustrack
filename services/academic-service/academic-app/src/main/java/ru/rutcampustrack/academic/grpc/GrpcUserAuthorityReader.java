package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One fresh nonsecret PostgreSQL snapshot; no profile cache or legacy-role fallback. */
@Repository
public class GrpcUserAuthorityReader {
    private final JdbcTemplate jdbc;

    public GrpcUserAuthorityReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot read(long userId) {
        return jdbc.query(
                """
                SELECT u.roles_version, u.telegram_id, g.id grant_id, g.role, g.status, g.group_id,
                       COALESCE(grp.is_active, FALSE) group_active
                FROM users u LEFT JOIN user_role_grants g ON g.user_id = u.id
                LEFT JOIN groups grp ON grp.id = g.group_id
                WHERE u.id = ? ORDER BY g.id
                """,
                rows -> {
                    if (!rows.next())
                        throw Status.NOT_FOUND
                                .withDescription("user not found")
                                .asRuntimeException();
                    long version = rows.getLong("roles_version");
                    Long telegramId = rows.getObject("telegram_id", Long.class);
                    var grants = new ArrayList<UserRoleGrant>();
                    Long activeStudentGroup = null;
                    do {
                        Long grantId = rows.getObject("grant_id", Long.class);
                        if (grantId == null) continue;
                        String role = rows.getString("role").toUpperCase(Locale.ROOT);
                        String status = rows.getString("status").toUpperCase(Locale.ROOT);
                        Long groupId = rows.getObject("group_id", Long.class);
                        var grant =
                                UserRoleGrant.newBuilder()
                                        .setGrantId(grantId)
                                        .setRole(role)
                                        .setStatus(status);
                        if (groupId != null) grant.setGroupId(groupId);
                        grants.add(grant.build());
                        if ("STUDENT".equals(role)
                                && "ACTIVE".equals(status)
                                && groupId != null
                                && rows.getBoolean("group_active")) activeStudentGroup = groupId;
                    } while (rows.next());
                    return new Snapshot(
                            version, telegramId, List.copyOf(grants), activeStudentGroup);
                },
                userId);
    }

    public long requireOwnStudentGroup(BotOwnHomeworksForWeekRequest request) {
        if (request.getTelegramSenderId() <= 0
                || request.getExpectedUserId() <= 0
                || request.getExpectedRolesVersion() <= 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("bound bot identity and version required")
                    .asRuntimeException();
        }
        Snapshot snapshot = read(request.getExpectedUserId());
        if (snapshot.telegramId() == null
                || snapshot.telegramId() != request.getTelegramSenderId()) {
            throw Status.PERMISSION_DENIED
                    .withDescription("own Telegram binding required")
                    .asRuntimeException();
        }
        if (snapshot.rolesVersion() != request.getExpectedRolesVersion()) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("current roles version required")
                    .asRuntimeException();
        }
        if (snapshot.activeStudentGroup() == null) {
            throw Status.PERMISSION_DENIED
                    .withDescription("current active student group required")
                    .asRuntimeException();
        }
        return snapshot.activeStudentGroup();
    }

    public record Snapshot(
            long rolesVersion,
            Long telegramId,
            List<UserRoleGrant> roles,
            Long activeStudentGroup) {}
}
