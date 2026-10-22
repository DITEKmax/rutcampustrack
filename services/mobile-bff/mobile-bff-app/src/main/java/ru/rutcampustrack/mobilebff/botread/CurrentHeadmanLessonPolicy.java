package ru.rutcampustrack.mobilebff.botread;

import io.grpc.Status;

import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects from the complete day response for a freshly resolved own headman group. */
public final class CurrentHeadmanLessonPolicy {
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private CurrentHeadmanLessonPolicy() {}

    public static Selection select(
            long ownGroupId,
            long semesterId,
            Instant now,
            List<LessonResponse> dayLessons,
            long selectedLessonId) {
        if (ownGroupId <= 0
                || semesterId <= 0
                || now == null
                || dayLessons == null
                || selectedLessonId < 0) throw unavailable();
        LocalDate today = now.atZone(MOSCOW).toLocalDate();
        Set<Long> identities = new HashSet<>();
        for (LessonResponse lesson : dayLessons) {
            if (lesson == null
                    || lesson.getId() <= 0
                    || !identities.add(lesson.getId())
                    || lesson.getGroupId() != ownGroupId
                    || lesson.getSemesterId() != semesterId
                    || !today.toString().equals(lesson.getDate())) throw unavailable();
            String transfer = lesson.getTransferState();
            if (!transfer.isBlank() && !"COMPLETED".equals(transfer)) throw unavailable();
        }
        List<LessonResponse> current =
                dayLessons.stream()
                        .filter(lesson -> current(lesson, today, now))
                        .sorted(
                                Comparator.comparing(LessonResponse::getStartTime)
                                        .thenComparingInt(LessonResponse::getLessonNumber)
                                        .thenComparingLong(LessonResponse::getId))
                        .toList();
        if (selectedLessonId > 0) {
            LessonResponse selected =
                    current.stream()
                            .filter(lesson -> lesson.getId() == selectedLessonId)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            Status.PERMISSION_DENIED
                                                    .withDescription("Пара больше недоступна")
                                                    .asRuntimeException());
            return new Selection(State.READY, List.of(), selected);
        }
        if (current.isEmpty()) return new Selection(State.NO_CURRENT, List.of(), null);
        if (current.size() > 1) return new Selection(State.CHOOSE, current, null);
        return new Selection(State.READY, List.of(), current.getFirst());
    }

    private static boolean current(LessonResponse lesson, LocalDate today, Instant now) {
        String status = lesson.getStatus();
        if (!Set.of("planned", "active", "closed", "cancelled", "transferred")
                .contains(status.toLowerCase(java.util.Locale.ROOT))) throw unavailable();
        if (!"active".equalsIgnoreCase(status)) return false;
        try {
            LocalTime start = LocalTime.parse(lesson.getStartTime());
            LocalTime end = LocalTime.parse(lesson.getEndTime());
            if (!end.isAfter(start)) throw unavailable();
            Instant startsAt = today.atTime(start).atZone(MOSCOW).toInstant();
            Instant endsAt = today.atTime(end).atZone(MOSCOW).toInstant();
            return !now.isBefore(startsAt) && now.isBefore(endsAt);
        } catch (java.time.DateTimeException invalid) {
            throw unavailable();
        }
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("Расписание текущей пары недоступно")
                .asRuntimeException();
    }

    public enum State {
        NO_CURRENT,
        CHOOSE,
        READY
    }

    public record Selection(State state, List<LessonResponse> choices, LessonResponse lesson) {
        public Selection {
            choices = List.copyOf(choices);
        }
    }
}
