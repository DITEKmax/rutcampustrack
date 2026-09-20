package ru.rutcampustrack.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.AuthSessionApi;
import ru.rutcampustrack.auth.dto.AccountHistoryEvent;
import ru.rutcampustrack.auth.dto.AccountHistoryPage;
import ru.rutcampustrack.auth.dto.AuthSessionSummary;
import ru.rutcampustrack.auth.dto.AuthSessionsPage;
import ru.rutcampustrack.auth.dto.CurrentSessionResponse;
import ru.rutcampustrack.auth.dto.PasswordPolicyResponse;
import ru.rutcampustrack.auth.dto.RoleGrantResponse;
import ru.rutcampustrack.auth.dto.SelectActiveRoleRequest;
import ru.rutcampustrack.auth.dto.SelectActiveRoleResponse;
import ru.rutcampustrack.auth.security.AuthCookies;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.WsTicketService;
import ru.rutcampustrack.auth.session.PasswordPolicy;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class AuthSessionController implements AuthSessionApi {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionController.class);

    private final AuthService authService;
    private final AuthSessionQueryPort queryPort;
    private final WsTicketService wsTicketService;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    public AuthSessionController(
            AuthService authService,
            AuthSessionQueryPort queryPort,
            WsTicketService wsTicketService) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort");
        this.wsTicketService = Objects.requireNonNull(wsTicketService, "wsTicketService");
    }

    @Override
    public ResponseEntity<CurrentSessionResponse> currentSession(Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        return noStore(ResponseEntity.ok(toCurrent(authService.admit(principal))));
    }

    @Override
    public ResponseEntity<SelectActiveRoleResponse> selectActiveRole(
            SelectActiveRoleRequest request, Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        SessionStatePort.RoleSelectionResult result = authService.selectRole(
                principal, request, null, null);
        SessionSnapshot snapshot = result.snapshot();
        ru.rutcampustrack.auth.dto.TokenResponse selected = authService.selectedAccess(result);
        return noStore(ResponseEntity.ok(new SelectActiveRoleResponse(
                selected.accessToken(), selected.expiresIn(),
                toCurrent(snapshot))));
    }

    @Override
    public ResponseEntity<AuthSessionsPage> sessions(
            String cursor, Integer limit, Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        authService.admit(principal);
        AuthSessionQueryPort.SessionPage page = queryPort.findLiveSessions(
                principal.userId(), principal.sessionId(), cursor, limit == null ? 0 : limit);
        List<AuthSessionSummary> items = page.items().stream()
                .map(item -> new AuthSessionSummary(
                        item.sessionId().toString(), item.authMethod(), item.clientLabel(),
                        item.locationLabel(), item.createdAt(), item.lastSeenAt(),
                        item.sessionId().equals(principal.sessionId())))
                .toList();
        return noStore(ResponseEntity.ok(new AuthSessionsPage(items, page.nextCursor())));
    }

    @Override
    public ResponseEntity<Void> logout(String refreshCookie, Authentication authentication) {
        SessionPrincipal current = authentication != null
                && authentication.getPrincipal() instanceof SessionPrincipal sessionPrincipal
                ? sessionPrincipal : null;
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            SessionStatePort.RevokeResult result = authService.logoutRefreshCookie(refreshCookie, current);
            if (result != null && result.snapshot() != null) {
                invalidateWsTicketsBestEffort(result.snapshot().userId());
            }
        } else if (current != null) {
            authService.logout(current);
            invalidateWsTicketsBestEffort(current.userId());
        }
        return clearedCookie();
    }

    @Override
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        authService.logoutAll(principal);
        invalidateWsTicketsBestEffort(principal.userId());
        return clearedCookie();
    }

    @Override
    public ResponseEntity<AccountHistoryPage> accountHistory(
        String cursor, Integer limit, Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        if (principal.isBootstrap()) {
            throw new ru.rutcampustrack.auth.session.AuthSessionException(
                    ru.rutcampustrack.auth.session.AuthSessionException.Code.BOOTSTRAP_SCOPE_DENIED);
        }
        authService.admit(principal);
        AuthSessionQueryPort.HistoryPage page = queryPort.findHistory(
                principal.userId(), cursor, limit == null ? 0 : limit);
        List<AccountHistoryEvent> items = page.items().stream()
                .map(item -> new AccountHistoryEvent(
                        Long.toString(item.id()), item.type(), item.occurredAt(), item.authMethod(),
                        item.clientLabel(), item.locationLabel()))
                .toList();
        return noStore(ResponseEntity.ok(new AccountHistoryPage(items, page.nextCursor())));
    }

    @Override
    public ResponseEntity<Void> changePassword(
            ru.rutcampustrack.auth.dto.ChangePasswordRequest request,
            Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        authService.changePassword(principal, request);
        invalidateWsTicketsBestEffort(principal.userId());
        return clearedCookie();
    }

    private void invalidateWsTicketsBestEffort(long userId) {
        try {
            wsTicketService.invalidateAllFor(userId);
        } catch (RuntimeException exception) {
            // Redis is an optional after-commit signal; never turn a durable
            // logout/password change into a failed HTTP response.
            log.warn("Optional WebSocket ticket cleanup failed after committed auth-session change: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private CurrentSessionResponse toCurrent(SessionSnapshot snapshot) {
        AuthSessionQueryPort.UserIdentity identity = queryPort.findUserIdentity(snapshot.userId())
                .orElseThrow(() -> new ru.rutcampustrack.auth.session.AuthSessionException(
                        ru.rutcampustrack.auth.session.AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        String groupLabel = snapshot.activeRole() == null || snapshot.activeRole().groupId() == null
                ? null : identity.groupLabels().get(snapshot.activeRole().groupId());
        List<RoleGrantResponse> roles = snapshot.roles().stream()
                .map(grant -> toRole(grant, identity))
                .toList();
        return new CurrentSessionResponse(
                snapshot.sessionId().toString(), Long.toString(snapshot.userId()), identity.displayName(),
                groupLabel, Long.toString(snapshot.sessionVersion()), Long.toString(snapshot.rolesVersion()),
                snapshot.activeRole() == null ? null : snapshot.activeRole().role().name(), roles,
                snapshot.isReadOnly(), new PasswordPolicyResponse(
                        PasswordPolicy.MIN_CODE_POINTS, PasswordPolicy.MAX_UTF8_BYTES,
                        true, List.of("P", "S"), "NONE"));
    }

    private static RoleGrantResponse toRole(RoleGrant grant, AuthSessionQueryPort.UserIdentity identity) {
        return new RoleGrantResponse(
                Long.toString(grant.grantId()), grant.role().name(), grant.status().name(),
                grant.groupId() == null ? null : Long.toString(grant.groupId()),
                grant.groupId() == null ? null : identity.groupLabels().get(grant.groupId()),
                grant.isSelectable(), grant.isReadOnly());
    }

    private static SessionPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new ru.rutcampustrack.auth.session.AuthSessionException(
                    ru.rutcampustrack.auth.session.AuthSessionException.Code.INVALID_SESSION);
        }
        return principal;
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(headers -> headers.addAll(response.getHeaders()))
                .cacheControl(CacheControl.noStore())
                .body(response.getBody());
    }

    private static ResponseEntity<Void> clearedCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clear().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
