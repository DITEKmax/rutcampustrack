package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

/**
 * Domain event for attendance reminders during an active lesson.
 *
 * <p>{@code phase=midpoint} is published after the lesson midpoint;
 * {@code phase=near_end} is published roughly five minutes before the end.
 * Both phases are idempotent via {@code lessons.reminder_midpoint_sent_at}
 * and {@code lessons.reminder_near_end_sent_at}.
 *
 * <p>Per-user delivery filtering is done by notification consumers using
 * notification preferences and reminder attendance state, so students who
 * have already checked in do not receive repeated reminders.
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
