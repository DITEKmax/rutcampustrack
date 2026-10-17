package ru.rutcampustrack.auth.export;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;
import ru.rutcampustrack.auth.session.model.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.*;

class ExportReportAuthorityTest {
    @Test
    void fullFileMetadataRequiresFullEvenWhenOrdinaryAdmissionAndRolesVersionAreValid() {
        var principal =
                new SessionPrincipal(
                        7,
                        UUID.randomUUID(),
                        1,
                        1,
                        AuthRole.ADMIN,
                        RoleStatus.ACTIVE,
                        null,
                        false,
                        false);
        var report =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.ADMIN_USERS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.AdminUsersParameters(
                                null, null, null, null, null, ReportDownloadFormat.HTML),
                        null);
        var tickets = mock(ReportDownloadTicketService.class);
        // Independent live profile fixture. No revocation, stale session or version mismatch masks
        // this guard.
        var admins =
                new AdminAccessJdbcAuthority(mock(JdbcTemplate.class)) {
                    @Override
                    public AdminAccessProfile current(InternalJwtClaims claims) {
                        // Negative-oracle sensitivity control exists only in test fixtures, never
                        // in product admission.
                        boolean knownBadFixture =
                                "permissive".equals(System.getenv("RCT_EXPORT_GUARD_FIXTURE"));
                        return new AdminAccessProfile(
                                7,
                                true,
                                knownBadFixture ? "FULL" : "LIMITED",
                                List.of("USERS_VIEW"),
                                2);
                    }
                };
        var authority = new ExportReportAuthority(tickets, admins);
        Instant expiry = Instant.now().plusSeconds(60);
        assertThatCode(() -> authority.admit(principal, report, expiry, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authority.admit(principal, report, expiry, true))
                .isInstanceOf(AdminAccessException.class);
    }
}
