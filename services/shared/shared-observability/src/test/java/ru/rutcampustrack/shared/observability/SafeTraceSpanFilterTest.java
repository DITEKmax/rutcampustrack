package ru.rutcampustrack.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper.ScopeAttachedEvent;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class SafeTraceSpanFilterTest {
    @Test
    void actualCompositeExporterRetainsIdsAndErrorOutcomeWithoutRequestExceptionOrLinkContent() {
        String marker = "SYNTHETIC_CREDENTIAL_MARKER";
        var exported = new ArrayList<SpanData>();
        SpanExporter capture =
                new SpanExporter() {
                    @Override
                    public CompletableResultCode export(Collection<SpanData> spans) {
                        exported.addAll(spans);
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
                };
        var composite =
                new CompositeSpanExporter(
                        List.of(capture), List.of(), List.of(), List.of(new SafeTraceSpanFilter()));
        var builder =
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(composite));
        builder.addSpanProcessor(
                new TracePrivacyAutoConfiguration().canonicalRequestCorrelationSpanProcessor());
        new TracePrivacyAutoConfiguration().boundedTraceSpanLimits().customize(builder);
        new TracePrivacyAutoConfiguration()
                .coherentTraceResourceIdentity(
                        new MockEnvironment()
                                .withProperty("spring.application.name", "test-service")
                                .withProperty("DEPLOYMENT_ENVIRONMENT", "test-environment")
                                .withProperty("SERVICE_VERSION", "test-version")
                                .withProperty("HOSTNAME", "test-container"))
                .customize(builder);
        var parent =
                SpanContext.createFromRemoteParent(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "bbbbbbbbbbbbbbbb",
                        TraceFlags.getSampled(),
                        TraceState.builder().put("vendor", marker).build());
        String requestId = "11111111-1111-4111-8111-111111111111";
        Context remote =
                Baggage.builder()
                        .put("requestId", requestId)
                        .put("authorization", marker)
                        .build()
                        .storeInContext(Context.root().with(Span.wrap(parent)));
        try (var provider = builder.build()) {
            var spanBuilder =
                    provider.get("safe-export-test")
                            .spanBuilder("GET /reports/download/" + marker)
                            .setSpanKind(SpanKind.CLIENT)
                            .setParent(remote);
            for (int i = 0; i < 40; i++) {
                spanBuilder.addLink(
                        parent, Attributes.of(AttributeKey.stringKey("authorization"), marker));
            }
            var span = spanBuilder.startSpan();
            span.setAttribute("http.method", "GET");
            span.setAttribute("http.status_code", 503);
            span.setAttribute("uri", "/reports/download/" + marker);
            span.setAttribute("http.route", "/reports/download/" + marker);
            span.setAttribute("exception", "IllegalStateException");
            for (String field :
                    List.of(
                            "http.url",
                            "url.full",
                            "url.query",
                            "url.path",
                            "http.target",
                            "authorization",
                            "http.request.header.authorization",
                            "http.request.body",
                            "password")) {
                span.setAttribute(
                        field, "https://service/reports/download/" + marker + "?ticket=" + marker);
            }
            span.recordException(new IllegalStateException(marker));
            span.setStatus(StatusCode.ERROR, marker);
            for (int i = 0; i < 40; i++) {
                span.addEvent(
                        "untrusted-" + marker,
                        Attributes.of(AttributeKey.stringKey("secret"), marker));
            }
            for (int i = 0; i < 80; i++) {
                span.setAttribute("untrusted-" + i, marker.repeat(30));
            }
            SpanData raw = ((ReadableSpan) span).toSpanData();
            assertThat(raw.getAttributes().size()).isEqualTo(64);
            assertThat(raw.getAttributes().get(AttributeKey.stringKey("untrusted-0"))).hasSize(256);
            assertThat(raw.getEvents()).hasSize(32);
            assertThat(raw.getLinks()).hasSize(32);
            String spanId = span.getSpanContext().getSpanId();
            span.end();

            assertThat(exported).hasSize(1);
            var safe = exported.getFirst();
            assertThat(safe.getResource().getAttribute(AttributeKey.stringKey("service.name")))
                    .isEqualTo("test-service");
            assertThat(
                            safe.getResource()
                                    .getAttribute(AttributeKey.stringKey("deployment.environment")))
                    .isEqualTo("test-environment");
            assertThat(safe.getResource().getAttribute(AttributeKey.stringKey("service.version")))
                    .isEqualTo("test-version");
            assertThat(
                            safe.getResource()
                                    .getAttribute(AttributeKey.stringKey("service.instance.id")))
                    .isEqualTo("test-container");
            assertThat(safe.getAttributes().get(AttributeKey.stringKey("requestId")))
                    .isEqualTo(requestId);
            assertThat(safe.getAttributes().get(AttributeKey.stringKey("authorization"))).isNull();
            assertThat(safe.getTraceId()).isEqualTo(parent.getTraceId());
            assertThat(safe.getSpanId()).isEqualTo(spanId);
            assertThat(safe.getParentSpanId()).isEqualTo(parent.getSpanId());
            assertThat(safe.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(safe.getStatus().getDescription()).isEmpty();
            assertThat(safe.getAttributes().get(AttributeKey.stringKey("exception")))
                    .isEqualTo("IllegalStateException");
            assertThat(safe.getAttributes().get(AttributeKey.stringKey("uri"))).isNull();
            assertThat(safe.getAttributes().get(AttributeKey.stringKey("http.route"))).isNull();
            assertThat(safe.getName()).isEqualTo("http GET");
            assertThat(safe.getSpanContext().getTraceState().isEmpty()).isTrue();
            assertThat(safe.getParentSpanContext().getTraceState().isEmpty()).isTrue();
            assertThat(
                            safe.getAttributes().toString()
                                    + safe.getEvents()
                                    + safe.getLinks()
                                    + safe.getStatus()
                                    + safe.getName())
                    .doesNotContain(marker);
            var server =
                    provider.get("safe-export-test")
                            .spanBuilder("GET report")
                            .setSpanKind(SpanKind.SERVER)
                            .startSpan();
            new TracePrivacyAutoConfiguration()
                    .canonicalRequestCorrelationScopeListener()
                    .onEvent(new ScopeAttachedEvent(remote.with(server)));
            server.setAttribute("uri", "/reports/download/{ticket}");
            server.end();
            assertThat(exported.getLast().getAttributes().get(AttributeKey.stringKey("uri")))
                    .isEqualTo("/reports/download/{ticket}");
            var malformed =
                    provider.get("safe-export-test")
                            .spanBuilder("invalid baggage")
                            .setParent(
                                    Baggage.builder()
                                            .put("requestId", marker)
                                            .build()
                                            .storeInContext(Context.root()))
                            .startSpan();
            new TracePrivacyAutoConfiguration()
                    .canonicalRequestCorrelationScopeListener()
                    .onEvent(
                            new ScopeAttachedEvent(
                                    Baggage.builder()
                                            .put("requestId", marker)
                                            .build()
                                            .storeInContext(Context.root().with(malformed))));
            malformed.end();
            assertThat(exported.getLast().getAttributes().get(AttributeKey.stringKey("requestId")))
                    .isNull();
            assertThat(Span.current().getSpanContext().isValid()).isFalse();
            assertThat(Baggage.current().getEntryValue("requestId")).isNull();
            assertThat(safe.getLinks())
                    .allSatisfy(
                            link -> {
                                assertThat(link.getAttributes().isEmpty()).isTrue();
                                assertThat(link.getSpanContext().getTraceState().isEmpty())
                                        .isTrue();
                            });
        }
    }
}
