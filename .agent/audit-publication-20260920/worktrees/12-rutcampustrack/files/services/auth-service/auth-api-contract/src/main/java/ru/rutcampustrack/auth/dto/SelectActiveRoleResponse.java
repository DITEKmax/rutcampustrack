package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

@Schema(name = "SelectActiveRoleResponse")
public record SelectActiveRoleResponse(
        @NotBlank String accessToken,
        @Positive long expiresIn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CurrentSessionResponse session
) {
    public SelectActiveRoleResponse {
        Objects.requireNonNull(accessToken, "accessToken");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (expiresIn <= 0) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
        Objects.requireNonNull(session, "session");
    }
    @Override
    public String toString() {
        return "SelectActiveRoleResponse[accessToken=<redacted>, expiresIn=" + expiresIn
                + ", session=" + session
                + "]";
    }
}
