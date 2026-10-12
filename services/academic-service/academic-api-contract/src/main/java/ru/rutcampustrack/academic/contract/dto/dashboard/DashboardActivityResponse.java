package ru.rutcampustrack.academic.contract.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Explicit nulls distinguish zero activity, missing history and unavailable dependencies. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DashboardActivityResponse(
        Instant observedAt,
        String timeZone,
        String period,
        String bucket,
        String historyScope,
        String consistency,
        Section summary,
        List<Section> series) {
    public enum State {
        AVAILABLE,
        UNAVAILABLE,
        INSUFFICIENT_HISTORY
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Range(Instant from, Instant to) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Section(Range range, Map<String, Metric> metrics) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Coverage(String kind, Instant from) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Comparison(
            State state,
            Range range,
            Long previousValue,
            Long absoluteDelta,
            BigDecimal relativeDeltaPercent,
            BigDecimal previousPercent,
            BigDecimal percentPointDelta) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Metric(
            State state,
            Long value,
            Long numerator,
            Long denominator,
            BigDecimal percent,
            Coverage coverage,
            String reason,
            Comparison comparison) {}
}
