package ru.rutcampustrack.auth.authority;

import io.grpc.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

/** Current administrator capability and permitted direct credential disclosure only. */
@Service
public class AuthAdministrativeAuthorityQuery {
    private final JdbcTemplate jdbc;
    private final AuthInternalActorReader actors;
    private final AuthAdminJdbcAuthority admins;
    private final AuthInitialCredentialReader credentialReader;

    public AuthAdministrativeAuthorityQuery(JdbcTemplate jdbc, AuthInternalActorReader actors) {
        this.jdbc = jdbc;
        this.actors = actors;
        this.admins = new AuthAdminJdbcAuthority(jdbc);
        this.credentialReader = new AuthInitialCredentialReader(jdbc);
    }

    @Transactional
    public CheckAdminAccessResponse check(CheckAdminAccessRequest request) {
        if (!request.getUnknownFields().asMap().isEmpty()
                || request.getPermissionsList().stream()
                        .anyMatch(p -> !AdminPermission.allCodes().contains(p))
                || (request.hasTargetUserId() && request.getTargetUserId() <= 0))
            throw Status.INVALID_ARGUMENT
                    .withDescription("valid admin capability required")
                    .asRuntimeException();
        var actor = actors.read();
        admins.lockGlobal();
        admins.lockUsers(
                actor.userId(), request.hasTargetUserId() ? request.getTargetUserId() : null);
        var claims = AuthInternalActorReader.claims(actor);
        var current = admins.current(claims);
        if (request.getFullOnly() && !current.full()) throw AdminAccessException.fullRequired();
        for (String permission : request.getPermissionsList())
            if (!current.allows(permission)) throw AdminAccessException.denied();
        if (request.getMutation() && request.hasTargetUserId())
            admins.requireMutableTarget(claims, request.getTargetUserId());
        var profile =
                AuthorityAdminProfile.newBuilder()
                        .setAssigned(true)
                        .setMode(current.mode())
                        .setVersion(current.version());
        if (!current.full()) profile.addAllPermissions(current.permissions());
        return CheckAdminAccessResponse.newBuilder()
                .setActorUserId(actor.userId())
                .setSessionVersion(actor.sessionVersion())
                .setRolesVersion(actor.rolesVersion())
                .setProfile(profile)
                .build();
    }

    @Transactional
    public ReadInitialCredentialsResponse credentials(ReadInitialCredentialsRequest request) {
        var ids = AuthIdentityAuthorityQuery.ids(request.getUserIdsList());
        if (!request.getUnknownFields().asMap().isEmpty())
            throw Status.INVALID_ARGUMENT
                    .withDescription("valid credential request required")
                    .asRuntimeException();
        var actor = actors.read();
        admins.lockGlobal();
        var locks = new java.util.TreeSet<>(ids);
        locks.add(actor.userId());
        for (long id : locks)
            jdbc.queryForList(
                    "SELECT id FROM auth_identities WHERE id=? FOR UPDATE", Long.class, id);
        var claims = AuthInternalActorReader.claims(actor);
        admins.require(claims, "USER_CREDENTIALS_MANAGE");
        for (long id : ids)
            if (!admins.canReadCredentials(claims, id)) throw AdminAccessException.denied();
        var result = ReadInitialCredentialsResponse.newBuilder();
        for (long id : ids) {
            var credential = credentialReader.active(id);
            if (credential == null)
                throw Status.FAILED_PRECONDITION
                        .withDescription("credential owner unavailable")
                        .asRuntimeException();
            result.addCredentials(credential);
        }
        return result.build();
    }
}
