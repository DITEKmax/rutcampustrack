package ru.rutcampustrack.gateway.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.auth.dto.AuthAdmissionRequest;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.dto.RedeemReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketRedemptionResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Per-request client for the auth authority's live session admission endpoint.
 * The access token is sent only as the single field of {@link AuthAdmissionRequest}.
 */
@Component
public class InternalJwtIssuerClient {

    private static final Logger log = LoggerFactory.getLogger(InternalJwtIssuerClient.class);
    private static final String SECRET_HEADER = "X-Internal-Issuer-Secret";
    private static final String ADMIT_PATH = "/internal/auth/admit";
    private static final String REPORT_TICKET_REDEEM_PATH = "/internal/report-download-tickets/redeem";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final InternalIssuerClientProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ObjectMapper reportTicketObjectMapper;

    @Autowired
    public InternalJwtIssuerClient(InternalIssuerClientProperties properties) {
        this(properties, WebClient.builder()
                .baseUrl(properties.getAuthServiceUrl())
                .build());
    }

    InternalJwtIssuerClient(InternalIssuerClientProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.reportTicketObjectMapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Admit one external access token against the live auth session authority.
     * There is deliberately no cache, single-flight, retry, or stale fallback.
     */
    public Mono<AuthAdmissionResponse> admit(String accessToken) {
        final AuthAdmissionRequest request;
        try {
            request = new AuthAdmissionRequest(accessToken);
        } catch (RuntimeException e) {
            return Mono.error(unavailable());
        }

        return webClient.post()
                .uri(ADMIT_PATH)
                .header(SECRET_HEADER, properties.getSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.OK.value()) {
                        return decodeAdmission(response);
                    }
                    return decodeError(response);
                })
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .onErrorMap(error -> error instanceof InternalIssuerUnavailableException
                        || error instanceof InternalAdmissionDeniedException
                        ? error
                        : unavailable(error))
                .doOnError(error -> log.warn("Auth admission request failed ({})",
                        error.getClass().getSimpleName()));
    }

    /** Redeem a capability only through Auth's secret-protected endpoint, without caller identity headers. */
    public Mono<Optional<ReportDownloadTicketRedemptionResponse>> redeemReportTicket(String ticket) {
        final RedeemReportDownloadTicketRequest request;
        try {
            request = new RedeemReportDownloadTicketRequest(ticket);
        } catch (RuntimeException e) {
            return Mono.error(unavailable());
        }

        return webClient.post()
                .uri(REPORT_TICKET_REDEEM_PATH)
                .header(SECRET_HEADER, properties.getSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.OK.value()) {
                        return decodeReportTicket(response);
                    }
                    if (response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        return response.releaseBody().thenReturn(Optional.empty());
                    }
                    if (response.statusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                        return response.releaseBody().then(Mono.error(
                                new InternalReportTicketRateLimitedException()));
                    }
                    return decodeReportTicketError(response);
                })
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .onErrorMap(error -> error instanceof InternalIssuerUnavailableException
                                || error instanceof InternalAdmissionDeniedException
                                || error instanceof InternalReportTicketRateLimitedException
                        ? error
                        : unavailable(error))
                .doOnError(error -> log.warn("Auth report-ticket redemption failed ({})",
                        error.getClass().getSimpleName()));
    }

    private Mono<Optional<ReportDownloadTicketRedemptionResponse>> decodeReportTicket(ClientResponse response) {
        return boundedBody(response)
                .flatMap(body -> {
                    try {
                        JsonNode root = reportTicketObjectMapper.readTree(body);
                        validateReportTicketWire(root);
                        ReportDownloadTicketRedemptionResponse redemption = reportTicketObjectMapper.treeToValue(
                                root, ReportDownloadTicketRedemptionResponse.class);
                        return Mono.just(Optional.of(redemption));
                    } catch (Exception e) {
                        return Mono.error(unavailable());
                    }
                });
    }

    private Mono<Optional<ReportDownloadTicketRedemptionResponse>> decodeReportTicketError(
            ClientResponse response) {
        int status = response.statusCode().value();
        return boundedBody(response)
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        String code = readExactErrorCode(root, status);
                        return Mono.error(mapError(status, code));
                    } catch (InternalAdmissionDeniedException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(unavailable());
                    }
                });
    }

    private static void validateReportTicketWire(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("report ticket response must be an object");
        }
        JsonNode admission = root.get("admission");
        validateAdmissionWire(admission);
        JsonNode expiresAt = root.get("ticketExpiresAt");
        if (expiresAt == null || !expiresAt.isTextual() || expiresAt.textValue().isBlank()) {
            throw new IllegalArgumentException("ticketExpiresAt must be text");
        }
        Instant.parse(expiresAt.textValue());
        JsonNode bindingHash = root.get("reportBindingHash");
        if (bindingHash == null || !bindingHash.isTextual()
                || !bindingHash.textValue().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("reportBindingHash must be a SHA-256 hex digest");
        }
        JsonNode report = root.get("report");
        if (report == null || !report.isObject()) {
            throw new IllegalArgumentException("report selector must be an object");
        }
    }

    private Mono<AuthAdmissionResponse> decodeAdmission(ClientResponse response) {
        return boundedBody(response)
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        validateAdmissionWire(root);
                        return Mono.just(objectMapper.treeToValue(root, AuthAdmissionResponse.class));
                    } catch (Exception e) {
                        return Mono.error(unavailable());
                    }
                });
    }

    private Mono<AuthAdmissionResponse> decodeError(ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        return boundedBody(response)
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        String code = readExactErrorCode(root, status.value());
                        return Mono.error(mapError(status.value(), code));
                    } catch (InternalAdmissionDeniedException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(unavailable());
                    }
                });
    }

    private Mono<String> boundedBody(ClientResponse response) {
        return response.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(unavailable()))
                .flatMap(body -> {
                    if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                        return Mono.error(unavailable());
                    }
                    return Mono.just(body);
                });
    }

    private static void validateAdmissionWire(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("admission response must be an object");
        }
        requireText(root, "internalToken");
        requireText(root, "expiresAt");
        requireText(root, "sessionId");
        requireText(root, "userId");
        requireText(root, "sessionVersion");
        requireText(root, "rolesVersion");
        requireText(root, "role");
        requireText(root, "status");
        JsonNode groupId = root.get("groupId");
        if (groupId != null && !groupId.isNull() && !groupId.isTextual()) {
            throw new IllegalArgumentException("groupId must be a string or null");
        }
        requireBoolean(root, "isHeadman");
        requireBoolean(root, "readOnly");
    }

    private static String readExactErrorCode(JsonNode root, int httpStatus) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("error response must be an object");
        }
        JsonNode bodyStatus = root.get("status");
        JsonNode extras = root.get("extras");
        JsonNode code = extras == null ? null : extras.get("code");
        if (bodyStatus == null || !bodyStatus.isIntegralNumber()
                || bodyStatus.intValue() != httpStatus
                || extras == null || !extras.isObject()
                || code == null || !code.isTextual()) {
            throw new IllegalArgumentException("error response does not have an exact typed code");
        }
        return code.textValue();
    }

    private static RuntimeException mapError(int httpStatus, String code) {
        if (httpStatus == HttpStatus.UNAUTHORIZED.value()
                && ("INVALID_SESSION".equals(code) || "SESSION_REVOKED".equals(code))) {
            return new InternalAdmissionDeniedException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION");
        }
        if (httpStatus == HttpStatus.FORBIDDEN.value()
                && ("ROLE_NOT_GRANTED".equals(code) || "ROLE_NOT_SELECTABLE".equals(code))) {
            return new InternalAdmissionDeniedException(HttpStatus.FORBIDDEN, "WRONG_ROLE");
        }
        if (httpStatus == HttpStatus.CONFLICT.value()
                && "SESSION_STATE_STALE".equals(code)) {
            return new InternalAdmissionDeniedException(HttpStatus.CONFLICT, "SESSION_STATE_STALE");
        }
        return unavailable();
    }

    private static void requireText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
    }

    private static void requireBoolean(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
    }

    private static InternalIssuerUnavailableException unavailable() {
        return new InternalIssuerUnavailableException("Auth authority unavailable");
    }

    private static InternalIssuerUnavailableException unavailable(Throwable cause) {
        return new InternalIssuerUnavailableException("Auth authority unavailable", cause);
    }
}
