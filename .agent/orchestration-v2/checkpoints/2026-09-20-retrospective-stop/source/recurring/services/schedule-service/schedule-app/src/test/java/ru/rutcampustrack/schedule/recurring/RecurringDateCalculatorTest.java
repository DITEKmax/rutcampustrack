package ru.rutcampustrack.schedule.recurring;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringDateCalculatorTest {

    @Test
    void midpointCreationKeepsEarlierSemesterDatesAndStopsAtExclusiveCap() {
        CreateScheduleItemRequest request = request(WeekType.ALL);
        RecurringAssignmentAuthority authority = authority(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 1));

        List<LocalDate> dates = RecurringDateCalculator.compute(
                request, authority,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 1));

        assertThat(dates).contains(LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 28));
        assertThat(dates).doesNotContain(LocalDate.of(2026, 10, 5));
        assertThat(dates).isSorted();
        assertThat(dates.getLast()).isBefore(LocalDate.of(2026, 10, 1));
    }

    @Test
    void semesterEndIsInclusiveWhenItFallsOnTheRequestedWeekday() {
        CreateScheduleItemRequest request = request(WeekType.ALL);
        RecurringAssignmentAuthority authority = authority(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29));

        List<LocalDate> dates = RecurringDateCalculator.compute(
                request, authority,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 8));

        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 7));
    }

    @Test
    void parityUsesIsoWeekWithoutSemesterRelativeRewrite() {
        CreateScheduleItemRequest request = request(WeekType.ODD);
        RecurringAssignmentAuthority authority = authority(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

        List<LocalDate> dates = RecurringDateCalculator.compute(
                request, authority,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 1));

        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 28));
    }

    private static CreateScheduleItemRequest request(WeekType weekType) {
        return new CreateScheduleItemRequest(501L, 10L, 20L, 30L,
                (short) 1, (short) 2, LocalTime.of(10, 0),
                LocalTime.of(11, 30), weekType, "A-101");
    }

    private static RecurringAssignmentAuthority authority(LocalDate from, LocalDate until) {
        return new RecurringAssignmentAuthority(501L, 700L, 20L, 10L, 30L,
                "lecture", from, until);
    }
}
