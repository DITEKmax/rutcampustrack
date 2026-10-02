package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ConfirmUserArchiveRequest(
        @NotBlank String internalToken, @NotBlank String password,
        @NotNull Purpose purpose, @Positive long targetId,
        @NotNull UUID operationId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String previewDigest) {
    public enum Purpose { USER_ARCHIVE }
    @Override public String toString() { return "ConfirmUserArchiveRequest[redacted]"; }
}
