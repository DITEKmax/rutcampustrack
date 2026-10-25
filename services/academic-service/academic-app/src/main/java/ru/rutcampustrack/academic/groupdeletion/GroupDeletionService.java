package ru.rutcampustrack.academic.groupdeletion;

import org.springframework.stereotype.Service;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.group.GroupDeletionModels.*;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.subjectdeletion.SubjectDeletionAcademicData;
import ru.rutcampustrack.groupdeletion.grpc.*;

import java.util.*;

@Service
public class GroupDeletionService {
    private final AcademicAdminAccess admin;
    private final RequestContext context;
    private final GroupDeletionStore store;
    private final GroupDeletionParticipants participants;
    private final AuthGroupDeletionClient auth;
    private final GroupDeletionCoordinator coordinator;

    public GroupDeletionService(
            AcademicAdminAccess admin,
            RequestContext context,
            GroupDeletionStore store,
            GroupDeletionParticipants participants,
            AuthGroupDeletionClient auth,
            GroupDeletionCoordinator coordinator) {
        this.admin = admin;
        this.context = context;
        this.store = store;
        this.participants = participants;
        this.auth = auth;
        this.coordinator = coordinator;
    }

    private record Snapshot(
            GroupDeletionStore.Local local,
            GroupDeletionManifest participants,
            String digest,
            Map<String, Long> counts) {}

    public Preview preview(long group) {
        authorize();
        var s = snapshot(group);
        return new Preview(Long.toString(group), s.local().name(), s.digest(), s.counts());
    }

    public Operation confirm(long group, UUID key, ConfirmRequest request) {
        authorize();
        var existing = store.get(key);
        if (existing != null) {
            if (existing.phase() == Phase.CANCELLED && "NOT_ACCEPTED".equals(existing.failure()))
                throw GroupDeletionStore.error(409, "group_deletion_attempt_cancelled");
            same(existing, group, request.previewDigest());
            return acceptedResponse(key);
        }
        try {
            var s = snapshot(group);
            if (!s.digest().equals(request.previewDigest()))
                throw GroupDeletionStore.error(409, "group_deletion_stale_preview");
            var proof = auth.confirm(group, key, s.digest(), request.password());
            store.accept(key, s.local(), s.digest(), s.participants(), proof, s.counts());
        } catch (org.springframework.dao.DataIntegrityViolationException
                | org.springframework.web.server.ResponseStatusException race) {
            var winner = store.get(key);
            if (winner == null) throw race;
            same(winner, group, request.previewDigest());
        }
        return acceptedResponse(key);
    }

    public Operation resolve(long group, UUID id) {
        authorize();
        if (group <= 0) throw GroupDeletionStore.error(400, "invalid_group_id");
        var operation = store.cancelUnaccepted(id, group);
        // No receipt body, ownership or existence detail is disclosed across either scope.
        if (operation.actor() != context.getClaims().userId() || operation.group() != group)
            throw GroupDeletionStore.error(404, "group_deletion_operation_not_found");
        return acceptedResponse(id);
    }

    public Operation status(UUID id) {
        authorize();
        return acceptedResponse(id);
    }

    // This request was already admitted before acceptance. Do not turn its durable outcome into
    // a second authorization error; the coordinator separately checks the original live proof.
    private Operation acceptedResponse(UUID id) {
        var operation = store.get(id);
        if (operation == null)
            throw GroupDeletionStore.error(404, "group_deletion_operation_not_found");
        if (context.getClaims().userId() != operation.actor())
            throw GroupDeletionStore.error(403, "group_deletion_other_actor");
        coordinator.advance(id);
        operation = store.get(id);
        return new Operation(
                id,
                Long.toString(operation.group()),
                operation.phase(),
                operation.phase() != Phase.COMPLETED && operation.phase() != Phase.CANCELLED,
                operation.counts(),
                operation.failure());
    }

    private void same(GroupDeletionStore.Saved saved, long group, String digest) {
        var actor = context.getClaims();
        if (saved.group() != group
                || saved.actor() != actor.userId()
                || !saved.sid().equals(actor.sessionId())
                || saved.sv() != actor.sessionVersion()
                || saved.rv() != actor.rolesVersion()
                || !saved.digest().equals(digest))
            throw GroupDeletionStore.error(409, "group_deletion_key_conflict");
    }

    private void authorize() {
        admin.require("GROUPS_MANAGE", "DATA_DELETE");
        var actor = context.getClaims();
        if (actor == null
                || actor.sessionId() == null
                || actor.sessionVersion() <= 0
                || actor.rolesVersion() <= 0)
            throw GroupDeletionStore.error(401, "invalid_session");
    }

    private Snapshot snapshot(long group) {
        var local = store.snapshot(group);
        var manifest = GroupDeletionManifest.newBuilder();
        Map<String, Long> counts = new TreeMap<>(local.counts());
        StringBuilder digest =
                new StringBuilder("group-delete-v1:")
                        .append(group)
                        .append(':')
                        .append(local.digest());
        for (int i = 0; i < 3; i++) {
            var command =
                    GroupDeletionCommand.newBuilder()
                            .setOperationId(new UUID(0, 0).toString())
                            .setGroupId(group)
                            .setActorId(context.getClaims().userId())
                            .setAction(GroupDeletionAction.PREVIEW)
                            .build();
            if (i == 2)
                command =
                        command.toBuilder()
                                .addAllLessons(manifest.getParticipants(1).getLessonsList())
                                .build();
            var receipt = participants.apply(i, command);
            manifest.addParticipants(receipt);
            digest.append(':').append(receipt.getParticipantDigest());
            if (i == 0) counts.putAll(receipt.getCountsMap());
            else {
                String prefix = i == 1 ? "schedule." : "attendance.";
                receipt.getCountsMap().forEach((key, value) -> counts.put(prefix + key, value));
                counts.put(
                        i == 1 ? "scheduleRows" : "attendanceRows",
                        receipt.getCountsMap().values().stream().mapToLong(Long::longValue).sum());
            }
        }
        return new Snapshot(
                local,
                manifest.build(),
                SubjectDeletionAcademicData.hash(digest.toString()),
                Map.copyOf(counts));
    }
}
