package ru.rutcampustrack.schedule.week;

import ru.rutcampustrack.schedule.contract.dto.week.AcademicWeekPolicyResponse;
import ru.rutcampustrack.schedule.contract.dto.week.AcademicWeekPolicyResponse.WeekTypeOption;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;

/** Single source for ISO parity and its public names, independent of semester dates. */
public final class AcademicWeekPolicy {
    private static final WeekTypeOption ALL =
            new WeekTypeOption(WeekType.ALL, null, "Каждую неделю");
    private static final WeekTypeOption ISO_EVEN = new WeekTypeOption(WeekType.ODD, 1, "1");
    private static final WeekTypeOption ISO_ODD = new WeekTypeOption(WeekType.EVEN, 2, "2");
    private static final List<WeekTypeOption> CATALOG = List.of(ALL, ISO_EVEN, ISO_ODD);

    private AcademicWeekPolicy() {}

    public static WeekType weekType(LocalDate date) {
        return option(isoWeek(date)).weekType();
    }

    public static AcademicWeekPolicyResponse forDate(LocalDate date) {
        int isoWeek = isoWeek(date);
        WeekTypeOption selected = option(isoWeek);
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new AcademicWeekPolicyResponse(
                monday,
                monday.plusDays(6),
                isoWeek,
                selected.weekType(),
                selected.parityNumber(),
                selected.label(),
                CATALOG);
    }

    private static int isoWeek(LocalDate date) {
        return date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    private static WeekTypeOption option(int isoWeek) {
        return isoWeek % 2 == 0 ? ISO_EVEN : ISO_ODD;
    }
}
