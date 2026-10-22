package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Short-lived report download capability")
public record ReportDownloadTicketResponse(
        String downloadPath, Instant expiresAt, String suggestedFilename) {
    @Override
    public String toString() {
        return "ReportDownloadTicketResponse[downloadPath=<redacted>, expiresAt="
                + expiresAt
                + ", suggestedFilename="
                + suggestedFilename
                + ']';
    }
}
