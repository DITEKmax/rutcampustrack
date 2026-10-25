package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public final class GroupDeletionModels {
    private GroupDeletionModels() {}

    @Schema(name = "AdminGroupDeletionPhase")
    public enum Phase {
        PREPARING,
        COMMITTING,
        COMPLETED,
        RELEASING,
        CANCELLED
    }

    /** Counts distinguish erased business rows from retained accounts and affected grants. */
    @Schema(name = "AdminGroupDeletionPreview")
    public record Preview(
            String groupId, String groupName, String previewDigest, Map<String, Long> counts) {}

    @Schema(name = "ConfirmAdminGroupDeletionRequest")
    public record ConfirmRequest(
            @NotBlank @Size(max = 256) String password,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewDigest) {
        @Override
        public String toString() {
            return "ConfirmRequest[password=<redacted>,previewDigest=" + previewDigest + "]";
        }
    }

    @Schema(name = "AdminGroupDeletionOperation")
    public record Operation(
            UUID operationId,
            String groupId,
            Phase phase,
            boolean retryable,
            Map<String, Long> counts,
            String failure) {}
}
