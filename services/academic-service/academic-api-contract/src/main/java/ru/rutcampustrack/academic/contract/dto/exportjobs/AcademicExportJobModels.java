package ru.rutcampustrack.academic.contract.dto.exportjobs;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;

import java.time.Instant;
import java.util.UUID;

/** Academic owns the lifecycle; the existing typed selector remains the sole wire definition. */
public final class AcademicExportJobModels {
    private AcademicExportJobModels() {}

    public record Create(
            @NotNull UUID requestId, @NotNull @Valid IssueReportDownloadTicketRequest report) {}

    @Schema(name = "Status")
    public record Status(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID jobId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                    ru.rutcampustrack.auth.dto.ExportJobModels.State state,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String suggestedFilename,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
            @Schema(
                            types = {"string", "null"},
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String errorCode) {}

    public record Render(@NotNull UUID leaseId) {}
}
