package ru.rutcampustrack.auth.export;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;

@Component
public class ExportReportAuthority {
    private final ReportDownloadTicketService tickets;
    private final AdminAccessJdbcAuthority admins;

    @Autowired
    public ExportReportAuthority(ReportDownloadTicketService tickets, JdbcTemplate jdbc) {
        this(tickets, new AdminAccessJdbcAuthority(jdbc));
    }

    ExportReportAuthority(ReportDownloadTicketService tickets, AdminAccessJdbcAuthority admins) {
        this.tickets = tickets;
        this.admins = admins;
    }

    public ru.rutcampustrack.shared.security.teacher.TeacherReportScope captureTeacherScope(
            SessionPrincipal principal, IssueReportDownloadTicketRequest report, String hash) {
        return tickets.captureTeacherScope(principal, report, hash);
    }

    public void requireReady(ru.rutcampustrack.auth.service.StoredReportDownloadTicket identity) {
        tickets.requireReadyTeacher(identity);
    }

    public boolean requiresFull(
            SessionPrincipal principal, IssueReportDownloadTicketRequest report) {
        var profile = RegistryExportAdmission.requireCurrent(admins, principal, report.kind());
        return report.kind() == ReportDownloadKind.ADMIN_USERS && profile.full();
    }

    public ReportDownloadTicketRedemptionResponse admit(
            SessionPrincipal principal,
            IssueReportDownloadTicketRequest report,
            Instant expiry,
            boolean requiresFull) {
        var response = tickets.authorizeJob(principal, report, expiry);
        requiresFull(principal, report);
        if (requiresFull) admins.requireFull(RegistryExportAdmission.claims(principal));
        return response;
    }
}
