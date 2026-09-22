package ru.rutcampustrack.attendance.contract.dto.headman;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Whole-ticket decision; the app service applies the conditional rejection rule. */
public record HeadmanRequestDecisionRequest(
        @NotNull HeadmanRequestDecision decision,
        @Size(max = 1000) String reason
) {
}
