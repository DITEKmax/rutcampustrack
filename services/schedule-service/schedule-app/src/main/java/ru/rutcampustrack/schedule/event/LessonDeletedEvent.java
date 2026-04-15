package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Domain event published when one or more Lesson rows are physically deleted
 * from schedule-service. Unlike {@code lesson.cancelled}, which keeps the row
 * and only marks its status, this event fires when the Lesson row no longer
 * exists — downstream services must drop any records keyed to those lesson_ids.
 * <p>
 * Use case: {@code LessonGenerationService.regenerateFromDate()} deletes PLANNED
 * lessons before regenerating them from an updated {@code ScheduleItem}. Any
 * attendance doc attached to those ids would otherwise become an orphan and
 * surface as a "duplicate" alongside the newly created Lesson.
 * <p>
 * Consumers: attendance-service (attendance docs).
 */
public class LessonDeletedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_ids") List<Long> lessonIds
    ) {}

    public LessonDeletedEvent(Object source, List<Long> lessonIds) {
        super(source, "lesson.deleted", new Payload(lessonIds));
    }
}
