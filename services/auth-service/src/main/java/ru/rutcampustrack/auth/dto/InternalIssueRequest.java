package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Claims passed by api-gateway to {@code POST /internal/issue-internal-jwt}.
 * Auth-service signs an Internal JWT with these claims and returns it.
 */
public record InternalIssueRequest(
        @NotNull @Positive Long userId,
        @NotBlank String role,
        Long groupId,
        boolean isHeadman
) {
}
