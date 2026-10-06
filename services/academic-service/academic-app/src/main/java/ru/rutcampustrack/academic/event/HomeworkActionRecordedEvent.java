package ru.rutcampustrack.academic.event;

import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.entity.Homework;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Existing SQL outbox receives immutable content and actual placement snapshots in the caller's transaction. */
public final class HomeworkActionRecordedEvent extends DomainEvent {
    private HomeworkActionRecordedEvent(Map<String, Object> payload, OffsetDateTime occurredAt) {
        super(null, "homework.action-recorded", payload);
        setOccurredAt(occurredAt);
    }

    public static Map<String, Object> snapshot(Homework homework) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("homework_id", homework.getId()); state.put("group_id", homework.getGroupId());
        state.put("subject_id", homework.getSubjectId()); state.put("semester_id", homework.getSemesterId());
        state.put("binding_id", homework.getBindingId()); state.put("lesson_id", homework.getPlacementLessonId());
        state.put("occurrence_id", homework.getPlacementOccurrenceId());
        state.put("binding_revision", homework.getPlacementBindingRevision());
        state.put("revision", homework.getRevision()); state.put("title", homework.getTitle());
        state.put("description", homework.getDescription()); state.put("link", homework.getLink());
        state.put("binding_mode", homework.getBindingMode().name()); state.put("lesson_date", homework.getLessonDate().toString());
        state.put("lesson_type", homework.getLessonType() == null ? null : homework.getLessonType().name());
        state.put("lesson_number", homework.getLessonNumber()); state.put("publication_state", homework.getPublicationState().name());
        return Collections.unmodifiableMap(state);
    }

    public static void publish(ApplicationEventPublisher events, String action, Homework homework, Long actorId,
                               Map<String, Object> before, OffsetDateTime occurredAt) {
        Map<String, Object> after = snapshot(homework);
        Long oldLesson = before == null ? null : (Long) before.get("lesson_id");
        Long newLesson = homework.getPlacementLessonId();
        emit(events, action, homework, actorId, oldLesson != null ? oldLesson : newLesson, before, after, occurredAt);
        if (oldLesson != null && newLesson != null && !Objects.equals(oldLesson, newLesson)) {
            emit(events, action, homework, actorId, newLesson, before, after, occurredAt);
        }
    }

    private static void emit(ApplicationEventPublisher events, String action, Homework homework, Long actorId,
                             Long lessonId, Map<String, Object> before, Map<String, Object> after, OffsetDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", homework.getGroupId()); payload.put("semester_id", homework.getSemesterId());
        payload.put("lesson_id", lessonId); payload.put("homework_id", homework.getId()); payload.put("action", action);
        Map<String, Object> actor = new LinkedHashMap<>();
        if (actorId != null) {
            if (actorId <= 0) throw new IllegalArgumentException("action actor must be positive");
            actor.put("user_id", actorId); actor.put("role", null); actor.put("display_name", null);
        }
        payload.put("actor", actorId == null ? null : actor); payload.put("student", null);
        payload.put("before", before); payload.put("after", after); payload.put("geo", null); payload.put("ticket", null);
        payload.put("homework", Map.of("id", homework.getId(), "revision", homework.getRevision()));
        payload.put("revision", homework.getRevision());
        events.publishEvent(new HomeworkActionRecordedEvent(payload, occurredAt));
    }
}
