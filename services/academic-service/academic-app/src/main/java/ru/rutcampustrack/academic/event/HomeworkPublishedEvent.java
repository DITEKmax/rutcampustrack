package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import ru.rutcampustrack.academic.entity.Homework;

/**
 * Published when a new homework assignment is created by a headman or assistant.
 * <p>
 * Per EVENT-03: includes event_id UUID for idempotent downstream processing.
 * Per Phase 61 D-07: payload extended with lesson_date + lesson_number so that
 * downstream consumers (notification-bot) can render a push with the exact lesson slot.
 */
public class HomeworkPublishedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("homework_id") Long homeworkId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            String title,
            String description,
            String link,
            @JsonProperty("has_link") boolean hasLink,
            @JsonProperty("lesson_date") String lessonDate,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("binding_mode") String bindingMode,
            @JsonProperty("semester_id") @JsonInclude(JsonInclude.Include.NON_NULL) Long semesterId,
            @JsonProperty("lesson_type") @JsonInclude(JsonInclude.Include.NON_NULL) String lessonType,
            @JsonProperty("actor_user_id") @JsonInclude(JsonInclude.Include.NON_NULL) Long actorUserId
    ) {}

    public HomeworkPublishedEvent(Object source, Long homeworkId, Long groupId,
                                   Long subjectId, String title, String description, String link,
                                   String lessonDate, Integer lessonNumber) {
        super(source, "homework.published",
                new Payload(homeworkId, groupId, subjectId, title, description, link,
                        hasLink(link), lessonDate, lessonNumber,
                        lessonNumber == null ? "DATE" : "LESSON", null, null, null));
    }

    public HomeworkPublishedEvent(Object source, Homework homework, Long actor) {
        super(source, "homework.published", new Payload(homework.getId(), homework.getGroupId(), homework.getSubjectId(),
                homework.getTitle(), homework.getDescription(), homework.getLink(), hasLink(homework.getLink()),
                homework.getLessonDate().toString(), homework.getLessonNumber(), homework.getBindingMode().name(),
                homework.getSemesterId(), homework.getLessonType() == null ? null : homework.getLessonType().name().toLowerCase(java.util.Locale.ROOT),
                actor != null && actor > 0 ? actor : null));
    }

    private static boolean hasLink(String link) {
        return link != null && !link.isBlank();
    }
}
