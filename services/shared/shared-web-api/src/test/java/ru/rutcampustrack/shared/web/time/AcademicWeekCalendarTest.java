package ru.rutcampustrack.shared.web.time;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class AcademicWeekCalendarTest {
    @Test
    void partialFirstWeekAndSundayShareSemesterOrdinalWithoutRollingFromFirstDay() {
        LocalDate start = LocalDate.parse("2026-09-02");
        assertThat(AcademicWeekCalendar.monday(start)).isEqualTo(LocalDate.parse("2026-08-31"));
        assertThat(AcademicWeekCalendar.weekOfSemester(start, start)).isEqualTo(1);
        assertThat(AcademicWeekCalendar.weekOfSemester(start, LocalDate.parse("2026-09-06")))
                .isEqualTo(1);
        assertThat(AcademicWeekCalendar.weekOfSemester(start, LocalDate.parse("2026-09-07")))
                .isEqualTo(2);
    }

    @Test
    void iso53ToOneKeepsPublicParityWhileSemesterOrdinalAdvances() {
        LocalDate first = LocalDate.parse("2020-12-30");
        LocalDate next = LocalDate.parse("2021-01-04");
        assertThat(AcademicWeekCalendar.isoWeek(first)).isEqualTo(53);
        assertThat(AcademicWeekCalendar.isoWeek(next)).isEqualTo(1);
        assertThat(AcademicWeekCalendar.parity(first))
                .isEqualTo(AcademicWeekCalendar.Parity.ISO_ODD);
        assertThat(AcademicWeekCalendar.parity(next))
                .isEqualTo(AcademicWeekCalendar.Parity.ISO_ODD);
        assertThat(AcademicWeekCalendar.parity(first).number()).isEqualTo(2);
        assertThat(AcademicWeekCalendar.parity(first).label()).isEqualTo("2");
        assertThat(AcademicWeekCalendar.weekOfSemester(first, next)).isEqualTo(2);
        assertThat(AcademicWeekCalendar.parity(LocalDate.parse("2021-01-11")).number())
                .isEqualTo(1);
    }

    @Test
    void leapDayAndCalendarYearRolloverUseMondayBuckets() {
        LocalDate start = LocalDate.parse("2024-02-29");
        assertThat(AcademicWeekCalendar.weekOfSemester(start, LocalDate.parse("2024-03-03")))
                .isEqualTo(1);
        assertThat(AcademicWeekCalendar.weekOfSemester(start, LocalDate.parse("2024-03-04")))
                .isEqualTo(2);
        assertThat(AcademicWeekCalendar.monday(LocalDate.parse("2019-12-30")))
                .isEqualTo(LocalDate.parse("2019-12-30"));
        assertThat(AcademicWeekCalendar.isoWeek(LocalDate.parse("2019-12-30"))).isEqualTo(1);
    }
}
