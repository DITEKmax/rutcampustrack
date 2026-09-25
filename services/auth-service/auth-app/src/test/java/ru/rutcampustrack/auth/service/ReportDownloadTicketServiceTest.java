package ru.rutcampustrack.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadFormat;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportDownloadTicketServiceTest {

    private static final long USER_ID = 17L;
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final Instant NOW = Instant.parse("2026-09-25T10:15:30Z");
    private static final String NEVER_STORE_THIS = "opaque-bearer-or-init-data-sentinel";

    @Test
    void issues256BitRelativeTicketAndRemintsFreshAuthorityForReusableRedemptionsUntilAbsoluteExpiry() {
        MutableClock clock = new MutableClock(NOW);
        MemoryStore store = new MemoryStore();
        AuthService auth = mock(AuthService.class);
        JwtService jwt = mock(JwtService.class);
        InternalIssuerProperties issuer = new InternalIssuerProperties();
        issuer.setTokenTtlSeconds(300);
        SessionPrincipal principal = principal();
        SessionSnapshot snapshot = snapshot(NOW);
        IssueReportDownloadTicketRequest report = report();
        when(auth.admit(principal)).thenReturn(snapshot);
        when(jwt.generateInternalReportDownloadToken(eq(snapshot), any(), any(), anyString(), any()))
                .thenReturn("fresh-signed-authority");

        ReportDownloadTicketService service = service(store, auth, jwt, issuer, clock);
        var issued = service.issue(principal, report);
        String ticket = issued.downloadPath().substring("/api/report-download/".length());

        assertTrue(ticket.matches("[A-Za-z0-9_-]{43}"), "ticket has 256 bits of URL-safe entropy");
        assertEquals(NOW.plusSeconds(60), issued.expiresAt());
        assertEquals("teacher-journal.pdf", issued.suggestedFilename());
        assertEquals(Duration.ofSeconds(60), store.ticketTtl);
        assertFalse(store.serializedTicket.contains(ticket), "the opaque capability is not stored as a value");
        assertFalse(store.serializedTicket.contains(NEVER_STORE_THIS));
        verify(jwt, org.mockito.Mockito.never()).generateInternalToken(any(), any(), any());

        var first = service.redeem(ticket).orElseThrow();
        var retry = service.redeem(ticket).orElseThrow();
        assertEquals(NOW.plusSeconds(60), first.admission().expiresAt());
        assertEquals(first.admission().expiresAt(), retry.admission().expiresAt());
        assertEquals(2, store.redemptions);
        verify(auth, times(3)).admit(principal);
        verify(jwt, times(2)).generateInternalReportDownloadToken(eq(snapshot), any(), any(),
                eq(report.bindingHash()), eq(NOW.plusSeconds(60)));

        clock.set(NOW.plusSeconds(61));
        assertTrue(service.redeem(ticket).isEmpty(), "an expired ticket cannot be redeemed or extended");
        verify(auth, times(3)).admit(principal);
        assertEquals(2, store.redemptions);
    }

    @Test
    void rechecksLiveSessionBeforeMintingAndDeniesARevokedOrChangedSession() {
        MutableClock clock = new MutableClock(NOW);
        MemoryStore store = new MemoryStore();
        AuthService auth = mock(AuthService.class);
        JwtService jwt = mock(JwtService.class);
        InternalIssuerProperties issuer = new InternalIssuerProperties();
        issuer.setTokenTtlSeconds(300);
        SessionPrincipal principal = principal();
        SessionSnapshot snapshot = snapshot(NOW);
        when(auth.admit(principal)).thenReturn(snapshot)
                .thenThrow(new AuthSessionException(AuthSessionException.Code.SESSION_STATE_STALE));
        ReportDownloadTicketService service = service(store, auth, jwt, issuer, clock);
        String ticket = service.issue(principal, report()).downloadPath()
                .substring("/api/report-download/".length());

        assertThrows(AuthSessionException.class, () -> service.redeem(ticket));
        assertEquals(0, store.redemptions, "permission changes are checked before counting/serving a use");
        verifyNoInteractions(jwt);
        verify(auth, times(2)).admit(principal);
    }

    @Test
    void malformedSelectorHasExplicitBadRequestFailureInsteadOfGenericIllegalArgumentException() {
        AuthService auth = mock(AuthService.class);
        IssueReportDownloadTicketRequest malformed = new IssueReportDownloadTicketRequest(
                ReportDownloadKind.TEACHER_JOURNAL, null,
                new IssueReportDownloadTicketRequest.TeacherStatsParameters(
                        4L, "student", null, null, null, null, null, ReportDownloadFormat.PDF),
                null, null, null);
        ReportDownloadTicketService service = service(new MemoryStore(), auth, mock(JwtService.class),
                new InternalIssuerProperties(), new MutableClock(NOW));

        assertThrows(ru.rutcampustrack.auth.exception.InvalidReportDownloadTicketRequestException.class,
                () -> service.issue(principal(), malformed));
        verifyNoInteractions(auth);
    }

    private static ReportDownloadTicketService service(MemoryStore store,
                                                        AuthService auth,
                                                        JwtService jwt,
                                                        InternalIssuerProperties issuer,
                                                        Clock clock) {
        return new ReportDownloadTicketService(store, auth, jwt, issuer,
                new ObjectMapper().registerModule(new JavaTimeModule()), clock, new SecureRandom());
    }

    private static SessionPrincipal principal() {
        return new SessionPrincipal(USER_ID, SESSION_ID, 2L, 3L,
                AuthRole.TEACHER, RoleStatus.ACTIVE, 31L, false, false);
    }

    private static SessionSnapshot snapshot(Instant now) {
        RoleGrant role = new RoleGrant(5L, USER_ID, AuthRole.TEACHER, RoleStatus.ACTIVE, 31L,
                now.minusSeconds(10), now.minusSeconds(2));
        return new SessionSnapshot(SESSION_ID, USER_ID, 2L, 3L, role, List.of(role),
                now.plusSeconds(900), now.minusSeconds(10), now.minusSeconds(1), null, null,
                AuthMethod.PASSWORD, null, null);
    }

    private static IssueReportDownloadTicketRequest report() {
        return new IssueReportDownloadTicketRequest(ReportDownloadKind.TEACHER_JOURNAL,
                new IssueReportDownloadTicketRequest.TeacherJournalParameters(
                        7L, 31L, 41L, List.of("LECTURE"), ReportDownloadFormat.PDF),
                null, null, null, null);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        private void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private static final class MemoryStore implements ReportDownloadTicketStore {
        private String key;
        private String serializedTicket;
        private Duration ticketTtl;
        private int redemptions;

        @Override
        public boolean putIfAbsent(String ticketDigest, String value, Duration ttl) {
            key = ticketDigest;
            serializedTicket = value;
            ticketTtl = ttl;
            return true;
        }

        @Override
        public Optional<String> find(String ticketDigest) {
            return key.equals(ticketDigest) ? Optional.of(serializedTicket) : Optional.empty();
        }

        @Override
        public boolean allowIssue(String sessionDigest, int maximum, Duration window) {
            return true;
        }

        @Override
        public boolean allowRedemption(String ticketDigest, int maximum, Duration window) {
            redemptions++;
            return true;
        }
    }
}
