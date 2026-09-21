package ru.rutcampustrack.attendance.journal;

import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Shared server-side timing and metadata gate for the headman journal.
 *
 * <p>The schedule service owns the lesson snapshot. Attendance must not infer
 * editability from a UTC date alone or from the local wall clock of the
 * attendance process, so this policy always evaluates the concrete start
 * instant in Moscow and requires the immutable semester metadata.</p>
 */
public final class JournalLessonPolicy {

    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private JournalLessonPolicy() {
    }

    /**
     * Validates the metadata needed by both report and write paths and returns
     * the normalized status and real start instant.
     */
    public static Timing requireTiming(LessonResponse lesson) {
        if (lesson == null || lesson.getId() <= 0) {
            throw new BadRequestException("Пара недоступна");
        }
        if (lesson.getSemesterId() <= 0) {
            throw new AcademicServiceUnavailableException(
                    "Schedule returned a lesson without a positive semester");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(lesson.getDate());
        } catch (RuntimeException ex) {
            throw new BadRequestException("Дата пары недоступна");
        }
        LocalTime start;
        try {
            if (lesson.getStartTime() == null || lesson.getStartTime().isBlank()) {
                throw new IllegalArgumentException("missing start time");
            }
            start = LocalTime.parse(lesson.getStartTime());
        } catch (RuntimeException ex) {
            throw new BadRequestException("Время начала пары недоступно");
        }
        return new Timing(date, normalizedStatus(lesson.getStatus()),
                LocalDateTime.of(date, start).atZone(MOSCOW).toInstant());
    }

    public static void requireStarted(Timing timing, Clock clock) {
        if ("CANCELLED".equals(timing.status())) {
            throw new BadRequestException("Пара отменена");
        }
        if (clock == null || clock.instant().isBefore(timing.startsAt())) {
            throw new BadRequestException("Нельзя изменять посещаемость до начала пары");
        }
    }

    private static String normalizedStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Состояние пары недоступно");
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "STARTED", "ACTIVE" -> "ACTIVE";
            case "PLANNED" -> "PLANNED";
            case "CLOSED" -> "CLOSED";
            case "CANCELLED" -> "CANCELLED";
            default -> throw new BadRequestException("Неизвестное состояние пары");
        };
    }

    public record Timing(LocalDate date, String status, Instant startsAt) {
        public boolean hasStarted(Instant now) {
            return now != null && !now.isBefore(startsAt);
        }
    }
}
