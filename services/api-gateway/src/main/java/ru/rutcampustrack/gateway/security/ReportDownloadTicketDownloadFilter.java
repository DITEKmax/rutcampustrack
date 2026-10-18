package ru.rutcampustrack.gateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import ru.rutcampustrack.gateway.clientip.TrustedClientIpResolver;

import java.time.Duration;
import java.util.List;

/** Exact bearer-free route and admission budget. Academic alone resolves jobs and serves bytes. */
@Component
public final class ReportDownloadTicketDownloadFilter implements GlobalFilter, Ordered {
    private static final String PREFIX = "/api/report-download/";
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final WebClient academic;
    private final ReportDownloadAttemptRateLimiter limiter;

    @Autowired
    public ReportDownloadTicketDownloadFilter(
            ReportDownloadBackendProperties properties, ReportDownloadAttemptRateLimiter limiter) {
        this(WebClient.builder().baseUrl(properties.getAcademicServiceUrl()).build(), limiter);
    }

    ReportDownloadTicketDownloadFilter(
            WebClient academic, ReportDownloadAttemptRateLimiter limiter) {
        this.academic = academic;
        this.limiter = limiter;
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path == null || !path.startsWith(PREFIX)) return chain.filter(exchange);
        String ticket = path.substring(PREFIX.length());
        if (ticket.isEmpty() || ticket.contains("/")) return chain.filter(exchange);
        var response = exchange.getResponse();
        response.getHeaders().setCacheControl(CacheControl.noStore());
        response.getHeaders().set("Referrer-Policy", "no-referrer");
        response.getHeaders().set("X-Content-Type-Options", "nosniff");
        response.getHeaders().set("Accept-Ranges", "none");
        if ("https://web.telegram.org".equals(exchange.getRequest().getHeaders().getOrigin())) {
            response.getHeaders().setAccessControlAllowOrigin("https://web.telegram.org");
            response.getHeaders().setVary(List.of("Origin"));
        }
        if (exchange.getRequest().getMethod() != HttpMethod.GET) return problem(exchange, 405);
        if (exchange.getRequest().getURI().getRawQuery() != null
                || exchange.getRequest().getHeaders().getContentLength() > 0
                || exchange.getRequest().getHeaders().containsKey(HttpHeaders.TRANSFER_ENCODING))
            return problem(exchange, 400);
        if (exchange.getRequest().getHeaders().containsKey(HttpHeaders.RANGE))
            return problem(exchange, 416);
        if (!ticket.matches("[A-Za-z0-9_-]{43}")) return problem(exchange, 404);
        String address = exchange.getAttribute(TrustedClientIpResolver.CLIENT_IP_ATTRIBUTE);
        String clientIp =
                address == null
                        ? TrustedClientIpResolver.canonicalizeRemoteAddress(
                                exchange.getRequest().getRemoteAddress())
                        : TrustedClientIpResolver.canonicalizeLiteral(address)
                                .orElse(TrustedClientIpResolver.UNKNOWN);
        return limiter.tryAcquire(clientIp)
                .flatMap(allowed -> allowed ? forward(exchange, ticket) : problem(exchange, 429))
                .onErrorResume(failure -> problem(exchange, 503));
    }

    private Mono<Void> forward(ServerWebExchange exchange, String ticket) {
        // Deliberately forward no caller Authorization, cookies, selector, URL or internal header.
        return academic.get()
                .uri("/academic/report-download/{ticket}", ticket)
                .exchangeToMono(
                        remote -> {
                            if (remote.statusCode().value() != 200) {
                                int status = remote.statusCode().value();
                                return remote.releaseBody()
                                        .then(
                                                problem(
                                                        exchange,
                                                        List.of(400, 401, 403, 404, 413, 422, 429)
                                                                        .contains(status)
                                                                ? status
                                                                : 503));
                            }
                            MediaType type = remote.headers().contentType().orElse(null);
                            String disposition =
                                    remote.headers()
                                            .asHttpHeaders()
                                            .getFirst(HttpHeaders.CONTENT_DISPOSITION);
                            String filename;
                            try {
                                filename = ContentDisposition.parse(disposition).getFilename();
                            } catch (RuntimeException invalid) {
                                return remote.releaseBody().then(problem(exchange, 503));
                            }
                            if (type == null
                                    || filename == null
                                    || !filename.matches(
                                            "[a-z0-9-]+\\.(docx|pdf|zip|html|xlsx|png)")
                                    || remote.headers().contentLength().orElse(0) > MAX_BYTES)
                                return remote.releaseBody().then(problem(exchange, 503));
                            String expected =
                                    switch (filename.substring(filename.lastIndexOf('.') + 1)) {
                                        case "docx" ->
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                                        case "xlsx" ->
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                                        case "pdf" -> "application/pdf";
                                        case "zip" -> "application/zip";
                                        case "png" -> "image/png";
                                        default -> "text/html";
                                    };
                            if (!MediaType.parseMediaType(expected).isCompatibleWith(type))
                                return remote.releaseBody().then(problem(exchange, 503));
                            return DataBufferUtils.join(
                                            remote.bodyToFlux(DataBuffer.class), MAX_BYTES)
                                    .switchIfEmpty(
                                            Mono.error(new IllegalStateException("empty report")))
                                    .flatMap(
                                            buffer -> {
                                                try {
                                                    if (buffer.readableByteCount() == 0)
                                                        return problem(exchange, 503);
                                                    byte[] bytes =
                                                            new byte[buffer.readableByteCount()];
                                                    buffer.read(bytes);
                                                    exchange.getResponse()
                                                            .setStatusCode(HttpStatus.OK);
                                                    exchange.getResponse()
                                                            .getHeaders()
                                                            .setContentType(type);
                                                    exchange.getResponse()
                                                            .getHeaders()
                                                            .set(
                                                                    HttpHeaders.CONTENT_DISPOSITION,
                                                                    ContentDisposition.attachment()
                                                                            .filename(
                                                                                    filename,
                                                                                    java.nio.charset
                                                                                            .StandardCharsets
                                                                                            .UTF_8)
                                                                            .build()
                                                                            .toString());
                                                    return exchange.getResponse()
                                                            .writeWith(
                                                                    Mono.just(
                                                                            exchange.getResponse()
                                                                                    .bufferFactory()
                                                                                    .wrap(bytes)));
                                                } finally {
                                                    DataBufferUtils.release(buffer);
                                                }
                                            });
                        })
                .timeout(Duration.ofSeconds(60));
    }

    private static Mono<Void> problem(ServerWebExchange exchange, int status) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl(CacheControl.noStore());
        byte[] body =
                ("{\"status\":" + status + ",\"title\":\"Report unavailable\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
