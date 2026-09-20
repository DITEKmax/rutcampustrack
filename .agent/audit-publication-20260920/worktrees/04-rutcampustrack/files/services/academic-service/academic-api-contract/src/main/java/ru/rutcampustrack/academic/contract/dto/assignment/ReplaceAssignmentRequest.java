package ru.rutcampustrack.academic.contract.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.Objects;

@Schema(name = "ReplaceAssignmentRequest")
public record ReplaceAssignmentRequest(
        @NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String replacementTeacherId,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String lessonType,
        @NotNull LocalDate validFrom,
        @Schema(nullable = true) LocalDate validUntilExclusive
) {
    public ReplaceAssignmentRequest {
        requirePositiveDecimal(replacementTeacherId, "replacementTeacherId");
        requireUppercase(lessonType, "lessonType");
        Objects.requireNonNull(validFrom, "validFrom");
        if (validUntilExclusive != null && !validUntilExclusive.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntilExclusive must be after validFrom");
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }

    private static void requireUppercase(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
}
