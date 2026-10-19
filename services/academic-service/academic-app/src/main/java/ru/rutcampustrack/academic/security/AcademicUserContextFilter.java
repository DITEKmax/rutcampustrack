package ru.rutcampustrack.academic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.security.DualModeUserContextFilter;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.io.IOException;

/**
 * M03a: academic-service adaptation of {@link DualModeUserContextFilter}.
 *
 * <p>Bridges the validated Internal JWT (or legacy {@code X-User-*} headers while {@code
 * legacyHeadersEnabled} is true) into the request-scoped {@link RequestContext}, which depends on
 * the contract-module {@link UserRole} enum.
 */
@Component
public class AcademicUserContextFilter extends DualModeUserContextFilter {
    private static final org.slf4j.Logger previewLog =
            org.slf4j.LoggerFactory.getLogger(AcademicUserContextFilter.class);

    private final RequestContext requestContext;
    private final InternalJwtProperties properties;
    private final InternalJwtValidator reportValidator;
    private final ru.rutcampustrack.academic.exportjobs.ExportJobRepository reports;

    @org.springframework.beans.factory.annotation.Autowired
    private AcademicLiveSessionClient liveSessions;

    public AcademicUserContextFilter(
            InternalJwtValidator validator,
            InternalJwtProperties properties,
            RequestContext requestContext,
            BusinessMetrics businessMetrics) {
        this(validator, properties, requestContext, businessMetrics, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AcademicUserContextFilter(
            InternalJwtValidator validator,
            InternalJwtProperties properties,
            RequestContext requestContext,
            BusinessMetrics businessMetrics,
            ru.rutcampustrack.academic.exportjobs.ExportJobRepository reports) {
        super(validator, properties, businessMetrics);
        this.requestContext = requestContext;
        this.properties = properties;
        this.reportValidator = validator;
        this.reports = reports;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Forward only the exact signed token accepted at the HTTP boundary.
        // Schedule validates the signature and frozen claims independently;
        // legacy X-User-* headers never become a binding actor identity.
        requestContext.setInternalToken(request.getHeader(properties.headerName()));
        String path = request.getServletPath();
        if ("POST".equals(request.getMethod())
                && "/academic/internal/export-jobs/admit".equals(path)) {
            // The exact controller admits Notification's distinct directed service credential.
            chain.doFilter(request, response);
            return;
        }
        if ("GET".equals(request.getMethod())
                && path.matches("/academic/report-download/[A-Za-z0-9_-]{43}")) {
            // The capability controller performs redemption and current owner authorization.
            chain.doFilter(request, response);
            return;
        }
        if ("POST".equals(request.getMethod())
                && path.matches("/academic/export-jobs/[0-9a-f-]{36}/render")) {
            if (reports == null) {
                response.sendError(503);
                return;
            }
            try {
                String id =
                        path.substring(
                                "/academic/export-jobs/".length(),
                                path.length() - "/render".length());
                var job = reports.load(java.util.UUID.fromString(id));
                if (!job.id().toString().equals(id)
                        || job.leaseId() == null
                        || !"preparing".equals(job.state())
                        || job.leaseUntil() == null
                        || !java.time.Instant.now().isBefore(job.leaseUntil())) {
                    response.sendError(403);
                    return;
                }
                var claims =
                        reportValidator.validateAcademicReport(
                                request.getHeader(properties.headerName()),
                                job.identity().report().kind().code(),
                                job.identity().report().bindingHash(),
                                id,
                                job.leaseId().toString());
                if (!claims.equals(job.identity().actor())) {
                    response.sendError(403);
                    return;
                }
                applyInternalJwt(claims);
                chain.doFilter(request, response);
            } catch (ru.rutcampustrack.shared.security.InternalJwtException
                    | IllegalArgumentException denied) {
                response.sendError(401);
            } catch (org.springframework.web.server.ResponseStatusException unavailable) {
                response.sendError(unavailable.getStatusCode().value());
            }
            return;
        }
        try {
            super.doFilterInternal(request, response, chain);
        } catch (io.grpc.StatusRuntimeException unavailable) {
            previewLog.warn(
                    "semester.preview failure source=ACADEMIC_CONTEXT stage=CURRENT_CONTEXT"
                        + " status={} cause={}",
                    unavailable.getStatus().getCode().name(),
                    previewCause(unavailable, true));
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Current session authority unavailable");
        }
    }

    @Override
    protected void applyInternalJwt(InternalJwtClaims claims, String rawToken) {
        // The base filter invokes this hook only for an ordinary validated identity.
        // The exact report-purpose branch above deliberately calls the one-argument
        // mapper and is admitted by its separate report capability path.
        ru.rutcampustrack.shared.security.admin.AdminAccessProfile profile;
        try {
            profile = liveSessions.admit(claims);
        } catch (io.grpc.StatusRuntimeException denied) {
            switch (denied.getStatus().getCode()) {
                case PERMISSION_DENIED, UNAUTHENTICATED, FAILED_PRECONDITION, INVALID_ARGUMENT ->
                        throw new ru.rutcampustrack.shared.security.InternalJwtException(
                                "Current session rejected");
                default -> throw denied;
            }
        }
        applyInternalJwt(claims);
        requestContext.setInternalToken(rawToken);
        requestContext.setAdmittedAdminProfile(profile);
    }

    @Override
    protected void applyInternalJwt(InternalJwtClaims claims) {
        requestContext.setClaims(claims);
        requestContext.setUserId(claims.userId());
        requestContext.setRole(UserRole.valueOf(claims.domainRole().toUpperCase()));
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

    private static String previewCause(Throwable failure, boolean rpc) {
        boolean dns = false, tls = false, connect = false, timeout = false, io = false;
        try {
            for (int depth = 0;
                    failure != null && depth < 8;
                    depth++, failure = failure.getCause()) {
                dns |=
                        failure instanceof java.net.UnknownHostException
                                || failure instanceof java.nio.channels.UnresolvedAddressException;
                tls |= failure instanceof javax.net.ssl.SSLException;
                connect |= failure instanceof java.net.ConnectException;
                timeout |=
                        failure instanceof java.net.SocketTimeoutException
                                || failure instanceof java.net.http.HttpTimeoutException
                                || failure instanceof java.util.concurrent.TimeoutException;
                io |= failure instanceof java.io.IOException;
            }
        } catch (RuntimeException ignored) {
            return "UNKNOWN";
        }
        if (dns) return "DNS";
        if (tls) return "TLS";
        if (connect) return "CONNECT";
        if (timeout) return "TIMEOUT";
        if (io) return "TRANSPORT_IO";
        return rpc ? "RPC_STATUS" : "UNKNOWN";
    }
}
