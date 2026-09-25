package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.ReportDownloadTicketApi;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketResponse;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;
import ru.rutcampustrack.auth.session.SessionAdmissionException;

@RestController
public final class ReportDownloadTicketController implements ReportDownloadTicketApi {

    private final ReportDownloadTicketService ticketService;

    public ReportDownloadTicketController(ReportDownloadTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public ResponseEntity<ReportDownloadTicketResponse> issue(
            Authentication authentication,
            IssueReportDownloadTicketRequest request
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        ReportDownloadTicketResponse response = ticketService.issue(principal, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(response);
    }
}
