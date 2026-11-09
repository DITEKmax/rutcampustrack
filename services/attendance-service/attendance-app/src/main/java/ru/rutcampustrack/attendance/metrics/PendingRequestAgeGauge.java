package ru.rutcampustrack.attendance.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.shared.observability.MetricNames;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only OPEN backlog diagnostics. Scrapes use a cached timestamp and never query Mongo. */
@Component
public class PendingRequestAgeGauge {
    private static final Duration MAX_SAMPLE_AGE = Duration.ofSeconds(120);
    private final MongoTemplate mongo;
    private final Clock clock;
    private final List<Partition> partitions =
            List.of(
                    new Partition(
                            "EXCUSE",
                            "NOT_APPLICABLE",
                            "excuse_tickets",
                            List.of("SUBMITTED", "submitted")),
                    new Partition(
                            "LATE_CHECKIN",
                            "MANUAL",
                            "late_checkin_requests",
                            List.of("PENDING", "pending")),
                    new Partition(
                            "LATE_CHECKIN",
                            "AUTO_GEO_FAILURE",
                            "late_checkin_requests",
                            List.of("PENDING", "pending")));

    public PendingRequestAgeGauge(MongoTemplate mongo, MeterRegistry registry, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
        for (Partition partition : partitions) {
            Gauge.builder(MetricNames.PENDING_REQUEST_OLDEST_AGE, partition, this::age)
                    .tags("kind", partition.kind, "origin", partition.origin)
                    .baseUnit("seconds")
                    .description(
                            "Age of oldest OPEN request; empty backlog returns zero, NaN is"
                                    + " unknown")
                    .register(registry);
        }
    }

    // Every instance samples its own cached gauges. A distributed lock would leave other instances
    // permanently unknown. Fixed cardinality and indexed limit(1) keep the read budget bounded.
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void refresh() {
        for (Partition partition : partitions) {
            try {
                Document filter = new Document("status", new Document("$in", partition.statuses));
                if (partition.kind.equals("LATE_CHECKIN")) {
                    // The canonical request projection treats historical missing/null origin as
                    // MANUAL.
                    filter.append(
                            "origin",
                            new Document(
                                    "$in",
                                    partition.origin.equals("MANUAL")
                                            ? Arrays.asList("MANUAL", "manual", null)
                                            : List.of("AUTO_GEO_FAILURE", "auto_geo_failure")));
                }
                Document oldest =
                        mongo.getCollection(partition.collection)
                                .withTimeout(3, TimeUnit.SECONDS)
                                .find(filter)
                                .sort(new Document("created_at", 1))
                                .projection(new Document("created_at", 1).append("_id", 0))
                                .limit(1)
                                .first();
                Object createdAt = oldest == null ? null : oldest.get("created_at");
                boolean known = oldest == null || createdAt instanceof Date;
                partition.sample.set(
                        new Sample(
                                known,
                                createdAt instanceof Date date ? date.toInstant() : null,
                                clock.instant()));
            } catch (RuntimeException unavailable) {
                // No exception text, raw origin values, identities, or request data in logs/labels.
                partition.sample.set(new Sample(false, null, clock.instant()));
            }
        }
    }

    private double age(Partition partition) {
        Sample sample = partition.sample.get();
        Instant now = clock.instant();
        if (!sample.known
                || sample.sampledAt == null
                || now.isBefore(sample.sampledAt)
                || Duration.between(sample.sampledAt, now).compareTo(MAX_SAMPLE_AGE) > 0)
            return Double.NaN;
        if (sample.oldest == null) return 0;
        Duration elapsed = Duration.between(sample.oldest, now);
        return Math.max(0, elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000.0);
    }

    private record Sample(boolean known, Instant oldest, Instant sampledAt) {}

    private static final class Partition {
        final String kind;
        final String origin;
        final String collection;
        final List<String> statuses;
        final AtomicReference<Sample> sample = new AtomicReference<>(new Sample(false, null, null));

        Partition(String kind, String origin, String collection, List<String> statuses) {
            this.kind = kind;
            this.origin = origin;
            this.collection = collection;
            this.statuses = statuses;
        }
    }
}
