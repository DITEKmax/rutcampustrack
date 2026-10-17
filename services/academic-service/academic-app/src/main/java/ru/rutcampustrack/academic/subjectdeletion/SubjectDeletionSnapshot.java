package ru.rutcampustrack.academic.subjectdeletion;

import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.util.UUID;

record SubjectDeletionSnapshot(
        SubjectDeletionAcademicData.Target target,
        SubjectDeletionAcademicData.Snapshot academic,
        SubjectDeletionReceipt schedule,
        SubjectDeletionReceipt attendance,
        String digest) {
    SubjectDeletionCommand command(
            UUID operation, String participantDigest, SubjectDeletionAction action) {
        return SubjectDeletionCommand.newBuilder()
                .setOperationId(operation.toString())
                .setSubjectId(target.subject())
                .setSemesterId(target.semester())
                .setGroupId(target.group())
                .setActorId(target.actor().userId())
                .setParticipantDigest(participantDigest)
                .setManifestDigest(digest)
                .setAction(action)
                .addAllLessons(schedule.getLessonsList())
                .build();
    }
}
