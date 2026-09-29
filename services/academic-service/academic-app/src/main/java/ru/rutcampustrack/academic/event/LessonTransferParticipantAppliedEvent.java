package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.rutcampustrack.shared.events.EventVersion;

/** Durable per-batch acknowledgement for Schedule's lesson-transfer operation. */
@EventVersion(1)
public final class LessonTransferParticipantAppliedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("operation_id") String operationId,
            String participant,
            @JsonProperty("batch_index") Integer batchIndex,
            String result,
            @JsonProperty("error_code") String errorCode,
            boolean retryable,
            @JsonProperty("payload_hash") String payloadHash,
            @JsonProperty("source_lesson_id") Long sourceLessonId,
            @JsonProperty("target_lesson_id") Long targetLessonId
    ) { }

    public LessonTransferParticipantAppliedEvent(Object source,
                                                  String operationId,
                                                  int batchIndex,
                                                  String result,
                                                  String errorCode,
                                                  boolean retryable,
                                                  String payloadHash,
                                                  long sourceLessonId,
                                                  long targetLessonId) {
        super(source, "lesson.transfer.participant.applied", new Payload(
                operationId, "ACADEMIC", batchIndex, result, errorCode, retryable,
                payloadHash, sourceLessonId, targetLessonId));
    }
}
