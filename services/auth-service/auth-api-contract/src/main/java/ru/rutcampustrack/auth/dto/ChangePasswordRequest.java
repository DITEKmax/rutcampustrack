package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "currentPassword is required")
    String currentPassword,
    @NotBlank(message = "newPassword is required")
    String newPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=<redacted>, newPassword=<redacted>]";
    }
}
