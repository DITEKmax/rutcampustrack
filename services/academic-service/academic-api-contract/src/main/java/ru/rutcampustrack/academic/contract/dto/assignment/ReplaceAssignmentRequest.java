package ru.rutcampustrack.academic.contract.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Schema(name = "ReplaceAssignmentRequest")
public record ReplaceAssignmentRequest(
        @NotBlank @Pattern(regexp = "^[1-9][0-9]*$")
        @Schema(description = "ID преподавателя, который примет назначение", example = "501")
        String replacementTeacherId,
        @NotNull
        @Schema(description = "Дата начала нового назначения, без нормализации", example = "2026-10-01")
        LocalDate effectiveFrom,
        @NotNull
        @Schema(description = "UUID для повтора этой же операции", example = "b9c6d13f-3326-4333-b59d-e7555f5ffeca")
        UUID requestKey
) {
    public ReplaceAssignmentRequest {
        Objects.requireNonNull(replacementTeacherId, "replacementTeacherId");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(requestKey, "requestKey");
        if (!replacementTeacherId.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("replacementTeacherId must be a positive decimal string");
        }
    }
}
