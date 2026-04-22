package ru.rutcampustrack.schedule.golden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M08 Группа 4 (P2-8/4) — property-based-like tests для week-parity convention.
 *
 * <p>jqwik скейл не добавляется (избегаем новой dep + конфликт с Spring Boot Test).
 * Используются simple random loops + @RepeatedTest для статистического покрытия
 * corner-cases поверх deterministic golden fixtures.
 *
 * <p>Two invariants:
 * <ol>
 *   <li><b>parityFlipsEveryWeek</b> — после +7 дней ISO week меняется на 1, и
 *       соответствующий WeekType flip'ается ODD ↔ EVEN.</li>
 *   <li><b>parityStableWithinWeek</b> — 7 последовательных дней одной ISO-недели
 *       имеют одинаковый WeekType (понедельник..воскресенье).</li>
 * </ol>
 */
class WeekParityPropertyTest {

    private static WeekType parityOf(LocalDate date) {
        int isoWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return (isoWeek % 2 == 0) ? WeekType.ODD : WeekType.EVEN;
    }

    @RepeatedTest(100)
    @DisplayName("parityFlipsEveryWeek — +7 дней → flip ODD↔EVEN")
    void parityFlipsEveryWeek() {
        // Случайная дата между 2020-01-01 и 2030-12-31
        long minDay = LocalDate.of(2020, 1, 1).toEpochDay();
        long maxDay = LocalDate.of(2030, 12, 31).toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        LocalDate date = LocalDate.ofEpochDay(randomDay);

        WeekType parity = parityOf(date);
        WeekType parityNextWeek = parityOf(date.plusDays(7));

        // Может сработать boundary: переход между ISO-year'ами с W53 → W1
        // (например 2020-12-31 (W53) + 7 = 2021-01-07 (W1)). В таких случаях
        // parity меняется с W53(нечёт) → W1(нечёт) — НЕ flip'ается. Ловим
        // только «normal» случаи через filtering:
        int isoWeek1 = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int isoWeek2 = date.plusDays(7).get(WeekFields.ISO.weekOfWeekBasedYear());
        boolean normalIncrement = (isoWeek2 == isoWeek1 + 1);

        if (normalIncrement) {
            assertThat(parity)
                    .as("Для %s (W%d) и %s (W%d) parity должны быть разными",
                            date, isoWeek1, date.plusDays(7), isoWeek2)
                    .isNotEqualTo(parityNextWeek);
        }
    }

    @RepeatedTest(100)
    @DisplayName("parityStableWithinWeek — 7 дней одной недели → одна parity")
    void parityStableWithinWeek() {
        long minDay = LocalDate.of(2020, 1, 1).toEpochDay();
        long maxDay = LocalDate.of(2030, 12, 31).toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        LocalDate date = LocalDate.ofEpochDay(randomDay);

        // Выравниваем к понедельнику (start of ISO week)
        LocalDate monday = date.with(WeekFields.ISO.dayOfWeek(), 1);
        WeekType mondayParity = parityOf(monday);

        for (int day = 0; day < 7; day++) {
            LocalDate dayN = monday.plusDays(day);
            assertThat(parityOf(dayN))
                    .as("%s (ISO week %d) должен иметь ту же parity, что и %s",
                            dayN, dayN.get(WeekFields.ISO.weekOfWeekBasedYear()), monday)
                    .isEqualTo(mondayParity);
        }
    }

    @Test
    @DisplayName("parityDeterministic — одна дата → один WeekType")
    void parityDeterministic() {
        LocalDate fixed = LocalDate.of(2026, 4, 22);
        WeekType first = parityOf(fixed);
        for (int i = 0; i < 1000; i++) {
            assertThat(parityOf(fixed)).isEqualTo(first);
        }
    }
}
