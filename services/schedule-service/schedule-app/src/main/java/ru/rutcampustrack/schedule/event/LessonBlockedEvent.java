package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Domain event published when a headman blocks self check-in for a lesson.
 * Matches event-schemas/lesson.blocked.json contract.
 */
public class LessonBlockedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("room") String room,
            @JsonProperty("blocked_by") Long blockedBy,
            @JsonProperty("blocked_at") String blockedAt
    ) {}

    public LessonBlockedEvent(Object source,
                              Long lessonId,
                              Long groupId,
                              Long subjectId,
                              LocalDate date,
                              LocalTime startTime,
                              LocalTime endTime,
                              Integer lessonNumber,
                              String room,
                              Long blockedBy,
                              OffsetDateTime blockedAt) {
        super(source, "lesson.blocked",
                new Payload(
                        lessonId,
                        groupId,
                        subjectId,
                        date.toString(),
                        startTime != null ? startTime.toString() : null,
                        endTime != null ? endTime.toString() : null,
                        lessonNumber,
                        room,
                        blockedBy,
                        blockedAt != null ? blockedAt.toString() : null));
    }
}
