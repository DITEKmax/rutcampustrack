package ru.rutcampustrack.gateway.security;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;
import ru.rutcampustrack.gateway.filter.JwtAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Exchanges one locally validated access token for a live, signed internal
 * identity.  The downstream request is trusted only after both the authority
 * response and the internal JWT agree with the original access expectations.
 */
@Component
public class InternalJwtIssuerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalJwtIssuerFilter.class);
    private static final ObjectMapper JWT_PAYLOAD_READER = new ObjectMapper();
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String EXPECTED_ISSUER = "rutcampustrack-auth";
    private static final String EXPECTED_INTERNAL_AUDIENCE = "rutcampustrack-internal";
    private static final String INTERNAL_TOKEN_USE = "internal";
    private static final int MAX_WIRE_STRING_LENGTH = 64;

    private static final Set<String> ALLOWED_ROLES =
            Set.of("STUDENT", "HEADMAN", "TEACHER", "ADMIN");
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("ACTIVE", "EXPELLED", "GRADUATED", "ARCHIVED");
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("EXPELLED", "GRADUATED", "ARCHIVED");

    private final InternalJwtIssuerClient client;
    private final PublicKeyConfig publicKeyConfig;

    public InternalJwtIssuerFilter(InternalJwtIssuerClient client,
                                   PublicKeyConfig publicKeyConfig) {
        this.client = client;
        this.publicKeyConfig = publicKeyConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String accessToken = exchange.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE);
        if (accessToken == null || accessToken.isBlank()) {
            return chain.filter(exchange);
        }

        AccessExpectations expectations;
        try {
            expectations = readExpectations(exchange);
        } catch (RuntimeException e) {
            return serviceUnavailable(exchange);
        }

        return client.admit(accessToken)
                .flatMap(response -> prepareForwardedChain(exchange, chain, response, expectations))
                .onErrorResume(InternalAdmissionDeniedException.class,
                        error -> problem(exchange, error.publicStatus(), publicTitle(error.publicCode()),
                                publicDetail(error.publicCode()), error.publicCode()))
                .onErrorResume(InternalIssuerUnavailableException.class,
                        error -> serviceUnavailable(exchange))
                .onErrorResume(DownstreamChainFailure.class,
                        error -> Mono.error(error.getCause()));
    }

    private Mono<Void> prepareForwardedChain(ServerWebExchange exchange,
                                             GatewayFilterChain chain,
                                             AuthAdmissionResponse response,
                                             AccessExpectations expectations) {
        final ServerWebExchange forwardedExchange;
        try {
            validateAdmission(response, expectations);
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(InternalJwtIssuerFilter::removeIdentityHeaders)
                    .header(INTERNAL_TOKEN_HEADER, response.internalToken())
                    .build();
            exchange.getAttributes().put(
                    JwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE,
                    response.userId());
            forwardedExchange = exchange.mutate().request(request).build();
        } catch (RuntimeException e) {
            return Mono.error(unavailable(e));
        }

        return Mono.defer(() -> chain.filter(forwardedExchange))
                .onErrorMap(InternalJwtIssuerFilter::wrapDownstreamError);
    }

    private static Throwable wrapDownstreamError(Throwable error) {
        if (error instanceof DownstreamChainFailure) {
            return error;
        }
        return new DownstreamChainFailure(error);
    }

    private static AccessExpectations readExpectations(ServerWebExchange exchange) {
        String userId = requiredAttribute(exchange, JwtAuthenticationFilter.EXPECTED_USER_ID_ATTRIBUTE);
        String sessionId = requiredAttribute(exchange, JwtAuthenticationFilter.EXPECTED_SESSION_ID_ATTRIBUTE);
        String sessionVersion = requiredAttribute(
                exchange, JwtAuthenticationFilter.EXPECTED_SESSION_VERSION_ATTRIBUTE);
        String rolesVersion = requiredAttribute(
                exchange, JwtAuthenticationFilter.EXPECTED_ROLES_VERSION_ATTRIBUTE);
        Instant accessExpiry = exchange.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE);
        if (accessExpiry == null) {
            throw new IllegalArgumentException("missing access expiry expectation");
        }
        return new AccessExpectations(userId, sessionId, sessionVersion, rolesVersion, accessExpiry);
    }

    private static String requiredAttribute(ServerWebExchange exchange, String name) {
        Object value = exchange.getAttribute(name);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("missing auth expectation");
        }
        return string;
    }

    private void validateAdmission(AuthAdmissionResponse response,
                                   AccessExpectations expectations) {
        if (response == null || response.internalToken() == null
                || response.internalToken().isBlank()
                || response.internalToken().length() > 16_384
                || response.expiresAt() == null) {
            throw new IllegalArgumentException("incoherent admission response");
        }

        String userId = requirePositiveLongString(response.userId(), "userId");
        String sessionId = requireCanonicalUuid(response.sessionId(), "sessionId");
        String sessionVersion = requirePositiveLongString(response.sessionVersion(), "sessionVersion");
        String rolesVersion = requirePositiveLongString(response.rolesVersion(), "rolesVersion");
        String role = requireKnownRole(response.role());
        String status = requireAdmissionStatus(response.status());
        String groupId = response.groupId() == null
                ? null : requirePositiveLongString(response.groupId(), "groupId");

        if (!expectations.userId().equals(userId)
                || !expectations.sessionId().equals(sessionId)
                || !expectations.sessionVersion().equals(sessionVersion)
                || !expectations.rolesVersion().equals(rolesVersion)) {
            throw new IllegalArgumentException("admission response does not match access session");
        }
        if (response.isHeadman() != "HEADMAN".equals(role)) {
            throw new IllegalArgumentException("headman flag does not match role");
        }
        if (response.readOnly() != TERMINAL_STATUSES.contains(status)) {
            throw new IllegalArgumentException("readOnly flag does not match status");
        }

        verifyInternalToken(response, userId, sessionId, sessionVersion, rolesVersion,
                role, status, groupId, expectations.accessExpiry());
    }

    private void verifyInternalToken(AuthAdmissionResponse response,
                                     String userId,
                                     String sessionId,
                                     String sessionVersion,
                                     String rolesVersion,
                                     String role,
                                     String status,
                                     String groupId,
                                     Instant accessExpiry) {
        try {
            PublicKey publicKey = publicKeyConfig.getPublicKey();
            if (!(publicKey instanceof RSAPublicKey)) {
                throw new IllegalStateException("Gateway public key is not RSA");
            }
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(response.internalToken());
            if (!"RS256".equals(parsed.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("internal JWT algorithm must be RS256");
            }

            Claims claims = parsed.getPayload();
            JsonNode originalPayload = readOriginalPayload(response.internalToken());
            requireExactStringClaim(claims, "iss", EXPECTED_ISSUER);
            requireSingletonAudience(claims, EXPECTED_INTERNAL_AUDIENCE);
            requireExactStringClaim(claims, "token_use", INTERNAL_TOKEN_USE);

            Date issuedAt = requireWholeSecondDate(claims, originalPayload, "iat");
            Date expiresAt = requireWholeSecondDate(claims, originalPayload, "exp");
            Instant now = Instant.now();
            if (!issuedAt.toInstant().isBefore(expiresAt.toInstant())
                    || issuedAt.toInstant().isAfter(now)
                    || !expiresAt.toInstant().isAfter(now)) {
                throw new IllegalArgumentException("internal JWT time claims are not live");
            }

            if (!userId.equals(requirePositiveLongStringClaim(claims, "sub"))
                    || !sessionId.equals(requireCanonicalUuidClaim(claims, "sid"))
                    || !sessionVersion.equals(requirePositiveLongStringClaim(claims, "sv"))
                    || !rolesVersion.equals(requirePositiveLongStringClaim(claims, "rv"))
                    || !role.equals(requireKnownRole(requireExactStringClaim(claims, "role")))
                    || !status.equals(requireAdmissionStatus(requireExactStringClaim(claims, "status")))) {
                throw new IllegalArgumentException("internal JWT identity does not match response");
            }

            String tokenGroupId = optionalPositiveLongStringClaim(claims, originalPayload, "group_id");
            if (groupId == null ? tokenGroupId != null : !groupId.equals(tokenGroupId)) {
                throw new IllegalArgumentException("internal JWT group does not match response");
            }
            if (!Boolean.valueOf(response.isHeadman()).equals(requireBooleanClaim(claims, "is_headman"))
                    || !Boolean.valueOf(response.readOnly()).equals(requireBooleanClaim(claims, "readOnly"))) {
                throw new IllegalArgumentException("internal JWT flags do not match response");
            }

            Instant tokenExpiry = expiresAt.toInstant();
            if (!response.expiresAt().equals(tokenExpiry) || tokenExpiry.isAfter(accessExpiry)) {
                throw new IllegalArgumentException("internal JWT expiry does not match response/access");
            }
        } catch (JwtException | IllegalArgumentException | IllegalStateException e) {
            throw new IllegalArgumentException("internal JWT is incoherent", e);
        }
    }

    private static String requireKnownRole(String value) {
        requireBoundedUppercase(value, "role");
        if (!ALLOWED_ROLES.contains(value)) {
            throw new IllegalArgumentException("role is not recognized");
        }
        return value;
    }

    private static String requireAdmissionStatus(String value) {
        requireBoundedUppercase(value, "status");
        if (!ALLOWED_STATUSES.contains(value)) {
            throw new IllegalArgumentException("status is not admitted");
        }
        return value;
    }

    private static void requireBoundedUppercase(String value, String name) {
        if (value == null || value.isBlank() || value.length() > MAX_WIRE_STRING_LENGTH
                || !value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " is outside the wire bounds");
        }
    }

    private static String requirePositiveLongString(String value, String name) {
        if (value == null || value.length() > MAX_WIRE_STRING_LENGTH
                || !value.matches("[1-9][0-9]*")) {
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

    private static String requireCanonicalUuid(String value, String name) {
        if (value == null || value.length() > MAX_WIRE_STRING_LENGTH) {
            throw new IllegalArgumentException(name + " must be a canonical UUID");
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

    private static Date requireWholeSecondDate(Claims claims, JsonNode originalPayload, String name) {
        JsonNode raw = originalPayload.get(name);
        if (raw == null || !raw.isIntegralNumber() || !raw.canConvertToLong()) {
            throw new IllegalArgumentException(name + " must be a whole-second NumericDate");
        }
        Date date;
        try {
            date = "iat".equals(name) ? claims.getIssuedAt() : claims.getExpiration();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + " must be a whole-second NumericDate", e);
        }
        if (date == null || date.getTime() % 1_000 != 0) {
            throw new IllegalArgumentException(name + " must be a whole-second NumericDate");
        }
        return date;
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

    private static String requirePositiveLongStringClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return requirePositiveLongString(value, name);
    }

    private static String requireCanonicalUuidClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return requireCanonicalUuid(value, name);
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

    private static Boolean requireBooleanClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof Boolean value)) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value;
    }

    private static String requireExactStringClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof String value) || value.isBlank()
                || value.length() > MAX_WIRE_STRING_LENGTH) {
            throw new IllegalArgumentException(name + " must be a bounded string");
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

    private static void removeIdentityHeaders(HttpHeaders headers) {
        List<String> names = List.copyOf(headers.keySet());
        names.stream()
                .filter(InternalJwtIssuerFilter::isIdentityHeader)
                .forEach(headers::remove);
    }

    private static boolean isIdentityHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("x-user-")
                || normalized.equals("x-internal-token")
                || normalized.equals("x-login")
                || normalized.equals("x-group-id")
                || normalized.equals("x-is-headman");
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return problem(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Dependency unavailable", "DEPENDENCY_UNAVAILABLE");
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
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String publicTitle(String code) {
        return switch (code) {
            case "INVALID_SESSION" -> "Unauthorized";
            case "WRONG_ROLE" -> "Forbidden";
            case "SESSION_STATE_STALE" -> "Conflict";
            default -> "Unauthorized";
        };
    }

    private static String publicDetail(String code) {
        return switch (code) {
            case "INVALID_SESSION" -> "Invalid session";
            case "WRONG_ROLE" -> "Wrong role";
            case "SESSION_STATE_STALE" -> "Session state is stale";
            default -> "Request denied";
        };
    }

    private static InternalIssuerUnavailableException unavailable() {
        return new InternalIssuerUnavailableException("Auth authority unavailable");
    }

    private static InternalIssuerUnavailableException unavailable(Throwable cause) {
        return new InternalIssuerUnavailableException("Auth authority unavailable", cause);
    }

    private record AccessExpectations(String userId,
                                      String sessionId,
                                      String sessionVersion,
                                      String rolesVersion,
                                      Instant accessExpiry) {
    }

    private static final class DownstreamChainFailure extends RuntimeException {
        private DownstreamChainFailure(Throwable cause) {
            super(cause);
        }
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
