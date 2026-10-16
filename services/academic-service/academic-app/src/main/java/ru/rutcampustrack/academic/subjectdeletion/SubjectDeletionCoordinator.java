package ru.rutcampustrack.academic.subjectdeletion;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.Phase;
import ru.rutcampustrack.subjectdeletion.grpc.SubjectDeletionAction;

import java.time.Clock;
import java.util.UUID;

/** Retry persisted phases; irreversible intent is never cancelled after a remote effect. */
@Service
public class SubjectDeletionCoordinator {
    private final SubjectDeletionCommands commands;
    private final SubjectDeletionParticipants participants;
    private final Clock clock;

    public SubjectDeletionCoordinator(
            SubjectDeletionCommands commands,
            SubjectDeletionParticipants participants,
            Clock clock) {
        this.commands = commands;
        this.participants = participants;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${subject-deletion.retry-delay-ms:1000}")
    @SchedulerLock(
            name = "academic-subject-deletion-retry",
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT1S")
    public void resumePending() {
        for (UUID operation : commands.pending()) advance(operation);
    }

    public synchronized void advance(UUID id) {
        SubjectDeletionOperation operation = commands.get(id);
        if (operation == null) return;
        try {
            if ((operation.phase() == Phase.PREPARING || operation.phase() == Phase.SEALED)
                    && operation.created().plusSeconds(300).isBefore(clock.instant())) {
                commands.release(id, "PREPARE_EXPIRED");
                operation = commands.get(id);
            }
            if (operation.phase() == Phase.RELEASING) {
                release(operation);
                return;
            }
            if (operation.phase() == Phase.PREPARING || operation.phase() == Phase.SEALED) {
                if (!operation.scheduleState().equals("SEALED")) {
                    participants.schedule(operation.command(true, SubjectDeletionAction.PREPARE));
                    commands.participant(id, true, "PREPARED");
                    participants.schedule(operation.command(true, SubjectDeletionAction.SEAL));
                    commands.participant(id, true, "SEALED");
                }
                if (!operation.attendanceState().equals("SEALED")) {
                    participants.attendance(
                            operation.command(false, SubjectDeletionAction.PREPARE));
                    commands.participant(id, false, "PREPARED");
                    participants.attendance(operation.command(false, SubjectDeletionAction.SEAL));
                    commands.participant(id, false, "SEALED");
                }
                if (!commands.beginCommit(id)) {
                    commands.release(id, "PROOF_OR_SCOPE_STALE");
                    return;
                }
                operation = commands.get(id);
            }
            if (operation.phase() == Phase.COMMITTING) {
                if (!operation.scheduleState().equals("DELETED")) {
                    participants.schedule(operation.command(true, SubjectDeletionAction.COMMIT));
                    commands.participant(id, true, "DELETED");
                }
                if (!operation.attendanceState().equals("DELETED")) {
                    participants.attendance(operation.command(false, SubjectDeletionAction.COMMIT));
                    commands.participant(id, false, "DELETED");
                }
                commands.finish(id);
            }
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode().value() == 409) commands.release(id, "SCOPE_CHANGED");
            else commands.failure(id);
        } catch (RuntimeException unavailable) {
            commands.failure(id);
        }
    }

    private void release(SubjectDeletionOperation operation) {
        boolean failed = false;
        // Attempt both releases even if the first participant is unavailable.
        try {
            participants.schedule(operation.command(true, SubjectDeletionAction.RELEASE));
            commands.participant(operation.id(), true, "RELEASED");
        } catch (RuntimeException unavailable) {
            failed = true;
        }
        try {
            participants.attendance(operation.command(false, SubjectDeletionAction.RELEASE));
            commands.participant(operation.id(), false, "RELEASED");
        } catch (RuntimeException unavailable) {
            failed = true;
        }
        if (failed) commands.failure(operation.id());
        else commands.cancelReleased(operation.id());
    }
}
