package ru.rutcampustrack.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.io.IOException;

/**
 * Dual-mode user context filter — transition helper for C0-1 rollout.
 *
 * <ul>
 *   <li>If {@code X-Internal-Token} is present → validate and apply claims (strict path).</li>
 *   <li>If absent AND {@link InternalJwtProperties#legacyHeadersEnabled()} → fall back to
 *       {@code X-User-Id}/{@code X-User-Role}/{@code X-Group-Id}/{@code X-Is-Headman}.</li>
 *   <li>Otherwise → 401 Unauthorized.</li>
 * </ul>
 *
 * Downstream services extend this filter and implement the two {@code apply*} hooks
 * to bridge into their service-specific {@code RequestContext} (which depends on
 * the service's {@code UserRole} enum from its contract module).
 *
 * <p>M04 Группа 8 — KI-2 counter {@code internal_jwt_fallback_total{from,to}}
 * инкрементируется при silent fallback на legacy headers, чтобы видеть когда
 * service-to-service calls всё ещё работают без internal JWT.
 */
public abstract class DualModeUserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DualModeUserContextFilter.class);

    private final InternalJwtValidator validator;
    private final InternalJwtProperties properties;
    private final BusinessMetrics businessMetrics;

    protected DualModeUserContextFilter(InternalJwtValidator validator,
                                        InternalJwtProperties properties) {
        this(validator, properties, null);
    }

    protected DualModeUserContextFilter(InternalJwtValidator validator,
                                        InternalJwtProperties properties,
                                        BusinessMetrics businessMetrics) {
        this.validator = validator;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (isExcludedPath(request)) {
            chain.doFilter(request, response);
            return;
        }

        String internalToken = request.getHeader(properties.headerName());
        if (internalToken != null && !internalToken.isBlank()) {
            try {
                InternalJwtClaims claims = validator.validate(internalToken);
                applyInternalJwt(claims);
                chain.doFilter(request, response);
                return;
            } catch (InternalJwtException e) {
                log.warn("Internal JWT rejected: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal token");
                return;
            }
        }

        if (properties.legacyHeadersEnabled()) {
            String legacyUserId = request.getHeader("X-User-Id");
            if (legacyUserId != null && !legacyUserId.isBlank()) {
                try {
                    applyLegacyHeaders(request);
                    // M04 Группа 8 / KI-2 — silent fallback. Инкрементим только
                    // при успешном применении legacy headers (иначе counter
                    // шумит от уже отваленных 401). businessMetrics может
                    // быть null в юнит-тестах — защищаемся.
                    if (businessMetrics != null) {
                        businessMetrics.internalJwtFallbackCounter("internal-jwt", "legacy-headers")
                                .increment();
                    }
                    chain.doFilter(request, response);
                    return;
                } catch (Exception e) {
                    log.warn("Legacy X-User-* headers rejected: {}", e.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid user headers");
                    return;
                }
            }
            // No identity headers at all — let downstream decide (permit-all endpoints).
            chain.doFilter(request, response);
            return;
        }

        // Strict mode + no internal token = 401.
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal token required");
    }

    /**
     * Hook for downstream service to write claims into service-specific RequestContext.
     */
    protected abstract void applyInternalJwt(InternalJwtClaims claims);

    /**
     * Hook for downstream service to parse legacy X-User-* headers into RequestContext.
     * Called only when {@link InternalJwtProperties#legacyHeadersEnabled()} is true.
     */
    protected abstract void applyLegacyHeaders(HttpServletRequest request);

    /**
     * Override to add service-specific exclusions; combine with
     * {@link #isInfrastructurePath(HttpServletRequest)} via {@code super.isExcluded...}
     * or the convenience method {@link #isInfrastructurePath(HttpServletRequest)}.
     *
     * <p>Default returns {@link #isInfrastructurePath(HttpServletRequest)} — covers
     * actuator / swagger / api-docs which MUST stay reachable even in strict mode
     * (otherwise Docker HEALTHCHECK on {@code /actuator/health} fails and containers
     * enter unhealthy loop, M03a Группа 16 audit finding H3).</p>
     */
    protected boolean isExcludedPath(HttpServletRequest request) {
        return isInfrastructurePath(request);
    }

    /**
     * Infrastructure paths that must bypass identity checks:
     * <ul>
     *   <li>{@code /actuator/**} — Spring Boot health/metrics/prometheus.</li>
     *   <li>{@code /api-docs**}, {@code /v3/api-docs**} — OpenAPI schema.</li>
     *   <li>{@code /swagger-ui**} — dev/staging API explorer.</li>
     * </ul>
     */
    protected static boolean isInfrastructurePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
