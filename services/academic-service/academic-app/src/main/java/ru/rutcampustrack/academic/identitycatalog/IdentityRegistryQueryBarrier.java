package ru.rutcampustrack.academic.identitycatalog;

import io.grpc.Status;

import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.user.RoleGrantViewResponse;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exportjobs.ExportJobService.UserRegistryReadScope;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.authauthority.grpc.CheckAdminAccessResponse;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The whole registry filter/count/sort/page runs against one caught-up local snapshot. */
@Service
public class IdentityRegistryQueryBarrier {
    private final IdentityCatalogSynchronizer catalog;
    private final AuthIdentityDataClient auth;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate snapshot;
    private final AcademicIdentityReadFence fences;
    private final UserRoleGrantReader grantReader;

    public IdentityRegistryQueryBarrier(
            IdentityCatalogSynchronizer catalog,
            AuthIdentityDataClient auth,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            AcademicIdentityReadFence fences,
            UserRoleGrantReader grantReader) {
        this.catalog = catalog;
        this.auth = auth;
        this.jdbc = jdbc;
        this.fences = fences;
        this.grantReader = grantReader;
        this.snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Result read(Supplier<Page<User>> fullLocalQuery) {
        return read(fullLocalQuery, auth::checkRegistryActor);
    }

    public Result readForReport(
            Supplier<Page<User>> fullLocalQuery, UserRegistryReadScope reportScope) {
        return read(fullLocalQuery, java.util.Objects.requireNonNull(reportScope)::check);
    }

    private Result read(
            Supplier<Page<User>> fullLocalQuery,
            LongFunction<CheckAdminAccessResponse> currentActor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        for (int attempt = 0; attempt < 3 && System.nanoTime() < deadline; attempt++) {
            var actor = currentActor.apply(deadline);
            long version = catalog.watermark(deadline);
            catalog.await(version, deadline);
            var result =
                    snapshot.execute(
                            status -> {
                                fences.requireRegistryReadable();
                                var page = fullLocalQuery.get();
                                var identities =
                                        page.getContent().stream().map(User::getId).toList();
                                var projected =
                                        identities.isEmpty()
                                                ? Map.<Long, Projection>of()
                                                : projection(identities);
                                return new Snapshot(
                                        page, projected, grantReader.findByUserIds(identities));
                            });
            if (result == null) throw unavailable();
            boolean exact = true;
            var ids = result.page().getContent().stream().map(User::getId).toList();
            for (int start = 0; start < ids.size(); start += 200) {
                var current =
                        auth.read(ids.subList(start, Math.min(start + 200, ids.size())), deadline);
                for (var entry : current.entrySet()) {
                    var projected = result.identities().get(entry.getKey());
                    if (projected == null || !projected.matches(entry.getValue())) exact = false;
                }
            }
            var stillActor = currentActor.apply(deadline);
            long after = catalog.watermark(deadline);
            if (exact && actor.equals(stillActor) && version == after)
                return new Result(
                        result.page(),
                        result.grants(),
                        "FULL".equals(actor.getProfile().getMode()));
        }
        throw unavailable();
    }

    private Map<Long, Projection> projection(List<Long> ids) {
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc
                .query(
                        "SELECT"
                            + " user_id,roles_version,telegram_binding_version,account_state,academic_revision"
                            + " FROM academic_identity_read_model WHERE user_id IN ("
                                + placeholders
                                + ")",
                        (rs, n) ->
                                new Projection(
                                        rs.getLong("user_id"),
                                        rs.getLong("roles_version"),
                                        rs.getLong("telegram_binding_version"),
                                        rs.getString("account_state"),
                                        rs.getLong("academic_revision")),
                        ids.toArray())
                .stream()
                .collect(Collectors.toMap(Projection::userId, p -> p));
    }

    public record Result(
            Page<User> page, Map<Long, List<RoleGrantViewResponse>> grants, boolean full) {}

    private record Snapshot(
            Page<User> page,
            Map<Long, Projection> identities,
            Map<Long, List<RoleGrantViewResponse>> grants) {}

    private record Projection(
            long userId,
            long rolesVersion,
            long bindingVersion,
            String accountState,
            long academicRevision) {
        boolean matches(IdentityAuthoritySnapshot identity) {
            return userId == identity.getUserId()
                    && rolesVersion == identity.getRolesVersion()
                    && bindingVersion == identity.getTelegramBindingVersion()
                    && academicRevision == identity.getAcademicRevision()
                    && ("AUTHORITY_ACCOUNT_STATE_" + accountState)
                            .equals(identity.getAccountState().name());
        }
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("registry changed; retry with current identity")
                .asRuntimeException();
    }
}
