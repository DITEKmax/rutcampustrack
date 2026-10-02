package ru.rutcampustrack.academic.homework;

import ru.rutcampustrack.academic.contract.dto.homework.HomeworkSnapshot;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.schedule.grpc.HomeworkEditIdentity;
import ru.rutcampustrack.schedule.grpc.MoveHomeworkBindingRequest;
import com.google.protobuf.ByteString;
import java.util.UUID;

/** Durable command tuple, containing no user token or mutable authorization data. */
public record HomeworkEditOperation(UUID operationId, long homeworkId, long bindingId, long actorId,
                                    UUID requestKey, byte[] hash, long groupId, long subjectId, long semesterId,
                                    long expectedRevision, long expectedBindingRevision, UpdateHomeworkRequest request,
                                    HomeworkSnapshot before, HomeworkSnapshot desired,
                                    Long targetOccurrence, Long targetLessonRevision,
                                    String state, String outcome, HomeworkSnapshot result, Long resultRevision) {
    public HomeworkEditIdentity identity() {
        return HomeworkEditIdentity.newBuilder().setOperationId(operationId.toString()).setBindingId(bindingId)
                .setHomeworkId(homeworkId).setActorId(actorId).setRequestKey(requestKey.toString())
                .setCommandHash(ByteString.copyFrom(hash)).setGroupId(groupId).setSubjectId(subjectId).setSemesterId(semesterId).build();
    }
    public MoveHomeworkBindingRequest move() {
        return MoveHomeworkBindingRequest.newBuilder().setIdentity(identity()).setExpectedBindingRevision(expectedBindingRevision)
                .setBindingMode(desired.bindingMode().name()).setDate(desired.lessonDate().toString())
                .setLessonNumber(desired.lessonNumber() == null ? 0 : desired.lessonNumber())
                .setTargetOccurrenceId(targetOccurrence == null ? 0 : targetOccurrence)
                .setExpectedLessonRevision(targetLessonRevision == null ? 0 : targetLessonRevision).build();
    }
}
