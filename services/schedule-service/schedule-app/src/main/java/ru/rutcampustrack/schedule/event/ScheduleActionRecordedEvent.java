package ru.rutcampustrack.schedule.event;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

/** Authoritative mutation snapshots, delivered by the existing transactional outbox listener. */
public final class ScheduleActionRecordedEvent extends DomainEvent {
    private ScheduleActionRecordedEvent(Map<String, Object> payload, OffsetDateTime occurredAt) {
        super(null, "schedule.action-recorded", payload);
        setOccurredAt(occurredAt);
    }

    /** Caller holds the mutation's existing lesson/occurrence locks. */
    public static Map<String, Object> snapshot(JdbcTemplate jdbc, long lessonId) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        """
                SELECT id AS lesson_id, occurrence_id, schedule_item_id, one_off_lesson_id,
                       assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                       lesson_type, date, lesson_number, start_time, end_time, room_snapshot,
                       status::text AS status, generation, revision, cancel_reason, cancelled_by,
                       cancelled_at, is_geo_blocked, is_blocked_by_headman, blocked_by_user_id, blocked_at
                  FROM lessons WHERE id = ?
                """,
                        lessonId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        row.forEach(
                (key, value) ->
                        snapshot.put(
                                key,
                                value == null
                                                || value instanceof Number
                                                || value instanceof Boolean
                                                || value instanceof String
                                        ? value
                                        : value.toString()));
        return Collections.unmodifiableMap(snapshot);
    }

    public static void publish(
            JdbcTemplate jdbc,
            ApplicationEventPublisher events,
            String action,
            long lessonId,
            Long actorId,
            Map<String, Object> before,
            OffsetDateTime occurredAt) {
        publish(events, action, lessonId, actorId, before, snapshot(jdbc, lessonId), occurredAt);
    }

    public static Map<String, Object> snapshot(Lesson lesson, ScheduleItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("lesson_id", lesson.getId());
        snapshot.put(
                "group_id", lesson.getGroupId() != null ? lesson.getGroupId() : item.getGroupId());
        snapshot.put("semester_id", lesson.getSemesterId());
        snapshot.put(
                "subject_id",
                lesson.getSubjectId() != null ? lesson.getSubjectId() : item.getSubjectId());
        snapshot.put("occurrence_id", lesson.getOccurrenceId());
        snapshot.put("schedule_item_id", lesson.getScheduleItemId());
        snapshot.put("one_off_lesson_id", lesson.getOneOffLessonId());
        snapshot.put("assignment_id", lesson.getAssignmentId());
        snapshot.put("assigned_teacher_id", lesson.getAssignedTeacherId());
        snapshot.put("lesson_type", lesson.getLessonType());
        snapshot.put("date", lesson.getDate().toString());
        snapshot.put(
                "lesson_number",
                lesson.getLessonNumber() != null
                        ? lesson.getLessonNumber()
                        : item.getLessonNumber());
        snapshot.put(
                "start_time",
                String.valueOf(
                        lesson.getStartTime() != null
                                ? lesson.getStartTime()
                                : item.getStartTime()));
        snapshot.put(
                "end_time",
                String.valueOf(
                        lesson.getEndTime() != null ? lesson.getEndTime() : item.getEndTime()));
        snapshot.put("status", lesson.getStatus().name().toLowerCase(java.util.Locale.ROOT));
        snapshot.put("cancel_reason", lesson.getCancelReason());
        snapshot.put("cancelled_by", lesson.getCancelledBy());
        snapshot.put(
                "cancelled_at",
                lesson.getCancelledAt() == null ? null : lesson.getCancelledAt().toString());
        snapshot.put("revision", lesson.getRevision());
        snapshot.put("generation", lesson.getGeneration());
        snapshot.put("is_geo_blocked", lesson.isGeoBlocked());
        snapshot.put("is_blocked_by_headman", lesson.isBlockedByHeadman());
        snapshot.put("blocked_by_user_id", lesson.getBlockedByUserId());
        snapshot.put(
                "blocked_at",
                lesson.getBlockedAt() == null ? null : lesson.getBlockedAt().toString());
        snapshot.put("room_snapshot", lesson.resolveRoom(item.getRoom()));
        return Collections.unmodifiableMap(snapshot);
    }

    public static void publish(
            ApplicationEventPublisher events,
            String action,
            long lessonId,
            Long actorId,
            Map<String, Object> before,
            Map<String, Object> after,
            OffsetDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", after.get("group_id"));
        payload.put("semester_id", after.get("semester_id"));
        payload.put("lesson_id", lessonId);
        payload.put("action", action);
        Map<String, Object> actor = new LinkedHashMap<>();
        if (actorId != null) {
            if (actorId <= 0) throw new IllegalArgumentException("action actor must be positive");
            actor.put("user_id", actorId);
            actor.put("role", null);
            actor.put("display_name", null);
        }
        payload.put("actor", actorId == null ? null : actor);
        payload.put("student", null);
        payload.put("before", before);
        payload.put("after", after);
        payload.put("geo", null);
        payload.put("ticket", null);
        payload.put("homework", null);
        payload.put("revision", after.get("revision"));
        events.publishEvent(new ScheduleActionRecordedEvent(payload, occurredAt));
    }

    public static void publishReplacement(
            JdbcTemplate jdbc,
            ApplicationEventPublisher events,
            String action,
            long sourceId,
            long targetId,
            long actorId,
            Map<String, Object> before,
            OffsetDateTime occurredAt,
            java.util.UUID operationId) {
        Map<String, Object> sourceAfter = new LinkedHashMap<>(snapshot(jdbc, sourceId));
        sourceAfter.put("target_lesson_id", targetId);
        Map<String, Object> targetAfter = new LinkedHashMap<>(snapshot(jdbc, targetId));
        targetAfter.put("source_lesson_id", sourceId);
        sourceAfter.put("operation_id", operationId.toString());
        targetAfter.put("operation_id", operationId.toString());
        publish(events, action, sourceId, actorId, before, sourceAfter, occurredAt);
        publish(events, action, targetId, actorId, null, targetAfter, occurredAt);
    }
}
