package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketResponse;

@Tag(name = "Authentication", description = "Authenticated session operations")
@RequestMapping("/auth")
public interface ReportDownloadTicketApi {

    @Operation(summary = "Issue a short-lived URL for one allowlisted report")
    @ApiResponse(responseCode = "200", description = "Report download ticket issued")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "403", description = "No current selectable report session")
    @ApiResponse(responseCode = "429", description = "Ticket issuance limit exceeded")
    @ApiResponse(responseCode = "503", description = "Session or ticket store unavailable")
    @PostMapping("/report-download-tickets")
    ResponseEntity<ReportDownloadTicketResponse> issue(
            Authentication authentication,
            @Valid @RequestBody IssueReportDownloadTicketRequest request
    );
}
