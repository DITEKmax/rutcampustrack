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
 * Exchanges one locally validated access token for a live, signed internal identity. The downstream
 * request is trusted only after both the authority response and the internal JWT agree with the
 * original access expectations.
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

    public InternalJwtIssuerFilter(
            InternalJwtIssuerClient client, PublicKeyConfig publicKeyConfig) {
        this.client = client;
        this.publicKeyConfig = publicKeyConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String accessToken =
                exchange.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE);
        if (accessToken == null || accessToken.isBlank()) {
            return chain.filter(exchange);
        }

        Diagnostic diagnostic = new Diagnostic();
        AccessExpectations expectations;
        try {
            expectations = readExpectations(exchange, diagnostic);
        } catch (RuntimeException e) {
            diagnostic.logFailure("EXPECTATIONS", e);
            return serviceUnavailable(exchange);
        }

        return client.admit(accessToken)
                .flatMap(response -> prepareForwardedChain(exchange, chain, response, expectations, diagnostic))
                .onErrorResume(
                        InternalAdmissionDeniedException.class,
                        error ->
                                problem(
                                        exchange,
                                        error.publicStatus(),
                                        publicTitle(error.publicCode()),
                                        publicDetail(error.publicCode()),
                                        error.publicCode()))
                .onErrorResume(
                        InternalIssuerUnavailableException.class,
                        error -> serviceUnavailable(exchange))
                .onErrorResume(DownstreamChainFailure.class, error -> Mono.error(error.getCause()));
    }

    private Mono<Void> prepareForwardedChain(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            AuthAdmissionResponse response,
            AccessExpectations expectations, Diagnostic diagnostic) {
        final ServerWebExchange forwardedExchange;
        try {
            validateAdmission(response, expectations, diagnostic);
            diagnostic.check = Check.FORWARD_REQUEST;
            ServerHttpRequest request =
                    exchange.getRequest()
                            .mutate()
                            .headers(InternalJwtIssuerFilter::removeIdentityHeaders)
                            .header(INTERNAL_TOKEN_HEADER, response.internalToken())
                            .build();
            exchange.getAttributes()
                    .put(
                            JwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE,
                            response.userId());
            forwardedExchange = exchange.mutate().request(request).build();
        } catch (RuntimeException e) {
            diagnostic.logFailure("FORWARDED_IDENTITY", e);
            return Mono.error(unavailable(e));
        }

        diagnostic.logAccepted();
        return Mono.defer(() -> chain.filter(forwardedExchange))
                .onErrorMap(InternalJwtIssuerFilter::wrapDownstreamError);
    }

    private static Throwable wrapDownstreamError(Throwable error) {
        if (error instanceof DownstreamChainFailure) {
            return error;
        }
        return new DownstreamChainFailure(error);
    }

    private static AccessExpectations readExpectations(ServerWebExchange exchange, Diagnostic diagnostic) {
        diagnostic.check = Check.EXPECT_USER;
        String userId =
                requiredAttribute(exchange, JwtAuthenticationFilter.EXPECTED_USER_ID_ATTRIBUTE);
        diagnostic.check = Check.EXPECT_SESSION;
        String sessionId =
                requiredAttribute(exchange, JwtAuthenticationFilter.EXPECTED_SESSION_ID_ATTRIBUTE);
        diagnostic.check = Check.EXPECT_SESSION_VERSION;
        String sessionVersion =
                requiredAttribute(
                        exchange, JwtAuthenticationFilter.EXPECTED_SESSION_VERSION_ATTRIBUTE);
        diagnostic.check = Check.EXPECT_ROLES_VERSION;
        String rolesVersion =
                requiredAttribute(
                        exchange, JwtAuthenticationFilter.EXPECTED_ROLES_VERSION_ATTRIBUTE);
        diagnostic.check = Check.EXPECT_ACCESS_EXPIRY;
        Instant accessExpiry =
                exchange.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE);
        if (accessExpiry == null) {
            throw new IllegalArgumentException("missing access expiry expectation");
        }
        return new AccessExpectations(
                userId, sessionId, sessionVersion, rolesVersion, accessExpiry);
    }

    private static String requiredAttribute(ServerWebExchange exchange, String name) {
        Object value = exchange.getAttribute(name);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("missing auth expectation");
        }
        return string;
    }

    private void validateAdmission(
            AuthAdmissionResponse response, AccessExpectations expectations, Diagnostic diagnostic) {
        diagnostic.check = Check.RESPONSE_STRUCTURE;
        requireResponse(response);
        AdmissionIdentity identity = readIdentity(response, diagnostic);
        requireMatch(diagnostic, Check.ACCESS_USER, expectations.userId().equals(identity.userId()));
        requireMatch(diagnostic, Check.ACCESS_SESSION, expectations.sessionId().equals(identity.sessionId()));
        requireMatch(diagnostic, Check.ACCESS_SESSION_VERSION, expectations.sessionVersion().equals(identity.sessionVersion()));
        requireMatch(diagnostic, Check.ACCESS_ROLES_VERSION, expectations.rolesVersion().equals(identity.rolesVersion()));
        requireFlags(response, identity, diagnostic);
        verifyInternalToken(response, identity, expectations.accessExpiry(), null, null, diagnostic);
    }

    private static void requireResponse(AuthAdmissionResponse response) {
        if (response == null
                || response.internalToken() == null
                || response.internalToken().isBlank()
                || response.internalToken().length() > 16_384
                || response.expiresAt() == null) {
            throw new IllegalArgumentException("incoherent admission response");
        }
    }

    private static AdmissionIdentity readIdentity(AuthAdmissionResponse response, Diagnostic diagnostic) {
        diagnostic.check = Check.RESPONSE_USER;
        String userId = requirePositiveLongString(response.userId(), "userId");
        diagnostic.check = Check.RESPONSE_SESSION;
        String sessionId = requireCanonicalUuid(response.sessionId(), "sessionId");
        diagnostic.check = Check.RESPONSE_SESSION_VERSION;
        String sessionVersion = requirePositiveLongString(response.sessionVersion(), "sessionVersion");
        diagnostic.check = Check.RESPONSE_ROLES_VERSION;
        String rolesVersion = requirePositiveLongString(response.rolesVersion(), "rolesVersion");
        diagnostic.check = Check.RESPONSE_ROLE;
        String role = requireKnownRole(response.role());
        diagnostic.check = Check.RESPONSE_STATUS;
        String status = requireAdmissionStatus(response.status());
        diagnostic.check = Check.RESPONSE_GROUP;
        String groupId = response.groupId() == null ? null : requirePositiveLongString(response.groupId(), "groupId");
        return new AdmissionIdentity(userId, sessionId, sessionVersion, rolesVersion, role, status, groupId);
    }

    private static void requireFlags(AuthAdmissionResponse response, AdmissionIdentity identity, Diagnostic diagnostic) {
        requireMatch(diagnostic, Check.RESPONSE_HEADMAN_FLAG, response.isHeadman() == "HEADMAN".equals(identity.role()));
        requireMatch(diagnostic, Check.RESPONSE_HEADMAN_GROUP, !"HEADMAN".equals(identity.role()) || identity.groupId() != null);
        requireMatch(diagnostic, Check.RESPONSE_READONLY_FLAG, response.readOnly() == TERMINAL_STATUSES.contains(identity.status()));
    }

    private static void requireMatch(Diagnostic diagnostic, Check check, boolean matches) {
        diagnostic.check = check;
        if (!matches) {
            throw new IllegalArgumentException("internal admission check failed");
        }
    }

    void verifyInternalReportDownloadToken(
            AuthAdmissionResponse response, Instant ticketExpiresAt, String reportBindingHash) {
        Diagnostic diagnostic = new Diagnostic();
        try {
            diagnostic.check = Check.REPORT_RESPONSE_STRUCTURE;
            requireResponse(response);
            if (ticketExpiresAt == null || reportBindingHash == null || !reportBindingHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("incoherent report-ticket admission response");
            }
            AdmissionIdentity identity = readIdentity(response, diagnostic);
            requireFlags(response, identity, diagnostic);
            requireMatch(diagnostic, Check.REPORT_TICKET_EXPIRED, ticketExpiresAt.isAfter(Instant.now()));
            requireMatch(diagnostic, Check.REPORT_TICKET_INTERVAL, !ticketExpiresAt.isAfter(Instant.now().plusSeconds(60)));
            verifyInternalToken(response, identity, ticketExpiresAt, reportBindingHash, ticketExpiresAt.toString(), diagnostic);
            diagnostic.logAccepted();
        } catch (RuntimeException error) {
            diagnostic.logFailure("REPORT_TICKET_IDENTITY", error);
            throw error;
        }
    }

    private void verifyInternalToken(
            AuthAdmissionResponse response, AdmissionIdentity identity, Instant accessExpiry,
            String expectedReportBindingHash, String expectedReportTicketExpiry, Diagnostic diagnostic) {
        try {
            diagnostic.check = Check.JWT_KEY;
            PublicKey publicKey = publicKeyConfig.getPublicKey();
            if (!(publicKey instanceof RSAPublicKey)) {
                throw new IllegalStateException("Gateway public key is not RSA");
            }
            diagnostic.check = Check.JWT_PARSE;
            Jws<Claims> parsed = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(response.internalToken());
            requireMatch(diagnostic, Check.JWT_ALGORITHM, "RS256".equals(parsed.getHeader().getAlgorithm()));
            Claims claims = parsed.getPayload();
            diagnostic.check = Check.JWT_PAYLOAD;
            JsonNode originalPayload = readOriginalPayload(response.internalToken());
            diagnostic.check = Check.JWT_ISSUER;
            requireExactStringClaim(claims, "iss", EXPECTED_ISSUER);
            diagnostic.check = Check.JWT_AUDIENCE;
            requireSingletonAudience(claims, EXPECTED_INTERNAL_AUDIENCE);
            diagnostic.check = Check.JWT_USE;
            requireExactStringClaim(claims, "token_use", INTERNAL_TOKEN_USE);

            diagnostic.check = Check.JWT_IAT_WIRE;
            Date issuedAt = requireWholeSecondDate(claims, originalPayload, "iat");
            diagnostic.check = Check.JWT_EXP_WIRE;
            Date expiresAt = requireWholeSecondDate(claims, originalPayload, "exp");
            Instant now = Instant.now();
            diagnostic.validationNowMs = now.toEpochMilli();
            diagnostic.issuedAtMs = issuedAt.getTime();
            diagnostic.expiresAtMs = expiresAt.getTime();
            requireMatch(diagnostic, Check.JWT_TIME_INTERVAL, issuedAt.toInstant().isBefore(expiresAt.toInstant()));
            requireMatch(diagnostic, Check.JWT_IAT_FUTURE, !issuedAt.toInstant().isAfter(now));
            requireMatch(diagnostic, Check.JWT_EXPIRED, expiresAt.toInstant().isAfter(now));

            diagnostic.check = Check.JWT_USER;
            requireMatch(diagnostic, Check.JWT_USER, identity.userId().equals(requirePositiveLongStringClaim(claims, "sub")));
            diagnostic.check = Check.JWT_SESSION;
            requireMatch(diagnostic, Check.JWT_SESSION, identity.sessionId().equals(requireCanonicalUuidClaim(claims, "sid")));
            diagnostic.check = Check.JWT_SESSION_VERSION;
            requireMatch(diagnostic, Check.JWT_SESSION_VERSION, identity.sessionVersion().equals(requirePositiveLongStringClaim(claims, "sv")));
            diagnostic.check = Check.JWT_ROLES_VERSION;
            requireMatch(diagnostic, Check.JWT_ROLES_VERSION, identity.rolesVersion().equals(requirePositiveLongStringClaim(claims, "rv")));
            diagnostic.check = Check.JWT_ROLE;
            requireMatch(diagnostic, Check.JWT_ROLE, identity.role().equals(requireKnownRole(requireExactStringClaim(claims, "role"))));
            diagnostic.check = Check.JWT_STATUS;
            requireMatch(diagnostic, Check.JWT_STATUS, identity.status().equals(requireAdmissionStatus(requireExactStringClaim(claims, "status"))));
            diagnostic.check = Check.JWT_GROUP;
            String tokenGroupId = optionalPositiveLongStringClaim(claims, originalPayload, "group_id");
            requireMatch(diagnostic, Check.JWT_GROUP, identity.groupId() == null ? tokenGroupId == null : identity.groupId().equals(tokenGroupId));
            diagnostic.check = Check.JWT_HEADMAN_FLAG;
            requireMatch(diagnostic, Check.JWT_HEADMAN_FLAG, Boolean.valueOf(response.isHeadman()).equals(requireBooleanClaim(claims, "is_headman")));
            diagnostic.check = Check.JWT_READONLY_FLAG;
            requireMatch(diagnostic, Check.JWT_READONLY_FLAG, Boolean.valueOf(response.readOnly()).equals(requireBooleanClaim(claims, "readOnly")));
            if (expectedReportBindingHash != null) {
                diagnostic.check = Check.JWT_REPORT_BINDING;
                requireExactStringClaim(claims, "report_hash", expectedReportBindingHash);
                diagnostic.check = Check.JWT_REPORT_EXPIRY;
                requireExactStringClaim(claims, "report_ticket_exp", expectedReportTicketExpiry);
            }
            Instant tokenExpiry = expiresAt.toInstant();
            requireMatch(diagnostic, Check.JWT_RESPONSE_EXPIRY, response.expiresAt().equals(tokenExpiry));
            requireMatch(diagnostic, Check.JWT_ACCESS_EXPIRY, !tokenExpiry.isAfter(accessExpiry));
        } catch (JwtException | IllegalArgumentException | IllegalStateException error) {
            diagnostic.parserFailure = parserFailure(error);
            throw new IllegalArgumentException("internal JWT is incoherent", error);
        }
    }

    private static ParserFailure parserFailure(RuntimeException error) {
        if (error instanceof io.jsonwebtoken.ExpiredJwtException) return ParserFailure.EXPIRED;
        if (error instanceof io.jsonwebtoken.PrematureJwtException) return ParserFailure.NOT_YET_VALID;
        if (error instanceof io.jsonwebtoken.security.SignatureException) return ParserFailure.SIGNATURE;
        if (error instanceof io.jsonwebtoken.security.SecurityException) return ParserFailure.SECURITY;
        if (error instanceof io.jsonwebtoken.UnsupportedJwtException) return ParserFailure.UNSUPPORTED;
        if (error instanceof io.jsonwebtoken.MalformedJwtException) return ParserFailure.MALFORMED;
        if (error instanceof JwtException) return ParserFailure.OTHER_JWT;
        return ParserFailure.NONE;
    }

    private record AdmissionIdentity(String userId, String sessionId, String sessionVersion,
            String rolesVersion, String role, String status, String groupId) {}

    private enum ParserFailure { NONE, EXPIRED, NOT_YET_VALID, SIGNATURE, SECURITY, UNSUPPORTED, MALFORMED, OTHER_JWT }

    private enum Check {
        EXPECT_USER, EXPECT_SESSION, EXPECT_SESSION_VERSION, EXPECT_ROLES_VERSION, EXPECT_ACCESS_EXPIRY,
        RESPONSE_STRUCTURE, RESPONSE_USER, RESPONSE_SESSION, RESPONSE_SESSION_VERSION, RESPONSE_ROLES_VERSION,
        RESPONSE_ROLE, RESPONSE_STATUS, RESPONSE_GROUP, RESPONSE_HEADMAN_FLAG, RESPONSE_HEADMAN_GROUP, RESPONSE_READONLY_FLAG,
        ACCESS_USER, ACCESS_SESSION, ACCESS_SESSION_VERSION, ACCESS_ROLES_VERSION,
        REPORT_RESPONSE_STRUCTURE, REPORT_TICKET_EXPIRED, REPORT_TICKET_INTERVAL,
        JWT_KEY, JWT_PARSE, JWT_ALGORITHM, JWT_PAYLOAD, JWT_ISSUER, JWT_AUDIENCE, JWT_USE,
        JWT_IAT_WIRE, JWT_EXP_WIRE, JWT_TIME_INTERVAL, JWT_IAT_FUTURE, JWT_EXPIRED,
        JWT_USER, JWT_SESSION, JWT_SESSION_VERSION, JWT_ROLES_VERSION, JWT_ROLE, JWT_STATUS,
        JWT_GROUP, JWT_HEADMAN_FLAG, JWT_READONLY_FLAG, JWT_REPORT_BINDING, JWT_REPORT_EXPIRY,
        JWT_RESPONSE_EXPIRY, JWT_ACCESS_EXPIRY, FORWARD_REQUEST
    }

    /** Private diagnostics: closed codes and clock numbers only; never log the exception/claims. */
    private static final class Diagnostic {
        private final long startWallMs = Instant.now().toEpochMilli();
        private final long startNanos = System.nanoTime();
        private Check check = Check.EXPECT_USER;
        private ParserFailure parserFailure = ParserFailure.NONE;
        private Long validationNowMs;
        private Long issuedAtMs;
        private Long expiresAtMs;

        private void logFailure(String stage, RuntimeException error) {
            log.warn("semester.preview failure source=GATEWAY_ISSUER_FILTER stage={} status=NONE cause={} check={} parser={} startWallMs={} validationNowMs={} issuedAtMs={} expiresAtMs={} elapsedMs={} wallElapsedMs={} correlation=UNCONFIRMED",
                    stage, error instanceof IllegalArgumentException || error instanceof JwtException ? "IDENTITY_REJECTED" : "UNEXPECTED",
                    check, parserFailure, startWallMs, validationNowMs, issuedAtMs, expiresAtMs,
                    (System.nanoTime() - startNanos) / 1_000_000, Instant.now().toEpochMilli() - startWallMs);
        }

        private void logAccepted() {
            log.debug("admission.diagnostic source=GATEWAY_ISSUER_FILTER outcome=ACCEPTED startWallMs={} validationNowMs={} issuedAtMs={} expiresAtMs={} elapsedMs={} wallElapsedMs={} correlation=UNCONFIRMED",
                    startWallMs, validationNowMs, issuedAtMs, expiresAtMs,
                    (System.nanoTime() - startNanos) / 1_000_000, Instant.now().toEpochMilli() - startWallMs);
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
        if (value == null
                || value.isBlank()
                || value.length() > MAX_WIRE_STRING_LENGTH
                || !value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " is outside the wire bounds");
        }
    }

    private static String requirePositiveLongString(String value, String name) {
        if (value == null
                || value.length() > MAX_WIRE_STRING_LENGTH
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

    private static Date requireWholeSecondDate(
            Claims claims, JsonNode originalPayload, String name) {
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

    private static String optionalPositiveLongStringClaim(
            Claims claims, JsonNode originalPayload, String name) {
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
        if (!(raw instanceof String value)
                || value.isBlank()
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
            if (values.size() != 1
                    || !(values.iterator().next() instanceof String value)
                    || !expected.equals(value)) {
                throw new IllegalArgumentException("JWT audience must be a singleton");
            }
        } else {
            throw new IllegalArgumentException("JWT audience has an invalid type");
        }
        Set<String> parsedAudience = claims.getAudience();
        if (parsedAudience == null
                || parsedAudience.size() != 1
                || !parsedAudience.contains(expected)) {
            throw new IllegalArgumentException("JWT audience must be a singleton");
        }
    }

    private static void removeIdentityHeaders(HttpHeaders headers) {
        List<String> names = List.copyOf(headers.keySet());
        names.stream().filter(InternalJwtIssuerFilter::isIdentityHeader).forEach(headers::remove);
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
        return problem(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "Dependency unavailable",
                "DEPENDENCY_UNAVAILABLE");
    }

    private Mono<Void> problem(
            ServerWebExchange exchange,
            HttpStatus status,
            String title,
            String detail,
            String code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl("no-store");
        String body =
                "{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\",\"code\":\"%s\"}"
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

    private record AccessExpectations(
            String userId,
            String sessionId,
            String sessionVersion,
            String rolesVersion,
            Instant accessExpiry) {}

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
