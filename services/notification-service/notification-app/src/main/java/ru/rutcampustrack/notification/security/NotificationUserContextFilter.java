package ru.rutcampustrack.notification.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.shared.security.DualModeUserContextFilter;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

/**
 * M03a: notification-service adaptation of {@link DualModeUserContextFilter}.
 * Bridges Internal JWT claims (or legacy X-User-* headers) into {@link RequestContext}.
 *
 * Note: WebSocket handshake flow uses {@code JwtHandshakeInterceptor} with external
 * JWT (query-string ticket flow scope of M03b). Only REST endpoints (/push/*) use
 * this filter.
 */
@Component
public class NotificationUserContextFilter extends DualModeUserContextFilter {

    private final RequestContext requestContext;

    public NotificationUserContextFilter(InternalJwtValidator validator,
                                         InternalJwtProperties properties,
                                         RequestContext requestContext) {
        super(validator, properties);
        this.requestContext = requestContext;
    }

    @Override
    protected void applyInternalJwt(InternalJwtClaims claims) {
        requestContext.setUserId(claims.userId());
        requestContext.setRole(UserRole.valueOf(claims.role().toUpperCase()));
        requestContext.setGroupId(claims.groupId());
        requestContext.setHeadman(claims.isHeadman());
    }

    @Override
    protected void applyLegacyHeaders(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        requestContext.setUserId(Long.parseLong(userIdHeader));
        requestContext.setRole(UserRole.valueOf(request.getHeader("X-User-Role").toUpperCase()));
        String groupIdHeader = request.getHeader("X-Group-Id");
        if (groupIdHeader != null && !groupIdHeader.isEmpty()) {
            requestContext.setGroupId(Long.parseLong(groupIdHeader));
        }
        requestContext.setHeadman(Boolean.parseBoolean(request.getHeader("X-Is-Headman")));
    }

    @Override
    protected boolean isExcludedPath(HttpServletRequest request) {
        // WebSocket upgrade uses JwtHandshakeInterceptor with external JWT (M03b ws-ticket scope).
        // + infrastructure paths (actuator/api-docs/swagger-ui) from base class.
        String path = request.getRequestURI();
        return path.startsWith("/ws") || isInfrastructurePath(request);
    }
}
