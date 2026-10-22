package ru.rutcampustrack.academic.subjectdeletion;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.Phase;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.time.Instant;
import java.util.UUID;

record SubjectDeletionOperation(
        UUID id,
        UUID key,
        long subject,
        long semester,
        long group,
        long actor,
        UUID sid,
        String digest,
        String confirmationHash,
        String academicDigest,
        String scheduleDigest,
        String attendanceDigest,
        byte[] manifest,
        Phase phase,
        String scheduleState,
        String attendanceState,
        Instant created,
        String failure,
        UUID proof) {
    SubjectDeletionCommand command(boolean schedule, SubjectDeletionAction action) {
        try {
            return SubjectDeletionCommand.parseFrom(manifest).toBuilder()
                    .setAction(action)
                    .setParticipantDigest(schedule ? scheduleDigest : attendanceDigest)
                    .build();
        } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
            throw SubjectDeletionAcademicData.conflict();
        }
    }
}
