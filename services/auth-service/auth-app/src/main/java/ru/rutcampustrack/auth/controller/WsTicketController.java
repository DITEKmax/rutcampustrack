package ru.rutcampustrack.auth.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.WsTicketApi;
import ru.rutcampustrack.auth.dto.WsTicketResponse;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.service.WsTicketService;

/**
 * M03b Группа 3: issues short-lived tickets для WebSocket handshake.
 * Защищён access-JWT (Spring Security default + {@code JwtAuthenticationFilter}).
 * Никогда не отдаётся анонимно — {@code Authentication} обязателен.
 *
 * <p>Извлекает полный identity (userId/role/groupId/isHeadman) из access-JWT —
 * notification-service SubscriptionAuthInterceptor требует groupId + isHeadman.</p>
 */
@RestController
public class WsTicketController implements WsTicketApi {

    private final WsTicketService wsTicketService;
    private final JwtService jwtService;

    public WsTicketController(WsTicketService wsTicketService, JwtService jwtService) {
        this.wsTicketService = wsTicketService;
        this.jwtService = jwtService;
    }

    @Override
    public ResponseEntity<WsTicketResponse> issueTicket(Authentication authentication,
                                                        HttpServletRequest request) {
        long userId = Long.parseLong(authentication.getName());
        String role = extractRole(authentication);
        Claims claims = parseAccessTokenClaims(request);
        long groupId = extractLong(claims, "group_id", 0L);
        boolean isHeadman = Boolean.TRUE.equals(claims.get("is_headman", Boolean.class));

        WsTicketService.Issued issued = wsTicketService.issue(userId, role, groupId, isHeadman);
        return ResponseEntity.ok(new WsTicketResponse(issued.ticket(), issued.expiresAt()));
    }

    private Claims parseAccessTokenClaims(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException();
        }
        String token = authHeader.substring(7);
        try {
            return jwtService.parseToken(token).getPayload();
        } catch (Exception e) {
            throw new InvalidCredentialsException();
        }
    }

    private static long extractLong(Claims claims, String name, long defaultValue) {
        Object value = claims.get(name);
        if (value instanceof Number n) {
            return n.longValue();
        }
        return defaultValue;
    }

    private static String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("UNKNOWN");
    }
}
