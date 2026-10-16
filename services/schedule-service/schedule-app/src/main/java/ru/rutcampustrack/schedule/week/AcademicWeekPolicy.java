package ru.rutcampustrack.schedule.week;

import ru.rutcampustrack.schedule.contract.dto.week.AcademicWeekPolicyResponse;
import ru.rutcampustrack.schedule.contract.dto.week.AcademicWeekPolicyResponse.WeekTypeOption;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.shared.web.time.AcademicWeekCalendar;

import java.time.LocalDate;
import java.util.List;

/** Single source for ISO parity and its public names, independent of semester dates. */
public final class AcademicWeekPolicy {
    private static final WeekTypeOption ALL =
            new WeekTypeOption(WeekType.ALL, null, "Каждую неделю");
    private static final WeekTypeOption ISO_EVEN =
            new WeekTypeOption(
                    WeekType.ODD,
                    AcademicWeekCalendar.Parity.ISO_EVEN.number(),
                    AcademicWeekCalendar.Parity.ISO_EVEN.label());
    private static final WeekTypeOption ISO_ODD =
            new WeekTypeOption(
                    WeekType.EVEN,
                    AcademicWeekCalendar.Parity.ISO_ODD.number(),
                    AcademicWeekCalendar.Parity.ISO_ODD.label());
    private static final List<WeekTypeOption> CATALOG = List.of(ALL, ISO_EVEN, ISO_ODD);

    private AcademicWeekPolicy() {}

    public static WeekType weekType(LocalDate date) {
        return option(date).weekType();
    }

    public static AcademicWeekPolicyResponse forDate(LocalDate date) {
        int isoWeek = AcademicWeekCalendar.isoWeek(date);
        WeekTypeOption selected = option(date);
        LocalDate monday = AcademicWeekCalendar.monday(date);
        return new AcademicWeekPolicyResponse(
                monday,
                monday.plusDays(6),
                isoWeek,
                selected.weekType(),
                selected.parityNumber(),
                selected.label(),
                CATALOG);
    }

    private static WeekTypeOption option(LocalDate date) {
        return switch (AcademicWeekCalendar.parity(date)) {
            case ISO_EVEN -> ISO_EVEN;
            case ISO_ODD -> ISO_ODD;
        };
    }
}
