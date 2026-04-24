package ru.rutcampustrack.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * M03a: guards {@code /internal/**} endpoints on auth-service. Verifies the
 * {@code X-Internal-Issuer-Secret} header with timing-safe comparison.
 *
 * Pattern mirrors {@code GrpcSecretInterceptor} — defence in depth for the
 * token-exchange endpoint (even though it sits inside the docker private-net).
 */
@Component
public class InternalIssuerSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Issuer-Secret";
    private static final String INTERNAL_PREFIX = "/internal/";

    private static final Logger log = LoggerFactory.getLogger(InternalIssuerSecretFilter.class);

    private final byte[] expectedSecret;

    public InternalIssuerSecretFilter(InternalIssuerProperties properties) {
        this.expectedSecret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || provided.isBlank()) {
            log.warn("Missing {} header on {}", HEADER, request.getRequestURI());
            writeUnauthorized(response, "Missing internal-issuer secret");
            return;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSecret, providedBytes)) {
            log.warn("Invalid {} header on {}", HEADER, request.getRequestURI());
            writeUnauthorized(response, "Invalid internal-issuer secret");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        response.getWriter().flush();
    }
}
