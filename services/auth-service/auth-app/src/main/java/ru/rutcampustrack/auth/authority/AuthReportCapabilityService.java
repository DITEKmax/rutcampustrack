package ru.rutcampustrack.auth.authority;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.service.ReportDownloadTicketStore;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.authauthority.grpc.AuthorityAdminProfile;
import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Auth owns admission and opaque capability security, never a report selector or job state. */
@Service
public class AuthReportCapabilityService {
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private final AuthService sessions;
    private final JwtService jwt;
    private final InternalIssuerProperties issuer;
    private final ReportDownloadTicketStore tickets;
    private final ObjectMapper mapper;
    private final AuthAdminJdbcAuthority admins;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public AuthReportCapabilityService(
            AuthService sessions,
            JwtService jwt,
            InternalIssuerProperties issuer,
            ReportDownloadTicketStore tickets,
            ObjectMapper mapper,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager) {
        this(
                sessions,
                jwt,
                issuer,
                tickets,
                mapper,
                new AuthAdminJdbcAuthority(jdbc),
                manager,
                Clock.systemUTC());
    }

    AuthReportCapabilityService(
            AuthService sessions,
            JwtService jwt,
            InternalIssuerProperties issuer,
            ReportDownloadTicketStore tickets,
            ObjectMapper mapper,
            AuthAdminJdbcAuthority admins,
            PlatformTransactionManager manager,
            Clock clock) {
        this.sessions = sessions;
        this.jwt = jwt;
        this.issuer = issuer;
        this.tickets = tickets;
        this.mapper =
                mapper.copy()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.admins = admins;
        this.clock = clock;
        this.tx = new TransactionTemplate(manager);
    }

    public LiveSessionAuthority admitSession(StoredSessionIdentity identity) {
        SessionPrincipal actor = principal(identity);
        return tx.execute(
                status -> {
                    admins.lockGlobal();
                    admins.lockUsers(actor.userId(), null);
                    var snapshot = sessions.admit(actor);
                    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
                    Instant until =
                            minimum(
                                    now.plusSeconds(issuer.getTokenTtlSeconds()),
                                    snapshot.refreshExpiresAt());
                    var result =
                            LiveSessionAuthority.newBuilder()
                                    .setIdentity(identity)
                                    .setActiveRoleGrantId(snapshot.activeRole().grantId())
                                    .setRefreshExpiresAt(snapshot.refreshExpiresAt().toString())
                                    .setInternalToken(
                                            jwt.generateInternalToken(snapshot, now, until))
                                    .setExpiresAt(until.toString());
                    if (actor.selectedRole() == AuthRole.ADMIN)
                        result.setAdminProfile(
                                profile(admins.current(AuthInternalActorReader.claims(actor))));
                    return result.build();
                });
    }

    public AdmittedReportAuthority admit(AdmitStoredSessionRequest request) {
        validate(request);
        SessionPrincipal actor = principal(request.getIdentity());
        ReportDownloadKind kind = ReportDownloadKind.valueOf(request.getReportKind());
        return tx.execute(
                status -> {
                    admins.lockGlobal();
                    admins.lockUsers(actor.userId(), null);
                    var snapshot = sessions.admit(actor);
                    AdminAccessProfile admin = authorize(actor, kind, request.getRequiresFull());
                    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
                    Instant until =
                            minimum(
                                    minimum(
                                            Instant.parse(request.getNotAfter()),
                                            snapshot.refreshExpiresAt()),
                                    now.plusSeconds(
                                            request.getOwnerResourceId().isEmpty()
                                                    ? issuer.getTokenTtlSeconds()
                                                    : Math.min(issuer.getTokenTtlSeconds(), 30)));
                    if (!now.isBefore(until)) throw Status.PERMISSION_DENIED.asRuntimeException();
                    var reply =
                            AdmittedReportAuthority.newBuilder()
                                    .setIdentity(request.getIdentity())
                                    .setExpiresAt(until.toString())
                                    .setRequiresFull(request.getRequiresFull());
                    if (admin != null) reply.setAdminProfile(profile(admin));
                    // Status/ticket admission returns no bearer. Only an exact local
                    // render lease obtains a purpose-bound, non-CRUD token.
                    if (!request.getOwnerResourceId().isEmpty())
                        reply.setInternalToken(
                                jwt.generateAcademicReportToken(
                                        snapshot,
                                        now,
                                        until,
                                        kind.name(),
                                        request.getSelectorHash(),
                                        UUID.fromString(request.getOwnerResourceId()),
                                        UUID.fromString(request.getLeaseId()),
                                        until));
                    return reply.build();
                });
    }

    public IssuedReportCapability issue(IssueReportCapabilityRequest request) {
        if (!request.hasAuthority() || !request.getUnknownFields().asMap().isEmpty())
            throw invalid();
        uuid(request.getOwnerResourceId());
        var authority = request.getAuthority();
        if (!authority.getOwnerResourceId().isEmpty() || !authority.getLeaseId().isEmpty())
            throw invalid();
        var admitted = admit(authority);
        var actor = principal(authority.getIdentity());
        if (!tickets.allowIssue(digest(actor.userId() + ":" + actor.sessionId()), 10, TICKET_TTL))
            throw Status.RESOURCE_EXHAUSTED.asRuntimeException();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expires =
                minimum(
                        minimum(now.plus(TICKET_TTL), Instant.parse(authority.getNotAfter())),
                        Instant.parse(admitted.getExpiresAt()));
        if (!now.isBefore(expires)) throw Status.PERMISSION_DENIED.asRuntimeException();
        // Explicit local record catalogue: no selector bytes, credentials or bearer.
        String encoded;
        try {
            encoded =
                    mapper.writeValueAsString(
                            new Ticket(
                                    1,
                                    authority.getIdentity().toByteArray(),
                                    authority.getReportKind(),
                                    authority.getSelectorHash(),
                                    request.getOwnerResourceId(),
                                    expires.toString(),
                                    authority.getRequiresFull()));
        } catch (Exception failed) {
            throw Status.UNAVAILABLE.asRuntimeException();
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (tickets.putIfAbsent(digest(token), encoded, Duration.between(now, expires))) {
                // Close a security-generation change while Redis was accessed.
                admit(authority);
                return IssuedReportCapability.newBuilder()
                        .setTicket(token)
                        .setExpiresAt(expires.toString())
                        .setRequiresFull(authority.getRequiresFull())
                        .build();
            }
        }
        throw Status.UNAVAILABLE.asRuntimeException();
    }

    public RedeemedReportCapability redeem(RedeemReportCapabilityRequest request) {
        String token = request.getTicket();
        if (!request.getUnknownFields().asMap().isEmpty() || !token.matches("[A-Za-z0-9_-]{43}"))
            throw invalid();
        if (!tickets.allowRedemption(digest(token), 20, TICKET_TTL))
            throw Status.RESOURCE_EXHAUSTED.asRuntimeException();
        String encoded =
                tickets.find(digest(token))
                        .orElseThrow(() -> Status.NOT_FOUND.asRuntimeException());
        Ticket stored;
        try {
            stored = mapper.readValue(encoded, Ticket.class);
        } catch (Exception invalid) {
            throw Status.UNAVAILABLE.asRuntimeException();
        }
        if (stored.schema() != 1 || !clock.instant().isBefore(Instant.parse(stored.expiresAt())))
            throw Status.NOT_FOUND.asRuntimeException();
        StoredSessionIdentity identity;
        try {
            identity = StoredSessionIdentity.parseFrom(stored.identity());
        } catch (Exception invalid) {
            throw Status.UNAVAILABLE.asRuntimeException();
        }
        var authority =
                admit(
                        AdmitStoredSessionRequest.newBuilder()
                                .setIdentity(identity)
                                .setReportKind(stored.kind())
                                .setSelectorHash(stored.hash())
                                .setNotAfter(stored.expiresAt())
                                .setRequiresFull(stored.requiresFull())
                                .build());
        return RedeemedReportCapability.newBuilder()
                .setAuthority(authority)
                .setReportKind(stored.kind())
                .setSelectorHash(stored.hash())
                .setOwnerResourceId(stored.resource())
                .setExpiresAt(stored.expiresAt())
                .build();
    }

    private AdminAccessProfile authorize(
            SessionPrincipal actor, ReportDownloadKind kind, boolean full) {
        if (actor.readOnly() || actor.selectedStatus() != RoleStatus.ACTIVE)
            throw Status.PERMISSION_DENIED.asRuntimeException();
        String permission =
                switch (kind) {
                    case ADMIN_USERS -> "USERS_VIEW";
                    case ADMIN_GROUPS -> "GROUPS_VIEW";
                    case ADMIN_GROUP_ATTENDANCE -> "DASHBOARD_VIEW";
                    default -> null;
                };
        if (permission != null) {
            var p = admins.current(AuthInternalActorReader.claims(actor));
            if (!p.allows(permission) || (full && !p.full()))
                throw Status.PERMISSION_DENIED.asRuntimeException();
            return p;
        }
        if (full) throw invalid();
        boolean permitted =
                switch (kind) {
                    case TEACHER_JOURNAL, TEACHER_STATS -> actor.selectedRole() == AuthRole.TEACHER;
                    case HEADMAN_GROUP_COMPOSITION ->
                            actor.selectedRole() == AuthRole.HEADMAN
                                    && actor.headman()
                                    && actor.groupId() != null;
                    default ->
                            (actor.selectedRole() == AuthRole.STUDENT
                                            || actor.selectedRole() == AuthRole.HEADMAN)
                                    && actor.groupId() != null;
                };
        if (!permitted) throw Status.PERMISSION_DENIED.asRuntimeException();
        return null; // VIEW_STATS/assignment/roster/selector are Academic authority.
    }

    static SessionPrincipal principal(StoredSessionIdentity identity) {
        if (!identity.getUnknownFields().asMap().isEmpty()) throw invalid();
        try {
            return new SessionPrincipal(
                    identity.getUserId(),
                    uuid(identity.getSessionId()),
                    identity.getSessionVersion(),
                    identity.getRolesVersion(),
                    AuthRole.valueOf(identity.getRole()),
                    RoleStatus.valueOf(identity.getStatus()),
                    identity.hasGroupId() ? identity.getGroupId() : null,
                    identity.getIsHeadman(),
                    identity.getReadOnly());
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private static void validate(AdmitStoredSessionRequest r) {
        if (!r.hasIdentity()
                || !r.getUnknownFields().asMap().isEmpty()
                || !r.getSelectorHash().matches("[0-9a-f]{64}")) throw invalid();
        try {
            ReportDownloadKind.valueOf(r.getReportKind());
            Instant.parse(r.getNotAfter());
        } catch (RuntimeException invalid) {
            throw invalid();
        }
        if (r.getOwnerResourceId().isEmpty() != r.getLeaseId().isEmpty()) throw invalid();
        if (!r.getOwnerResourceId().isEmpty()) {
            uuid(r.getOwnerResourceId());
            uuid(r.getLeaseId());
        }
    }

    private static AuthorityAdminProfile profile(AdminAccessProfile p) {
        var value =
                AuthorityAdminProfile.newBuilder()
                        .setAssigned(p.assigned())
                        .setMode(p.mode())
                        .setVersion(p.version());
        if (!p.full()) value.addAllPermissions(p.permissions());
        return value.build();
    }

    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw invalid();
            return id;
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private static Instant minimum(Instant a, Instant b) {
        return (a.isBefore(b) ? a : b).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static io.grpc.StatusRuntimeException invalid() {
        return Status.INVALID_ARGUMENT
                .withDescription("Invalid security capability")
                .asRuntimeException();
    }

    private record Ticket(
            int schema,
            byte[] identity,
            String kind,
            String hash,
            String resource,
            String expiresAt,
            boolean requiresFull) {}
}
