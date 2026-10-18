package ru.rutcampustrack.academic.groupavailability;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.group.*;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.group.GroupPromotionService;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityHttpOperations;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityPendingException;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Existing group/restore/promotion callers, durable status and explicit current-authorized
 * recovery.
 */
@Service
public class GroupAvailabilityService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper canonical;
    private final TransactionTemplate tx;
    private final AcademicIdentityHttpOperations http;
    private final GroupAvailabilityStore store;
    private final GroupAvailabilityCoordinator coordinator;
    private final ObjectProvider<GroupService> groups;
    private final ObjectProvider<GroupPromotionService> cycles;
    private final AuthIdentityDataClient identities;
    private final Clock clock;
    private final ru.rutcampustrack.academic.admin.AcademicAdminAccess admin;

    public GroupAvailabilityService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager transactions,
            AcademicIdentityHttpOperations http,
            GroupAvailabilityStore store,
            GroupAvailabilityCoordinator coordinator,
            ObjectProvider<GroupService> groups,
            ObjectProvider<GroupPromotionService> cycles,
            AuthIdentityDataClient identities,
            Clock clock,
            ru.rutcampustrack.academic.admin.AcademicAdminAccess admin) {
        this.jdbc = jdbc;
        this.canonical =
                mapper.copy()
                        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.tx = new TransactionTemplate(transactions);
        this.http = http;
        this.store = store;
        this.coordinator = coordinator;
        this.groups = groups;
        this.cycles = cycles;
        this.identities = identities;
        this.clock = clock;
        this.admin = admin;
    }

    public void requireAvailableScope(long group) {
        store.requireAvailableScope(group);
    }

    public Group updateGroup(long group, UpdateGroupRequest request) {
        if (request == null) throw Status.INVALID_ARGUMENT.asRuntimeException();
        if (request.active())
            return tx.execute(status -> groups.getObject().applyOrdinaryUpdate(group, request));
        String digest = digest(request);
        var operation =
                execute(
                        http.beginOperation(),
                        GroupAvailabilityCommand.Action.DEACTIVATE,
                        group,
                        digest,
                        () ->
                                tx.execute(
                                        status -> {
                                            groups.getObject()
                                                    .validateAvailabilityUpdate(group, request);
                                            return new GroupAvailabilityCommand(
                                                    GroupAvailabilityCommand.Action.DEACTIVATE,
                                                    group,
                                                    digest,
                                                    request,
                                                    LocalDate.now(clock).getYear(),
                                                    null,
                                                    List.of(revision(group)));
                                        }));
        finish(operation);
        return groups.getObject().findGroupById(group);
    }

    public void restoreGroup(long group) {
        String digest = digest(new RestoreIntent(group));
        UUID id = http.beginOperation();
        var intent =
                GroupAvailabilityIntent.direct(
                        GroupAvailabilityCommand.Action.RESTORE, group, digest);
        http.checkGroupAttempt(id, intent);
        var replay = store.findForActor(id, intent);
        if (replay != null) {
            finish(run(replay));
            return;
        }
        var failed = store.failedIntent(GroupAvailabilityCommand.Action.RESTORE, group, digest);
        if (failed != null) {
            finish(run(reconcileAccepted(id, failed, intent)));
            return;
        }
        var accepted =
                localAdmission(
                        id,
                        intent,
                        () -> {
                            store.requireAvailableScope(group);
                            var preparation =
                                    tx.execute(
                                            status -> {
                                                boolean needed =
                                                        groups.getObject()
                                                                .validateAvailabilityRestore(group);
                                                return new RestorePreparation(
                                                        needed,
                                                        new GroupAvailabilityCommand(
                                                                GroupAvailabilityCommand.Action
                                                                        .RESTORE,
                                                                group,
                                                                digest,
                                                                null,
                                                                null,
                                                                null,
                                                                List.of(revision(group))));
                                            });
                            return preparation.needed()
                                    ? store.accept(
                                            id, preparation.command(), null, Map.of(), intent)
                                    : store.recordNoop(id, preparation.command());
                        });
        finish(run(accepted));
    }

    public PromotionSummary executePromotion(PromotionExecuteRequest request) {
        String digest = digest(request);
        long scope = request == null || request.getGroupId() == null ? 0 : request.getGroupId();
        var operation =
                execute(
                        http.beginOperation(),
                        GroupAvailabilityCommand.Action.MANUAL_CYCLE,
                        scope,
                        digest,
                        () ->
                                tx.execute(
                                        status -> {
                                            var plan =
                                                    GroupAvailabilityCommand.Plan.from(
                                                            cycles.getObject()
                                                                    .prepareAvailabilityPlan(
                                                                            request));
                                            return cycleCommand(
                                                    GroupAvailabilityCommand.Action.MANUAL_CYCLE,
                                                    digest,
                                                    plan);
                                        }));
        finish(operation);
        return operation.command().cycle().result();
    }

    public Optional<PromotionSummary> executeAutomaticallyIfDue() {
        var prepared = tx.execute(status -> cycles.getObject().prepareAutomaticAvailabilityPlan());
        if (prepared.isEmpty()) return Optional.empty();
        var plan = GroupAvailabilityCommand.Plan.from(prepared.get());
        if (plan.promote().isEmpty() && plan.archive().isEmpty()) return Optional.of(plan.result());
        String digest = digest(new AutomaticIntent(plan.semester(), plan.previewVersion()));
        var command =
                tx.execute(
                        status ->
                                cycleCommand(
                                        GroupAvailabilityCommand.Action.AUTOMATIC_CYCLE,
                                        digest,
                                        plan));
        UUID id =
                UUID.nameUUIDFromBytes(
                        ("academic-group-cycle:" + digest)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var operation =
                store.accept(
                        id,
                        command,
                        null,
                        Map.of(),
                        GroupAvailabilityIntent.direct(
                                command.action(), command.scopeGroupId(), command.requestDigest()));
        finish(run(operation));
        return Optional.of(plan.result());
    }

    public IdentityOperationHttpModels.Operation rejectedGroupStatusForCurrentAdmin(
            UUID operation) {
        admin.require("GROUPS_MANAGE");
        return new IdentityOperationHttpModels.Operation(
                operation, IdentityOperationHttpModels.Status.REJECTED, 0);
    }

    public IdentityOperationHttpModels.Operation statusForCurrentAdmin(UUID operation) {
        admin.require("GROUPS_MANAGE");
        return status(store.find(operation));
    }

    public IdentityOperationHttpModels.Operation reconcile(UUID failedId) {
        admin.require("GROUPS_MANAGE");
        UUID id = http.beginOperation();
        var intent = GroupAvailabilityIntent.reconcile(failedId);
        http.checkGroupAttempt(id, intent);
        var replay = store.findForActor(id, intent);
        if (replay != null) {
            if (!failedId.equals(replay.supersedes()))
                throw Status.ALREADY_EXISTS.asRuntimeException();
            return status(finish(run(replay)));
        }
        GroupAvailabilityStore.Operation failed;
        try {
            failed = store.find(failedId);
            if (failed.phase() != GroupAvailabilityStore.Phase.FAILED_SAFE)
                throw Status.FAILED_PRECONDITION
                        .withDescription(
                                "only a failed-safe fixed group operation can be reconciled")
                        .asRuntimeException();
        } catch (StatusRuntimeException localRejected) {
            return status(
                    finish(
                            run(
                                    localAdmission(
                                            id,
                                            intent,
                                            () -> {
                                                throw localRejected;
                                            }))));
        }
        return status(finish(run(reconcileAccepted(id, failed, intent))));
    }

    private GroupAvailabilityStore.Operation reconcileAccepted(
            UUID id,
            GroupAvailabilityStore.Operation failed,
            ru.rutcampustrack.academic.identityoperation.AcademicIdentityLocalCommand.ApiIntent
                    intent) {
        var users =
                failed.auth().getGroupsList().stream()
                        .map(g -> g.getHeadmanUserId())
                        .filter(u -> u > 0)
                        .distinct()
                        .sorted()
                        .toList();
        // Machine snapshot is only expected CAS material, never permission. Prepare admits the new
        // actor.
        var fresh = new java.util.TreeMap<Long, IdentityAuthoritySnapshot>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        for (int offset = 0; offset < users.size(); offset += 200)
            fresh.putAll(
                    identities.readServiceIdentity(
                            users.subList(offset, Math.min(offset + 200, users.size())), deadline));
        return localAdmission(
                id, intent, () -> store.accept(id, failed.command(), failed.id(), fresh, intent));
    }

    private GroupAvailabilityStore.Operation execute(
            UUID id,
            GroupAvailabilityCommand.Action action,
            long scope,
            String digest,
            Supplier<GroupAvailabilityCommand> build) {
        var intent = GroupAvailabilityIntent.direct(action, scope, digest);
        http.checkGroupAttempt(id, intent);
        var replay = store.findForActor(id, intent);
        if (replay != null) return run(replay);
        var failed = store.failedIntent(action, scope, digest);
        if (failed != null) return run(reconcileAccepted(id, failed, intent));
        return run(
                localAdmission(
                        id,
                        intent,
                        () -> {
                            store.requireAvailableScope(scope);
                            var command = build.get();
                            return command.groups().isEmpty()
                                    ? store.recordNoop(id, command)
                                    : store.accept(id, command, null, Map.of(), intent);
                        }));
    }

    private GroupAvailabilityStore.Operation localAdmission(
            UUID id,
            ru.rutcampustrack.academic.identityoperation.AcademicIdentityLocalCommand.ApiIntent
                    intent,
            Supplier<GroupAvailabilityStore.Operation> local) {
        return http.groupPreflight(
                id,
                intent,
                local,
                () -> {
                    var winner = store.findForActor(id, intent);
                    if (winner == null) throw Status.UNAVAILABLE.asRuntimeException();
                    return winner;
                });
    }

    private GroupAvailabilityStore.Operation run(GroupAvailabilityStore.Operation operation) {
        try {
            return coordinator.execute(operation);
        } catch (StatusRuntimeException unknown) {
            if (switch (unknown.getStatus().getCode()) {
                case ABORTED, UNAVAILABLE, DEADLINE_EXCEEDED, CANCELLED, UNKNOWN, INTERNAL -> true;
                default -> false;
            }) return store.find(operation.id());
            throw unknown;
        } catch (DataAccessException unknown) {
            return store.find(operation.id());
        }
    }

    private GroupAvailabilityStore.Operation finish(GroupAvailabilityStore.Operation operation) {
        var status = status(operation);
        if (status.status() == IdentityOperationHttpModels.Status.PENDING)
            throw new AcademicIdentityPendingException(status);
        if (status.status() == IdentityOperationHttpModels.Status.FAILED_SAFE)
            throw new ru.rutcampustrack.academic.exception.ConflictException(
                    "Требуется новое авторизованное восстановление операции группы",
                    Map.of("operationId", operation.id().toString()));
        if (status.status() == IdentityOperationHttpModels.Status.REJECTED) {
            if (operation.rejection()
                    == ru.rutcampustrack.authoperations.grpc.IdentityOperationRejectionReason
                            .IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED)
                throw Status.PERMISSION_DENIED.asRuntimeException();
            if (operation.supersedes() != null)
                throw new ru.rutcampustrack.academic.exception.ConflictException(
                        "Требуется восстановление исходной операции группы",
                        Map.of("operationId", operation.supersedes().toString()));
            throw Status.FAILED_PRECONDITION.asRuntimeException();
        }
        return operation;
    }

    public static IdentityOperationHttpModels.Operation status(
            GroupAvailabilityStore.Operation operation) {
        var state =
                switch (operation.phase()) {
                    case SUCCEEDED -> IdentityOperationHttpModels.Status.SUCCEEDED;
                    case FAILED_SAFE -> IdentityOperationHttpModels.Status.FAILED_SAFE;
                    case REJECTED -> IdentityOperationHttpModels.Status.REJECTED;
                    default -> IdentityOperationHttpModels.Status.PENDING;
                };
        return new IdentityOperationHttpModels.Operation(
                operation.id(), state, state == IdentityOperationHttpModels.Status.PENDING ? 2 : 0);
    }

    private GroupAvailabilityCommand cycleCommand(
            GroupAvailabilityCommand.Action action,
            String digest,
            GroupAvailabilityCommand.Plan plan) {
        var revisions =
                java.util.stream.Stream.concat(plan.promote().stream(), plan.archive().stream())
                        .map(GroupAvailabilityCommand.Item::id)
                        .distinct()
                        .sorted()
                        .map(this::revision)
                        .toList();
        return new GroupAvailabilityCommand(
                action,
                plan.scope() == null ? 0 : plan.scope(),
                digest,
                null,
                null,
                plan,
                revisions);
    }

    private GroupAvailabilityCommand.GroupRevision revision(long group) {
        var rows =
                jdbc.queryForList(
                        "SELECT academic_revision FROM groups WHERE id=?", Long.class, group);
        if (rows.size() != 1) throw Status.NOT_FOUND.asRuntimeException();
        return new GroupAvailabilityCommand.GroupRevision(group, rows.getFirst());
    }

    private String digest(Object body) {
        try {
            return HexFormat.of()
                    .formatHex(
                            GroupAvailabilityStore.hash(canonical.writeValueAsBytes(body))
                                    .toByteArray());
        } catch (java.io.IOException invalid) {
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        }
    }

    private record RestoreIntent(long groupId) {}

    private record AutomaticIntent(long cycle, String previewVersion) {}

    private record RestorePreparation(boolean needed, GroupAvailabilityCommand command) {}
}
