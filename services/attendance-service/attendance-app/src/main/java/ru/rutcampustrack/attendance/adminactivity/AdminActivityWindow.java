package ru.rutcampustrack.attendance.adminactivity;

import ru.rutcampustrack.attendance.grpc.AdminActivityBucket;
import ru.rutcampustrack.attendance.grpc.AdminActivityPeriod;
import ru.rutcampustrack.attendance.grpc.AdminActivityQuery;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Fixed Moscow calendar windows; the caller cannot request arbitrary history. */
public record AdminActivityWindow(Instant observedAt, Range summary, Range previous,
                                  List<Range> series, boolean weekly) {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    public record Range(Instant from, Instant to) { }

    public static AdminActivityWindow of(AdminActivityQuery query, Clock clock) {
        if (query.getPeriod() != AdminActivityPeriod.ADMIN_ACTIVITY_TODAY
                && query.getPeriod() != AdminActivityPeriod.ADMIN_ACTIVITY_ALL_TIME
                || query.getBucket() != AdminActivityBucket.ADMIN_ACTIVITY_DAY
                && query.getBucket() != AdminActivityBucket.ADMIN_ACTIVITY_WEEK) {
            throw new IllegalArgumentException("Unsupported activity period or bucket");
        }
        Instant at = Instant.ofEpochMilli(query.getObservedAtEpochMs());
        Instant now = clock.instant();
        if (at.isBefore(now.minusSeconds(60)) || at.isAfter(now.plusSeconds(5))) {
            throw new IllegalArgumentException("Activity observation must be current");
        }
        ZonedDateTime local = at.atZone(MOSCOW);
        Instant midnight = local.toLocalDate().atStartOfDay(MOSCOW).toInstant();
        boolean today = query.getPeriod() == AdminActivityPeriod.ADMIN_ACTIVITY_TODAY;
        boolean weekly = query.getBucket() == AdminActivityBucket.ADMIN_ACTIVITY_WEEK;
        ZonedDateTime first = local.toLocalDate().atStartOfDay(MOSCOW);
        if (weekly) first = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11);
        else first = first.minusDays(13);
        List<Range> series = new ArrayList<>();
        for (int i = 0; i < (weekly ? 12 : 14); i++) {
            ZonedDateTime next = weekly ? first.plusWeeks(1) : first.plusDays(1);
            series.add(new Range(first.toInstant(), next.toInstant().isAfter(at) ? at : next.toInstant()));
            first = next;
        }
        return new AdminActivityWindow(at, new Range(today ? midnight : null, at),
                today ? new Range(midnight.minus(1, ChronoUnit.DAYS), at.minus(1, ChronoUnit.DAYS)) : null,
                List.copyOf(series), weekly);
    }
}
