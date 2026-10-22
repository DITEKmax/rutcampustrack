package ru.rutcampustrack.auth.authority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.authauthority.grpc.*;

import java.util.*;

/** Auth-only bounded identity reads and the restricted non-authoritative registry feed. */
@Repository
public class AuthIdentityAuthorityQuery {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuthIdentityAuthorityQuery(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReadIdentityAuthorityResponse read(ReadIdentityAuthorityRequest request) {
        var ids = ids(request.getUserIdsList());
        var result = ReadIdentityAuthorityResponse.newBuilder();
        for (long id : ids) {
            var rows =
                    jdbc.query(
                            """
SELECT id,login,account_state,fence_operation_id,roles_version,credentials_version,
    telegram_binding_version,telegram_id,telegram_username,password_changed
FROM auth_identities WHERE id=?
""",
                            (rs, n) -> {
                                String state = rs.getString(3);
                                if (!"PENDING".equals(state) && rs.getObject(4) != null)
                                    state = "FENCED";
                                var row =
                                        IdentityAuthoritySnapshot.newBuilder()
                                                .setUserId(rs.getLong(1))
                                                .setLogin(rs.getString(2))
                                                .setAccountState(accountState(state))
                                                .setRolesVersion(rs.getLong(5))
                                                .setCredentialsVersion(rs.getLong(6))
                                                .setTelegramBindingVersion(rs.getLong(7))
                                                .setPasswordChanged(rs.getBoolean(10));
                                Long telegram = rs.getObject(8, Long.class);
                                if (telegram != null) row.setTelegramId(telegram);
                                String username = rs.getString(9);
                                if (username != null) row.setTelegramUsername(username);
                                return row;
                            },
                            id);
            if (rows.isEmpty()) continue;
            var row = rows.getFirst();
            row.addAllGrants(
                    jdbc.query(
                            "SELECT id,role,status,group_id FROM user_role_grants WHERE user_id=?"
                                    + " ORDER BY id",
                            (rs, n) -> {
                                var g =
                                        AuthorityRoleGrant.newBuilder()
                                                .setGrantId(rs.getLong(1))
                                                .setRole(rs.getString(2).toUpperCase(Locale.ROOT))
                                                .setStatus(
                                                        rs.getString(3).toUpperCase(Locale.ROOT));
                                Long group = rs.getObject(4, Long.class);
                                if (group != null) g.setGroupId(group);
                                return g.build();
                            },
                            id));
            var profiles =
                    jdbc.query(
                            "SELECT assigned,mode,permissions,version FROM admin_access_profiles"
                                    + " WHERE user_id=?",
                            (rs, n) -> {
                                var p =
                                        AuthorityAdminProfile.newBuilder()
                                                .setAssigned(rs.getBoolean(1))
                                                .setVersion(rs.getLong(4));
                                String mode = rs.getString(2);
                                if (mode != null) p.setMode(mode);
                                p.addAllPermissions(
                                        Arrays.asList((String[]) rs.getArray(3).getArray()));
                                return p.build();
                            },
                            id);
            if (!profiles.isEmpty()) row.setAdminProfile(profiles.getFirst());
            Long revision =
                    jdbc.queryForObject(
                            """
SELECT coalesce(max(committed_academic_revision),0) FROM (
 SELECT t.committed_academic_revision FROM auth_identity_operation_targets t
 JOIN auth_identity_operations o USING(operation_id) WHERE t.user_id=? AND o.phase='COMMITTED'
 UNION ALL SELECT g.committed_academic_revision FROM auth_credential_reader_groups g
 JOIN auth_credential_reader_invalidations o USING(operation_id)
 WHERE g.headman_user_id=? AND o.phase='COMMITTED') revisions
""",
                            Long.class,
                            id,
                            id);
            row.setAcademicRevision(Objects.requireNonNull(revision));
            result.addIdentities(row);
        }
        return result.build();
    }

    @Transactional(readOnly = true)
    public IdentityCatalogWatermark watermark() {
        return IdentityCatalogWatermark.newBuilder().setCatalogVersion(version()).build();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IdentityCatalogChangesResponse changes(IdentityCatalogChangesRequest request) {
        if (request.getAfterCatalogVersion() < 0
                || request.getLimit() < 1
                || request.getLimit() > 200
                || !request.getUnknownFields().asMap().isEmpty()) throw invalid();
        long watermark = version();
        if (request.getAfterCatalogVersion() > watermark)
            throw Status.FAILED_PRECONDITION
                    .withDescription("identity catalog cursor is ahead")
                    .asRuntimeException();
        var changes =
                jdbc.query(
                        "SELECT catalog_version,projection FROM auth_identity_catalog_changes WHERE"
                                + " catalog_version>? ORDER BY catalog_version LIMIT ?",
                        (rs, n) ->
                                IdentityCatalogChange.newBuilder()
                                        .setCatalogVersion(rs.getLong(1))
                                        .setIdentity(projection(rs.getString(2)))
                                        .build(),
                        request.getAfterCatalogVersion(),
                        request.getLimit());
        long expected = request.getAfterCatalogVersion() + 1;
        for (var change : changes)
            if (change.getCatalogVersion() != expected++) throw unavailable();
        if (changes.isEmpty() && watermark > request.getAfterCatalogVersion()) throw unavailable();
        return IdentityCatalogChangesResponse.newBuilder()
                .addAllChanges(changes)
                .setCatalogVersion(watermark)
                .build();
    }

    private IdentityCatalogProjection projection(String value) {
        try {
            JsonNode p = mapper.readTree(value);
            var out =
                    IdentityCatalogProjection.newBuilder()
                            .setUserId(p.path("user_id").asLong())
                            .setLogin(p.path("login").asText())
                            .setAccountState(accountState(p.path("account_state").asText()))
                            .setRolesVersion(p.path("roles_version").asLong())
                            .setTelegramBindingVersion(p.path("telegram_binding_version").asLong())
                            .setPasswordChanged(p.path("password_changed").asBoolean())
                            .setAcademicRevision(p.path("academic_revision").asLong());
            if (!p.path("telegram_id").isNull()) out.setTelegramId(p.path("telegram_id").asLong());
            if (!p.path("telegram_username").isNull())
                out.setTelegramUsername(p.path("telegram_username").asText());
            for (var node : p.path("grants")) {
                var g =
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(node.path("grant_id").asLong())
                                .setRole(node.path("role").asText())
                                .setStatus(node.path("status").asText());
                if (!node.path("group_id").isNull()) g.setGroupId(node.path("group_id").asLong());
                out.addGrants(g);
            }
            return out.build();
        } catch (Exception malformed) {
            throw unavailable();
        }
    }

    private long version() {
        Long result =
                jdbc.queryForObject(
                        "SELECT version FROM auth_identity_catalog_clock WHERE singleton=1",
                        Long.class);
        if (result == null) throw unavailable();
        return result;
    }

    static List<Long> ids(List<Long> values) {
        if (values.isEmpty()
                || values.size() > 200
                || values.stream().anyMatch(id -> id <= 0)
                || new HashSet<>(values).size() != values.size()) throw invalid();
        return values.stream().sorted().toList();
    }

    private static AuthorityAccountState accountState(String state) {
        try {
            return AuthorityAccountState.valueOf("AUTHORITY_ACCOUNT_STATE_" + state);
        } catch (IllegalArgumentException invalid) {
            throw unavailable();
        }
    }

    private static io.grpc.StatusRuntimeException invalid() {
        return Status.INVALID_ARGUMENT
                .withDescription("valid bounded identity request required")
                .asRuntimeException();
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("identity catalog unavailable")
                .asRuntimeException();
    }
}
