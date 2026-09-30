package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetVerifyRequest(
        @NotBlank @Size(max = 64) String challengeId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    @Override
    public String toString() {
        return "PasswordResetVerifyRequest[challengeId=<redacted>, code=<redacted>]";
    }
}
