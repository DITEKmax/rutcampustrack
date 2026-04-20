package ru.rutcampustrack.academic.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.security.DualModeUserContextFilter;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

/**
 * M03a: academic-service adaptation of {@link DualModeUserContextFilter}.
 *
 * Bridges the validated Internal JWT (or legacy {@code X-User-*} headers while
 * {@code legacyHeadersEnabled} is true) into the request-scoped
 * {@link RequestContext}, which depends on the contract-module {@link UserRole} enum.
 */
@Component
public class AcademicUserContextFilter extends DualModeUserContextFilter {

    private final RequestContext requestContext;

    public AcademicUserContextFilter(InternalJwtValidator validator,
                                     InternalJwtProperties properties,
                                     RequestContext requestContext,
                                     BusinessMetrics businessMetrics) {
        super(validator, properties, businessMetrics);
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
