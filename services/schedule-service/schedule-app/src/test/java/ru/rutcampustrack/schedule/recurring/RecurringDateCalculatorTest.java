package ru.rutcampustrack.schedule.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.lesson.LessonGenerationService;

class RecurringDateCalculatorTest {

    @Test
    void midpointCreationKeepsEarlierSemesterDatesAndStopsAtExclusiveCap() {
        CreateScheduleItemRequest request = request(WeekType.ALL);
        RecurringAssignmentAuthority authority =
                authority(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 1));

        List<LocalDate> dates =
                RecurringDateCalculator.compute(
                        request,
                        authority,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 15),
                        LocalDate.of(2026, 10, 1));

        assertThat(dates).contains(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 28));
        assertThat(dates).doesNotContain(LocalDate.of(2026, 10, 5));
        assertThat(dates).isSorted();
        assertThat(dates.getLast()).isBefore(LocalDate.of(2026, 10, 1));
    }

    @Test
    void semesterEndIsInclusiveWhenItFallsOnTheRequestedWeekday() {
        CreateScheduleItemRequest request = request(WeekType.ALL);
        RecurringAssignmentAuthority authority =
                authority(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29));

        List<LocalDate> dates =
                RecurringDateCalculator.compute(
                        request,
                        authority,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2026, 9, 8));

        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 7));
    }

    @Test
    void parityUsesIsoWeekWithoutSemesterRelativeRewrite() {
        CreateScheduleItemRequest request = request(WeekType.ODD);
        RecurringAssignmentAuthority authority =
                authority(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

        List<LocalDate> dates =
                RecurringDateCalculator.compute(
                        request,
                        authority,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 10, 1));

        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 28));
    }

    @Test
    void bothGeneratorsPreserveIsoYearBoundaryIncludingWeek53() {
        LocalDate start = LocalDate.of(2020, 12, 28);
        LocalDate end = LocalDate.of(2021, 1, 18);
        LocalDate cap = end.plusDays(1);
        LessonGenerationService legacy = new LessonGenerationService(null, Clock.systemUTC(), null);
        for (WeekType type : List.of(WeekType.ODD, WeekType.EVEN)) {
            List<LocalDate> expected =
                    type == WeekType.ODD
                            ? List.of(LocalDate.of(2021, 1, 11))
                            : List.of(start, LocalDate.of(2021, 1, 4), end);
            assertThat(
                            RecurringDateCalculator.compute(
                                    request(type), authority(start, cap), start, end, cap))
                    .containsExactlyElementsOf(expected);
            assertThat(legacy.computeLessonDates(start, end, WeekType.ODD, (short) 1, type))
                    .containsExactlyElementsOf(expected);
            assertThat(legacy.computeLessonDates(start, end, WeekType.EVEN, (short) 1, type))
                    .containsExactlyElementsOf(expected);
        }
    }

    private static CreateScheduleItemRequest request(WeekType weekType) {
        return new CreateScheduleItemRequest(
                501L,
                10L,
                20L,
                30L,
                (short) 1,
                (short) 2,
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                weekType,
                "A-101");
    }

    private static RecurringAssignmentAuthority authority(LocalDate from, LocalDate until) {
        return new RecurringAssignmentAuthority(501L, 700L, 20L, 10L, 30L, "lecture", from, until);
    }
}
