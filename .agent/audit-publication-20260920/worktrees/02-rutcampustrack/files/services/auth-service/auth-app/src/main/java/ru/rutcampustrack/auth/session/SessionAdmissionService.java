package ru.rutcampustrack.auth.session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Exchanges one original session-bound access token for a live internal JWT.
 * Authority is read exactly once per call and no successful result is cached.
 */
@Service
public final class SessionAdmissionService {

    private final JwtService jwtService;
    private final SessionStatePort sessionStatePort;
    private final InternalIssuerProperties issuerProperties;
    private final Clock clock;

    @Autowired
    public SessionAdmissionService(
            JwtService jwtService,
            SessionStatePort sessionStatePort,
            InternalIssuerProperties issuerProperties
    ) {
        this(jwtService, sessionStatePort, issuerProperties, Clock.systemUTC());
    }

    public SessionAdmissionService(
            JwtService jwtService,
            SessionStatePort sessionStatePort,
            InternalIssuerProperties issuerProperties,
            Clock clock
    ) {
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.sessionStatePort = Objects.requireNonNull(sessionStatePort, "sessionStatePort");
        this.issuerProperties = Objects.requireNonNull(issuerProperties, "issuerProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthAdmissionResponse admit(String accessToken) {
        Instant initialNow = clock.instant();
        Jws<Claims> parsed;
        try {
            parsed = jwtService.parseSessionAccessToken(accessToken);
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }

        if (parsed == null || parsed.getPayload() == null) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        Claims claims = parsed.getPayload();
        long userId;
        UUID sessionId;
        long sessionVersion;
        long rolesVersion;
        Instant originalAccessExpiration;
        try {
            userId = parsePositiveClaim(claims.getSubject(), "sub");
            sessionId = parseSessionId(claims.get("sid"));
            sessionVersion = parsePositiveClaim(claims.get("sv"), "sv");
            rolesVersion = parsePositiveClaim(claims.get("rv"), "rv");
            originalAccessExpiration = parseExpiration(claims);
        } catch (SessionAdmissionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }

        SessionStatePort.SnapshotResult result;
        try {
            result = sessionStatePort.snapshot(
                    new SessionStatePort.SnapshotCommand(userId, sessionId, initialNow)
            );
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(
                    SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE,
                    exception
            );
        }
        if (result == null) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw mapFailure(result.failureCode());
        }

        SessionSnapshot snapshot = result.snapshot();
        if (snapshot == null) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (snapshot.userId() != userId || !snapshot.sessionId().equals(sessionId)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        Instant freshNow = clock.instant();
        if (!snapshot.isLiveAt(freshNow)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.SESSION_REVOKED);
        }

        RoleGrant activeRole = snapshot.activeRole();
        if (activeRole == null) {
            String requestedRole = stringClaim(claims, "role");
            boolean requestedGrantIsUnselectable = snapshot.roles().stream()
                    .anyMatch(grant -> grant.role().name().equals(requestedRole)
                            && !grant.isSelectable());
            throw new SessionAdmissionException(requestedGrantIsUnselectable
                    ? SessionAdmissionException.Code.ROLE_NOT_SELECTABLE
                    : SessionAdmissionException.Code.ROLE_NOT_GRANTED);
        }
        if (!activeRole.isSelectable() || activeRole.status() == RoleStatus.SUSPENDED) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.ROLE_NOT_SELECTABLE);
        }
        if (activeRole.userId() != userId) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        if (snapshot.sessionVersion() != sessionVersion || snapshot.rolesVersion() != rolesVersion) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.SESSION_STATE_STALE);
        }
        requireIdentityMatch(claims, activeRole, snapshot);

        if (!freshNow.isBefore(originalAccessExpiration)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }

        Instant issuedAt = freshNow.truncatedTo(ChronoUnit.SECONDS);
        Instant ttlDeadline = freshNow.plusSeconds(issuerProperties.getTokenTtlSeconds());
        Instant internalExpiration = originalAccessExpiration
                .isBefore(ttlDeadline) ? originalAccessExpiration : ttlDeadline;
        internalExpiration = internalExpiration.truncatedTo(ChronoUnit.SECONDS);
        if (!issuedAt.isBefore(internalExpiration) || !freshNow.isBefore(internalExpiration)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }

        String internalToken;
        try {
            internalToken = jwtService.generateInternalToken(snapshot, issuedAt, internalExpiration);
        } catch (RuntimeException exception) {
            throw new SessionAdmissionException(
                    SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE,
                    exception
            );
        }
        Instant returnNow = clock.instant();
        if (!snapshot.isLiveAt(returnNow)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.SESSION_REVOKED);
        }
        if (!returnNow.isBefore(internalExpiration)
                || !returnNow.isBefore(originalAccessExpiration)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        return new AuthAdmissionResponse(
                internalToken,
                internalExpiration,
                snapshot.sessionId().toString(),
                Long.toString(snapshot.userId()),
                Long.toString(snapshot.sessionVersion()),
                Long.toString(snapshot.rolesVersion()),
                activeRole.role().name(),
                activeRole.status().name(),
                activeRole.groupId() == null ? null : Long.toString(activeRole.groupId()),
                activeRole.role() == AuthRole.HEADMAN,
                activeRole.isReadOnly()
        );
    }

    private static SessionAdmissionException mapFailure(SessionStatePort.FailureCode failureCode) {
        if (failureCode == null) {
            return new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
        }
        return switch (failureCode) {
            case INVALID_SESSION -> new SessionAdmissionException(
                    SessionAdmissionException.Code.INVALID_SESSION);
            case SESSION_REVOKED -> new SessionAdmissionException(
                    SessionAdmissionException.Code.SESSION_REVOKED);
            case ROLE_NOT_GRANTED -> new SessionAdmissionException(
                    SessionAdmissionException.Code.ROLE_NOT_GRANTED);
            case ROLE_NOT_SELECTABLE -> new SessionAdmissionException(
                    SessionAdmissionException.Code.ROLE_NOT_SELECTABLE);
            case SESSION_STATE_STALE -> new SessionAdmissionException(
                    SessionAdmissionException.Code.SESSION_STATE_STALE);
            case AUTHORITY_UNAVAILABLE -> new SessionAdmissionException(
                    SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
            default -> new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
        };
    }

    private static void requireIdentityMatch(Claims claims, RoleGrant activeRole, SessionSnapshot snapshot) {
        String role = stringClaim(claims, "role");
        String status = stringClaim(claims, "status");
        String groupId = optionalStringClaim(claims, "group_id");
        boolean isHeadman = booleanClaim(claims, "is_headman");
        boolean readOnly = booleanClaim(claims, "readOnly");
        String expectedGroupId = activeRole.groupId() == null
                ? null : Long.toString(activeRole.groupId());
        if (!activeRole.role().name().equals(role)
                || !activeRole.status().name().equals(status)
                || !Objects.equals(expectedGroupId, groupId)
                || isHeadman != (activeRole.role() == AuthRole.HEADMAN)
                || readOnly != activeRole.isReadOnly()
                || snapshot.isReadOnly() != activeRole.isReadOnly()) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.SESSION_STATE_STALE);
        }
    }

    private static long parsePositiveClaim(Object value, String name) {
        if (!(value instanceof String string) || !string.matches("[1-9][0-9]*")) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        try {
            return Long.parseLong(string);
        } catch (NumberFormatException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
    }

    private static UUID parseSessionId(Object value) {
        if (!(value instanceof String string)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        try {
            UUID parsed = UUID.fromString(string);
            if (!parsed.toString().equals(string)) {
                throw new IllegalArgumentException("non-canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
    }

    private static Instant parseExpiration(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        return expiration.toInstant();
    }

    private static String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String string)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        return string;
    }

    private static String optionalStringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        return string;
    }

    private static boolean booleanClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof Boolean booleanValue)) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        }
        return booleanValue;
    }
}
