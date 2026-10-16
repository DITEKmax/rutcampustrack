package ru.rutcampustrack.shared.web.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

/** Shared educational week geometry; public parity is independent of semester ordinal. */
public final class AcademicWeekCalendar {
    private AcademicWeekCalendar() {}

    public enum Parity {
        ISO_EVEN(1, "1"),
        ISO_ODD(2, "2");

        private final int number;
        private final String label;

        Parity(int number, String label) {
            this.number = number;
            this.label = label;
        }

        public int number() {
            return number;
        }

        public String label() {
            return label;
        }
    }

    public static int isoWeek(LocalDate date) {
        return date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    public static Parity parity(LocalDate date) {
        return isoWeek(date) % 2 == 0 ? Parity.ISO_EVEN : Parity.ISO_ODD;
    }

    public static LocalDate monday(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** The Monday-containing first partial semester week is week one. */
    public static int weekOfSemester(LocalDate semesterStart, LocalDate date) {
        long days = ChronoUnit.DAYS.between(monday(semesterStart), monday(date));
        return (int) (days / 7L) + 1;
    }
}
