package ru.rutcampustrack.schedule.lesson;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.schedule.contract.enums.LessonSlot;

import java.time.Duration;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonSlotTest {
    @Test
    void eightOrderedMoscowSlotsHaveExactTimesAndDoNotOverlap() {
        assertThat(LessonSlot.TIMEZONE).isEqualTo("Europe/Moscow");
        assertThat(LessonSlot.ordered().stream().map(slot -> slot.startTime() + "–" + slot.endTime()))
                .containsExactly("08:30–09:50", "10:05–11:25", "11:40–13:00", "13:45–15:05",
                        "15:20–16:40", "16:55–18:15", "18:30–19:50", "20:00–21:20");
        for (int number = 1; number <= 8; number++) {
            LessonSlot slot = LessonSlot.forNumber(number);
            assertThat(slot.lessonNumber()).isEqualTo(number);
            assertThat(Duration.between(slot.startTime(), slot.endTime()).toMinutes()).isEqualTo(80);
            if (number > 1) assertThat(slot.startTime()).isAfter(LessonSlot.forNumber(number - 1).endTime());
        }
        assertThatThrownBy(() -> LessonSlot.ordered().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> LessonSlot.forNumber(0)).isInstanceOf(LessonSlot.ValidationException.class);
        assertThatThrownBy(() -> LessonSlot.forNumber(9)).isInstanceOf(LessonSlot.ValidationException.class);
        assertThatThrownBy(() -> LessonSlot.forNumber(null)).isInstanceOf(LessonSlot.ValidationException.class);
        assertThatThrownBy(() -> LessonSlot.forNumber(1.5)).isInstanceOf(LessonSlot.ValidationException.class);
    }

    @Test
    void optionalLegacyTimePairMustMatchTheSelectedSlotExactly() {
        LessonSlot slot = LessonSlot.forNumber(2);
        slot.validateTimes(null, null);
        slot.validateTimes(slot.startTime(), slot.endTime());
        assertThatThrownBy(() -> slot.validateTimes(LocalTime.of(8, 30), LocalTime.of(9, 50)))
                .isInstanceOf(LessonSlot.ValidationException.class);
        assertThatThrownBy(() -> slot.validateTimes(slot.startTime(), null))
                .isInstanceOf(LessonSlot.ValidationException.class);
    }
}
