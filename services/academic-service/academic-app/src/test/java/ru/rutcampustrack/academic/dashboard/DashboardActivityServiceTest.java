package ru.rutcampustrack.academic.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.*;
import ru.rutcampustrack.academic.security.RequestContext;

class DashboardActivityServiceTest {
    @Test
    void moscowBoundariesAndYesterdayHaveSameElapsedTimeIncludingMondayWeeks() {
        var clock =
                Clock.fixed(
                        Instant.parse("2026-10-04T21:30:00Z"),
                        ZoneOffset.UTC); // Monday 00:30 Moscow
        var window = ActivityWindow.of("today", "week", clock);
        assertThat(window.summary().from()).isEqualTo(Instant.parse("2026-10-04T21:00:00Z"));
        assertThat(Duration.between(window.summary().from(), window.summary().to()))
                .isEqualTo(Duration.between(window.previous().from(), window.previous().to()))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(window.previous().to()).isEqualTo(Instant.parse("2026-10-03T21:30:00Z"));
        assertThat(window.series()).hasSize(12);
        assertThat(window.series().getLast().from())
                .isEqualTo(Instant.parse("2026-10-04T21:00:00Z"));
        assertThat(window.series())
                .allSatisfy(
                        range ->
                                assertThat(
                                                range.from()
                                                        .atZone(ActivityWindow.MOSCOW)
                                                        .getDayOfWeek())
                                        .isEqualTo(DayOfWeek.MONDAY));
        assertThat(ActivityWindow.of("all_time", "day", clock).summary().from()).isNull();
        assertThatThrownBy(() -> ActivityWindow.of("invalid", "day", clock))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void zeroDenominatorIsUnknownAndGeoShareDeltaUsesUnroundedFractions() {
        var range = new Range(Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        var metric =
                DashboardActivityService.metric(
                        1L,
                        3L,
                        new Coverage("RETAINED_DOMAIN_HISTORY", null),
                        State.AVAILABLE,
                        range,
                        1L,
                        6L,
                        State.AVAILABLE);
        assertThat(metric.percent()).isEqualByComparingTo("33.33");
        assertThat(metric.comparison().percentPointDelta()).isEqualByComparingTo("16.67");
        assertThat(metric.comparison().absoluteDelta()).isZero();
        var zero =
                DashboardActivityService.metric(
                        0L, 0L, metric.coverage(), State.AVAILABLE, range, 0L, 0L, State.AVAILABLE);
        assertThat(zero.value()).isZero();
        assertThat(zero.percent()).isNull();
        assertThat(zero.comparison().relativeDeltaPercent()).isNull();
        assertThat(zero.comparison().percentPointDelta()).isNull();
    }

    @Test
    void failedAttendancePreservesRealMapZeroAndExplicitNullFieldsUnderGlobalNonNull()
            throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-10-08T09:30:00Z"), ZoneOffset.UTC);
        var window = ActivityWindow.of("today", "day", clock);
        var maps = mock(DashboardActivityReadRepository.class);
        var attendance = mock(AttendanceAdminActivityClient.class);
        var context = new RequestContext();
        context.setInternalToken("synthetic-forwarded-token");
        when(attendance.read(any(), any()))
                .thenThrow(
                        new AttendanceAdminActivityClient.ActivityUnavailableException(
                                new RuntimeException()));
        var zero =
                new DashboardActivityReadRepository.MapCount(
                        State.AVAILABLE,
                        0L,
                        new Coverage("RAW_RETENTION", window.previous().from()));
        var unknown =
                new DashboardActivityReadRepository.MapCount(
                        State.INSUFFICIENT_HISTORY,
                        null,
                        new Coverage("BOUNDED_CAPTURE", window.observedAt()));
        List<DashboardActivityReadRepository.MapCount> series =
                window.series().stream().map(r -> unknown).toList();
        when(maps.read(any()))
                .thenReturn(new DashboardActivityReadRepository.MapSnapshot(zero, zero, series));
        var result =
                new DashboardActivityService(maps, attendance, context, clock).read("today", "day");
        assertThat(result.summary().metrics().get("positiveMarks").state())
                .isEqualTo(State.UNAVAILABLE);
        assertThat(result.summary().metrics().get("positiveMarks").value()).isNull();
        assertThat(result.summary().metrics().get("mapOpens").value()).isZero();
        assertThat(result.series().getFirst().metrics().get("mapOpens").state())
                .isEqualTo(State.INSUFFICIENT_HISTORY);
        var json =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                        .valueToTree(result);
        assertThat(json.at("/summary/metrics/positiveMarks/value").isNull()).isTrue();
        assertThat(json.at("/summary/metrics/positiveMarks/percent").isNull()).isTrue();
        assertThat(json.at("/series/0/metrics/mapOpens/comparison").isNull()).isTrue();
        assertThat(json.at("/summary/metrics/positiveMarks/coverage/from").isNull()).isTrue();
        assertThat(json.path("summary").path("metrics").size()).isEqualTo(5);
    }
}
