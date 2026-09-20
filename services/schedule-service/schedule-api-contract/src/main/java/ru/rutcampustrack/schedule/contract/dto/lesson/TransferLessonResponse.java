package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(name = "TransferLessonResponse")
public record TransferLessonResponse(
        String occurrenceId,
        String sourceLessonId,
        String targetLessonId,
        String revision
) {
    public TransferLessonResponse {
        requirePositiveDecimal(occurrenceId, "occurrenceId");
        requirePositiveDecimal(sourceLessonId, "sourceLessonId");
        requirePositiveDecimal(targetLessonId, "targetLessonId");
        requirePositiveDecimal(revision, "revision");
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }
}
