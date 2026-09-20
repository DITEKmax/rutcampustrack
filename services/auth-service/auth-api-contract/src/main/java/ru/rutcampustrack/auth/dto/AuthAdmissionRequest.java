package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@Schema(name = "AuthAdmissionRequest")
public record AuthAdmissionRequest(
        @NotBlank String accessToken
) {
    public AuthAdmissionRequest {
        Objects.requireNonNull(accessToken, "accessToken");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }
    @Override
    public String toString() {
        return "AuthAdmissionRequest[accessToken=<redacted>]";
    }
}
