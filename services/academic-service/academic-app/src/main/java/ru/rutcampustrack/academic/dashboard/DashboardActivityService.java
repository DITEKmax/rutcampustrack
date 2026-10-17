package ru.rutcampustrack.academic.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.*;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.attendance.grpc.AdminActivityCounts;
import ru.rutcampustrack.attendance.grpc.AdminActivitySnapshot;

@Service
public class DashboardActivityService {
    private static final Coverage RETAINED = new Coverage("RETAINED_DOMAIN_HISTORY", null);
    private final DashboardActivityReadRepository maps;
    private final AttendanceAdminActivityClient attendance;
    private final RequestContext context;
    private final Clock clock;

    public DashboardActivityService(
            DashboardActivityReadRepository maps,
            AttendanceAdminActivityClient attendance,
            RequestContext context,
            Clock clock) {
        this.maps = maps;
        this.attendance = attendance;
        this.context = context;
        this.clock = clock;
    }

    public DashboardActivityResponse read(String period, String bucket) {
        ActivityWindow window = ActivityWindow.of(period, bucket, clock);
        AdminActivitySnapshot activity = null;
        try {
            activity = attendance.read(window, context.getInternalToken());
        } catch (AttendanceAdminActivityClient.ActivityUnavailableException unavailable) {
            /* Explicit partial response below. */
        }
        DashboardActivityReadRepository.MapSnapshot map = null;
        try {
            map = maps.read(window);
        } catch (DataAccessException unavailable) {
            /* A failed query must never become zero. */
        }
        Section summary =
                section(
                        window.summary(),
                        activity == null ? null : activity.getSummary(),
                        map == null ? null : map.summary(),
                        window.previous(),
                        activity == null ? null : activity.getPrevious(),
                        map == null ? null : map.previous());
        List<Section> series = new ArrayList<>();
        for (int i = 0; i < window.series().size(); i++) {
            series.add(
                    section(
                            window.series().get(i),
                            activity == null ? null : activity.getSeries(i).getCounts(),
                            map == null ? null : map.series().get(i),
                            null,
                            null,
                            null));
        }
        return new DashboardActivityResponse(
                window.observedAt(),
                "Europe/Moscow",
                period,
                bucket,
                "RETAINED_DOMAIN_HISTORY",
                "EVENTUAL_ATTENDANCE_PROJECTION",
                summary,
                List.copyOf(series));
    }

    private Section section(
            Range range,
            AdminActivityCounts now,
            DashboardActivityReadRepository.MapCount map,
            Range previousRange,
            AdminActivityCounts previous,
            DashboardActivityReadRepository.MapCount previousMap) {
        Map<String, Metric> result = new LinkedHashMap<>();
        result.put(
                "positiveMarks",
                metric(
                        now == null ? null : now.getPositiveMarks(),
                        null,
                        RETAINED,
                        now == null ? State.UNAVAILABLE : State.AVAILABLE,
                        previousRange,
                        previous == null ? null : previous.getPositiveMarks(),
                        null,
                        previous == null ? State.UNAVAILABLE : State.AVAILABLE));
        result.put(
                "geoMarks",
                metric(
                        now == null ? null : now.getGeoMarks(),
                        now == null ? null : now.getPositiveMarks(),
                        RETAINED,
                        now == null ? State.UNAVAILABLE : State.AVAILABLE,
                        previousRange,
                        previous == null ? null : previous.getGeoMarks(),
                        previous == null ? null : previous.getPositiveMarks(),
                        previous == null ? State.UNAVAILABLE : State.AVAILABLE));
        result.put(
                "mapOpens",
                metric(
                        map == null ? null : map.count(),
                        null,
                        map == null ? RETAINED : map.coverage(),
                        map == null ? State.UNAVAILABLE : map.state(),
                        previousRange,
                        previousMap == null ? null : previousMap.count(),
                        null,
                        previousMap == null ? State.UNAVAILABLE : previousMap.state()));
        result.put(
                "requests",
                metric(
                        now == null ? null : now.getRequests(),
                        null,
                        RETAINED,
                        now == null ? State.UNAVAILABLE : State.AVAILABLE,
                        previousRange,
                        previous == null ? null : previous.getRequests(),
                        null,
                        previous == null ? State.UNAVAILABLE : State.AVAILABLE));
        result.put(
                "homeworkPublications",
                metric(
                        now == null ? null : now.getHomeworkPublications(),
                        null,
                        RETAINED,
                        now == null ? State.UNAVAILABLE : State.AVAILABLE,
                        previousRange,
                        previous == null ? null : previous.getHomeworkPublications(),
                        null,
                        previous == null ? State.UNAVAILABLE : State.AVAILABLE));
        return new Section(range, Collections.unmodifiableMap(result));
    }

    static Metric metric(
            Long value,
            Long denominator,
            Coverage coverage,
            State state,
            Range previousRange,
            Long previous,
            Long previousDenominator,
            State previousState) {
        BigDecimal percent = denominator == null ? null : percent(value, denominator);
        Comparison comparison = null;
        if (previousRange != null) {
            State comparisonState = state == State.AVAILABLE ? previousState : state;
            boolean available = comparisonState == State.AVAILABLE;
            BigDecimal previousPercent =
                    available && previousDenominator != null
                            ? percent(previous, previousDenominator)
                            : null;
            comparison =
                    new Comparison(
                            comparisonState,
                            previousRange,
                            available ? previous : null,
                            available ? value - previous : null,
                            available && previous != 0
                                    ? BigDecimal.valueOf(value - previous)
                                            .multiply(BigDecimal.valueOf(100))
                                            .divide(
                                                    BigDecimal.valueOf(previous),
                                                    2,
                                                    RoundingMode.HALF_UP)
                                    : null,
                            previousPercent,
                            available && percent != null && previousPercent != null
                                    ? pointDelta(value, denominator, previous, previousDenominator)
                                    : null);
        }
        return new Metric(
                state,
                value,
                denominator == null ? null : value,
                denominator,
                percent,
                coverage,
                state == State.AVAILABLE
                        ? null
                        : state == State.INSUFFICIENT_HISTORY
                                ? "INSUFFICIENT_HISTORY"
                                : "DEPENDENCY_UNAVAILABLE",
                comparison);
    }

    private static BigDecimal percent(Long numerator, long denominator) {
        return numerator == null || denominator == 0
                ? null
                : BigDecimal.valueOf(numerator)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pointDelta(
            long value, long denominator, long previous, long previousDenominator) {
        BigDecimal currentDenominator = BigDecimal.valueOf(denominator);
        BigDecimal oldDenominator = BigDecimal.valueOf(previousDenominator);
        return BigDecimal.valueOf(value)
                .multiply(oldDenominator)
                .subtract(BigDecimal.valueOf(previous).multiply(currentDenominator))
                .multiply(BigDecimal.valueOf(100))
                .divide(currentDenominator.multiply(oldDenominator), 2, RoundingMode.HALF_UP);
    }
}
