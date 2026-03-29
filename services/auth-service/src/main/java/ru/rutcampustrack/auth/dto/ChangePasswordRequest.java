package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "currentPassword is required")
    String currentPassword,
    @NotBlank(message = "newPassword is required")
    @Size(min = 6, message = "newPassword must be at least 6 characters")
    String newPassword
) {}
