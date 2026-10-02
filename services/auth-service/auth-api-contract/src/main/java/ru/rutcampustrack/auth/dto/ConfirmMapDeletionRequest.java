package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Password proof whose target/operation/preview binding is retained by the caller. */
public record ConfirmMapDeletionRequest(
        @NotBlank String internalToken,
        @NotBlank String password,
        @NotNull TargetType targetType,
        @Positive long targetId,
        @NotNull UUID operationId,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String previewDigest
) {
    public enum TargetType { FLOOR, BUILDING }

    @Override
    public String toString() {
        return "ConfirmMapDeletionRequest[redacted]";
    }
}
