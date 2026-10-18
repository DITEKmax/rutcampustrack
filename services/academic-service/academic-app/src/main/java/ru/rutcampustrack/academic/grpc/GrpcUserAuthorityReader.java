package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Fresh service-only Auth metadata plus Academic-owned presentation; never admission. */
@Repository
public class GrpcUserAuthorityReader {
    private final JdbcTemplate jdbc;
    private final AuthIdentityDataClient auth;
    private final AcademicIdentityReadFence fences;
    private final TransactionTemplate snapshot;

    @Autowired
    public GrpcUserAuthorityReader(
            JdbcTemplate jdbc,
            AuthIdentityDataClient auth,
            AcademicIdentityReadFence fences,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.fences = fences;
        snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setTimeout(3);
    }

    /** Old isolated harnesses have no remote authority and therefore fail closed. */
    public GrpcUserAuthorityReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        auth = null;
        fences = null;
        snapshot = null;
    }

    public UserResponse readUser(long userId) {
        if (userId <= 0) throw Status.INVALID_ARGUMENT.asRuntimeException();
        if (auth == null || snapshot == null) throw unavailable();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        // Read locally first so an unknown Academic profile retains NOT_FOUND semantics.
        var profile = snapshot.execute(ignored -> profile(userId));
        var before = identity(userId, deadline);
        var after = identity(userId, deadline);
        if (!before.equals(after)) throw unavailable();
        snapshot.executeWithoutResult(
                ignored -> {
                    if (!Objects.equals(profile, profile(userId))) throw unavailable();
                });
        if (profile == null
                || profile.revision() != after.getAcademicRevision()
                || profile.rolesVersion() != after.getRolesVersion()) throw unavailable();
        String status = profile.status();
        if (after.getAccountState() == AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ARCHIVED)
            status = "archived";
        var response =
                UserResponse.newBuilder()
                        .setId(userId)
                        .setLogin(after.getLogin())
                        .setDisplayName(profile.displayName())
                        .setRole(profile.role())
                        .setStatus(status)
                        .setGroupId(profile.groupId() == null ? 0 : profile.groupId())
                        .setIsHeadman(profile.headman())
                        .setAvatarId(profile.avatarId() == null ? "" : profile.avatarId())
                        .setTelegramId(after.hasTelegramId() ? after.getTelegramId() : 0)
                        .setRolesVersion(after.getRolesVersion());
        after.getGrantsList()
                .forEach(
                        grant -> {
                            var mapped =
                                    UserRoleGrant.newBuilder()
                                            .setGrantId(grant.getGrantId())
                                            .setRole(grant.getRole().toUpperCase(Locale.ROOT))
                                            .setStatus(grant.getStatus().toUpperCase(Locale.ROOT));
                            if (grant.hasGroupId()) mapped.setGroupId(grant.getGroupId());
                            response.addRoles(mapped);
                        });
        return response.build();
    }

    private IdentityAuthoritySnapshot identity(long userId, long deadline) {
        var identity = auth.readServiceIdentity(List.of(userId), deadline).get(userId);
        if (identity == null
                || identity.getUserId() != userId
                || identity.getLogin().isBlank()
                || identity.getRolesVersion() <= 0
                || identity.getTelegramBindingVersion() <= 0
                || identity.getAcademicRevision() <= 0
                || identity.getAccountState() == AuthorityAccountState.UNRECOGNIZED
                || identity.getAccountState()
                        == AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_UNSPECIFIED
                || identity.getAccountState()
                        == AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_PENDING
                || identity.getAccountState()
                        == AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_FENCED)
            throw unavailable();
        return identity;
    }

    private Profile profile(long userId) {
        fences.requireUserReadable(userId);
        var rows =
                jdbc.query(
                        """
SELECT p.academic_revision,p.applied_auth_roles_version,p.last_name,p.first_name,
       p.middle_name,p.directory_role,p.avatar_id,
       CASE WHEN p.archived_at IS NOT NULL THEN 'archived'
            WHEN e.status='EXPELLED' THEN 'expelled'
            WHEN e.status='SUSPENDED' THEN 'suspended' ELSE 'active' END status,
       CASE WHEN s.status IN ('ACTIVE','SUSPENDED') THEN s.group_id END group_id,
       EXISTS(SELECT 1 FROM academic_role_eligibility h JOIN groups g ON g.id=h.group_id
         WHERE h.user_id=p.id AND h.role='HEADMAN' AND h.status='ACTIVE'
           AND s.status='ACTIVE' AND s.group_id=h.group_id
           AND p.archived_at IS NULL AND g.is_active) headman
  FROM academic_profiles p
  LEFT JOIN academic_role_eligibility e ON e.user_id=p.id AND e.role=p.directory_role
  LEFT JOIN academic_role_eligibility s ON s.user_id=p.id AND s.role='STUDENT'
 WHERE p.id=?
""",
                        (rs, n) -> {
                            String middle = rs.getString("middle_name");
                            String name =
                                    rs.getString("last_name")
                                            + " "
                                            + rs.getString("first_name")
                                            + (middle == null || middle.isBlank()
                                                    ? ""
                                                    : " " + middle);
                            return new Profile(
                                    rs.getLong("academic_revision"),
                                    rs.getLong("applied_auth_roles_version"),
                                    name,
                                    rs.getString("directory_role").toLowerCase(Locale.ROOT),
                                    rs.getString("status"),
                                    rs.getObject("group_id", Long.class),
                                    rs.getBoolean("headman"),
                                    rs.getString("avatar_id"));
                        },
                        userId);
        if (rows.isEmpty())
            throw Status.NOT_FOUND.withDescription("user not found").asRuntimeException();
        if (rows.size() != 1) throw unavailable();
        var profile = rows.getFirst();
        if (profile.groupId() != null) fences.requireGroupReadable(profile.groupId());
        return profile;
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("current user metadata unavailable")
                .asRuntimeException();
    }

    private record Profile(
            long revision,
            long rolesVersion,
            String displayName,
            String role,
            String status,
            Long groupId,
            boolean headman,
            String avatarId) {}
}
