package ru.rutcampustrack.academic.identitycatalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.authauthority.grpc.IdentityCatalogChangesResponse;

import java.util.HashSet;

/** Projection replacements and the contiguous applied frontier commit together. */
@Repository
public class JdbcIdentityCatalogProjection {
    private final JdbcTemplate jdbc;

    public JdbcIdentityCatalogProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public long frontier() {
        var value =
                jdbc.queryForObject(
                        "SELECT applied_catalog_version FROM academic_identity_catalog_cursor WHERE"
                                + " singleton",
                        Long.class);
        if (value == null || value < 0) throw AuthIdentityCatalogClient.unavailable();
        return value;
    }

    @Transactional
    public long apply(IdentityCatalogChangesResponse response) {
        long cursor =
                jdbc.queryForObject(
                        "SELECT applied_catalog_version FROM academic_identity_catalog_cursor WHERE"
                                + " singleton FOR UPDATE",
                        Long.class);
        if (response.getChangesCount() > 200 || response.getCatalogVersion() < cursor)
            throw AuthIdentityCatalogClient.unavailable();
        long previous = 0;
        for (var change : response.getChangesList()) {
            long version = change.getCatalogVersion();
            if (version <= previous || version > response.getCatalogVersion())
                throw AuthIdentityCatalogClient.unavailable();
            previous = version;
            if (version <= cursor)
                continue; // Another consumer already committed this exact prefix.
            if (version != cursor + 1) throw AuthIdentityCatalogClient.unavailable();
            var identity = change.getIdentity();
            String state =
                    switch (identity.getAccountState()) {
                        case AUTHORITY_ACCOUNT_STATE_PENDING -> "PENDING";
                        case AUTHORITY_ACCOUNT_STATE_ACTIVE -> "ACTIVE";
                        case AUTHORITY_ACCOUNT_STATE_ARCHIVED -> "ARCHIVED";
                        case AUTHORITY_ACCOUNT_STATE_FENCED -> "FENCED";
                        default -> throw AuthIdentityCatalogClient.unavailable();
                    };
            if (identity.getUserId() <= 0
                    || identity.getLogin().isBlank()
                    || identity.getLogin().length() > 32
                    || identity.getRolesVersion() <= 0
                    || identity.getTelegramBindingVersion() <= 0
                    || identity.getAcademicRevision() < 0
                    || identity.hasTelegramId() && identity.getTelegramId() <= 0)
                throw AuthIdentityCatalogClient.unavailable();
            jdbc.update(
                    """
INSERT INTO academic_identity_read_model(user_id,catalog_version,login,account_state,
  roles_version,telegram_binding_version,telegram_id,telegram_username,password_changed,academic_revision)
VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET
  catalog_version=excluded.catalog_version,login=excluded.login,account_state=excluded.account_state,
  roles_version=excluded.roles_version,telegram_binding_version=excluded.telegram_binding_version,
  telegram_id=excluded.telegram_id,telegram_username=excluded.telegram_username,
  password_changed=excluded.password_changed,academic_revision=excluded.academic_revision,updated_at=now()
""",
                    identity.getUserId(),
                    version,
                    identity.getLogin(),
                    state,
                    identity.getRolesVersion(),
                    identity.getTelegramBindingVersion(),
                    identity.hasTelegramId() ? identity.getTelegramId() : null,
                    identity.getTelegramUsername(),
                    identity.getPasswordChanged(),
                    identity.getAcademicRevision());
            jdbc.update(
                    "DELETE FROM academic_identity_read_model_grants WHERE user_id=?",
                    identity.getUserId());
            var roles = new HashSet<String>();
            for (var grant : identity.getGrantsList()) {
                if (grant.getGrantId() <= 0 || !roles.add(grant.getRole()))
                    throw AuthIdentityCatalogClient.unavailable();
                jdbc.update(
                        "INSERT INTO"
                            + " academic_identity_read_model_grants(grant_id,user_id,role,status,group_id)"
                            + " VALUES(?,?,?,?,?)",
                        grant.getGrantId(),
                        identity.getUserId(),
                        grant.getRole(),
                        grant.getStatus(),
                        grant.hasGroupId() ? grant.getGroupId() : null);
            }
            cursor = version;
        }
        jdbc.update(
                "UPDATE academic_identity_catalog_cursor SET applied_catalog_version=? WHERE"
                        + " singleton",
                cursor);
        return cursor;
    }
}
