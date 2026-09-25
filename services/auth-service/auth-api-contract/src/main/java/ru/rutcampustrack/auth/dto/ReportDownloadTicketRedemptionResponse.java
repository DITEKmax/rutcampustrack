package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Internal-only fresh identity and immutable report selector used by the Gateway dispatcher. */
@Schema(description = "Internal report-ticket redemption result")
public record ReportDownloadTicketRedemptionResponse(
        @NotNull AuthAdmissionResponse admission,
        @NotNull Instant ticketExpiresAt,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportBindingHash,
        @NotNull @Valid IssueReportDownloadTicketRequest report
) {
}
