package ru.rutcampustrack.schedule.contract.dto.lesson;

import ru.rutcampustrack.schedule.contract.enums.LessonSlot;

import java.time.LocalTime;
import java.util.List;

public record LessonSlotCatalogResponse(String timezone, List<Slot> slots) {
    public LessonSlotCatalogResponse { slots = List.copyOf(slots); }

    public static LessonSlotCatalogResponse canonical() {
        return new LessonSlotCatalogResponse(LessonSlot.TIMEZONE, LessonSlot.ordered().stream()
                .map(slot -> new Slot(slot.lessonNumber(), slot.startTime(), slot.endTime())).toList());
    }

    public record Slot(int lessonNumber, LocalTime startTime, LocalTime endTime) { }
}
