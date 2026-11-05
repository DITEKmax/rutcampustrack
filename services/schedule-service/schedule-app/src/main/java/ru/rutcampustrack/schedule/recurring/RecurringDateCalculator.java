package ru.rutcampustrack.schedule.recurring;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.week.AcademicWeekPolicy;

/** Physical semester dates. Teacher validity is resolved separately for each dated binding. */
public final class RecurringDateCalculator {

    private RecurringDateCalculator() {}

    public static List<LocalDate> compute(
            CreateScheduleItemRequest request,
            RecurringAssignmentAuthority authority,
            LocalDate semesterStart,
            LocalDate semesterEnd,
            LocalDate fenceCap) {
        LocalDate from = semesterStart;
        LocalDate exclusiveEnd = semesterEnd.plusDays(1);
        if (!from.isBefore(exclusiveEnd)) return List.of();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(exclusiveEnd); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() != request.dayOfWeek()) continue;
            if (request.weekType() == WeekType.ALL) {
                dates.add(date);
                continue;
            }
            if (AcademicWeekPolicy.weekType(date) == request.weekType()) dates.add(date);
        }
        return List.copyOf(dates);
    }
}
