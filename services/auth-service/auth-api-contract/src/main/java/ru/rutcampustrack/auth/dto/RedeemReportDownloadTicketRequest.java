package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Internal-only request body; the ticket is never accepted from caller headers. */
@Schema(description = "Internal report-ticket redemption request")
public record RedeemReportDownloadTicketRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String ticket
) {
}
