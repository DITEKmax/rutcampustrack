package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Schema(name = "TransferLessonResponse")
public record TransferLessonResponse(
        String operationId,
        String state,
        String occurrenceId,
        String sourceLessonId,
        String targetLessonId,
        String revision,
        boolean retryable,
        String errorCode,
        LocalDate targetDate
) {
    private static final Set<String> ERROR_CODES = Set.of(
            "TARGET_DATA_CONFLICT", "SOURCE_STATE_CONFLICT", "SCOPE_MISMATCH",
            "INVALID_SNAPSHOT", "DEPENDENCY_UNAVAILABLE", "ARCHIVED_SEMESTER");

    public TransferLessonResponse {
        Objects.requireNonNull(operationId, "operationId");
        UUID.fromString(operationId);
        if (!Objects.requireNonNull(state, "state").matches("PENDING|COMPLETED|ERROR")) {
            throw new IllegalArgumentException("state must be PENDING, COMPLETED, or ERROR");
        }
        requirePositiveDecimal(occurrenceId, "occurrenceId");
        requirePositiveDecimal(sourceLessonId, "sourceLessonId");
        requirePositiveDecimal(targetLessonId, "targetLessonId");
        requirePositiveDecimal(revision, "revision");
        Objects.requireNonNull(targetDate, "targetDate");
        if (state.equals("ERROR") == (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("errorCode is required only for ERROR transfers");
        }
        if (retryable != state.equals("PENDING")) {
            throw new IllegalArgumentException("only PENDING transfers are retryable");
        }
        if (errorCode != null && !ERROR_CODES.contains(errorCode)) {
            throw new IllegalArgumentException("errorCode is not an allowlisted transfer error");
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }
}
