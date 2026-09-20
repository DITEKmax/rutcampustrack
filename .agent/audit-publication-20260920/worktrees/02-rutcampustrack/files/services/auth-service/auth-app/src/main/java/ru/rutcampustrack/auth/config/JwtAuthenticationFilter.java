package ru.rutcampustrack.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.time.Clock;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String COOKIE_LOGOUT_FALLBACK_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".cookieLogoutFallback";

    private final JwtService jwtService;
    private final SessionStatePort sessionStatePort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtAuthenticationFilter(JwtService jwtService, SessionStatePort sessionStatePort,
                                   ObjectMapper objectMapper) {
        this(jwtService, sessionStatePort, Clock.systemUTC(), objectMapper);
    }

    /** Strict-token constructor for isolated parser/filter tests. */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this(jwtService, null, Clock.systemUTC(), new ObjectMapper());
    }

    JwtAuthenticationFilter(JwtService jwtService, SessionStatePort sessionStatePort,
                            Clock clock, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.sessionStatePort = sessionStatePort;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")
                && !isCookieAuthorizedPath(request)) {
            String token = authHeader.substring(7);
            SecurityContextHolder.clearContext();
            try {
                Jws<Claims> jws;
                boolean bootstrap;
                try {
                    jws = jwtService.parseSessionAccessToken(token);
                    bootstrap = false;
                } catch (RuntimeException selectedFailure) {
                    jws = jwtService.parseBootstrapToken(token);
                    bootstrap = true;
                }
                Claims claims = jws.getPayload();
                long userId = parsePositiveDecimal(claims.getSubject());
                UUID sessionId = UUID.fromString(requiredString(claims, "sid"));
                long sessionVersion = parsePositiveDecimal(requiredString(claims, "sv"));
                long rolesVersion = parsePositiveDecimal(requiredString(claims, "rv"));
                AuthRole role = null;
                RoleStatus status = null;
                Long groupId = null;
                boolean headman = false;
                boolean readOnly = false;
                if (!bootstrap) {
                    role = AuthRole.valueOf(requiredString(claims, "role"));
                    status = RoleStatus.valueOf(requiredString(claims, "status"));
                    String group = claims.get("group_id", String.class);
                    groupId = group == null ? null : parsePositiveDecimal(group);
                    headman = requiredBoolean(claims, "is_headman");
                    readOnly = requiredBoolean(claims, "readOnly");
                }
                SessionPrincipal principal = new SessionPrincipal(
                        userId, sessionId, sessionVersion, rolesVersion,
                        role, status, groupId, headman, readOnly);

                if (sessionStatePort != null && !admitted(principal, bootstrap, request, response)) {
                    if (Boolean.TRUE.equals(request.getAttribute(COOKIE_LOGOUT_FALLBACK_ATTRIBUTE))) {
                        filterChain.doFilter(request, response);
                    }
                    return;
                }

                if (bootstrap && !isBootstrapPathAllowed(request)) {
                    SecurityContextHolder.clearContext();
                    writeSessionFailure(request, response, AuthSessionException.Code.BOOTSTRAP_SCOPE_DENIED);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                role == null
                                        ? List.of()
                                        : List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
                if (isCookieBackedLogout(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                writeSessionFailure(request, response, AuthSessionException.Code.INVALID_SESSION);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean admitted(SessionPrincipal principal, boolean bootstrap,
                             HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionStatePort.SnapshotResult result;
        try {
                result = sessionStatePort.snapshot(new SessionStatePort.SnapshotCommand(
                        principal.userId(), principal.sessionId(), clock.instant()));
        } catch (RuntimeException exception) {
            writeSessionFailure(request, response, AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
            return false;
        }
        if (result == null) {
            writeSessionFailure(request, response, AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
            return false;
        }
        if (!result.succeeded()) {
            AuthSessionException.Code code = mapFailure(result.failureCode());
            if (isCookieBackedLogout(request) && canFallBackToCookieLogout(code)) {
                request.setAttribute(COOKIE_LOGOUT_FALLBACK_ATTRIBUTE, Boolean.TRUE);
                SecurityContextHolder.clearContext();
                return false;
            }
            writeSessionFailure(request, response, code);
            SecurityContextHolder.clearContext();
            return false;
        }
        SessionSnapshot snapshot = result.snapshot();
        if (snapshot == null || !snapshot.isLiveAt(clock.instant())
                || snapshot.userId() != principal.userId()
                || !snapshot.sessionId().equals(principal.sessionId())
                || snapshot.sessionVersion() != principal.sessionVersion()
                || snapshot.rolesVersion() != principal.rolesVersion()
                || bootstrap != (snapshot.activeRole() == null)
                || (!bootstrap && !sameIdentity(principal, snapshot))) {
            AuthSessionException.Code code = snapshot == null || !snapshot.isLiveAt(clock.instant())
                    ? AuthSessionException.Code.SESSION_REVOKED
                    : AuthSessionException.Code.SESSION_STATE_STALE;
            if (isCookieBackedLogout(request) && canFallBackToCookieLogout(code)) {
                request.setAttribute(COOKIE_LOGOUT_FALLBACK_ATTRIBUTE, Boolean.TRUE);
                SecurityContextHolder.clearContext();
                return false;
            }
            SecurityContextHolder.clearContext();
            writeSessionFailure(request, response, code);
            return false;
        }
        return true;
    }

    private static boolean sameIdentity(SessionPrincipal principal, SessionSnapshot snapshot) {
        var active = snapshot.activeRole();
        return active != null
                && principal.selectedRole() == active.role()
                && principal.selectedStatus() == active.status()
                && java.util.Objects.equals(principal.groupId(), active.groupId())
                && principal.headman() == (active.role() == AuthRole.HEADMAN)
                && principal.readOnly() == active.isReadOnly();
    }

    private void writeSessionFailure(HttpServletRequest request, HttpServletResponse response,
                                     AuthSessionException.Code code) throws IOException {
        int status = status(code);
        ErrorResponse body = new ErrorResponse(
                status, ErrorResponse.PROBLEM_BASE + "auth-session-failed",
                "Authentication session request failed",
                "The authentication session request was denied",
                request.getRequestURI(), java.time.Instant.now(), MDC.get("traceId"),
                null, null, java.util.Map.of("code", code.name()));
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    private static AuthSessionException.Code mapFailure(SessionStatePort.FailureCode code) {
        if (code == null) {
            return AuthSessionException.Code.AUTHORITY_UNAVAILABLE;
        }
        return switch (code) {
            case INVALID_SESSION -> AuthSessionException.Code.INVALID_SESSION;
            case SESSION_REVOKED -> AuthSessionException.Code.SESSION_REVOKED;
            case SESSION_STATE_STALE, SESSION_VERSION_CONFLICT -> AuthSessionException.Code.SESSION_STATE_STALE;
            case ROLE_NOT_GRANTED -> AuthSessionException.Code.ROLE_NOT_GRANTED;
            case ROLE_NOT_SELECTABLE -> AuthSessionException.Code.ROLE_NOT_SELECTABLE;
            case ROLE_READ_ONLY -> AuthSessionException.Code.ROLE_READ_ONLY;
            case AUTHORITY_UNAVAILABLE -> AuthSessionException.Code.AUTHORITY_UNAVAILABLE;
            default -> AuthSessionException.Code.INVALID_SESSION;
        };
    }

    private static int status(AuthSessionException.Code code) {
        return switch (code) {
            case PASSWORD_POLICY_VIOLATION, INVALID_CURSOR -> HttpServletResponse.SC_BAD_REQUEST;
            case CURRENT_PASSWORD_INVALID -> HttpServletResponse.SC_BAD_REQUEST;
            case INVALID_SESSION, SESSION_REVOKED, REFRESH_REJECTED -> HttpServletResponse.SC_UNAUTHORIZED;
            case ROLE_NOT_GRANTED, ROLE_NOT_SELECTABLE, ROLE_READ_ONLY,
                    BOOTSTRAP_SCOPE_DENIED -> HttpServletResponse.SC_FORBIDDEN;
            case SESSION_STATE_STALE, SESSION_VERSION_CONFLICT,
                    REFRESH_ALREADY_ROTATED -> HttpServletResponse.SC_CONFLICT;
            case AUTHORITY_UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        };
    }

    private static boolean isCookieAuthorizedPath(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return "POST".equalsIgnoreCase(method)
                && "/auth/refresh".equals(path);
    }

    private static boolean isCookieBackedLogout(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/auth/logout".equals(request.getRequestURI())) {
            return false;
        }
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader == null) {
            return false;
        }
        for (String part : cookieHeader.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0
                    && "rct_refresh".equals(part.substring(0, equals).trim())
                    && !part.substring(equals + 1).trim().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean canFallBackToCookieLogout(AuthSessionException.Code code) {
        return code != AuthSessionException.Code.AUTHORITY_UNAVAILABLE
                && code != AuthSessionException.Code.BOOTSTRAP_SCOPE_DENIED;
    }

    private static boolean isBootstrapPathAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("GET".equalsIgnoreCase(method) && ("/auth/session".equals(path)
                || "/auth/sessions".equals(path)))
                || ("PUT".equalsIgnoreCase(method) && "/auth/session/active-role".equals(path))
                || ("POST".equalsIgnoreCase(method) && ("/auth/logout".equals(path)
                || "/auth/logout-all".equals(path)));
    }

    private static String requiredString(Claims claims, String name) {
        String value = claims.get(name, String.class);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing claim");
        }
        return value;
    }

    private static boolean requiredBoolean(Claims claims, String name) {
        Boolean value = claims.get(name, Boolean.class);
        if (value == null) {
            throw new IllegalArgumentException("missing claim");
        }
        return value;
    }

    private static long parsePositiveDecimal(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("invalid decimal claim");
        }
        return Long.parseLong(value);
    }
}
