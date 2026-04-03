package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Domain event published when a lesson transitions from ACTIVE to CLOSED status.
 * Matches event-schemas/lesson.closed.json contract.
 * <p>
 * Published by the cron job (Plan 02) when end_time + 5 minutes is reached.
 */
public class LessonClosedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId
    ) {}

    public LessonClosedEvent(Object source, Long lessonId, Long groupId, Long subjectId) {
        super(source, "lesson.closed", new Payload(lessonId, groupId, subjectId));
    }
}
