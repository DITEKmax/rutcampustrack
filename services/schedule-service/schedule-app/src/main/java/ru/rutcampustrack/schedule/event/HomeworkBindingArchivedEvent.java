package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Durable signal emitted when cancelling a canonical lesson archives a binding. */
public class HomeworkBindingArchivedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("binding_id") Long bindingId,
            @JsonProperty("actor_id") Long actorId,
            @JsonProperty("request_key") UUID requestKey,
            @JsonProperty("occurrence_id") Long occurrenceId,
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("homework_id") Long homeworkId,
            @JsonProperty("binding_revision") Long bindingRevision
    ) { }

    public HomeworkBindingArchivedEvent(Object source,
                                        long bindingId,
                                        long actorId,
                                        UUID requestKey,
                                        long occurrenceId,
                                        long lessonId,
                                        Long homeworkId,
                                        long bindingRevision) {
        super(source, "homework.binding.archived", new Payload(
                bindingId, actorId, requestKey, occurrenceId, lessonId, homeworkId, bindingRevision));
    }
}
