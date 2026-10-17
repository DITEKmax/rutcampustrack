package ru.rutcampustrack.shared.observability;

import io.micrometer.tracing.exporter.SpanFilter;
import io.micrometer.tracing.otel.bridge.EventListener;
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper.ScopeAttachedEvent;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;

import org.springframework.boot.actuate.autoconfigure.tracing.SdkTracerProviderBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/** Applies at the actual framework export boundary, including exception events and status text. */
@AutoConfiguration(
        beforeName =
                "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration")
@ConditionalOnClass(
        name = {
            "io.micrometer.tracing.otel.bridge.OtelFinishedSpan",
            "io.opentelemetry.sdk.trace.SdkTracerProvider",
            "org.springframework.boot.actuate.autoconfigure.tracing.SdkTracerProviderBuilderCustomizer"
        })
public class TracePrivacyAutoConfiguration {
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SpanFilter safeTraceSpanFilter() {
        return new SafeTraceSpanFilter();
    }

    /** Remote extraction carries baggage without invoking Micrometer's explicit baggage mutator. */
    @Bean
    SpanProcessor canonicalRequestCorrelationSpanProcessor() {
        return new SpanProcessor() {
            @Override
            public void onStart(Context parentContext, ReadWriteSpan span) {
                String requestId = Baggage.fromContext(parentContext).getEntryValue("requestId");
                if (RequestCorrelation.isCanonical(requestId)) {
                    span.setAttribute("requestId", requestId);
                }
            }

            @Override
            public boolean isStartRequired() {
                return true;
            }

            @Override
            public void onEnd(ReadableSpan span) {}

            @Override
            public boolean isEndRequired() {
                return false;
            }
        };
    }

    /** Micrometer restores extracted remote baggage when it attaches the receiver span scope. */
    @Bean
    EventListener canonicalRequestCorrelationScopeListener() {
        return event -> {
            if (event instanceof ScopeAttachedEvent attached
                    && attached.getBaggage() != null
                    && attached.getSpan() != null) {
                String requestId = attached.getBaggage().getEntryValue("requestId");
                if (RequestCorrelation.isCanonical(requestId)) {
                    attached.getSpan().setAttribute("requestId", requestId);
                }
            }
        };
    }

    @Bean
    SdkTracerProviderBuilderCustomizer boundedTraceSpanLimits() {
        return builder ->
                builder.setSpanLimits(
                        SpanLimits.builder()
                                .setMaxNumberOfAttributes(64)
                                .setMaxNumberOfEvents(32)
                                .setMaxNumberOfLinks(32)
                                .setMaxNumberOfAttributesPerEvent(16)
                                .setMaxNumberOfAttributesPerLink(8)
                                .setMaxAttributeValueLength(256)
                                .build());
    }

    @Bean
    SdkTracerProviderBuilderCustomizer coherentTraceResourceIdentity(Environment environment) {
        return builder ->
                builder.addResource(
                        Resource.create(
                                Attributes.builder()
                                        .put(
                                                "service.name",
                                                environment.getProperty(
                                                        "spring.application.name",
                                                        "unknown-service"))
                                        .put(
                                                "deployment.environment",
                                                environment.getProperty(
                                                        "DEPLOYMENT_ENVIRONMENT", "local"))
                                        .put(
                                                "service.version",
                                                environment.getProperty(
                                                        "SERVICE_VERSION", "unknown"))
                                        .put(
                                                "service.instance.id",
                                                environment.getProperty("HOSTNAME", "unknown"))
                                        .build()));
    }
}
