package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

/**
 * Domain event published when a lesson is past its midpoint and not yet
 * closed — students who haven't checked in get a reminder.
 *
 * <p>Шлётся раз на пару (idempotent через {@code lessons.reminder_midpoint_sent_at}).
 * Per-user filtering ("отметился ли студент") выполняют consumer'ы:
 * notification-bot читает Redis ключ {@code reminder:lesson:user}, фронт
 * видит только если у пользователя ещё не выставлен PRESENT/EXCUSED/...
 *
 * <p>{@code phase} зарезервирован для будущих фаз ("near_end" — для второго
 * напоминания за 5 минут до конца); сейчас всегда {@code "midpoint"}.
 *
 * <p>Schema: {@code event-schemas/lesson.reminder.json}.
 */
public class LessonReminderEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("lesson_number") Short lessonNumber,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("room") String room,
            @JsonProperty("phase") String phase
    ) {}

    public LessonReminderEvent(Object source, Long lessonId, Long groupId, Long subjectId,
                               Short lessonNumber, LocalTime startTime, LocalTime endTime,
                               String room, String phase) {
        super(source, "lesson.reminder",
                new Payload(lessonId, groupId, subjectId, lessonNumber,
                        startTime.toString(), endTime.toString(), room, phase));
    }
}
