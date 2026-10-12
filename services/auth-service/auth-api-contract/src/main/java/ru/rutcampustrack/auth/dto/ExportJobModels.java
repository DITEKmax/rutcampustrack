package ru.rutcampustrack.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ExportJobModels {
    private ExportJobModels() {}
    public enum State {
        QUEUED("queued"), PREPARING("preparing"), READY("ready"), ERROR("error");
        private final String code;
        State(String code) { this.code = code; }
        @com.fasterxml.jackson.annotation.JsonValue public String code() { return code; }
        @com.fasterxml.jackson.annotation.JsonCreator public static State from(String code) {
            for (State state : values()) if (state.code.equals(code)) return state;
            throw new IllegalArgumentException("Unknown export state");
        }
    }
    public record Create(@NotNull UUID requestId, @NotNull @Valid IssueReportDownloadTicketRequest report) {}
    public record Status(UUID jobId, State state, String suggestedFilename, Instant expiresAt, String errorCode) {}
    public record Claim(UUID jobId, UUID leaseId, ReportDownloadTicketRedemptionResponse authority) {
        @Override public String toString() { return "ExportClaim[jobId=" + jobId + "]"; }
    }
    public record Complete(@NotNull UUID leaseId, @NotNull @Size(min = 1, max = 20 * 1024 * 1024) byte[] content,
            @NotBlank String contentType, @Min(0) int rowCount, boolean requiresFull) {
        @Override public String toString() { return "ExportComplete[bytes=" + (content == null ? 0 : content.length) + "]"; }
    }
    public record Fail(@NotNull UUID leaseId, @NotBlank @Pattern(regexp = "AUTHORITY_DENIED|GENERATION_FAILED|EXPORT_TOO_LARGE") String code) {}
    public record Admit(@NotNull UUID jobId, @NotNull @Valid WsSessionAdmissionRequest identity) {}
}
