package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalReportDownloadTicketApi;
import ru.rutcampustrack.auth.dto.RedeemReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketRedemptionResponse;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;

import java.util.Optional;

@RestController
public final class InternalReportDownloadTicketController implements InternalReportDownloadTicketApi {

    private final ReportDownloadTicketService ticketService;

    public InternalReportDownloadTicketController(ReportDownloadTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public ResponseEntity<ReportDownloadTicketRedemptionResponse> redeem(
            RedeemReportDownloadTicketRequest request
    ) {
        Optional<ReportDownloadTicketRedemptionResponse> result = ticketService.redeem(request.ticket());
        return result.map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .header("Referrer-Policy", "no-referrer")
                        .body(body))
                .orElseGet(() -> ResponseEntity.notFound()
                        .cacheControl(CacheControl.noStore())
                        .header("Referrer-Policy", "no-referrer")
                        .build());
    }
}
