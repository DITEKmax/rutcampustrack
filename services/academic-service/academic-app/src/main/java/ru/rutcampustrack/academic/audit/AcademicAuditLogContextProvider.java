package ru.rutcampustrack.academic.audit;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.observability.MdcKeys;
import ru.rutcampustrack.shared.web.audit.AuditLogContextProvider;

/**
 * M16 G6 — academic-app implementation {@link AuditLogContextProvider}.
 *
 * <p>Резолвит current user из academic-specific {@link RequestContext}
 * (request-scoped bean). Correlation ID — из shared-observability MDC
 * ({@link MdcKeys#TRACE_ID}).
 *
 * <p>{@code RequestContext} request-scoped → обёрнут в
 * {@code @Scope(proxyMode = TARGET_CLASS)}. ObjectProvider даёт fail-safe
 * resolution: вне request scope (system thread, тест без context)
 * вернётся {@code null} вместо {@code ScopeNotActiveException}.
 */
@Component
public class AcademicAuditLogContextProvider implements AuditLogContextProvider {

    private final ObjectProvider<RequestContext> requestContextProvider;

    public AcademicAuditLogContextProvider(ObjectProvider<RequestContext> requestContextProvider) {
        this.requestContextProvider = requestContextProvider;
    }

    @Override
    public Long currentUserId() {
        try {
            RequestContext ctx = requestContextProvider.getIfAvailable();
            return ctx == null ? null : ctx.getUserId();
        } catch (Exception e) {
            // Out of request scope (system thread / test) — return null gracefully.
            return null;
        }
    }

    @Override
    public String currentCorrelationId() {
        return MDC.get(MdcKeys.TRACE_ID);
    }
}
