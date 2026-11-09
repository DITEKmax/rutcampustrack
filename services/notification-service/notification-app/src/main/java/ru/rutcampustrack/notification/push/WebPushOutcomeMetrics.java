package ru.rutcampustrack.notification.push;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** Process-local observations, not durable receipts or evidence of device delivery/read. */
@Component
public class WebPushOutcomeMetrics {
    public enum DispatchOutcome {
        SKIPPED_NO_SUBSCRIPTIONS,
        SKIPPED_PREFERENCES,
        SKIPPED_AUDIENCE,
        RETRYABLE,
        UNCERTAIN
    }

    private final Counter accepted;
    private final Counter expired;
    private final Counter rejected;
    private final Map<DispatchOutcome, Counter> dispatch = new EnumMap<>(DispatchOutcome.class);

    public WebPushOutcomeMetrics(MeterRegistry registry) {
        accepted = providerCounter(registry, "accepted_by_provider");
        expired = providerCounter(registry, "expired_subscription");
        rejected = providerCounter(registry, "retryable");
        for (DispatchOutcome outcome : DispatchOutcome.values()) {
            dispatch.put(
                    outcome,
                    Counter.builder("notification.webpush.dispatch.outcomes")
                            .description(
                                    "Dispatch observations: skips per subscription (no"
                                        + " subscriptions per intent), retryable/uncertain per"
                                        + " dispatch; not user delivery")
                            .tag("outcome", outcome.name().toLowerCase(java.util.Locale.ROOT))
                            .register(registry));
        }
    }

    /** Record the response before local cleanup/receipt writes can fail. */
    public void providerResponse(int status) {
        if (status >= 200 && status < 300) accepted.increment();
        else if (status == 404 || status == 410) expired.increment();
        else rejected.increment();
    }

    public void dispatch(DispatchOutcome outcome) {
        dispatch.get(outcome).increment();
    }

    private static Counter providerCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("notification.webpush.provider.responses")
                .description(
                        "Observed provider HTTP responses; acceptance does not prove device"
                            + " delivery/read or a persisted intent receipt")
                .tag("outcome", outcome)
                .register(registry);
    }
}
