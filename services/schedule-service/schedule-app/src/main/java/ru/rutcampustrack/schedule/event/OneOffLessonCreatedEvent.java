package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Domain event published when a one-off lesson is created by a headman.
 * Matches event-schemas/lesson.one_off.created.json contract.
 * <p>
 * Published by OneOffLessonService.createOneOffLesson() after successful save.
 * Consumed by:
 *  - notification-bot (push to all group students, D-18)
 *  - notification-web (STOMP push to open PWA/web-panel tabs)
 *  - attendance-service (read-model update for lesson generation)
 */
public class OneOffLessonCreatedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("one_off_lesson_id") Long oneOffLessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("classroom") String classroom
    ) {}

    public OneOffLessonCreatedEvent(Object source, Long oneOffLessonId, Long groupId,
                                    Long subjectId, LocalDate date, Integer lessonNumber,
                                    String classroom) {
        super(source, "lesson.one_off.created",
                new Payload(oneOffLessonId, groupId, subjectId, date.toString(), lessonNumber, classroom));
    }
}
