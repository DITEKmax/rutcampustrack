package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Password proof bound by the caller to one frozen semester-deletion preview. */
public record ConfirmSemesterDeletionRequest(
        @NotBlank String internalToken,
        @NotBlank String password,
        @Positive long semesterId,
        @NotNull UUID operationId,
        @NotBlank String previewDigest
) {
    @Override
    public String toString() {
        return "ConfirmSemesterDeletionRequest[redacted]";
    }
}
