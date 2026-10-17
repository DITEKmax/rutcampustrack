package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.shared.events.EventVersion;

/** Only a manual ACTIVE publication withdrawal; automatic archival is not this fact. */
@EventVersion(1)
public final class HomeworkWithdrawnEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("homework_id") long homeworkId,
            @JsonProperty("group_id") long groupId,
            @JsonProperty("subject_id") long subjectId,
            @JsonProperty("semester_id") long semesterId,
            @JsonProperty("lesson_type") @JsonInclude(JsonInclude.Include.NON_NULL)
                    String lessonType,
            @JsonProperty("lesson_date") String lessonDate,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("binding_mode") String bindingMode,
            @JsonProperty("actor_user_id") long actorUserId) {}

    public HomeworkWithdrawnEvent(Object source, Homework homework, long actor) {
        super(
                source,
                "homework.withdrawn",
                new Payload(
                        homework.getId(),
                        homework.getGroupId(),
                        homework.getSubjectId(),
                        homework.getSemesterId(),
                        homework.getLessonType() == null
                                ? null
                                : homework.getLessonType()
                                        .name()
                                        .toLowerCase(java.util.Locale.ROOT),
                        homework.getLessonDate().toString(),
                        homework.getLessonNumber(),
                        homework.getBindingMode().name(),
                        actor));
    }
}
