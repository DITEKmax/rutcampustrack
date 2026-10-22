package ru.rutcampustrack.academic.dashboard;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.Range;
import ru.rutcampustrack.attendance.grpc.*;

public record ActivityWindow(
        Instant observedAt,
        String period,
        String bucket,
        Range summary,
        Range previous,
        List<Range> series) {
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    public static ActivityWindow of(String period, String bucket, Clock clock) {
        if (!("today".equals(period) || "all_time".equals(period))
                || !("day".equals(bucket) || "week".equals(bucket))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported activity period or bucket");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime local = now.atZone(MOSCOW);
        Instant midnight = local.toLocalDate().atStartOfDay(MOSCOW).toInstant();
        boolean weekly = "week".equals(bucket);
        ZonedDateTime first = local.toLocalDate().atStartOfDay(MOSCOW);
        first =
                weekly
                        ? first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .minusWeeks(11)
                        : first.minusDays(13);
        List<Range> series = new ArrayList<>();
        for (int i = 0; i < (weekly ? 12 : 14); i++) {
            ZonedDateTime next = weekly ? first.plusWeeks(1) : first.plusDays(1);
            series.add(
                    new Range(
                            first.toInstant(),
                            next.toInstant().isAfter(now) ? now : next.toInstant()));
            first = next;
        }
        boolean today = "today".equals(period);
        return new ActivityWindow(
                now,
                period,
                bucket,
                new Range(today ? midnight : null, now),
                today
                        ? new Range(
                                midnight.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS))
                        : null,
                List.copyOf(series));
    }

    public AdminActivityQuery query() {
        return AdminActivityQuery.newBuilder()
                .setObservedAtEpochMs(observedAt.toEpochMilli())
                .setPeriod(
                        "today".equals(period)
                                ? AdminActivityPeriod.ADMIN_ACTIVITY_TODAY
                                : AdminActivityPeriod.ADMIN_ACTIVITY_ALL_TIME)
                .setBucket(
                        "day".equals(bucket)
                                ? AdminActivityBucket.ADMIN_ACTIVITY_DAY
                                : AdminActivityBucket.ADMIN_ACTIVITY_WEEK)
                .build();
    }
}
