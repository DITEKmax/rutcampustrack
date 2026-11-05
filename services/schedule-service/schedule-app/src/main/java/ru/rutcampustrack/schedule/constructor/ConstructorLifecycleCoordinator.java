package ru.rutcampustrack.schedule.constructor;

import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.stereotype.Service;

import ru.rutcampustrack.constructor.grpc.ConstructorDeletionAction;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionCommand;
import ru.rutcampustrack.constructor.grpc.ConstructorParticipantReceipt;
import ru.rutcampustrack.schedule.contract.dto.item.ConstructorOperationResponse;
import ru.rutcampustrack.schedule.contract.dto.item.ConstructorProtectedReason;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemLifecyclePreviewResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Fresh public authorization and non-transactional, durable participant orchestration. */
@Service
public class ConstructorLifecycleCoordinator {
    private final ConstructorDeletionStore store;
    private final ConstructorParticipantClients participants;
    private final AcademicGrpcClient academic;
    private final RequestContext actor;
    private final ScheduleItemRepository items;
    private final ScheduleSemesterArchiveWriteFence archive;
    private final ConstructorWriteAdmission writeAdmission;

    public ConstructorLifecycleCoordinator(
            ConstructorDeletionStore store,
            ConstructorParticipantClients participants,
            AcademicGrpcClient academic,
            RequestContext actor,
            ScheduleItemRepository items,
            ScheduleSemesterArchiveWriteFence archive,
            ConstructorWriteAdmission writeAdmission) {
        this.store = store;
        this.participants = participants;
        this.academic = academic;
        this.actor = actor;
        this.items = items;
        this.archive = archive;
        this.writeAdmission = writeAdmission;
    }

    private record Preview(
            ConstructorDeletionStore.Snapshot local,
            ConstructorDeletionCommand attendance,
            ConstructorDeletionCommand academic,
            String revision,
            long protectedCount,
            long removableCount,
            List<ConstructorProtectedReason> reasons,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {}

    public ScheduleItemLifecyclePreviewResponse preview(long item) {
        var preview = inspect(item);
        return new ScheduleItemLifecyclePreviewResponse(
                preview.revision(),
                0,
                preview.removableCount(),
                0,
                0,
                "DELETE_UNUSED_SERIES",
                preview.protectedCount() == 0,
                preview.protectedCount(),
                preview.removableCount(),
                preview.reasons());
    }

    public ConstructorOperationResponse delete(long item, UUID key, String revision) {
        long user = requireUser();
        if (key == null || revision == null || !revision.matches("[0-9a-f]{64}"))
            throw new ConstructorLifecycleException(
                    400, "INVALID_CONSTRUCTOR_COMMAND", "Нужны ключ запроса и revision расчёта");
        var existing = store.byKey(user, key);
        byte[] requestHash = requestHash(item, key, revision);
        if (existing != null) {
            // Lookup is deliberately before the missing business-item lookup,
            // but its stored group is freshly authorized before any readback.
            authorize(existing.groupId());
            if (existing.itemId() != item
                    || !MessageDigest.isEqual(requestHash, existing.requestHash()))
                throw ConstructorLifecycleException.conflict("IDEMPOTENCY_PAYLOAD_MISMATCH");
            return publicResult(resume(existing.id(), 1500, actor.getInternalToken()));
        }
        var preview = inspect(item);
        store.assertNotLegacyLifecycleReplay(user, key);
        if (!revision.equals(preview.revision()))
            throw ConstructorLifecycleException.conflict("PREVIEW_STALE");
        if (preview.protectedCount() > 0)
            throw ConstructorLifecycleException.conflict("CONSTRUCTOR_HISTORY_PROTECTED");
        writeAdmission.requireEnabled();
        UUID operation = UUID.randomUUID();
        var attendance = accepted(preview.attendance(), operation, user, requestHash);
        var academicCommand = accepted(preview.academic(), operation, user, requestHash);
        // This separate proxied local transaction MUST COMMIT before the first
        // participant PREPARE. Recovery uses this immutable source authority.
        var accepted =
                store.accept(
                        preview.local(),
                        operation,
                        user,
                        key,
                        requestHash,
                        revision,
                        attendance,
                        academicCommand,
                        preview.prepared());
        if (accepted.itemId() != item
                || !MessageDigest.isEqual(accepted.requestHash(), requestHash))
            throw ConstructorLifecycleException.conflict("IDEMPOTENCY_PAYLOAD_MISMATCH");
        return publicResult(resume(accepted.id(), 1500, actor.getInternalToken()));
    }

    public ConstructorOperationResponse operation(UUID id) {
        requireUser();
        var operation = store.byId(id);
        if (operation == null)
            throw new ConstructorLifecycleException(
                    404, "CONSTRUCTOR_OPERATION_NOT_FOUND", "Операция не найдена");
        authorize(operation.groupId());
        return operation.response();
    }

    public void assertCreateNotDeleted(long group, UUID key, byte[] hash) {
        long user = requireUser();
        authorize(group);
        store.assertCreateNotDeleted(user, key, hash, group);
    }

    public void authorizeInactiveRead(long group) {
        requireUser();
        authorize(group);
    }

    private Preview inspect(long item) {
        requireUser();
        var target =
                items.findById(item)
                        .orElseThrow(
                                () ->
                                        new ConstructorLifecycleException(
                                                404,
                                                "CONSTRUCTOR_SERIES_NOT_FOUND",
                                                "Шаблон не найден"));
        authorize(target.getGroupId());
        var semester = academic.getActiveSemester();
        if (semester == null || semester.getId() != target.getSemesterId())
            throw ConstructorLifecycleException.conflict("CONSTRUCTOR_SEMESTER_NOT_ACTIVE");
        var prepared = archive.prepareBusinessWrite(target.getSemesterId());
        var local = store.snapshot(item);
        UUID previewId = UUID.randomUUID();
        var command =
                ConstructorDeletionCommand.newBuilder()
                        .setOperationId(previewId.toString())
                        .setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW)
                        .setOriginalActorId(requireUser())
                        .setScope(local.scope())
                        .setScopeHash(
                                ByteString.copyFrom(
                                        ConstructorHashes.sha256(local.scope().toByteArray())))
                        .setRequestHash(
                                ByteString.copyFrom(
                                        ConstructorHashes.sha256(
                                                ("DELETE_UNUSED_SERIES|PREVIEW|" + item)
                                                        .getBytes(StandardCharsets.UTF_8))))
                        .build();
        ConstructorParticipantReceipt attendance;
        ConstructorParticipantReceipt homework;
        try {
            attendance = participants.attendance(command, actor.getInternalToken());
            homework = participants.academic(command, actor.getInternalToken());
        } catch (StatusRuntimeException unavailable) {
            throw new ConstructorLifecycleException(
                    503,
                    "CONSTRUCTOR_PARTICIPANT_UNAVAILABLE",
                    "Не удалось проверить историю пары");
        }
        Set<Long> protectedIds = new HashSet<>(local.protectedOccurrenceIds());
        protectedIds.addAll(validateReceipt(command, attendance, Set.of("PREVIEW")));
        protectedIds.addAll(validateReceipt(command, homework, Set.of("PREVIEW")));
        var after = store.snapshot(item);
        if (!local.manifest().equals(after.manifest()) || !local.scope().equals(after.scope()))
            throw ConstructorLifecycleException.conflict("PREVIEW_STALE");
        byte[] revision =
                ConstructorHashes.json(
                        Map.of(
                                "kind",
                                "DELETE_UNUSED_SERIES",
                                "scope",
                                ConstructorHashes.hex(command.getScopeHash().toByteArray()),
                                "attendance",
                                ConstructorHashes.hex(
                                        attendance.getParticipantDigest().toByteArray()),
                                "academic",
                                ConstructorHashes.hex(
                                        homework.getParticipantDigest().toByteArray())));
        var reasons = new LinkedHashMap<String, Long>();
        local.reasons().forEach(reason -> reasons.merge(reason.code(), reason.count(), Math::max));
        for (var receipt : List.of(attendance, homework))
            receipt.getProtectedReasonsList()
                    .forEach(
                            reason ->
                                    reasons.merge(reason.getCode(), reason.getCount(), Math::max));
        return new Preview(
                local,
                command.toBuilder().setParticipantDigest(attendance.getParticipantDigest()).build(),
                command.toBuilder().setParticipantDigest(homework.getParticipantDigest()).build(),
                ConstructorHashes.hex(revision),
                protectedIds.size(),
                local.scope().getLessonsList().stream()
                        .map(lesson -> lesson.getOccurrenceId())
                        .distinct()
                        .filter(occurrence -> !protectedIds.contains(occurrence))
                        .count(),
                reasons.entrySet().stream()
                        .map(
                                entry ->
                                        new ConstructorProtectedReason(
                                                entry.getKey(), entry.getValue()))
                        .toList(),
                prepared);
    }

    /**
     * No user context is accessed here: accepted work survives expiry/revocation and lost replies.
     */
    public ConstructorDeletionStore.Operation resume(UUID id, long budgetMillis) {
        return resume(id, budgetMillis, null);
    }

    private ConstructorDeletionStore.Operation resume(
            UUID id, long budgetMillis, String signedActor) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        for (int step = 0; step < 4; step++) {
            var operation = store.byId(id);
            if (operation == null)
                throw new IllegalStateException("Accepted constructor operation missing");
            try {
                switch (operation.state()) {
                    case "PREPARING" -> {
                        apply(
                                operation,
                                ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE,
                                Set.of("PREPARED", "SEALED"),
                                signedActor);
                        store.state(id, "PREPARING", "SEALED", null);
                    }
                    case "SEALED" -> {
                        apply(
                                operation,
                                ConstructorDeletionAction.CONSTRUCTOR_DELETION_SEAL,
                                Set.of("SEALED"),
                                null);
                        // Irreversible intent commits BEFORE FIRST participant COMMIT.
                        store.state(id, "SEALED", "COMMITTING", null);
                    }
                    case "COMMITTING" -> {
                        apply(
                                operation,
                                ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT,
                                Set.of("DELETED"),
                                null);
                        store.complete(id);
                    }
                    case "RELEASING" -> {
                        apply(
                                operation,
                                ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE,
                                Set.of("RELEASED"),
                                null);
                        store.state(id, "RELEASING", "RELEASED", operation.errorCode());
                    }
                    case "COMPLETED", "RELEASED" -> {
                        return operation;
                    }
                    default -> throw new IllegalStateException("Unknown constructor state");
                }
            } catch (RuntimeException failed) {
                var current = store.byId(id);
                boolean rejected =
                        failed instanceof StatusRuntimeException rpc
                                && Set.of(
                                                Status.Code.FAILED_PRECONDITION,
                                                Status.Code.INVALID_ARGUMENT,
                                                Status.Code.PERMISSION_DENIED)
                                        .contains(rpc.getStatus().getCode());
                if (rejected && Set.of("PREPARING", "SEALED").contains(current.state())) {
                    store.state(id, current.state(), "RELEASING", "CONSTRUCTOR_HISTORY_PROTECTED");
                }
                // COMMITTING is never released, even if a COMMIT reply was lost.
                store.retry(id);
                return store.byId(id);
            }
            if (System.nanoTime() >= deadline) return store.byId(id);
        }
        return store.byId(id);
    }

    private void apply(
            ConstructorDeletionStore.Operation operation,
            ConstructorDeletionAction action,
            Set<String> states,
            String signedActor) {
        var attendance = operation.attendance().toBuilder().setAction(action).build();
        var homework = operation.academic().toBuilder().setAction(action).build();
        validateReceipt(attendance, participants.attendance(attendance, signedActor), states);
        validateReceipt(homework, participants.academic(homework, signedActor), states);
    }

    private static Set<Long> validateReceipt(
            ConstructorDeletionCommand command,
            ConstructorParticipantReceipt reply,
            Set<String> states) {
        Set<Long> supplied =
                new HashSet<>(
                        command.getScope().getLessonsList().stream()
                                .map(lesson -> lesson.getOccurrenceId())
                                .toList());
        Set<Long> protectedIds = new HashSet<>(reply.getProtectedOccurrenceIdsList());
        boolean sorted =
                reply.getProtectedOccurrenceIdsList()
                        .equals(protectedIds.stream().sorted().toList());
        if (!command.getOperationId().equals(reply.getOperationId())
                || !command.getRequestHash().equals(reply.getRequestHash())
                || !command.getScopeHash().equals(reply.getScopeHash())
                || reply.getParticipantDigest().size() != 32
                || command.getParticipantDigest().size() == 32
                        && !command.getParticipantDigest().equals(reply.getParticipantDigest())
                || !states.contains(reply.getState())
                || !sorted
                || !supplied.containsAll(protectedIds)
                || protectedIds.size() != reply.getProtectedCount())
            throw new ConstructorLifecycleException(
                    503,
                    "CONSTRUCTOR_PROTOCOL_MISMATCH",
                    "Не удалось подтвердить результат проверки");
        return protectedIds;
    }

    private static ConstructorDeletionCommand accepted(
            ConstructorDeletionCommand preview, UUID id, long user, byte[] hash) {
        return preview.toBuilder()
                .setOperationId(id.toString())
                .setOriginalActorId(user)
                .setRequestHash(ByteString.copyFrom(hash))
                .setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE)
                .build();
    }

    private static ConstructorOperationResponse publicResult(
            ConstructorDeletionStore.Operation operation) {
        if ("RELEASED".equals(operation.state()))
            throw ConstructorLifecycleException.conflict(
                    operation.errorCode() == null ? "PREVIEW_STALE" : operation.errorCode());
        return operation.response();
    }

    private void authorize(long group) {
        long user = requireUser();
        if (actor.getRole() != UserRole.ADMIN
                && (!actor.isHeadman() || !academic.isHeadman(user, group)))
            throw new AccessDeniedException("Управлять конструктором может староста своей группы");
        var checked = academic.validateGroup(group);
        if (checked == null || checked.getId() != group || !checked.getIsActive())
            throw new AccessDeniedException("Группа недоступна");
    }

    private long requireUser() {
        if (actor.getUserId() == null || actor.getUserId() <= 0)
            throw new AccessDeniedException("Нужна действующая сессия");
        return actor.getUserId();
    }

    private static byte[] requestHash(long item, UUID key, String revision) {
        return ConstructorHashes.sha256(
                ("DELETE_UNUSED_SERIES|" + item + "|" + key + "|" + revision)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
