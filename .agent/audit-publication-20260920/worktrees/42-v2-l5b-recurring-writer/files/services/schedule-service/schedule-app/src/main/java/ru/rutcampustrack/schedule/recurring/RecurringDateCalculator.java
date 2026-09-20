package ru.rutcampustrack.schedule.recurring;

import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/** Pure ISO-parity range calculation for the canonical recurring writer. */
public final class RecurringDateCalculator {

    private RecurringDateCalculator() {}

    public static List<LocalDate> compute(CreateScheduleItemRequest request,
                                          RecurringAssignmentAuthority authority,
                                          LocalDate semesterStart,
                                          LocalDate semesterEnd,
                                          LocalDate fenceCap) {
        LocalDate from = semesterStart.isAfter(authority.validFrom())
                ? semesterStart : authority.validFrom();
        LocalDate semesterExclusiveEnd = semesterEnd.plusDays(1);
        LocalDate exclusiveEnd = semesterExclusiveEnd.isBefore(fenceCap)
                ? semesterExclusiveEnd : fenceCap;
        if (!from.isBefore(exclusiveEnd)) return List.of();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(exclusiveEnd); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() != request.dayOfWeek()) continue;
            if (request.weekType() == WeekType.ALL) {
                dates.add(date);
                continue;
            }
            int isoWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            WeekType parity = isoWeek % 2 == 0 ? WeekType.ODD : WeekType.EVEN;
            if (parity == request.weekType()) dates.add(date);
        }
        return List.copyOf(dates);
    }
}
