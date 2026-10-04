package ru.rutcampustrack.schedule.contract.enums;

import java.time.LocalTime;
import java.util.List;

/** The single immutable timetable for new lesson slots, in Moscow local time. */
public enum LessonSlot {
    FIRST(1, "08:30", "09:50"),
    SECOND(2, "10:05", "11:25"),
    THIRD(3, "11:40", "13:00"),
    FOURTH(4, "13:45", "15:05"),
    FIFTH(5, "15:20", "16:40"),
    SIXTH(6, "16:55", "18:15"),
    SEVENTH(7, "18:30", "19:50"),
    EIGHTH(8, "20:00", "21:20");

    public static final String TIMEZONE = "Europe/Moscow";
    private static final List<LessonSlot> ORDERED = List.of(values());
    private final int lessonNumber;
    private final LocalTime startTime;
    private final LocalTime endTime;

    LessonSlot(int lessonNumber, String startTime, String endTime) {
        this.lessonNumber = lessonNumber;
        this.startTime = LocalTime.parse(startTime);
        this.endTime = LocalTime.parse(endTime);
    }

    public int lessonNumber() { return lessonNumber; }
    public LocalTime startTime() { return startTime; }
    public LocalTime endTime() { return endTime; }
    public static List<LessonSlot> ordered() { return ORDERED; }

    public static LessonSlot forNumber(Number number) {
        if (number == null || number.intValue() < 1 || number.intValue() > ORDERED.size()
                || number.doubleValue() != number.intValue()) {
            throw new ValidationException("Выбери номер пары от 1 до 8");
        }
        return ORDERED.get(number.intValue() - 1);
    }

    /** Optional legacy fields are accepted only as an exact pair matching this slot. */
    public void validateTimes(LocalTime start, LocalTime end) {
        if (start == null && end == null) return;
        if (start == null || end == null) {
            throw new ValidationException("Время начала и окончания пары нужно передать вместе");
        }
        if (!startTime.equals(start) || !endTime.equals(end)) {
            throw new ValidationException("Время пары №" + lessonNumber + " должно быть "
                    + startTime + "–" + endTime + " (Москва)");
        }
    }

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }
}
