package ru.rutcampustrack.academic.notification;

import io.grpc.Status;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.admin.AdminAccessService;
import ru.rutcampustrack.academic.grpc.NotificationAudienceRecipient;
import ru.rutcampustrack.academic.grpc.NotificationAudienceResponse;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Auth recipient data is not session admission: logging out does not remove a user's inbox. */
@Component
public class NotificationAudienceAuthority {
    private final IdentityCatalogSynchronizer catalog;
    private final AuthIdentityDataClient identities;

    public NotificationAudienceAuthority(
            IdentityCatalogSynchronizer catalog, AuthIdentityDataClient identities) {
        this.catalog = catalog;
        this.identities = identities;
    }

    public NotificationAudienceResponse resolve(Supplier<LocalSnapshot> capture) {
        requireOutsideTransaction();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        long watermark = watermark(deadline);
        try {
            catalog.await(watermark, deadline);
        } catch (io.grpc.StatusRuntimeException
                | org.springframework.dao.DataAccessException failure) {
            throw unavailable();
        }
        var local = capture(capture);
        requireOutsideTransaction();
        var ids = local.candidates().keySet().stream().sorted().toList();
        var current = new TreeMap<Long, IdentityAuthoritySnapshot>();
        for (int offset = 0; offset < ids.size(); offset += 200) {
            var batch = ids.subList(offset, Math.min(offset + 200, ids.size()));
            Map<Long, IdentityAuthoritySnapshot> values;
            try {
                values = identities.readServiceIdentity(batch, deadline);
            } catch (io.grpc.StatusRuntimeException failure) {
                throw unavailable();
            }
            if (!values.keySet().equals(new HashSet<>(batch))) throw unavailable();
            current.putAll(values);
        }
        var result =
                NotificationAudienceResponse.newBuilder()
                        .setRequestId(local.requestId())
                        .setEventType(local.eventType());
        for (long user : ids) {
            var identity = current.get(user);
            var profile = local.profiles().get(user);
            if (identity == null
                    || profile == null
                    || identity.getUserId() != user
                    || identity.getRolesVersion() != profile.authRolesVersion()
                    || identity.getAcademicRevision() != profile.academicRevision()
                    || identity.getCredentialsVersion() <= 0
                    || identity.getTelegramBindingVersion() <= 0) throw unavailable();
            boolean active =
                    switch (identity.getAccountState()) {
                        case AUTHORITY_ACCOUNT_STATE_ACTIVE -> true;
                        case AUTHORITY_ACCOUNT_STATE_ARCHIVED -> false;
                        default -> throw unavailable();
                    };
            var activeGrants = validateGrants(identity);
            var roles = new TreeSet<String>();
            for (String role : local.candidates().get(user)) {
                Long authGroup = activeGrants.get(role);
                if (!active || authGroup == null) continue;
                if (!role.equals("ADMIN")
                        && !Objects.equals(authGroup, profile.roleGroups().get(role)))
                    throw unavailable();
                if (role.equals("ADMIN")
                        && local.adminPermission() != null
                        && !AdminAccessService.profile(user, identity.getAdminProfile())
                                .allows(local.adminPermission())) continue;
                roles.add(role);
            }
            if (!roles.isEmpty())
                result.addRecipients(
                        NotificationAudienceRecipient.newBuilder()
                                .setUserId(user)
                                .addAllRoles(roles));
        }
        // The second RR capture checks domain eligibility/data and scoped fences together.
        // Auth's monotone catalog detects mutations during batch reads and candidate selection.
        if (!local.equals(capture(capture))) throw unavailable();
        requireOutsideTransaction();
        if (watermark(deadline) != watermark || System.nanoTime() >= deadline) throw unavailable();
        return result.build();
    }

    private long watermark(long deadline) {
        try {
            return catalog.watermark(deadline);
        } catch (io.grpc.StatusRuntimeException failure) {
            throw unavailable();
        }
    }

    private static LocalSnapshot capture(Supplier<LocalSnapshot> capture) {
        try {
            var snapshot = capture.get();
            if (snapshot == null) throw unavailable();
            return snapshot;
        } catch (org.springframework.dao.DataAccessException failure) {
            throw unavailable();
        }
    }

    private static Map<String, Long> validateGrants(IdentityAuthoritySnapshot identity) {
        var roles = new HashSet<String>();
        var ids = new HashSet<Long>();
        var active = new HashMap<String, Long>();
        for (var grant : identity.getGrantsList()) {
            Set<String> statuses =
                    switch (grant.getRole()) {
                        case "STUDENT" ->
                                Set.of("ACTIVE", "EXPELLED", "GRADUATED", "SUSPENDED", "ARCHIVED");
                        case "TEACHER" -> Set.of("ACTIVE", "DISMISSED", "SUSPENDED", "ARCHIVED");
                        case "HEADMAN" -> Set.of("ACTIVE", "SUSPENDED", "ARCHIVED");
                        case "ADMIN" -> Set.of("ACTIVE", "ARCHIVED");
                        default -> throw unavailable();
                    };
            if (grant.getGrantId() <= 0
                    || !ids.add(grant.getGrantId())
                    || !roles.add(grant.getRole())
                    || !statuses.contains(grant.getStatus())
                    || grant.hasGroupId() && grant.getGroupId() <= 0
                    || Set.of("TEACHER", "ADMIN").contains(grant.getRole()) && grant.hasGroupId()
                    || Set.of("STUDENT", "HEADMAN").contains(grant.getRole())
                            && grant.getStatus().equals("ACTIVE")
                            && !grant.hasGroupId()) throw unavailable();
            if (grant.getStatus().equals("ACTIVE"))
                active.put(grant.getRole(), grant.hasGroupId() ? grant.getGroupId() : 0L);
        }
        return active;
    }

    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
    }

    static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("notification audience snapshot unavailable")
                .asRuntimeException();
    }

    public record ProfileStamp(
            long academicRevision, long authRolesVersion, Map<String, Long> roleGroups) {
        public ProfileStamp {
            roleGroups = Map.copyOf(roleGroups);
        }
    }

    public record LocalSnapshot(
            String requestId,
            String eventType,
            Map<Long, SortedSet<String>> candidates,
            Map<Long, ProfileStamp> profiles,
            Map<Long, Long> groupRevisions,
            String adminPermission) {
        public LocalSnapshot {
            var copy = new TreeMap<Long, SortedSet<String>>();
            candidates.forEach(
                    (id, roles) ->
                            copy.put(id, Collections.unmodifiableSortedSet(new TreeSet<>(roles))));
            candidates = Collections.unmodifiableMap(copy);
            profiles = Map.copyOf(profiles);
            groupRevisions = Map.copyOf(groupRevisions);
        }
    }
}
