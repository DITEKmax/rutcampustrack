package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.RedeemReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketRedemptionResponse;

@Tag(name = "Internal report ticket", description = "Secret-protected Gateway redemption")
@RequestMapping("/internal")
public interface InternalReportDownloadTicketApi {

    @Operation(summary = "Redeem a report ticket against current session authority")
    @ApiResponse(responseCode = "200", description = "Fresh internal report authority issued")
    @ApiResponse(responseCode = "404", description = "Ticket unknown or expired")
    @ApiResponse(responseCode = "503", description = "Session or ticket store unavailable")
    @PostMapping("/report-download-tickets/redeem")
    ResponseEntity<ReportDownloadTicketRedemptionResponse> redeem(
            @Valid @RequestBody RedeemReportDownloadTicketRequest request
    );
}
