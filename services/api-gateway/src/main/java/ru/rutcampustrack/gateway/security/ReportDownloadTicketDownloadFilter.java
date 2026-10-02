package ru.rutcampustrack.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketRedemptionResponse;
import ru.rutcampustrack.gateway.clientip.TrustedClientIpResolver;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Handles only the exact one-segment bearer-free report download route. */
@Component
public final class ReportDownloadTicketDownloadFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReportDownloadTicketDownloadFilter.class);
    private static final String PATH_PREFIX = "/api/report-download/";
    private static final String TELEGRAM_WEB_ORIGIN = "https://web.telegram.org";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final Pattern TICKET_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final int MAX_REPORT_BYTES = 20 * 1024 * 1024;

    private final InternalJwtIssuerClient authClient;
    private final InternalJwtIssuerFilter tokenVerifier;
    private final ReportDownloadBackendProperties properties;
    private final ReportDownloadAttemptRateLimiter attemptRateLimiter;
    private final WebClient mobileBffClient;
    private final WebClient attendanceClient;

    @Autowired
    public ReportDownloadTicketDownloadFilter(
            InternalJwtIssuerClient authClient,
            InternalJwtIssuerFilter tokenVerifier,
            ReportDownloadBackendProperties properties,
            ReportDownloadAttemptRateLimiter attemptRateLimiter
    ) {
        this(authClient, tokenVerifier, properties, attemptRateLimiter,
                WebClient.builder().baseUrl(properties.getMobileBffUrl()).build(),
                WebClient.builder().baseUrl(properties.getAttendanceServiceUrl()).build());
    }

    ReportDownloadTicketDownloadFilter(
            InternalJwtIssuerClient authClient,
            InternalJwtIssuerFilter tokenVerifier,
            ReportDownloadBackendProperties properties,
            ReportDownloadAttemptRateLimiter attemptRateLimiter,
            WebClient mobileBffClient,
            WebClient attendanceClient
    ) {
        this.authClient = authClient;
        this.tokenVerifier = tokenVerifier;
        this.properties = properties;
        this.attemptRateLimiter = attemptRateLimiter;
        this.mobileBffClient = mobileBffClient;
        this.attendanceClient = attendanceClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ticket = exactTicketSegment(exchange.getRequest());
        if (ticket == null) {
            return chain.filter(exchange);
        }

        setBaseResponseHeaders(exchange);
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return writeProblem(exchange, HttpStatus.METHOD_NOT_ALLOWED,
                    "Method Not Allowed", "Only GET is supported");
        }
        if (StringUtils.hasText(exchange.getRequest().getURI().getRawQuery())
                || hasRequestBody(exchange.getRequest())) {
            return writeProblem(exchange, HttpStatus.BAD_REQUEST,
                    "Bad Request", "Download overrides are not accepted");
        }
        if (exchange.getRequest().getHeaders().containsKey(HttpHeaders.RANGE)) {
            return writeProblem(exchange, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                    "Range Not Satisfiable", "Range requests are not supported");
        }
        if (!TICKET_PATTERN.matcher(ticket).matches()) {
            return writeProblem(exchange, HttpStatus.NOT_FOUND, "Not Found", "Report not found");
        }

        return attemptRateLimiter.tryAcquire(canonicalClientIp(exchange))
                .flatMap(allowed -> allowed
                        ? redeemAndDownload(exchange, ticket)
                        : writeProblem(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                "Request limit exceeded", "Try again after the current window expires"))
                .onErrorResume(ReportDownloadAttemptRateLimiter.AttemptBudgetUnavailableException.class,
                        error -> {
                            log.warn("Report download attempt budget unavailable ({})",
                                    error.getCause() == null ? error.getClass().getSimpleName()
                                            : error.getCause().getClass().getSimpleName());
                            return writeProblem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                    "Service Unavailable", "Report service is unavailable");
                        });
    }

    private Mono<Void> redeemAndDownload(ServerWebExchange exchange, String ticket) {
        return authClient.redeemReportTicket(ticket)
                .flatMap(redeemed -> redeemed
                        .<Mono<Void>>map(value -> download(exchange, value))
                        .orElseGet(() -> writeProblem(exchange, HttpStatus.NOT_FOUND,
                                "Not Found", "Report not found")))
                .onErrorResume(InternalAdmissionDeniedException.class, error ->
                        writeProblem(exchange, error.publicStatus(), "Request Denied", "Report access denied"))
                .onErrorResume(InternalReportTicketRateLimitedException.class, error ->
                        writeProblem(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                "Request limit exceeded", "Retry after the ticket window expires"))
                .onErrorResume(InternalIssuerUnavailableException.class, error ->
                        writeProblem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "Service Unavailable", "Report authority is unavailable"))
                .onErrorResume(ReportDownloadResponseException.class, error ->
                        writeProblem(exchange, error.status(), title(error.status()), detail(error.status())))
                .onErrorResume(error -> {
                    // Never include the path, ticket, selector, or downstream body in logs or responses.
                    log.warn("Report download dependency failed ({})", error.getClass().getSimpleName());
                    return writeProblem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            "Service Unavailable", "Report service is unavailable");
                });
    }

    private Mono<Void> download(ServerWebExchange exchange,
                                ReportDownloadTicketRedemptionResponse redemption) {
        final Dispatch dispatch;
        try {
            validateRedemption(redemption);
            dispatch = dispatch(redemption);
        } catch (RuntimeException exception) {
            return writeProblem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    "Service Unavailable", "Report authority response is invalid");
        }

        WebClient client = dispatch.backend() == Backend.MOBILE_BFF ? mobileBffClient : attendanceClient;
        WebClient.RequestBodySpec request = client.method(dispatch.method())
                .uri(builder -> buildUri(builder, dispatch))
                .header(INTERNAL_TOKEN_HEADER, redemption.admission().internalToken())
                .accept(dispatch.backend() == Backend.MOBILE_BFF
                        ? MediaType.ALL : MediaType.parseMediaType(dispatch.expectedMediaType()));
        WebClient.RequestHeadersSpec<?> requestSpec = dispatch.body() == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).bodyValue(dispatch.body());

        return requestSpec.exchangeToMono(response -> handleDownstreamResponse(exchange, response, dispatch))
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .onErrorMap(DataBufferLimitException.class, ignored ->
                        new ReportDownloadResponseException(HttpStatus.PAYLOAD_TOO_LARGE))
                .onErrorMap(error -> !(error instanceof ReportDownloadResponseException),
                        error -> new ReportDownloadResponseException(HttpStatus.SERVICE_UNAVAILABLE, error));
    }

    private Mono<Void> handleDownstreamResponse(ServerWebExchange exchange,
                                                 ClientResponse response,
                                                 Dispatch dispatch) {
        if (!response.statusCode().is2xxSuccessful()) {
            HttpStatus status = downstreamStatus(response.statusCode().value());
            return response.releaseBody().then(Mono.error(new ReportDownloadResponseException(status)));
        }

        MediaType expected = MediaType.parseMediaType(dispatch.expectedMediaType());
        MediaType actual = response.headers().contentType().orElse(null);
        if (actual == null || !expected.isCompatibleWith(actual)) {
            return response.releaseBody().then(Mono.error(
                    new ReportDownloadResponseException(HttpStatus.SERVICE_UNAVAILABLE)));
        }
        java.util.OptionalLong declaredLength = response.headers().contentLength();
        if (declaredLength.isPresent() && declaredLength.getAsLong() > MAX_REPORT_BYTES) {
            return response.releaseBody().then(Mono.error(
                    new ReportDownloadResponseException(HttpStatus.PAYLOAD_TOO_LARGE)));
        }

        return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class), MAX_REPORT_BYTES)
                .switchIfEmpty(Mono.error(new ReportDownloadResponseException(HttpStatus.SERVICE_UNAVAILABLE)))
                .flatMap(buffer -> {
                    try {
                        int length = buffer.readableByteCount();
                        if (length <= 0) {
                            return Mono.error(new ReportDownloadResponseException(HttpStatus.SERVICE_UNAVAILABLE));
                        }
                        byte[] bytes = new byte[length];
                        buffer.read(bytes);
                        return writeReport(exchange, dispatch, bytes);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                });
    }

    private Mono<Void> writeReport(ServerWebExchange exchange, Dispatch dispatch, byte[] bytes) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.parseMediaType(dispatch.expectedMediaType()));
        response.getHeaders().setContentLength(bytes.length);
        response.getHeaders().setContentDisposition(ContentDisposition.attachment()
                .filename(dispatch.filename(), StandardCharsets.UTF_8).build());
        response.getHeaders().set("Accept-Ranges", "none");
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private void validateRedemption(ReportDownloadTicketRedemptionResponse redemption) {
        if (redemption == null || redemption.admission() == null || redemption.report() == null
                || !redemption.report().isParametersConsistent()
                || !redemption.reportBindingHash().equals(redemption.report().bindingHash())) {
            throw new IllegalArgumentException("incoherent report redemption");
        }
        tokenVerifier.verifyInternalReportDownloadToken(redemption.admission(),
                redemption.ticketExpiresAt(), redemption.reportBindingHash());
        String filename = redemption.report().suggestedFilename();
        if (!filename.matches("[a-z0-9-]+\\.(docx|pdf|zip|html|xlsx|png)")) {
            throw new IllegalArgumentException("report filename is not safe");
        }
    }

    private static Dispatch dispatch(ReportDownloadTicketRedemptionResponse redemption) {
        IssueReportDownloadTicketRequest report = redemption.report();
        String filename = report.suggestedFilename();
        String expectedMediaType = report.expectedMediaType();
        Map<String, List<String>> query = new LinkedHashMap<>();
        return switch (report.kind()) {
            case TEACHER_JOURNAL -> {
                var selection = report.teacherJournal();
                query.put("semesterId", values(selection.semesterId()));
                query.put("groupId", values(selection.groupId()));
                query.put("subjectId", values(selection.subjectId()));
                query.put("lessonType", selection.lessonTypes());
                addOptional(query, "dateFrom", selection.dateFrom());
                addOptional(query, "dateTo", selection.dateTo());
                query.put("format", values(selection.format().code()));
                yield new Dispatch(Backend.MOBILE_BFF, HttpMethod.GET,
                        "/api/v1/teacher/journal/export", query, null,
                        expectedMediaType, filename);
            }
            case TEACHER_STATS -> {
                var selection = report.teacherStats();
                query.put("semesterId", values(selection.semesterId()));
                query.put("scope", values(selection.scope()));
                addOptional(query, "groupId", selection.groupId());
                addOptional(query, "subjectId", selection.subjectId());
                addOptionalList(query, "lessonType", selection.lessonTypes());
                addOptionalList(query, "sort", selection.sorts());
                addOptionalList(query, "filter", selection.filters());
                query.put("format", values(selection.format().code()));
                yield new Dispatch(Backend.MOBILE_BFF, HttpMethod.GET,
                        "/api/v1/teacher/stats/export", query, null,
                        expectedMediaType, filename);
            }
            case HEADMAN_WEEKLY_CURRENT -> {
                var selection = report.headmanWeeklyCurrent();
                query.put("weekStart", values(selection.weekStart().toString()));
                query.put("format", values(selection.format().code()));
                yield new Dispatch(Backend.ATTENDANCE, HttpMethod.GET,
                        "/attendance/reports/headman-weekly/current", query, null,
                        expectedMediaType, filename);
            }
            case HEADMAN_WEEKLY_SELECTED -> {
                var selection = report.headmanWeeklySelected();
                // The payload is reconstructed from the stored selector only.
                WeeklyExportPayload body = new WeeklyExportPayload(
                        selection.weekStarts().stream().map(java.time.LocalDate::toString).toList(),
                        selection.format().code());
                yield new Dispatch(Backend.ATTENDANCE, HttpMethod.POST,
                        "/attendance/reports/headman-weekly/export", query, body,
                        expectedMediaType, filename);
            }
            case HEADMAN_STATS -> {
                var selection = report.headmanStats();
                HeadmanStatsExportPayload body = new HeadmanStatsExportPayload(
                        selection.subjectId(), selection.lessonTypes(), selection.sorts(),
                        selection.filters(), selection.format().code());
                yield new Dispatch(Backend.ATTENDANCE, HttpMethod.POST,
                        "/attendance/reports/headman/stats/export", query, body,
                        expectedMediaType, filename);
            }
            case HEADMAN_STATS_TREND -> {
                var selection = report.headmanStatsTrend();
                HeadmanStatsTrendQueryPayload trend = new HeadmanStatsTrendQueryPayload(
                        selection.mode().name(),
                        selection.weekStart() == null ? null : selection.weekStart().toString(),
                        selection.subjectId(),
                        selection.lessonTypes());
                HeadmanStatsTrendExportPayload body = new HeadmanStatsTrendExportPayload(
                        trend, selection.format().code());
                yield new Dispatch(Backend.ATTENDANCE, HttpMethod.POST,
                        "/attendance/reports/headman/stats/trend/export", query, body,
                        expectedMediaType, filename);
            }
            case HEADMAN_GROUP_COMPOSITION -> {
                query.put("format", values(report.headmanGroupComposition().format().code()));
                yield new Dispatch(Backend.ATTENDANCE, HttpMethod.GET,
                        "/attendance/reports/headman/group-composition/export", query, null,
                        expectedMediaType, filename);
            }
        };
    }

    private static java.net.URI buildUri(UriBuilder builder, Dispatch dispatch) {
        builder.path(dispatch.path());
        dispatch.query().forEach((name, values) -> builder.queryParam(name, values.toArray()));
        return builder.build();
    }

    private static void addOptional(Map<String, List<String>> query, String key, Object value) {
        if (value != null) {
            query.put(key, values(value));
        }
    }

    private static void addOptionalList(Map<String, List<String>> query, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            query.put(key, values);
        }
    }

    private static List<String> values(Object value) {
        return List.of(value.toString());
    }

    private static boolean hasRequestBody(ServerHttpRequest request) {
        String contentLength = request.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        return request.getHeaders().containsKey(HttpHeaders.TRANSFER_ENCODING);
    }

    private static String canonicalClientIp(ServerWebExchange exchange) {
        String normalized = exchange.getAttribute(TrustedClientIpResolver.CLIENT_IP_ATTRIBUTE);
        if (normalized != null && !normalized.isBlank()) {
            return TrustedClientIpResolver.canonicalizeLiteral(normalized)
                    .orElse(TrustedClientIpResolver.UNKNOWN);
        }
        return TrustedClientIpResolver.canonicalizeRemoteAddress(exchange.getRequest().getRemoteAddress());
    }

    private static String exactTicketSegment(ServerHttpRequest request) {
        String path = request.getURI().getRawPath();
        if (path == null || !path.startsWith(PATH_PREFIX)) {
            return null;
        }
        String segment = path.substring(PATH_PREFIX.length());
        return segment.isEmpty() || segment.contains("/") ? null : segment;
    }

    private static HttpStatus downstreamStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 413 -> HttpStatus.PAYLOAD_TOO_LARGE;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static void setBaseResponseHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Accept-Ranges", "none");
        String origin = exchange.getRequest().getHeaders().getOrigin() == null
                ? null : exchange.getRequest().getHeaders().getOrigin().toString();
        if (TELEGRAM_WEB_ORIGIN.equals(origin)) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, TELEGRAM_WEB_ORIGIN);
            headers.setVary(List.of(HttpHeaders.ORIGIN));
        }
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange,
                                    HttpStatus status,
                                    String title,
                                    String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl(CacheControl.noStore());
        String body = "{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\"}"
                .formatted(status.value(), title, detail);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Bad Request";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Not Found";
            case UNPROCESSABLE_ENTITY -> "Report selection rejected";
            case PAYLOAD_TOO_LARGE -> "Payload Too Large";
            case TOO_MANY_REQUESTS -> "Request limit exceeded";
            case METHOD_NOT_ALLOWED -> "Method Not Allowed";
            case REQUESTED_RANGE_NOT_SATISFIABLE -> "Range Not Satisfiable";
            default -> "Service Unavailable";
        };
    }

    private static String detail(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The stored report selector was rejected";
            case UNAUTHORIZED, FORBIDDEN -> "Report access denied";
            case NOT_FOUND -> "Report not found";
            case UNPROCESSABLE_ENTITY -> "The selected report cannot be generated with these parameters";
            case PAYLOAD_TOO_LARGE -> "Report exceeds the download limit";
            case TOO_MANY_REQUESTS -> "Retry after the ticket window expires";
            default -> "Report service is unavailable";
        };
    }

    @Override
    public int getOrder() {
        return -200;
    }

    private enum Backend {
        MOBILE_BFF,
        ATTENDANCE
    }

    private record Dispatch(
            Backend backend,
            HttpMethod method,
            String path,
            Map<String, List<String>> query,
            Object body,
            String expectedMediaType,
            String filename
    ) {
    }

    public record WeeklyExportPayload(List<String> weekStarts, String format) {
    }

    public record HeadmanStatsExportPayload(
            Long subjectId,
            List<String> lessonTypes,
            List<IssueReportDownloadTicketRequest.HeadmanStatsSort> sorts,
            List<IssueReportDownloadTicketRequest.HeadmanStatsFilter> filters,
            String format
    ) {
    }

    public record HeadmanStatsTrendQueryPayload(
            String mode,
            String weekStart,
            Long subjectId,
            List<String> lessonTypes
    ) {
    }

    public record HeadmanStatsTrendExportPayload(
            HeadmanStatsTrendQueryPayload query,
            String format
    ) {
    }

    private static final class ReportDownloadResponseException extends RuntimeException {
        private final HttpStatus status;

        private ReportDownloadResponseException(HttpStatus status) {
            this(status, null);
        }

        private ReportDownloadResponseException(HttpStatus status, Throwable cause) {
            super("Report download response failed", cause);
            this.status = status;
        }

        private HttpStatus status() {
            return status;
        }
    }
}
