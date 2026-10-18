package ru.rutcampustrack.academic.exportjobs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.academic.scheduling.SingleInstanceScheduled;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Committed job effects and a bounded owner-database snapshot; no identity or file labels. */
@Component
public class ExportJobMetrics {
    private static final String UNKNOWN = "unknown";
    private static final String[][] TERMINALS = {
        {"success", "none"},
        {"failure", "generation_failed"},
        {"failure", "expired"},
        {"denied", "authority_denied"},
        {"rejected", "export_too_large"}
    };

    private enum Field {
        QUEUED,
        PREPARING,
        READY,
        ERROR,
        DUE,
        WAITING,
        NONTERMINAL_AGE,
        DUE_AGE,
        RUNNING_AGE,
        STUCK,
        FAILURES,
        EXPIRED
    }

    private final JdbcTemplate jdbc;
    private final Map<String, Counter> accepted = new LinkedHashMap<>();
    private final Map<String, Counter> retries = new LinkedHashMap<>();
    private final Map<String, Counter> terminals = new LinkedHashMap<>();
    private final Map<String, Timer> durations = new LinkedHashMap<>();
    private final AtomicReference<Map<String, double[]>> snapshot = new AtomicReference<>();
    private final AtomicLong snapshotTimestamp = new AtomicLong();
    private final AtomicLong lastFailureTimestamp = new AtomicLong();
    private final Counter snapshotFailures;

    public ExportJobMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        var initial = new LinkedHashMap<String, double[]>();
        for (ReportDownloadKind kind : ReportDownloadKind.values())
            initial.put(kind.code(), empty());
        initial.put(UNKNOWN, empty());
        snapshot.set(Map.copyOf(initial));
        for (String kind : initial.keySet()) {
            accepted.put(kind, registry.counter("rct.export.jobs.accepted", "kind", kind));
            retries.put(
                    kind,
                    registry.counter(
                            "rct.export.jobs.retries",
                            "kind",
                            kind,
                            "reason",
                            "generation_failed"));
            for (String[] terminal : TERMINALS) {
                terminals.put(
                        key(kind, terminal[0], terminal[1]),
                        registry.counter(
                                "rct.export.jobs.terminal",
                                "kind",
                                kind,
                                "outcome",
                                terminal[0],
                                "reason",
                                terminal[1]));
                durations.computeIfAbsent(
                        key(kind, terminal[0]),
                        ignored ->
                                Timer.builder("rct.export.jobs.duration")
                                        .tags("kind", kind, "outcome", terminal[0])
                                        .serviceLevelObjectives(Duration.ofSeconds(60))
                                        .register(registry));
            }
            for (Field state : Arrays.copyOf(Field.values(), 4)) {
                gauge(
                        registry,
                        "rct.export.jobs.queue",
                        kind,
                        state,
                        "state",
                        state.name().toLowerCase(java.util.Locale.ROOT));
            }
            gauge(registry, "rct.export.jobs.due", kind, Field.DUE);
            gauge(registry, "rct.export.jobs.waiting", kind, Field.WAITING);
            gauge(registry, "rct.export.jobs.oldest.nonterminal.age", kind, Field.NONTERMINAL_AGE);
            gauge(registry, "rct.export.jobs.oldest.due.age", kind, Field.DUE_AGE);
            gauge(registry, "rct.export.jobs.oldest.running.age", kind, Field.RUNNING_AGE);
            gauge(registry, "rct.export.jobs.stuck.leases", kind, Field.STUCK);
            gauge(
                    registry,
                    "rct.export.jobs.failures",
                    kind,
                    Field.FAILURES,
                    "reason",
                    "generation_failed");
            gauge(registry, "rct.export.jobs.expired.pending", kind, Field.EXPIRED);
        }
        snapshotFailures = registry.counter("rct.export.jobs.snapshot.failures");
        Gauge.builder(
                        "rct.export.jobs.snapshot.timestamp",
                        snapshotTimestamp,
                        value -> value.get() / 1000.0)
                .baseUnit("seconds")
                .register(registry);
        // This is the last failure observed by this process; persisted ERROR gauges cover restart.
        Gauge.builder(
                        "rct.export.jobs.last.failure.timestamp",
                        lastFailureTimestamp,
                        value -> value.get() / 1000.0)
                .baseUnit("seconds")
                .register(registry);
    }

    void accepted(ReportDownloadKind kind) {
        accepted.get(label(kind.code())).increment();
    }

    void retried(ReportDownloadKind kind) {
        retries.get(label(kind.code())).increment();
    }

    void terminal(ReportDownloadKind kind, String code, Instant createdAt) {
        String outcome = "success", reason = "none";
        if ("AUTHORITY_DENIED".equals(code)) {
            outcome = "denied";
            reason = "authority_denied";
        } else if ("EXPORT_TOO_LARGE".equals(code)) {
            outcome = "rejected";
            reason = "export_too_large";
        } else if (code != null) {
            outcome = "failure";
            reason = "generation_failed";
        }
        record(kind.code(), outcome, reason, createdAt);
    }

    void expired(String kind, Instant createdAt) {
        record(kind, "failure", "expired", createdAt);
    }

    private void record(String rawKind, String outcome, String reason, Instant createdAt) {
        String kind = label(rawKind);
        Instant now = Instant.now();
        terminals.get(key(kind, outcome, reason)).increment();
        Duration elapsed = Duration.between(createdAt, now);
        durations.get(key(kind, outcome)).record(elapsed.isNegative() ? Duration.ZERO : elapsed);
        if (outcome.equals("failure")) lastFailureTimestamp.set(now.toEpochMilli());
    }

    /** One aggregate over at most the admitted backlog, never a SQL query during a scrape. */
    @Scheduled(fixedDelayString = "${rutcampustrack.export-jobs.snapshot-millis:5000}")
    @SingleInstanceScheduled(
            reason =
                    "Per-instance metric snapshot: every process must refresh its own gauges; this"
                        + " read performs no domain mutation.")
    public void refresh() {
        try {
            var next = new LinkedHashMap<String, double[]>();
            for (String kind : accepted.keySet()) next.put(kind, empty());
            jdbc.query(
                    """
SELECT identity->'report'->>'kind' AS kind,state,
  count(*) FILTER (WHERE expires_at>NOW()) AS live,
  count(*) FILTER (WHERE expires_at>NOW() AND state='queued' AND (lease_until IS NULL OR lease_until<=NOW())) AS due,
  count(*) FILTER (WHERE expires_at>NOW() AND state='queued' AND lease_until>NOW()) AS waiting,
  coalesce(max(EXTRACT(EPOCH FROM NOW()-created_at)) FILTER (WHERE expires_at>NOW() AND state IN ('queued','preparing')),0) AS nonterminal_age,
  coalesce(max(EXTRACT(EPOCH FROM NOW()-coalesce(lease_until,created_at))) FILTER (WHERE expires_at>NOW() AND state='queued' AND (lease_until IS NULL OR lease_until<=NOW())),0) AS due_age,
  coalesce(max(EXTRACT(EPOCH FROM NOW()-(lease_until-interval '90 seconds'))) FILTER (WHERE expires_at>NOW() AND state='preparing'),0) AS running_age,
  count(*) FILTER (WHERE expires_at>NOW() AND state='preparing' AND lease_until<=NOW()) AS stuck,
  count(*) FILTER (WHERE expires_at>NOW() AND state='error' AND error_code='GENERATION_FAILED') AS failures,
  count(*) FILTER (WHERE expires_at<=NOW() AND state IN ('queued','preparing')) AS expired
FROM academic_export_jobs GROUP BY identity->'report'->>'kind',state
""",
                    rs -> {
                        double[] values = next.get(label(rs.getString("kind")));
                        Field state =
                                switch (rs.getString("state")) {
                                    case "queued" -> Field.QUEUED;
                                    case "preparing" -> Field.PREPARING;
                                    case "ready" -> Field.READY;
                                    default -> Field.ERROR;
                                };
                        values[state.ordinal()] += rs.getDouble("live");
                        for (var entry :
                                Map.of(
                                                Field.DUE,
                                                "due",
                                                Field.WAITING,
                                                "waiting",
                                                Field.STUCK,
                                                "stuck",
                                                Field.FAILURES,
                                                "failures",
                                                Field.EXPIRED,
                                                "expired")
                                        .entrySet())
                            values[entry.getKey().ordinal()] += rs.getDouble(entry.getValue());
                        for (var entry :
                                Map.of(
                                                Field.NONTERMINAL_AGE,
                                                "nonterminal_age",
                                                Field.DUE_AGE,
                                                "due_age",
                                                Field.RUNNING_AGE,
                                                "running_age")
                                        .entrySet())
                            values[entry.getKey().ordinal()] =
                                    Math.max(
                                            values[entry.getKey().ordinal()],
                                            rs.getDouble(entry.getValue()));
                    });
            snapshot.set(Map.copyOf(next));
            snapshotTimestamp.set(System.currentTimeMillis());
        } catch (DataAccessException unavailable) {
            snapshotFailures.increment(); // Keep the previous values and timestamp visibly stale.
        }
    }

    private void gauge(
            MeterRegistry registry, String name, String kind, Field field, String... extraTags) {
        Gauge.builder(name, this, metrics -> metrics.snapshot.get().get(kind)[field.ordinal()])
                .tag("kind", kind)
                .tags(extraTags)
                .baseUnit(field.name().endsWith("AGE") ? "seconds" : null)
                .register(registry);
    }

    private String label(String raw) {
        return accepted.containsKey(raw) ? raw : UNKNOWN;
    }

    private static double[] empty() {
        return new double[Field.values().length];
    }

    private static String key(String... parts) {
        return String.join(":", parts);
    }
}
