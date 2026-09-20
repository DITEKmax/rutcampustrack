package ru.rutcampustrack.academic.contract.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Objects;

@Schema(name = "CloseAssignmentRequest")
public record CloseAssignmentRequest(
        @NotNull LocalDate validUntilExclusive
) {
    public CloseAssignmentRequest {
        Objects.requireNonNull(validUntilExclusive, "validUntilExclusive");
    }
}
