package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Domain event published when a lesson is cancelled by a headman.
 * Matches event-schemas/lesson.cancelled.json contract.
 * <p>
 * Published by LessonService.cancelLesson() and LessonService.massCancelLessons().
 */
public class LessonCancelledEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("cancel_reason") String cancelReason
    ) {}

    public LessonCancelledEvent(Object source, Long lessonId, Long groupId, Long subjectId,
                                 LocalDate date, String cancelReason) {
        super(source, "lesson.cancelled",
                new Payload(lessonId, groupId, subjectId, date.toString(), cancelReason));
    }
}
