package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetCompleteRequest(
        @NotBlank @Size(max = 64) String resetTicket,
        @NotBlank @Size(max = 256) String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetCompleteRequest[resetTicket=<redacted>, newPassword=<redacted>]";
    }
}
