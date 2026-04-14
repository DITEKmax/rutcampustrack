package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Domain event published when a one-off lesson is deleted by a headman.
 * Matches event-schemas/lesson.one_off.cancelled.json contract.
 * <p>
 * Published by OneOffLessonService.deleteOneOffLesson() after successful delete (any date — D-22).
 * Consumed by:
 *  - notification-bot (push to all group students, D-18)
 *  - notification-web (STOMP push to open PWA/web-panel tabs)
 *  - attendance-service (cascade delete of derived lesson + attendance marks — D-22)
 */
public class OneOffLessonCancelledEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("lesson_number") Integer lessonNumber
    ) {}

    public OneOffLessonCancelledEvent(Object source, Long groupId, Long subjectId,
                                      LocalDate date, Integer lessonNumber) {
        super(source, "lesson.one_off.cancelled",
                new Payload(groupId, subjectId, date.toString(), lessonNumber));
    }
}
