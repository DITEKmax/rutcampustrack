package ru.rutcampustrack.schedule.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.shared.security.DualModeUserContextFilter;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

/**
 * M03a: schedule-service adaptation of {@link DualModeUserContextFilter}.
 * Bridges Internal JWT claims (or legacy X-User-* headers) into {@link RequestContext}.
 */
@Component
public class ScheduleUserContextFilter extends DualModeUserContextFilter {

    private final RequestContext requestContext;

    public ScheduleUserContextFilter(InternalJwtValidator validator,
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
}
