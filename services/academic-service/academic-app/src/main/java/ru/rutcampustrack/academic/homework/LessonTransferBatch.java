package ru.rutcampustrack.academic.homework;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Strictly validated snapshot of one bounded immutable Schedule transfer batch. */
public record LessonTransferBatch(
        UUID eventId,
        UUID operationId,
        UUID requestKey,
        long actorId,
        String operationHash,
        long occurrenceId,
        long groupId,
        long subjectId,
        long semesterId,
        long expectedOccurrenceRevision,
        long sourceLessonId,
        long targetLessonId,
        LocalDate sourceDate,
        int sourceLessonNumber,
        LocalDate targetDate,
        int targetLessonNumber,
        int batchIndex,
        int batchCount,
        List<Binding> bindings,
        String batchHash) {

    public LessonTransferBatch {
        bindings = List.copyOf(bindings);
    }

    public record Binding(long bindingId, long actorId, UUID requestKey, Long homeworkId,
                          String payloadHash, String state, long revision) { }
}
