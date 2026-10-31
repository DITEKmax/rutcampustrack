package ru.rutcampustrack.gateway.observability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Jwts;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.WebFilter;

import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.UserRequest;
import ru.rutcampustrack.academic.grpc.UserResponse;
import ru.rutcampustrack.gateway.GatewayApplication;
import ru.rutcampustrack.mobilebff.MobileBffApplication;
import ru.rutcampustrack.shared.events.EventTraceContext;
import ru.rutcampustrack.shared.observability.RequestCorrelation;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Real Gateway and servlet BFF contexts plus a TCP generated Academic gRPC contract fixture. Auth
 * authority is synthetic; this test accepts transport propagation, not domain/auth authority.
 */
class GatewayBffTracePropagationIT {
    private static final String MARKER = "SYNTHETIC_PRIVATE_MARKER";
    private static final UUID SESSION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class AcademicTelemetryConfiguration {}

    record Seen(
            String requestId,
            String traceId,
            String spanId,
            String mdcTrace,
            String mdcRequest,
            String durableRequestId,
            String durableTraceparent) {}

    @Test
    void gatewayBffGrpcParentsAndCorrelationRemainIndependentAfterErrorAndHttpCancellation()
            throws Exception {
        assertActualServiceBaggageDefaults();
        var collector = CollectorSettings.fromEnvironment();
        CollectorLogs applicationLogs = null;
        var capture = new CapturedExporter();
        var signer = new InternalJwtTestFactory();
        var auth = new WireMockServer(wireMockConfig().dynamicPort());
        var grpcExecutor = Executors.newSingleThreadExecutor();
        var seen = new CopyOnWriteArrayList<Seen>();
        var mode = new AtomicReference<>("success");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Server grpc = null;
        var contexts = new ArrayList<ConfigurableApplicationContext>();
        auth.start();
        try {
            String pem =
                    "-----BEGIN PUBLIC KEY-----\n"
                            + Base64.getEncoder().encodeToString(signer.publicKey().getEncoded())
                            + "\n-----END PUBLIC KEY-----";
            auth.stubFor(
                    get(urlEqualTo("/auth/public-key"))
                            .willReturn(
                                    aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    JSON.writeValueAsString(
                                                            Map.of(
                                                                    "publicKey",
                                                                    pem,
                                                                    "algorithm",
                                                                    "RS256")))));
            String internal =
                    signer.validToken(42, SESSION, 7, 3, "STUDENT", "ACTIVE", null, false, false);
            auth.stubFor(
                    post(urlEqualTo("/internal/auth/admit"))
                            .willReturn(
                                    aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    JSON.writeValueAsString(
                                                            Map.ofEntries(
                                                                    Map.entry(
                                                                            "internalToken",
                                                                            internal),
                                                                    Map.entry(
                                                                            "expiresAt",
                                                                            Jwts.parser()
                                                                                    .verifyWith(
                                                                                            signer.keyPair()
                                                                                                    .getPublic())
                                                                                    .build()
                                                                                    .parseSignedClaims(
                                                                                            internal)
                                                                                    .getPayload()
                                                                                    .getExpiration()
                                                                                    .toInstant()
                                                                                    .toString()),
                                                                    Map.entry(
                                                                            "sessionId",
                                                                            SESSION.toString()),
                                                                    Map.entry("userId", "42"),
                                                                    Map.entry(
                                                                            "sessionVersion", "7"),
                                                                    Map.entry("rolesVersion", "3"),
                                                                    Map.entry("role", "STUDENT"),
                                                                    Map.entry("status", "ACTIVE"),
                                                                    Map.entry("isHeadman", false),
                                                                    Map.entry(
                                                                            "readOnly", false))))));
            var service =
                    start(
                            AcademicTelemetryConfiguration.class,
                            WebApplicationType.NONE,
                            capture,
                            collector,
                            "--spring.cloud.gateway.server.webflux.enabled=false",
                            "--spring.cloud.gateway.enabled=false");
            contexts.add(service);
            grpc =
                    ServerBuilder.forPort(0)
                            .executor(grpcExecutor)
                            .intercept(
                                    new ObservationGrpcServerInterceptor(
                                            service.getBean(ObservationRegistry.class)))
                            .addService(
                                    new AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase() {
                                        @Override
                                        public void getUserById(
                                                UserRequest request,
                                                StreamObserver<UserResponse> response) {
                                            var span = Span.current().getSpanContext();
                                            var durableEnvelope = new HashMap<String, Object>();
                                            EventTraceContext.capture(durableEnvelope);
                                            seen.add(
                                                    new Seen(
                                                            Baggage.current()
                                                                    .getEntryValue("requestId"),
                                                            span.getTraceId(),
                                                            span.getSpanId(),
                                                            MDC.get("traceId"),
                                                            MDC.get("requestId"),
                                                            (String)
                                                                    durableEnvelope.get(
                                                                            "request_id"),
                                                            (String)
                                                                    durableEnvelope.get(
                                                                            "traceparent")));
                                            String behavior = mode.get();
                                            if ("cancel".equals(behavior)) {
                                                entered.countDown();
                                                try {
                                                    release.await(5, TimeUnit.SECONDS);
                                                } catch (InterruptedException interrupted) {
                                                    Thread.currentThread().interrupt();
                                                }
                                            }
                                            if ("error".equals(behavior)) {
                                                response.onError(
                                                        Status.UNAVAILABLE
                                                                .withDescription(MARKER)
                                                                .asRuntimeException());
                                            } else {
                                                response.onNext(
                                                        UserResponse.newBuilder()
                                                                .setId(request.getUserId())
                                                                .setDisplayName(
                                                                        "Синтетический студент")
                                                                .build());
                                                response.onCompleted();
                                            }
                                        }
                                    })
                            .build()
                            .start();
            String authority = "http://127.0.0.1:" + auth.port();
            var bff =
                    start(
                            MobileBffApplication.class,
                            WebApplicationType.SERVLET,
                            capture,
                            collector,
                            "--spring.cloud.gateway.server.webflux.enabled=false",
                            "--spring.cloud.gateway.enabled=false",
                            "--rutcampustrack.security.internal-jwt.auth-service-url=" + authority,
                            "--grpc.client.academic-service.address=static://127.0.0.1:"
                                    + grpc.getPort(),
                            "--grpc.client.academic-service.negotiation-type=plaintext",
                            "--grpc.client.schedule-service.address=static://127.0.0.1:1",
                            "--grpc.client.schedule-service.negotiation-type=plaintext",
                            "--grpc.client.attendance-service.address=static://127.0.0.1:1",
                            "--grpc.client.attendance-service.negotiation-type=plaintext");
            contexts.add(bff);
            int bffPort = ((WebServerApplicationContext) bff).getWebServer().getPort();
            var gateway =
                    start(
                            GatewayApplication.class,
                            WebApplicationType.REACTIVE,
                            capture,
                            collector,
                            "--MOBILE_BFF_URL=http://127.0.0.1:" + bffPort,
                            "--gateway.auth-service-url=" + authority,
                            "--rutcampustrack.security.internal-issuer-client.auth-service-url="
                                    + authority,
                            "--spring.data.redis.host=127.0.0.1",
                            "--spring.data.redis.port=1",
                            "--spring.data.redis.connect-timeout=50ms",
                            "--spring.data.redis.timeout=50ms",
                            "--rutcampustrack.export-worker.enabled=false");
            contexts.add(gateway);
            assertThat(gateway).isInstanceOf(ReactiveWebServerApplicationContext.class);
            int gatewayPort = ((WebServerApplicationContext) gateway).getWebServer().getPort();
            applicationLogs = CollectorLogs.open(collector);
            Instant now = Instant.now();
            String access =
                    Jwts.builder()
                            .subject("42")
                            .issuer("rutcampustrack-auth")
                            .audience()
                            .add("rutcampustrack")
                            .and()
                            .claim("token_use", "access")
                            .claim("sid", SESSION.toString())
                            .claim("sv", "7")
                            .claim("rv", "3")
                            .claim("role", "STUDENT")
                            .claim("status", "ACTIVE")
                            .claim("is_headman", false)
                            .claim("readOnly", false)
                            .issuedAt(Date.from(now.minusSeconds(5)))
                            .expiration(Date.from(now.plusSeconds(300)))
                            .signWith(signer.keyPair().getPrivate(), Jwts.SIG.RS256)
                            .compact();
            var request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            "http://127.0.0.1:"
                                                    + gatewayPort
                                                    + "/api/v1/student/session?diagnostic="
                                                    + MARKER))
                            .header("Authorization", "Bearer " + access)
                            .header(RequestCorrelation.HEADER, MARKER)
                            .header("requestId", MARKER)
                            .header("baggage", "requestId=" + MARKER)
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
            try (var http = HttpClient.newHttpClient()) {
                var early =
                        http.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://127.0.0.1:"
                                                                + gatewayPort
                                                                + "/api/v1/student/session"))
                                        .header(RequestCorrelation.HEADER, MARKER)
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertThat(early.statusCode()).isEqualTo(401);
                assertThat(
                                RequestCorrelation.isCanonical(
                                        early.headers()
                                                .firstValue(RequestCorrelation.HEADER)
                                                .orElseThrow()))
                        .isTrue();
                assertThat(seen).isEmpty();
                var first = http.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(first.statusCode())
                        .as(
                                "first response=%s, service calls=%s, auth calls=%s",
                                first.body(), seen.size(), auth.getAllServeEvents().size())
                        .isEqualTo(200);
                assertThat(first.body()).contains("Синтетический студент");
                mode.set("error");
                var second = http.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(second.statusCode()).isEqualTo(503);
                assertThat(seen).hasSize(2);
                assertThat(first.headers().firstValue(RequestCorrelation.HEADER))
                        .contains(seen.get(0).requestId());
                assertThat(second.headers().firstValue(RequestCorrelation.HEADER))
                        .contains(seen.get(1).requestId());
                assertThat(JSON.readTree(second.body()).get("traceId").asText())
                        .isEqualTo(seen.get(1).traceId());

                mode.set("cancel");
                // Close the real caller connection after the backend has started; cancelling a
                // CompletableFuture alone does not guarantee HTTP transport cancellation.
                try (var caller = new Socket("127.0.0.1", gatewayPort)) {
                    caller.setSoLinger(true, 0);
                    caller.getOutputStream()
                            .write(
                                    ("GET /api/v1/student/session HTTP/1.1\r\n"
                                                    + "Host: 127.0.0.1:"
                                                    + gatewayPort
                                                    + "\r\n"
                                                    + "Authorization: Bearer "
                                                    + access
                                                    + "\r\n\r\n")
                                            .getBytes(StandardCharsets.US_ASCII));
                    caller.getOutputStream().flush();
                    assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                }
                long cancellationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                var meters = gateway.getBean(io.micrometer.core.instrument.MeterRegistry.class);
                while (meters
                                        .find("http.server.requests")
                                        .tag("routeId", "mobile-bff")
                                        .tag("termination", "cancelled")
                                        .timers()
                                        .stream()
                                        .mapToLong(io.micrometer.core.instrument.Timer::count)
                                        .sum()
                                == 0
                        && System.nanoTime() < cancellationDeadline) {
                    Thread.sleep(20);
                }
                release.countDown();
                assertThat(
                                grpcExecutor
                                        .submit(() -> Span.current().getSpanContext().isValid())
                                        .get(10, TimeUnit.SECONDS))
                        .isFalse();
                mode.set("success");
                var after = http.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(after.statusCode()).isEqualTo(200);
                assertGatewayMeters(http, gatewayPort, seen);
            }
            assertThat(seen).hasSize(4);
            assertThat(seen).extracting(Seen::requestId).doesNotHaveDuplicates();
            assertThat(seen).extracting(Seen::traceId).doesNotHaveDuplicates();
            for (Seen call : seen) {
                assertThat(RequestCorrelation.isCanonical(call.requestId())).isTrue();
                assertThat(call.mdcTrace()).isEqualTo(call.traceId());
                assertThat(call.mdcRequest()).isEqualTo(call.requestId());
                assertThat(call.durableRequestId()).isEqualTo(call.requestId());
                assertThat(call.durableTraceparent())
                        .startsWith("00-" + call.traceId() + "-" + call.spanId() + "-");
            }
            flush(contexts);
            for (Seen call : List.of(seen.get(0), seen.get(1), seen.get(3))) {
                assertParentChain(capture.spans, call);
            }
            assertThat(capture.spans)
                    .allSatisfy(
                            span ->
                                    assertThat(
                                                    span.getName()
                                                            + span.getAttributes()
                                                            + span.getEvents()
                                                            + span.getLinks()
                                                            + span.getStatus())
                                            .doesNotContain(MARKER, access, internal));
            assertThat(Span.current().getSpanContext().isValid()).isFalse();
            assertThat(MDC.get("traceId")).isNull();
            assertThat(MDC.get("requestId")).isNull();
            applicationLogs.close();
            applicationLogs.verifyAndRecord(capture.spans, seen, access, internal);
        } finally {
            if (applicationLogs != null) applicationLogs.close();
            collector.cleanLocalLogs();
            release.countDown();
            for (int i = contexts.size() - 1; i >= 0; i--) contexts.get(i).close();
            if (grpc != null) grpc.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpcExecutor.shutdownNow();
            auth.stop();
        }
    }

    private static Path applicationConfig(String module, String filename) {
        Path repository = Path.of("").toAbsolutePath();
        while (repository != null
                && !Files.isRegularFile(repository.resolve("settings.gradle.kts"))) {
            repository = repository.getParent();
        }
        assertThat(repository)
                .as("repository containing actual application configuration")
                .isNotNull();
        return repository
                .resolve("services")
                .resolve(module)
                .resolve("src/main/resources")
                .resolve(filename);
    }

    private static void assertActualServiceBaggageDefaults() throws Exception {
        var loader = new YamlPropertySourceLoader();
        for (String module :
                List.of(
                        "api-gateway",
                        "mobile-bff/mobile-bff-app",
                        "academic-service/academic-app",
                        "attendance-service/attendance-app",
                        "schedule-service/schedule-app",
                        "auth-service/auth-app",
                        "notification-service/notification-app")) {
            var defaults = new MockEnvironment();
            for (String filename : List.of("application.yml", "application-prod.yml")) {
                Path path = applicationConfig(module, filename);
                if (Files.exists(path)) {
                    for (var source :
                            loader.load(module + ":" + filename, new FileSystemResource(path))) {
                        defaults.getPropertySources().addFirst(source);
                    }
                }
            }
            for (String suffix : List.of("remote-fields", "correlation.fields", "tag-fields")) {
                String key = "management.tracing.baggage." + suffix;
                assertThat(
                                Binder.get(defaults)
                                        .bind(key, Bindable.listOf(String.class))
                                        .orElse(List.of()))
                        .as("%s deployed default %s", module, key)
                        .containsExactly("requestId");
            }
        }
    }

    private static ConfigurableApplicationContext start(
            Class<?> source,
            WebApplicationType type,
            CapturedExporter capture,
            CollectorSettings collector,
            String... properties) {
        var args = new ArrayList<>(List.of(properties));
        String module =
                source == GatewayApplication.class
                        ? "api-gateway"
                        : source == MobileBffApplication.class
                                ? "mobile-bff/mobile-bff-app"
                                : "academic-service/academic-app";
        args.add(
                "--spring.config.location=" + applicationConfig(module, "application.yml").toUri());
        args.addAll(
                List.of(
                        "--server.port=0",
                        "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off",
                        "--spring.jmx.enabled=false"));
        if (collector.endpoint() == null) {
            args.add("--management.otlp.tracing.export.enabled=false");
        } else {
            // Collector address is test I/O; all tracing/baggage defaults still come from app YAML.
            args.add("--management.otlp.tracing.endpoint=" + collector.endpoint());
        }
        if (type != WebApplicationType.REACTIVE) {
            args.add(
                    "--spring.main.web-application-type="
                            + type.name().toLowerCase(java.util.Locale.ROOT));
            // The test-only Gateway dependency is absent from the standalone BFF/Academic
            // classpaths.
            args.add("--spring.cloud.gateway.server.webflux.redis.enabled=false");
        }
        var application = new SpringApplicationBuilder(source).profiles("test");
        if (type != WebApplicationType.REACTIVE) {
            application.web(type);
        }
        // Gateway must select its reactive stack from its shipped application YAML, even
        // though this fixture also has servlet BFF classes on the test classpath.
        return application
                .initializers(
                        context -> {
                            context.getBeanFactory().registerSingleton("captureExporter", capture);
                            // Fixed messages execute inside the real framework observation scope;
                            // IDs are supplied solely by the existing tracing/MDC integration.
                            if (type == WebApplicationType.REACTIVE) {
                                WebFilter probe =
                                        (exchange, chain) ->
                                                reactor.core.publisher.Mono.defer(
                                                        () -> {
                                                            LoggerFactory.getLogger(
                                                                            "ru.rutcampustrack.gateway.observability.CollectorProof")
                                                                    .info(
                                                                            "Application request"
                                                                                    + " reached"
                                                                                    + " Gateway");
                                                            return chain.filter(exchange);
                                                        });
                                context.getBeanFactory()
                                        .registerSingleton("collectorLogProbe", probe);
                            } else if (type == WebApplicationType.SERVLET) {
                                var probe = new FilterRegistrationBean<jakarta.servlet.Filter>();
                                probe.setFilter(
                                        (request, response, chain) -> {
                                            LoggerFactory.getLogger(
                                                            "ru.rutcampustrack.mobilebff.observability.CollectorProof")
                                                    .info("Application request reached BFF");
                                            chain.doFilter(request, response);
                                        });
                                probe.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
                                context.getBeanFactory()
                                        .registerSingleton("collectorLogProbe", probe);
                            }
                        })
                .run(args.toArray(String[]::new));
    }

    private static void flush(List<ConfigurableApplicationContext> contexts)
            throws InterruptedException {
        // Servlet completion is asynchronous relative to the final bytes received by the test
        // client.
        Thread.sleep(100);
        for (var context : contexts) {
            assertThat(
                            context.getBean(SdkTracerProvider.class)
                                    .forceFlush()
                                    .join(5, TimeUnit.SECONDS)
                                    .isSuccess())
                    .isTrue();
        }
    }

    private static void assertParentChain(List<SpanData> spans, Seen call) {
        var sameTrace =
                spans.stream().filter(span -> span.getTraceId().equals(call.traceId())).toList();
        var service =
                sameTrace.stream()
                        .filter(span -> span.getSpanId().equals(call.spanId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(service.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(identity(service)).isEqualTo("academic-service");
        var bffClient =
                sameTrace.stream()
                        .filter(span -> span.getSpanId().equals(service.getParentSpanId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(identity(bffClient)).isEqualTo("mobile-bff");
        assertThat(bffClient.getKind()).isEqualTo(SpanKind.CLIENT);
        var bffServer =
                sameTrace.stream()
                        .filter(span -> span.getSpanId().equals(bffClient.getParentSpanId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(identity(bffServer)).isEqualTo("mobile-bff");
        assertThat(bffServer.getKind()).isEqualTo(SpanKind.SERVER);
        var gatewayClient =
                sameTrace.stream()
                        .filter(span -> span.getSpanId().equals(bffServer.getParentSpanId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(identity(gatewayClient)).isEqualTo("api-gateway");
        assertThat(gatewayClient.getKind()).isEqualTo(SpanKind.CLIENT);
        var gatewayServer =
                sameTrace.stream()
                        .filter(span -> span.getSpanId().equals(gatewayClient.getParentSpanId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(identity(gatewayServer)).isEqualTo("api-gateway");
        assertThat(gatewayServer.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(
                        service.getAttributes()
                                .get(
                                        io.opentelemetry.api.common.AttributeKey.stringKey(
                                                "requestId")))
                .isEqualTo(call.requestId());
    }

    private static String identity(SpanData span) {
        return span.getResource()
                .getAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"));
    }

    private static void assertGatewayMeters(HttpClient http, int port, List<Seen> calls)
            throws Exception {
        var response =
                http.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/actuator/prometheus"))
                                .header("Accept", "text/plain; version=0.0.4")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> business =
                response.body()
                        .lines()
                        .filter(
                                line ->
                                        line.startsWith("spring_cloud_gateway_requests_seconds_")
                                                && line.contains("routeId=\"mobile-bff\""))
                        .toList();
        assertThat(business).isNotEmpty();
        for (String edge : List.of("2.0", "3.0", "30.0")) {
            assertThat(business).anyMatch(line -> line.contains("le=\"" + edge + "\""));
        }
        List<String> finalHttp =
                response.body()
                        .lines()
                        .filter(
                                line ->
                                        line.startsWith("http_server_requests_seconds_")
                                                && line.contains("routeId=\"mobile-bff\""))
                        .toList();
        assertThat(finalHttp).allMatch(line -> line.contains("uri=\"/api/v1/student/**\""));
        assertThat(finalHttp)
                .anyMatch(
                        line ->
                                line.startsWith("http_server_requests_seconds_count{")
                                        && line.contains("termination=\"cancelled\"")
                                        && line.endsWith(" 1"));
        assertThat(finalHttp)
                .anyMatch(
                        line ->
                                line.startsWith("http_server_requests_seconds_count{")
                                        && line.contains("termination=\"completed\"")
                                        && line.contains("status=\"200\"")
                                        && line.endsWith(" 2"));
        assertThat(finalHttp)
                .anyMatch(
                        line ->
                                line.startsWith("http_server_requests_seconds_count{")
                                        && line.contains("termination=\"completed\"")
                                        && line.contains("status=\"503\"")
                                        && line.endsWith(" 1"));
        for (String edge : List.of("2.0", "3.0", "30.0")) {
            assertThat(finalHttp).anyMatch(line -> line.contains("le=\"" + edge + "\""));
        }
        finalHttp.stream()
                .filter(line -> line.startsWith("http_server_requests_seconds_count{"))
                .forEach(line -> System.out.println("M02_FINAL_HTTP_METRIC " + line));
        for (String line : business) {
            String labels = line.substring(line.indexOf('{'), line.indexOf('}') + 1);
            assertThat(labels).doesNotContain(MARKER, "requestId", "traceId", "jobId");
            for (Seen call : calls) {
                assertThat(labels).doesNotContain(call.requestId(), call.traceId());
            }
            if (line.contains("le=\"2.0\"")
                    || line.contains("le=\"3.0\"")
                    || line.contains("le=\"30.0\"")) {
                System.out.println("M02_GATEWAY_METRIC " + line);
            }
        }
    }

    /** The outer pipeline validates the checkout and private local audit before collector I/O. */
    private static String collectorSource(Map<String, String> env) {
        boolean local =
                List.of(
                                        "RCT_LOCAL_RELEASE_REVISION",
                                        "RCT_LOCAL_RELEASE_NONCE",
                                        "RCT_LOCAL_RELEASE_AUDIT")
                                .stream()
                                .anyMatch(env::containsKey)
                        || "isolated-local".equals(env.get("RCT_MONITORING_RUNTIME"));
        String source;
        if (local) {
            assertThat(
                            List.of(
                                            "GITHUB_ACTIONS",
                                            "GITHUB_SHA",
                                            "GITHUB_RUN_ID",
                                            "GITHUB_RUN_ATTEMPT",
                                            "GITHUB_EVENT_NAME",
                                            "RCT_CI_PR_HEAD_SHA")
                                    .stream()
                                    .noneMatch(env::containsKey))
                    .as("LOCAL_CONTROL must not claim a GitHub identity")
                    .isTrue();
            assertThat(env.get("RCT_MONITORING_RUNTIME")).isEqualTo("isolated-local");
            assertThat(env.get("RCT_LOCAL_RELEASE_NONCE")).matches("[0-9a-f]{24}");
            assertThat(env.get("RCT_LOCAL_RELEASE_AUDIT")).isNotBlank();
            source = env.get("RCT_LOCAL_RELEASE_REVISION");
        } else {
            assertThat(env.get("GITHUB_ACTIONS")).isEqualTo("true");
            assertThat(env.get("GITHUB_RUN_ID")).matches("[0-9]+");
            assertThat(env.get("GITHUB_RUN_ATTEMPT")).matches("[0-9]+");
            assertThat(env.get("RCT_MONITORING_RUNTIME")).isEqualTo("isolated-ci");
            source = env.get("GITHUB_SHA");
        }
        assertThat(source).as("exact executable collector source revision").matches("[0-9a-f]{40}");
        assertThat(env.get("SERVICE_VERSION"))
                .as("collector resource and receipt must identify the same checked source")
                .isEqualTo(source);
        return source;
    }

    /** Optional private collector I/O; the ordinary IT also verifies actual JSON log encoding. */
    private record CollectorSettings(URI endpoint, Path logDir, Path receipt, String sourceSha) {
        static CollectorSettings fromEnvironment() throws IOException {
            String endpoint = System.getenv("RCT_TEST_OTLP_ENDPOINT");
            String logs = System.getenv("RCT_TEST_APP_LOG_DIR");
            String receipt = System.getenv("RCT_TEST_TELEMETRY_RECEIPT");
            if (endpoint == null && logs == null && receipt == null) {
                return new CollectorSettings(
                        null, Files.createTempDirectory("rct-app-logs-"), null, null);
            }
            assertThat(endpoint != null && logs != null && receipt != null)
                    .as("all three private collector I/O settings are required together")
                    .isTrue();
            URI target = URI.create(endpoint);
            assertThat(
                            "http".equals(target.getScheme())
                                    && Set.of("127.0.0.1", "localhost", "[::1]")
                                            .contains(target.getHost())
                                    && target.getPort() > 0
                                    && "/v1/traces".equals(target.getPath())
                                    && target.getRawUserInfo() == null
                                    && target.getRawQuery() == null
                                    && target.getRawFragment() == null)
                    .as("owned loopback OTLP endpoint")
                    .isTrue();
            Path directory = Path.of(logs);
            Path output = Path.of(receipt);
            assertThat(directory.isAbsolute() && output.isAbsolute()).isTrue();
            assertThat(Files.exists(output))
                    .as("private receipt must not overwrite existing data")
                    .isFalse();
            String source = collectorSource(System.getenv());
            Files.createDirectories(directory);
            return new CollectorSettings(target, directory, output, source);
        }

        void cleanLocalLogs() throws IOException {
            if (receipt == null) {
                Files.deleteIfExists(logDir.resolve("api-gateway.log"));
                Files.deleteIfExists(logDir.resolve("mobile-bff.log"));
                Files.deleteIfExists(logDir);
            }
        }
    }

    /** Actual logging events use encoders loaded from the existing application/shared config. */
    private static final class CollectorLogs implements AutoCloseable {
        private final CollectorSettings settings;
        private final List<LoggerContext> encodingContexts = new ArrayList<>();
        private final List<ch.qos.logback.classic.Logger> loggers = new ArrayList<>();
        private final List<FileAppender<ILoggingEvent>> appenders = new ArrayList<>();
        private boolean closed;

        private CollectorLogs(CollectorSettings settings) {
            this.settings = settings;
        }

        static CollectorLogs open(CollectorSettings settings) throws Exception {
            var logs = new CollectorLogs(settings);
            try {
                logs.attach("api-gateway", "api-gateway", "ru.rutcampustrack.gateway");
                logs.attach(
                        "mobile-bff", "mobile-bff/mobile-bff-app", "ru.rutcampustrack.mobilebff");
                return logs;
            } catch (Exception failure) {
                logs.close();
                throw failure;
            }
        }

        private void attach(String service, String module, String namespace) throws Exception {
            Path file = settings.logDir().resolve(service + ".log");
            Files.createFile(
                    file,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
            var encoding = new LoggerContext();
            encoding.setName("application-proof-" + service);
            encodingContexts.add(encoding);
            var configurator = new JoranConfigurator();
            configurator.setContext(encoding);
            configurator.doConfigure(
                    applicationConfig(module, "logback-spring.xml").toUri().toURL());
            var console =
                    (OutputStreamAppender<ILoggingEvent>)
                            encoding.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                                    .getAppender("SHARED_JSON_STDOUT");
            assertThat(console).as("actual shared production JSON appender").isNotNull();
            var appender = new FileAppender<ILoggingEvent>();
            appender.setContext(encoding);
            appender.setName("application-proof-" + service);
            appender.setFile(file.toString());
            appender.setAppend(true);
            appender.setEncoder(console.getEncoder());
            appender.start();
            assertThat(appender.isStarted()).isTrue();
            var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(namespace);
            logger.addAppender(appender);
            loggers.add(logger);
            appenders.add(appender);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (int i = 0; i < appenders.size(); i++) {
                loggers.get(i).detachAppender(appenders.get(i));
                appenders.get(i).stop();
            }
            encodingContexts.forEach(LoggerContext::stop);
        }

        void verifyAndRecord(List<SpanData> spans, List<Seen> calls, String access, String internal)
                throws Exception {
            var logReceipts = new ArrayList<Map<String, Object>>();
            for (String service : List.of("api-gateway", "mobile-bff")) {
                Path path = settings.logDir().resolve(service + ".log");
                byte[] bytes = Files.readAllBytes(path);
                String text = new String(bytes, StandardCharsets.UTF_8);
                assertThat(
                                text.contains(MARKER)
                                        || text.contains(access)
                                        || text.contains(internal))
                        .as("actual JSON application logs exclude synthetic credential content")
                        .isFalse();
                var entries = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                for (String line : text.lines().toList()) entries.add(JSON.readTree(line));
                for (Seen call : List.of(calls.get(0), calls.get(1), calls.get(3))) {
                    assertThat(
                                    entries.stream()
                                            .anyMatch(
                                                    entry ->
                                                            service.equals(
                                                                            entry.path("service")
                                                                                    .asText())
                                                                    && call.requestId()
                                                                            .equals(
                                                                                    entry.path(
                                                                                                    "requestId")
                                                                                            .asText())
                                                                    && call.traceId()
                                                                            .equals(
                                                                                    entry.path(
                                                                                                    "traceId")
                                                                                            .asText())
                                                                    && spans.stream()
                                                                            .anyMatch(
                                                                                    span ->
                                                                                            identity(
                                                                                                                    span)
                                                                                                            .equals(
                                                                                                                    service)
                                                                                                    && span.getTraceId()
                                                                                                            .equals(
                                                                                                                    call
                                                                                                                            .traceId())
                                                                                                    && span.getSpanId()
                                                                                                            .equals(
                                                                                                                    entry.path(
                                                                                                                                    "spanId")
                                                                                                                            .asText()))))
                            .as(
                                    "actual %s log has observed request/trace/span correlation",
                                    service)
                            .isTrue();
                }
                logReceipts.add(
                        Map.of(
                                "service",
                                service,
                                "file",
                                path.getFileName().toString(),
                                "sha256",
                                HexFormat.of()
                                        .formatHex(
                                                MessageDigest.getInstance("SHA-256").digest(bytes)),
                                "correlatedEntries",
                                entries.stream()
                                        .filter(
                                                entry ->
                                                        RequestCorrelation.isCanonical(
                                                                entry.path("requestId").asText()))
                                        .count()));
            }
            if (settings.receipt() == null) return;
            Set<String> traceIds = calls.stream().map(Seen::traceId).collect(Collectors.toSet());
            var cases = new ArrayList<Map<String, String>>();
            List<String> outcomes = List.of("success", "error", "cancelled", "recovery");
            for (int i = 0; i < calls.size(); i++) {
                Seen call = calls.get(i);
                cases.add(
                        Map.of(
                                "outcome",
                                outcomes.get(i),
                                "requestId",
                                call.requestId(),
                                "traceId",
                                call.traceId(),
                                "academicSpanId",
                                call.spanId()));
            }
            var graph =
                    spans.stream()
                            .filter(span -> traceIds.contains(span.getTraceId()))
                            .map(
                                    span ->
                                            Map.of(
                                                    "service",
                                                    identity(span),
                                                    "kind",
                                                    span.getKind().name(),
                                                    "traceId",
                                                    span.getTraceId(),
                                                    "spanId",
                                                    span.getSpanId(),
                                                    "parentSpanId",
                                                    span.getParentSpanId()))
                            .toList();
            var receipt =
                    Map.of(
                            "schema",
                            1,
                            "kind",
                            "actual_application_transport",
                            "source_sha",
                            settings.sourceSha(),
                            "cases",
                            cases,
                            "spans",
                            graph,
                            "logs",
                            logReceipts,
                            "qualification",
                            "Single-JVM real Gateway/BFF with synthetic auth and generated Academic"
                                    + " contract fixture");
            Files.createFile(
                    settings.receipt(),
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
            Files.writeString(
                    settings.receipt(),
                    JSON.writeValueAsString(receipt) + "\n",
                    StandardOpenOption.WRITE);
        }
    }

    private static final class CapturedExporter implements SpanExporter {
        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
