package ru.rutcampustrack.auth.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketRedemptionResponse;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketResponse;
import ru.rutcampustrack.auth.exception.InvalidReportDownloadTicketRequestException;
import ru.rutcampustrack.auth.grpc.AcademicAssistantPermissionClient;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionAdmissionException;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Issues reusable 60-second report capabilities and remints current signed authority on every redemption. */
@Service
public final class ReportDownloadTicketService {

    public static final Duration TICKET_TTL = Duration.ofSeconds(60);
    static final int MAX_ISSUES_PER_SESSION = 10;
    static final int MAX_REDEMPTIONS_PER_TICKET = 20;
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_COLLISION_ATTEMPTS = 3;
    private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{43}";

    private final ReportDownloadTicketStore ticketStore;
    private final AuthService authService;
    private final JwtService jwtService;
    private final InternalIssuerProperties issuerProperties;
    private final ObjectMapper objectMapper;
    private final AcademicAssistantPermissionClient academicAssistantPermissionClient;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public ReportDownloadTicketService(
            ReportDownloadTicketStore ticketStore,
            AuthService authService,
            JwtService jwtService,
            InternalIssuerProperties issuerProperties,
            ObjectMapper objectMapper,
            AcademicAssistantPermissionClient academicAssistantPermissionClient
    ) {
        this(ticketStore, authService, jwtService, issuerProperties, objectMapper,
                Clock.systemUTC(), new SecureRandom(), academicAssistantPermissionClient);
    }

    ReportDownloadTicketService(
            ReportDownloadTicketStore ticketStore,
            AuthService authService,
            JwtService jwtService,
            InternalIssuerProperties issuerProperties,
            ObjectMapper objectMapper,
            Clock clock,
            SecureRandom secureRandom,
            AcademicAssistantPermissionClient academicAssistantPermissionClient
    ) {
        this.ticketStore = Objects.requireNonNull(ticketStore, "ticketStore");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.issuerProperties = Objects.requireNonNull(issuerProperties, "issuerProperties");
        this.academicAssistantPermissionClient = Objects.requireNonNull(
                academicAssistantPermissionClient, "academicAssistantPermissionClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public ReportDownloadTicketResponse issue(
            SessionPrincipal principal,
            IssueReportDownloadTicketRequest report
    ) {
        Objects.requireNonNull(principal, "principal");
        if (report == null || !report.isParametersConsistent()) {
            throw new InvalidReportDownloadTicketRequestException();
        }
        SessionSnapshot snapshot = authService.admit(principal);
        RoleGrant selectedRole = snapshot.activeRole();
        if (principal.isBootstrap() || selectedRole == null || !selectedRole.isSelectable()) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.ROLE_NOT_SELECTABLE);
        }
        if (report.kind() == ReportDownloadKind.HEADMAN_STATS_TREND) {
            requireCurrentViewStats(snapshot, selectedRole);
        }

        String sessionDigest = digest(principal.userId() + ":" + principal.sessionId());
        if (!storeCall(() -> ticketStore.allowIssue(sessionDigest,
                MAX_ISSUES_PER_SESSION, TICKET_TTL))) {
            throw new ReportDownloadTicketRateLimitException();
        }

        Instant expiresAt = clock.instant().plus(TICKET_TTL);
        StoredReportDownloadTicket stored = new StoredReportDownloadTicket(
                StoredReportDownloadTicket.CURRENT_SCHEMA_VERSION,
                snapshot.userId(), snapshot.sessionId(), snapshot.sessionVersion(), snapshot.rolesVersion(),
                selectedRole.role(), selectedRole.status(), selectedRole.groupId(),
                selectedRole.role() == AuthRole.HEADMAN, selectedRole.isReadOnly(), report, expiresAt);
        String payload = serialize(stored);
        for (int attempt = 0; attempt < TOKEN_COLLISION_ATTEMPTS; attempt++) {
            String ticket = randomTicket();
            String ticketDigest = digest(ticket);
            if (storeCall(() -> ticketStore.putIfAbsent(ticketDigest, payload, TICKET_TTL))) {
                return new ReportDownloadTicketResponse(
                        "/api/report-download/" + ticket, expiresAt, report.suggestedFilename());
            }
        }
        throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
    }

    private void requireCurrentViewStats(SessionSnapshot snapshot, RoleGrant selectedRole) {
        Long groupId = selectedRole.groupId();
        if (groupId == null || groupId <= 0) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.REPORT_PERMISSION_DENIED);
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        long verificationTtlSeconds = Math.min(TICKET_TTL.toSeconds(), issuerProperties.getTokenTtlSeconds());
        Instant configuredExpiry = issuedAt.plusSeconds(verificationTtlSeconds);
        Instant sessionExpiry = snapshot.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        Instant tokenExpiry = configuredExpiry.isBefore(sessionExpiry) ? configuredExpiry : sessionExpiry;
        if (!issuedAt.isBefore(tokenExpiry)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
        }
        String signedIdentity;
        try {
            signedIdentity = jwtService.generateInternalToken(snapshot, issuedAt, tokenExpiry);
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (!academicAssistantPermissionClient.hasViewStatsPermission(signedIdentity, groupId)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.REPORT_PERMISSION_DENIED);
        }
    }

    public Optional<ReportDownloadTicketRedemptionResponse> redeem(String ticket) {
        if (ticket == null || !ticket.matches(TOKEN_PATTERN)) {
            return Optional.empty();
        }
        String ticketDigest = digest(ticket);
        Optional<String> encoded = storeCall(() -> ticketStore.find(ticketDigest));
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        StoredReportDownloadTicket stored = deserialize(encoded.get());
        Instant now = clock.instant();
        if (!now.isBefore(stored.expiresAt())) {
            return Optional.empty();
        }

        // AuthService.admit performs the same selected-identity and version checks used by live auth routes.
        SessionSnapshot snapshot = authService.admit(stored.principal());
        Instant freshNow = clock.instant();
        if (!freshNow.isBefore(stored.expiresAt())) {
            return Optional.empty();
        }
        if (!storeCall(() -> ticketStore.allowRedemption(ticketDigest,
                MAX_REDEMPTIONS_PER_TICKET, TICKET_TTL))) {
            throw new ReportDownloadTicketRateLimitException();
        }

        Instant issuedAt = freshNow.truncatedTo(ChronoUnit.SECONDS);
        Instant ticketExpiry = stored.expiresAt().truncatedTo(ChronoUnit.SECONDS);
        Instant sessionExpiry = snapshot.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        Instant configuredExpiry = freshNow.plusSeconds(issuerProperties.getTokenTtlSeconds())
                .truncatedTo(ChronoUnit.SECONDS);
        Instant internalExpiry = min(ticketExpiry, sessionExpiry, configuredExpiry);
        if (!issuedAt.isBefore(internalExpiry) || !freshNow.isBefore(internalExpiry)) {
            return Optional.empty();
        }

        String reportBindingHash = stored.report().bindingHash();
        String internalToken;
        try {
            internalToken = jwtService.generateInternalReportDownloadToken(
                    snapshot, issuedAt, internalExpiry, reportBindingHash, stored.expiresAt());
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        AuthAdmissionResponse admission = admission(snapshot, internalToken, internalExpiry);
        return Optional.of(new ReportDownloadTicketRedemptionResponse(
                admission, stored.expiresAt(), reportBindingHash, stored.report()));
    }

    private String serialize(StoredReportDownloadTicket stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (Exception exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private StoredReportDownloadTicket deserialize(String payload) {
        try {
            StoredReportDownloadTicket stored = objectMapper.readValue(payload, StoredReportDownloadTicket.class);
            if (stored == null || !stored.report().isParametersConsistent()) {
                throw new IllegalArgumentException("Stored ticket is invalid");
            }
            return stored;
        } catch (Exception exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private <T> T storeCall(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ReportDownloadTicketRateLimitException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private String randomTicket() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static AuthAdmissionResponse admission(
            SessionSnapshot snapshot,
            String internalToken,
            Instant expiresAt
    ) {
        RoleGrant selectedRole = snapshot.activeRole();
        return new AuthAdmissionResponse(
                internalToken,
                expiresAt,
                snapshot.sessionId().toString(),
                Long.toString(snapshot.userId()),
                Long.toString(snapshot.sessionVersion()),
                Long.toString(snapshot.rolesVersion()),
                selectedRole.role().name(),
                selectedRole.status().name(),
                selectedRole.groupId() == null ? null : Long.toString(selectedRole.groupId()),
                selectedRole.role() == AuthRole.HEADMAN,
                selectedRole.isReadOnly());
    }

    private static Instant min(Instant first, Instant second, Instant third) {
        Instant result = first.isBefore(second) ? first : second;
        return result.isBefore(third) ? result : third;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
