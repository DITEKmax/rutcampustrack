package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SubjectDeletionService {
    private final SubjectDeletionAcademicData data;
    private final SubjectDeletionParticipants participants;
    private final AuthSubjectDeletionClient auth;
    private final SubjectDeletionCommands commands;
    private final SubjectDeletionCoordinator coordinator;

    public SubjectDeletionService(
            SubjectDeletionAcademicData data,
            SubjectDeletionParticipants participants,
            AuthSubjectDeletionClient auth,
            SubjectDeletionCommands commands,
            SubjectDeletionCoordinator coordinator) {
        this.data = data;
        this.participants = participants;
        this.auth = auth;
        this.commands = commands;
        this.coordinator = coordinator;
    }

    public Preview preview(long subject) {
        return response(
                snapshot(
                        previewRead(
                                SubjectDeletionUnavailableException.Phase.LOCAL_TARGET,
                                () -> data.target(subject))));
    }

    public Challenge challenge(long subject, ChallengeRequest request) {
        var target = data.target(subject);
        var snapshot = snapshot(target);
        if (!snapshot.digest().equals(request.previewDigest()))
            throw SubjectDeletionAcademicData.conflict();
        if (commands.get(request.operationId()) != null)
            throw SubjectDeletionAcademicData.conflict();
        return auth.challenge(target, request);
    }

    public Operation confirm(long subject, UUID key, ConfirmRequest request) {
        var existing = commands.get(request.operationId());
        UUID keyed = commands.byKey(key);
        if (existing != null || keyed != null) {
            if (existing == null || !existing.id().equals(keyed))
                throw SubjectDeletionAcademicData.conflict();
            sameRequest(existing, subject, key, request);
            return status(existing.id());
        }
        try {
            var target = data.target(subject);
            var snapshot = snapshot(target);
            if (!snapshot.digest().equals(request.previewDigest()))
                throw SubjectDeletionAcademicData.conflict();
            UUID proof = auth.verify(target, request);
            commands.accept(
                    request.operationId(),
                    key,
                    auth.check(target, request.operationId(), snapshot.digest(), proof),
                    confirmationHash(key, request),
                    snapshot);
        } catch (ResponseStatusException changedDuringReplay) {
            var winner = commands.get(request.operationId());
            if (winner == null) throw changedDuringReplay;
            sameRequest(winner, subject, key, request);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            var winner = commands.get(request.operationId());
            if (winner == null) throw race;
            sameRequest(winner, subject, key, request);
        }
        return status(request.operationId());
    }

    public Operation status(UUID id) {
        var operation = commands.get(id);
        if (operation == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Операция не найдена");
        var actor = data.actor(operation.group());
        if (actor.userId() != operation.actor()) throw SubjectDeletionAcademicData.denied();
        if (operation.phase() != Phase.COMPLETED && operation.phase() != Phase.CANCELLED) {
            coordinator.advance(id);
            operation = commands.get(id);
        }
        return new Operation(
                id,
                Long.toString(operation.subject()),
                Long.toString(operation.semester()),
                operation.phase(),
                operation.phase() != Phase.COMPLETED && operation.phase() != Phase.CANCELLED,
                operation.failure());
    }

    private void sameRequest(
            SubjectDeletionOperation operation, long subject, UUID key, ConfirmRequest request) {
        var actor = data.actor(operation.group());
        if (operation.subject() != subject
                || !operation.key().equals(key)
                || actor.userId() != operation.actor()
                || !actor.sessionId().equals(operation.sid())
                || !request.previewDigest().equals(operation.digest())
                || !operation.confirmationHash().equals(confirmationHash(key, request)))
            throw SubjectDeletionAcademicData.conflict();
    }

    private static String confirmationHash(UUID key, ConfirmRequest request) {
        return SubjectDeletionAcademicData.hash(
                key
                        + ":"
                        + request.operationId()
                        + ":"
                        + request.previewDigest()
                        + ":"
                        + request.challengeId()
                        + ":"
                        + request.code());
    }

    private SubjectDeletionSnapshot snapshot(SubjectDeletionAcademicData.Target target) {
        var academic =
                previewRead(
                        SubjectDeletionUnavailableException.Phase.LOCAL_SNAPSHOT,
                        () -> data.snapshot(target));
        UUID previewId = new UUID(0, 0);
        var base =
                SubjectDeletionCommand.newBuilder()
                        .setOperationId(previewId.toString())
                        .setSubjectId(target.subject())
                        .setSemesterId(target.semester())
                        .setGroupId(target.group())
                        .setActorId(target.actor().userId())
                        .setAction(SubjectDeletionAction.PREVIEW)
                        .build();
        var schedule = participants.schedule(base);
        var attendance =
                participants.attendance(
                        base.toBuilder().addAllLessons(schedule.getLessonsList()).build());
        String digest =
                SubjectDeletionAcademicData.hash(
                        target.subject()
                                + ":"
                                + target.semester()
                                + ":"
                                + target.group()
                                + ":"
                                + academic.digest()
                                + ":"
                                + schedule.getParticipantDigest()
                                + ":"
                                + attendance.getParticipantDigest()
                                + ":"
                                + java.util.Base64.getEncoder()
                                        .encodeToString(
                                                schedule.toBuilder()
                                                        .clearOperationId()
                                                        .clearAction()
                                                        .clearActorId()
                                                        .build()
                                                        .toByteArray()));
        return new SubjectDeletionSnapshot(target, academic, schedule, attendance, digest);
    }

    private static <T> T previewRead(
            SubjectDeletionUnavailableException.Phase phase, Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException error) {
            throw SubjectDeletionUnavailableException.local(phase, error);
        }
    }

    private static Preview response(SubjectDeletionSnapshot snapshot) {
        var target = snapshot.target();
        return new Preview(
                Long.toString(target.subject()),
                Long.toString(target.semester()),
                Long.toString(target.group()),
                target.name(),
                snapshot.digest(),
                snapshot.academic().rows(),
                snapshot.schedule().getAffectedRows(),
                snapshot.attendance().getAffectedRows(),
                snapshot.attendance().getMixedTickets());
    }
}
