package ru.rutcampustrack.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Sanitizes client identity headers and performs the local JWT admission
 * pre-check.  Live session admission is intentionally deferred to
 * {@code InternalJwtIssuerFilter}.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final ObjectMapper JWT_PAYLOAD_READER = new ObjectMapper();

    /** Set only by the live admission filter after its response is verified. */
    public static final String AUTHENTICATED_USER_ID_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".authenticatedUserId";
    /** Private exchange attributes shared with the next gateway filter. */
    public static final String ORIGINAL_ACCESS_TOKEN_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".originalAccessToken";
    public static final String EXPECTED_USER_ID_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".expectedUserId";
    public static final String EXPECTED_SESSION_ID_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".expectedSessionId";
    public static final String EXPECTED_SESSION_VERSION_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".expectedSessionVersion";
    public static final String EXPECTED_ROLES_VERSION_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".expectedRolesVersion";
    public static final String ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".originalAccessExpiry";

    private static final String EXPECTED_ISSUER = "rutcampustrack-auth";
    private static final String EXPECTED_ACCESS_AUDIENCE = "rutcampustrack";
    private static final String ACCESS_TOKEN_USE = "access";
    private static final String BOOTSTRAP_TOKEN_USE = "bootstrap";
    private static final Set<String> ALLOWED_ROLES =
            Set.of("STUDENT", "HEADMAN", "TEACHER", "ADMIN");
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("ACTIVE", "SUSPENDED", "EXPELLED", "GRADUATED", "ARCHIVED");

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/public-key",
            "/api/auth/tma",
            "/api/csp-report",
            "/swagger-ui.html"
    );

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/otp/",
            "/api/auth/password-reset/",
            "/api/ws/",
            "/swagger-ui/",
            "/v3/api-docs",
            "/openapi/"
    );

    private final PublicKeyConfig publicKeyConfig;

    public JwtAuthenticationFilter(PublicKeyConfig publicKeyConfig) {
        this.publicKeyConfig = publicKeyConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        clearAuthAttributes(exchange);
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(JwtAuthenticationFilter::removeClientIdentityHeaders)
                .build();
        exchange = exchange.mutate().request(sanitizedRequest).build();

        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring("Bearer ".length()).isBlank()) {
            if (isPublicRoute(path)) {
                return chain.filter(exchange);
            }
            return problem(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid session", "INVALID_SESSION");
        }

        String token = authorization.substring("Bearer ".length());
        if (isPublicRoute(path)) {
            return handlePublicBearer(exchange, chain, path, token);
        }

        try {
            Jws<Claims> parsed = parseAndVerify(token);
            Claims claims = parsed.getPayload();
            String tokenUse = requireExactStringClaim(claims, "token_use");

            if (BOOTSTRAP_TOKEN_USE.equals(tokenUse)) {
                validateBootstrapClaims(claims, token);
                if (!isBootstrapAllowed(exchange.getRequest().getMethod(), path)) {
                    return bootstrapScopeDenied(exchange);
                }
                return chain.filter(exchange);
            }
            if (!ACCESS_TOKEN_USE.equals(tokenUse)) {
                throw new IllegalArgumentException("unsupported token use");
            }

            putAccessExpectations(exchange, token, validateAccessClaims(claims, token));
            return chain.filter(exchange);
        } catch (JwtException | IllegalArgumentException | IllegalStateException e) {
            log.debug("JWT local validation failed ({})", e.getClass().getSimpleName());
            return problem(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid session", "INVALID_SESSION");
        }
    }

    private Mono<Void> handlePublicBearer(ServerWebExchange exchange,
                                           GatewayFilterChain chain,
                                           String path,
                                           String token) {
        try {
            Jws<Claims> parsed = parseAndVerify(token);
            Claims claims = parsed.getPayload();
            String tokenUse = requireExactStringClaim(claims, "token_use");
            if (BOOTSTRAP_TOKEN_USE.equals(tokenUse)) {
                validateBootstrapClaims(claims, token);
                if (!isBootstrapAllowed(exchange.getRequest().getMethod(), path)) {
                    return bootstrapScopeDenied(exchange);
                }
            } else if (ACCESS_TOKEN_USE.equals(tokenUse)
                    && isAccessAdmissionRoute(exchange.getRequest().getMethod(), path)) {
                putAccessExpectations(exchange, token, validateAccessClaims(claims, token));
            }
        } catch (JwtException | IllegalArgumentException | IllegalStateException e) {
            // Public auth and idempotent logout keep their existing unauthenticated
            // behavior.  A valid bootstrap token remains subject to its exact scope.
        }
        return chain.filter(exchange);
    }

    private Jws<Claims> parseAndVerify(String token) {
        PublicKey publicKey = publicKeyConfig.getPublicKey();
        if (!(publicKey instanceof RSAPublicKey)) {
            throw new IllegalStateException("Gateway public key is not RSA");
        }
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
        if (!"RS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("JWT algorithm must be RS256");
        }
        Claims claims = parsed.getPayload();
        requireExactStringClaim(claims, "iss", EXPECTED_ISSUER);
        requireSingletonAudience(claims, EXPECTED_ACCESS_AUDIENCE);
        return parsed;
    }

    private static AccessExpectations validateAccessClaims(Claims claims, String compactToken) {
        JsonNode originalPayload = readOriginalPayload(compactToken);
        requireExactStringClaim(claims, "token_use", ACCESS_TOKEN_USE);
        Date expiry = requireLiveTimeClaims(claims, originalPayload);
        String userId = requirePositiveLongStringClaim(claims, "sub");
        String sessionVersion = requirePositiveLongStringClaim(claims, "sv");
        String rolesVersion = requirePositiveLongStringClaim(claims, "rv");
        String sessionId = requireCanonicalUuidClaim(claims, "sid");
        requireAllowedStringClaim(claims, "role", ALLOWED_ROLES);
        requireAllowedStringClaim(claims, "status", ALLOWED_STATUSES);
        optionalPositiveLongStringClaim(claims, originalPayload, "group_id");
        requireBooleanClaim(claims, "is_headman");
        requireBooleanClaim(claims, "readOnly");
        return new AccessExpectations(userId, sessionId, sessionVersion, rolesVersion,
                expiry.toInstant());
    }

    private static void validateBootstrapClaims(Claims claims, String compactToken) {
        JsonNode originalPayload = readOriginalPayload(compactToken);
        requireExactStringClaim(claims, "token_use", BOOTSTRAP_TOKEN_USE);
        requireLiveTimeClaims(claims, originalPayload);
        requirePositiveLongStringClaim(claims, "sub");
        requirePositiveLongStringClaim(claims, "sv");
        requirePositiveLongStringClaim(claims, "rv");
        requireCanonicalUuidClaim(claims, "sid");
    }

    private static Date requireLiveTimeClaims(Claims claims, JsonNode originalPayload) {
        if (!isIntegralNumericDate(originalPayload, "iat")
                || !isIntegralNumericDate(originalPayload, "exp")) {
            throw new IllegalArgumentException("JWT iat and exp must be whole-second NumericDates");
        }
        Date issuedAt = claims.getIssuedAt();
        Date expiry = claims.getExpiration();
        if (issuedAt == null || !isWholeSecond(issuedAt)
                || expiry == null || !isWholeSecond(expiry)) {
            throw new IllegalArgumentException("JWT iat and exp must be whole-second NumericDates");
        }
        Instant now = Instant.now();
        if (!issuedAt.toInstant().isBefore(expiry.toInstant())
                || issuedAt.toInstant().isAfter(now)
                || !expiry.toInstant().isAfter(now)) {
            throw new IllegalArgumentException("JWT time claims are not live");
        }
        return expiry;
    }

    private static JsonNode readOriginalPayload(String compactToken) {
        try {
            String[] parts = compactToken.split("\\.", -1);
            if (parts.length != 3 || parts[1].isBlank()) {
                throw new IllegalArgumentException("JWT must have three compact parts");
            }
            JsonNode payload = JWT_PAYLOAD_READER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("JWT payload must be an object");
            }
            return payload;
        } catch (RuntimeException | java.io.IOException e) {
            throw new IllegalArgumentException("JWT payload is not valid JSON", e);
        }
    }

    private static boolean isIntegralNumericDate(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToLong();
    }

    private static boolean isWholeSecond(Date value) {
        return value.getTime() % 1_000 == 0;
    }

    private static String requirePositiveLongStringClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof String value) || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive Java-Long string");
        }
        try {
            if (Long.parseLong(value) <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " overflows Java Long", e);
        }
        return value;
    }

    private static String requireCanonicalUuidClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(name + " must be a string UUID");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException(name + " must be lowercase canonical UUID");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be lowercase canonical UUID", e);
        }
        return value;
    }

    private static String optionalPositiveLongStringClaim(Claims claims,
                                                         JsonNode originalPayload,
                                                         String name) {
        if (!originalPayload.has(name)) {
            return null;
        }
        JsonNode raw = originalPayload.get(name);
        if (raw == null || !raw.isTextual()) {
            throw new IllegalArgumentException(name + " must be a positive Java-Long string");
        }
        return requirePositiveLongStringClaim(claims, name);
    }

    private static void requireAllowedStringClaim(Claims claims,
                                                  String name,
                                                  Set<String> allowed) {
        String value = requireExactStringClaim(claims, name);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(name + " is not recognized");
        }
    }

    private static void requireBooleanClaim(Claims claims, String name) {
        if (!(claims.get(name) instanceof Boolean)) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
    }

    private static String requireExactStringClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return value;
    }

    private static void requireExactStringClaim(Claims claims, String name, String expected) {
        if (!expected.equals(requireExactStringClaim(claims, name))) {
            throw new IllegalArgumentException(name + " has an unexpected value");
        }
    }

    private static void requireSingletonAudience(Claims claims, String expected) {
        Object raw = claims.get("aud");
        if (raw instanceof String value) {
            if (!expected.equals(value)) {
                throw new IllegalArgumentException("JWT audience is not allowed");
            }
        } else if (raw instanceof Collection<?> values) {
            if (values.size() != 1 || !(values.iterator().next() instanceof String value)
                    || !expected.equals(value)) {
                throw new IllegalArgumentException("JWT audience must be a singleton");
            }
        } else {
            throw new IllegalArgumentException("JWT audience has an invalid type");
        }

        Set<String> parsedAudience = claims.getAudience();
        if (parsedAudience == null || parsedAudience.size() != 1
                || !parsedAudience.contains(expected)) {
            throw new IllegalArgumentException("JWT audience must be a singleton");
        }
    }

    private static boolean isBootstrapAllowed(HttpMethod method, String path) {
        return (HttpMethod.GET.equals(method) && "/api/auth/session".equals(path))
                || (HttpMethod.PUT.equals(method) && "/api/auth/session/active-role".equals(path))
                || (HttpMethod.GET.equals(method) && "/api/auth/sessions".equals(path))
                || (HttpMethod.POST.equals(method) && "/api/auth/logout".equals(path))
                || (HttpMethod.POST.equals(method) && "/api/auth/logout-all".equals(path));
    }

    private static boolean isAccessAdmissionRoute(HttpMethod method, String path) {
        return HttpMethod.POST.equals(method) && "/api/auth/logout".equals(path);
    }

    private static void putAccessExpectations(ServerWebExchange exchange,
                                              String token,
                                              AccessExpectations expectations) {
        exchange.getAttributes().put(ORIGINAL_ACCESS_TOKEN_ATTRIBUTE, token);
        exchange.getAttributes().put(EXPECTED_USER_ID_ATTRIBUTE, expectations.userId());
        exchange.getAttributes().put(EXPECTED_SESSION_ID_ATTRIBUTE, expectations.sessionId());
        exchange.getAttributes().put(EXPECTED_SESSION_VERSION_ATTRIBUTE,
                expectations.sessionVersion());
        exchange.getAttributes().put(EXPECTED_ROLES_VERSION_ATTRIBUTE,
                expectations.rolesVersion());
        exchange.getAttributes().put(ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE,
                expectations.accessExpiry());
    }

    private static boolean isPublicRoute(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private static void clearAuthAttributes(ServerWebExchange exchange) {
        exchange.getAttributes().remove(AUTHENTICATED_USER_ID_ATTRIBUTE);
        exchange.getAttributes().remove(ORIGINAL_ACCESS_TOKEN_ATTRIBUTE);
        exchange.getAttributes().remove(EXPECTED_USER_ID_ATTRIBUTE);
        exchange.getAttributes().remove(EXPECTED_SESSION_ID_ATTRIBUTE);
        exchange.getAttributes().remove(EXPECTED_SESSION_VERSION_ATTRIBUTE);
        exchange.getAttributes().remove(EXPECTED_ROLES_VERSION_ATTRIBUTE);
        exchange.getAttributes().remove(ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE);
    }

    private static void removeClientIdentityHeaders(HttpHeaders headers) {
        List<String> names = List.copyOf(headers.keySet());
        names.stream()
                .filter(JwtAuthenticationFilter::isClientIdentityHeader)
                .forEach(headers::remove);
    }

    private static boolean isClientIdentityHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("x-user-")
                || normalized.equals("x-internal-token")
                || normalized.equals("x-login")
                || normalized.equals("x-group-id")
                || normalized.equals("x-is-headman");
    }

    private Mono<Void> bootstrapScopeDenied(ServerWebExchange exchange) {
        return problem(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                "Bootstrap token scope denied", "BOOTSTRAP_SCOPE_DENIED");
    }

    private Mono<Void> problem(ServerWebExchange exchange,
                               HttpStatus status,
                               String title,
                               String detail,
                               String code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl("no-store");
        String body = "{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\",\"code\":\"%s\"}"
                .formatted(status.value(), title, detail, code);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    public record AccessExpectations(String userId,
                                     String sessionId,
                                     String sessionVersion,
                                     String rolesVersion,
                                     Instant accessExpiry) {
    }
}
