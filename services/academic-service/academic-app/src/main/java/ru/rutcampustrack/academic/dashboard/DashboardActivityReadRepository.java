package ru.rutcampustrack.academic.dashboard;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.Coverage;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.Range;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.State;

/** Bounded SQL counts, with a stable snapshot across raw retention and hourly history. */
@Repository
public class DashboardActivityReadRepository {
    private final JdbcTemplate jdbc;

    public DashboardActivityReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MapCount(State state, Long count, Coverage coverage) {}

    public record MapSnapshot(MapCount summary, MapCount previous, List<MapCount> series) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MapSnapshot read(ActivityWindow window) {
        Instant capturedFrom =
                jdbc.queryForObject(
                                "SELECT captured_from FROM campus_map_activity_coverage WHERE singleton",
                                OffsetDateTime.class)
                        .toInstant();
        Instant rawFrom =
                jdbc.queryForObject(
                                "SELECT CURRENT_TIMESTAMP - INTERVAL '48 hours'",
                                OffsetDateTime.class)
                        .toInstant();
        MapCount summary =
                window.summary().from() == null
                        ? new MapCount(
                                State.AVAILABLE,
                                total(window.observedAt()),
                                new Coverage("RETAINED_DOMAIN_HISTORY", null))
                        : count(window.summary(), capturedFrom, rawFrom);
        MapCount previous =
                window.previous() == null ? null : count(window.previous(), capturedFrom, rawFrom);
        List<MapCount> series = new ArrayList<>();
        for (Range range : window.series()) series.add(count(range, capturedFrom, rawFrom));
        return new MapSnapshot(summary, previous, List.copyOf(series));
    }

    private MapCount count(Range range, Instant capturedFrom, Instant rawFrom) {
        if (!range.from().isBefore(rawFrom)) {
            return new MapCount(
                    State.AVAILABLE,
                    rawCount(range.from(), range.to()),
                    new Coverage("RAW_RETENTION", rawFrom));
        }
        Coverage coverage = new Coverage("BOUNDED_CAPTURE", capturedFrom);
        if (range.from().isBefore(capturedFrom))
            return new MapCount(State.INSUFFICIENT_HISTORY, null, coverage);
        // Completed hours are durable. The current partial hour is read at its
        // exact cutoff from retained accepted intents, never rounded upward.
        Instant wholeHourEnd = range.to().truncatedTo(ChronoUnit.HOURS);
        Long wholeHours =
                jdbc.queryForObject(
                        """
                SELECT COALESCE(SUM(open_count), 0) FROM campus_map_floor_hourly_activity
                WHERE hour_start >= ? AND hour_start < ?
                """,
                        Long.class,
                        utc(range.from()),
                        utc(wholeHourEnd));
        long count = wholeHours == null ? 0 : wholeHours;
        if (wholeHourEnd.isBefore(range.to())) count += rawCount(wholeHourEnd, range.to());
        return new MapCount(State.AVAILABLE, count, coverage);
    }

    private long rawCount(Instant from, Instant to) {
        Long value =
                jdbc.queryForObject(
                        """
                SELECT COUNT(*) FROM campus_map_open_intent i
                JOIN campus_map_floor_demand_dedupe d ON d.owner_hmac=i.owner_hmac AND d.intent_id=i.intent_id
                WHERE i.accepted_at >= ? AND i.accepted_at < ?
                """,
                        Long.class,
                        utc(from),
                        utc(to));
        return value == null ? 0 : value;
    }

    private long total(Instant observedAt) {
        // Preserve all retained UTC lifetime days. Only the ongoing UTC day
        // needs raw timestamps to honor the same observation cutoff as Mongo.
        LocalDate utcDay = observedAt.atOffset(ZoneOffset.UTC).toLocalDate();
        Long value =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(open_count), 0) FROM campus_map_floor_daily_demand WHERE utc_day < ?",
                        Long.class,
                        utcDay);
        return (value == null ? 0 : value)
                + rawCount(utcDay.atStartOfDay(ZoneOffset.UTC).toInstant(), observedAt);
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
