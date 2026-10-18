package ru.rutcampustrack.academic.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public final class IdentityOperationHttpModels {
    private IdentityOperationHttpModels() {}

    /** Only ARCHIVE recovery accepts a fresh preview; password is never part of stored intent. */
    public record ReconcileRequest(
            @jakarta.validation.constraints.Pattern(regexp = "[0-9a-f]{64}")
                    String archivePreviewDigest,
            @jakarta.validation.constraints.Size(max = 1024) String password) {
        @Override
        public String toString() {
            return "ReconcileRequest[redacted]";
        }
    }

    public enum Status {
        PENDING,
        SUCCEEDED,
        REJECTED,
        FAILED_SAFE
    }

    @Schema(
            name = "IdentityOperationStatus",
            description =
                    "Состояние сохранённой операции изменения пользователя; без секретов и"
                            + " внутренней команды")
    public record Operation(UUID operationId, Status status, int retryAfterSeconds) {
        public Operation {
            if (operationId == null
                    || status == null
                    || retryAfterSeconds < 0
                    || retryAfterSeconds > 30)
                throw new IllegalArgumentException("valid operation status required");
        }
    }
}
