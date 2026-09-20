package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Schema(name = "TransferLessonRequest")
public record TransferLessonRequest(
        @NotNull LocalDate targetDate,
        @NotNull @Min(1) @Max(8) Integer targetLessonNumber,
        @Schema(nullable = true) LocalTime targetStartTime,
        @Schema(nullable = true) LocalTime targetEndTime,
        @Schema(nullable = true) String targetRoom,
        @NotNull @Pattern(regexp = "^[1-9][0-9]*$") String expectedRevision,
        @NotNull UUID requestKey
) {
    public TransferLessonRequest {
        Objects.requireNonNull(targetDate, "targetDate");
        Objects.requireNonNull(targetLessonNumber, "targetLessonNumber");
        if (targetLessonNumber < 1 || targetLessonNumber > 8) {
            throw new IllegalArgumentException("targetLessonNumber must be between 1 and 8");
        }
        if (targetStartTime != null && targetEndTime != null
                && !targetEndTime.isAfter(targetStartTime)) {
            throw new IllegalArgumentException("targetEndTime must be after targetStartTime");
        }
        requirePositiveDecimal(expectedRevision, "expectedRevision");
        Objects.requireNonNull(requestKey, "requestKey");
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }
}
