package ru.rutcampustrack.academic.groupdeletion;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ru.rutcampustrack.academic.contract.dto.group.GroupDeletionModels.Phase;
import ru.rutcampustrack.groupdeletion.grpc.*;

import java.util.UUID;

/**
 * Commit intent can be released only with Auth's durable zero-effect rejection; committed effects
 * only complete.
 */
@Service
public class GroupDeletionCoordinator {
    private final GroupDeletionStore store;
    private final GroupDeletionParticipants participants;
    private final ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer catalog;

    public GroupDeletionCoordinator(
            GroupDeletionStore store,
            GroupDeletionParticipants participants,
            ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer catalog) {
        this.store = store;
        this.participants = participants;
        this.catalog = catalog;
    }

    @Scheduled(fixedDelayString = "${group-deletion.retry-delay-ms:2000}")
    @SchedulerLock(
            name = "academic-group-deletion-retry",
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT1S")
    public void resume() {
        for (UUID id : store.pending()) advance(id);
    }

    public synchronized void advance(UUID id) {
        var operation = store.get(id);
        if (operation == null
                || operation.phase() == Phase.COMPLETED
                || operation.phase() == Phase.CANCELLED) return;
        try {
            var manifest = GroupDeletionManifest.parseFrom(operation.receipts());
            if (operation.phase() == Phase.RELEASING) {
                apply(operation, manifest, GroupDeletionAction.RELEASE);
                store.cancel(id);
                return;
            }
            if (operation.phase() == Phase.PREPARING) {
                apply(operation, manifest, GroupDeletionAction.PREPARE);
                apply(operation, manifest, GroupDeletionAction.SEAL);
                store.beginCommit(id);
                operation = store.get(id);
            }
            if (operation.phase() == Phase.COMMITTING) {
                if (!apply(operation, manifest, GroupDeletionAction.COMMIT)) {
                    store.rejectCommit(id);
                    return;
                }
                // Auth has published its grant removal. Catch up its existing restricted feed
                // before local completion makes registry/dashboard readers available again.
                catalog.poll();
                store.finish(id);
            }
        } catch (org.springframework.web.server.ResponseStatusException stale) {
            if (stale.getStatusCode().value() == 409 && operation.phase() == Phase.PREPARING)
                store.release(id, "STALE_PREVIEW");
            else store.failure(id);
        } catch (Exception unavailable) {
            store.failure(id);
        }
    }

    private boolean apply(
            GroupDeletionStore.Saved operation,
            GroupDeletionManifest manifest,
            GroupDeletionAction action) {
        if (manifest.getParticipantsCount() != 3)
            throw new IllegalStateException("Invalid saved participant manifest");
        for (int i = 0; i < 3; i++) {
            var saved = manifest.getParticipants(i);
            var command =
                    GroupDeletionCommand.newBuilder()
                            .setOperationId(operation.id().toString())
                            .setGroupId(operation.group())
                            .setActorId(operation.actor())
                            .setParticipantDigest(saved.getParticipantDigest())
                            .setPreviewDigest(operation.digest())
                            .setAction(action)
                            .setPasswordReceipt(
                                    i == 0
                                            ? com.google.protobuf.ByteString.copyFrom(
                                                    operation.proof())
                                            : com.google.protobuf.ByteString.EMPTY)
                            .addAllLessons(
                                    i == 0
                                            ? java.util.List.of()
                                            : manifest.getParticipants(1).getLessonsList())
                            .build();
            var receipt = participants.apply(i, command);
            if (receipt.getCommitRejected()) return false;
        }
        return true;
    }
}
