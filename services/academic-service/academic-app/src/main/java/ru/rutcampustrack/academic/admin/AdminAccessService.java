package ru.rutcampustrack.academic.admin;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authauthority.grpc.AuthorityAdminProfile;
import ru.rutcampustrack.shared.security.admin.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Public compatibility; Auth owns every permission profile, grant, CAS and live admission. */
@Service
public class AdminAccessService {
    private final JdbcTemplate jdbc;
    private final AcademicAdminAccess access;

    @org.springframework.beans.factory.annotation.Autowired
    private AuthIdentityDataClient identities;

    @org.springframework.beans.factory.annotation.Autowired
    private AcademicUserIdentityOperations operations;

    /**
     * Retains the source-era constructor while the actual runtime uses the required directed
     * clients.
     */
    public AdminAccessService(
            JdbcTemplate jdbc,
            AdminAccessJdbcAuthority ignoredAuthority,
            AcademicAdminAccess access,
            RequestContext ignoredContext,
            ru.rutcampustrack.academic.repository.UserRoleGrantWriter ignoredGrants,
            org.springframework.context.ApplicationEventPublisher ignoredPublisher,
            AcademicAuthDataCommands ignoredCommands) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminAccessProfile get(long target) {
        if (target <= 0) throw AdminAccessException.invalid();
        access.requireFull();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        var actor = identities.checkAdminAccess(List.of(), true, deadline);
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_profiles WHERE id=?)",
                        Boolean.class,
                        target)))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Пользователь не найден");
        var identity = identities.readServiceIdentity(List.of(target), deadline).get(target);
        if (identity == null
                || identity.getAccountState()
                        == AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_PENDING
                || identity.getAccountState()
                        == AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_FENCED)
            throw Status.UNAVAILABLE.asRuntimeException();
        var result = profile(target, identity.getAdminProfile());
        if (result.assigned()
                && (identity.getAccountState()
                                != AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE
                        || identity.getGrantsList().stream()
                                .noneMatch(
                                        g ->
                                                g.getRole().equals("ADMIN")
                                                        && g.getStatus().equals("ACTIVE"))))
            throw Status.UNAVAILABLE.asRuntimeException();
        if (!actor.equals(identities.checkAdminAccess(List.of(), true, deadline)))
            throw Status.UNAVAILABLE.asRuntimeException();
        return result;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminAccessProfile put(long target, AdminAccessInput input, Long expectedVersion) {
        access.requireFull();
        operations.adminAccess(target, input, expectedVersion, false);
        // The durable operation result is replay evidence; public profile data remains a fresh
        // read.
        return get(target);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void revoke(long target, Long expectedVersion) {
        access.requireFull();
        operations.adminAccess(target, null, expectedVersion, true);
    }

    public List<String> validate(AdminAccessInput input) {
        if (input == null) throw AdminAccessException.invalid();
        return AdminPermission.validate(input.mode(), input.permissions());
    }

    /** Converts only explicit Auth data; missing or malformed assignment can never imply FULL. */
    public static AdminAccessProfile profile(long user, AuthorityAdminProfile profile) {
        if (user <= 0 || profile.getVersion() < 0) throw Status.UNAVAILABLE.asRuntimeException();
        if (!profile.getAssigned()) {
            if (!profile.getMode().isEmpty() || profile.getPermissionsCount() != 0)
                throw Status.UNAVAILABLE.asRuntimeException();
            return new AdminAccessProfile(user, false, null, List.of(), profile.getVersion());
        }
        if (profile.getVersion() <= 0) throw Status.UNAVAILABLE.asRuntimeException();
        List<String> permissions;
        try {
            permissions = AdminPermission.validate(profile.getMode(), profile.getPermissionsList());
        } catch (AdminAccessException invalid) {
            throw Status.UNAVAILABLE.asRuntimeException();
        }
        return new AdminAccessProfile(
                user,
                true,
                profile.getMode(),
                "FULL".equals(profile.getMode()) ? AdminPermission.allCodes() : permissions,
                profile.getVersion());
    }
}
