package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

/**
 * Domain event published when a lesson transitions from PLANNED to ACTIVE status.
 * Matches event-schemas/lesson.started.json contract.
 * <p>
 * Published by the cron job (Plan 02) when start_time is reached.
 * <p>
 * D-16: teacher_id removed. Downstream consumers (notification-bot, notification-service,
 * attendance-service) never read it. Teacher journal access goes through
 * ScheduleItem × TeacherSubjectGroup JOIN, not through the event payload.
 */
public class LessonStartedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("lesson_number") Short lessonNumber,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("room") String room
    ) {}

    public LessonStartedEvent(Object source, Long lessonId, Long groupId, Long subjectId,
                               Short lessonNumber,
                               LocalTime startTime, LocalTime endTime, String room) {
        super(source, "lesson.started",
                new Payload(lessonId, groupId, subjectId, lessonNumber,
                        startTime.toString(), endTime.toString(), room));
    }
}
