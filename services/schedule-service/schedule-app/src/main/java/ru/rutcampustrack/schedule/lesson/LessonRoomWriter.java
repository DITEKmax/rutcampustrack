package ru.rutcampustrack.schedule.lesson;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.lesson.ChangeLessonRoomRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.ChangeLessonRoomResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonSlot.ValidationException;
import ru.rutcampustrack.schedule.event.ScheduleActionRecordedEvent;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

/** Atomic in-place mutation; the append-only SQL receipt is also the exact trigger authority. */
@Service
public class LessonRoomWriter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final ScheduleSemesterArchiveWriteFence archive;

    public LessonRoomWriter(
            JdbcTemplate jdbc,
            Clock clock,
            ApplicationEventPublisher events,
            ScheduleSemesterArchiveWriteFence archive) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
        this.archive = archive;
    }

    @Transactional
    public ChangeLessonRoomResponse change(
            long lessonId,
            long actorId,
            UUID key,
            ChangeLessonRoomRequest request,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        String room = normalizeRoom(request.room());
        long expected = positiveRevision(request.expectedRevision());
        if (key == null) throw new ValidationException("Нужен UUID Idempotency-Key");
        archive.lockPreparedBusinessWrite(prepared);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL",
                Boolean.class,
                "lesson-room:" + actorId + ":" + key);
        var prior =
                jdbc.queryForList(
                        "SELECT * FROM lesson_room_change_ledger WHERE actor_id=? AND request_key=?",
                        actorId,
                        key);
        if (!prior.isEmpty()) {
            var receipt = prior.getFirst();
            if (number(receipt, "lesson_id") != lessonId
                    || number(receipt, "expected_occurrence_revision") != expected
                    || !Objects.equals(receipt.get("after_room"), room)
                    || number(receipt, "semester_id") != prepared.semesterId()) {
                throw new ConflictException(
                        "Ключ операции уже использован с другой парой или данными");
            }
            return receipt(receipt);
        }

        var initial = jdbc.queryForList("SELECT * FROM lessons WHERE id=?", lessonId);
        if (initial.isEmpty()) throw new ResourceNotFoundException("Lesson", "id", lessonId);
        var before = initial.getFirst();
        if (before.get("occurrence_id") == null) throw conflict();
        if (number(before, "semester_id") != prepared.semesterId()) throw conflict();
        // Same archive → assignment → origin → occurrence → physical → binding order as
        // transfer/lifecycle.
        if (before.get("assignment_id") != null) {
            jdbc.queryForList(
                    "SELECT assignment_id FROM schedule_assignment_fences WHERE assignment_id=? FOR UPDATE",
                    before.get("assignment_id"));
        }
        Object item = before.get("schedule_item_id"), oneOff = before.get("one_off_lesson_id");
        if ((item == null) == (oneOff == null)) throw conflict();
        if (item != null) {
            jdbc.queryForList("SELECT id FROM schedule_items WHERE id=? FOR UPDATE", item);
        } else {
            var origins =
                    jdbc.queryForList(
                            "SELECT * FROM schedule_one_off_lessons WHERE id=? FOR UPDATE", oneOff);
            if (origins.size() != 1
                    || !Objects.equals(origins.getFirst().get("physical_lesson_id"), lessonId))
                throw conflict();
        }
        var occurrences =
                jdbc.queryForList(
                        "SELECT * FROM lesson_occurrences WHERE id=? FOR UPDATE",
                        before.get("occurrence_id"));
        if (occurrences.size() != 1) throw conflict();
        var occurrence = occurrences.getFirst();
        var lesson =
                jdbc.queryForMap(
                        "SELECT *, status::text AS state FROM lessons WHERE id=? FOR UPDATE",
                        lessonId);
        if (!Objects.equals(occurrence.get("current_lesson_id"), lessonId)
                || number(occurrence, "revision") != expected
                || !Objects.equals(occurrence.get("generation"), lesson.get("generation"))
                || !"planned".equals(lesson.get("state"))
                || !LocalDateTime.of(
                                ((java.sql.Date) lesson.get("date")).toLocalDate(),
                                ((java.sql.Time) lesson.get("start_time")).toLocalTime())
                        .isAfter(LocalDateTime.now(clock.withZone(ZoneId.of("Europe/Moscow")))))
            throw conflict();
        for (String field :
                new String[] {
                    "schedule_item_id",
                    "one_off_lesson_id",
                    "assignment_id",
                    "assigned_teacher_id",
                    "group_id",
                    "subject_id",
                    "semester_id",
                    "lesson_type"
                }) {
            if (!Objects.equals(occurrence.get(field), lesson.get(field))) throw conflict();
        }
        jdbc.queryForList(
                "SELECT binding_id FROM lesson_homework_bindings WHERE occurrence_id=? ORDER BY binding_id FOR UPDATE",
                before.get("occurrence_id"));
        Boolean pending =
                jdbc.queryForObject(
                        """
                SELECT EXISTS(SELECT 1 FROM lesson_transfer_operations WHERE occurrence_id=? AND state<>'COMPLETED')
                    OR EXISTS(SELECT 1 FROM lesson_homework_bindings WHERE occurrence_id=?
                              AND (state='PENDING' OR pending_edit_operation_id IS NOT NULL))
                """,
                        Boolean.class,
                        before.get("occurrence_id"),
                        before.get("occurrence_id"));
        if (Boolean.TRUE.equals(pending)) throw conflict();

        UUID operation = UUID.randomUUID();
        boolean changed = !Objects.equals(lesson.get("room_snapshot"), room);
        var actionBefore = changed ? ScheduleActionRecordedEvent.snapshot(jdbc, lessonId) : null;
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.lesson_room_operation_id', ?, true)",
                String.class,
                operation.toString());
        jdbc.update(
                """
                INSERT INTO lesson_room_change_ledger(operation_id, actor_id, request_key, semester_id,
                    lesson_id, occurrence_id, expected_occurrence_revision, after_occurrence_revision,
                    expected_lesson_revision, after_lesson_revision, before_room, after_room,
                    before_lesson, before_occurrence)
                SELECT ?, ?, ?, l.semester_id, l.id, o.id, o.revision, o.revision+?,
                    l.revision, l.revision+?, l.room_snapshot, ?, to_jsonb(l), to_jsonb(o)
                  FROM lessons l JOIN lesson_occurrences o ON o.id=l.occurrence_id WHERE l.id=?
                """,
                operation,
                actorId,
                key,
                changed ? 1 : 0,
                changed ? 1 : 0,
                room,
                lessonId);
        if (changed) {
            int lessonRows =
                    jdbc.update(
                            "UPDATE lessons SET room_snapshot=?, revision=revision+1 WHERE id=? AND revision=?",
                            room,
                            lessonId,
                            number(lesson, "revision"));
            int occurrenceRows =
                    jdbc.update(
                            "UPDATE lesson_occurrences SET revision=revision+1 WHERE id=? AND revision=? AND current_lesson_id=?",
                            before.get("occurrence_id"),
                            expected,
                            lessonId);
            if (lessonRows != 1 || occurrenceRows != 1) throw conflict();
            ScheduleActionRecordedEvent.publish(
                    jdbc,
                    events,
                    "ROOM_CHANGED",
                    lessonId,
                    actorId,
                    actionBefore,
                    OffsetDateTime.now(clock));
        }
        return receipt(
                jdbc.queryForMap(
                        "SELECT * FROM lesson_room_change_ledger WHERE operation_id=?", operation));
    }

    private static ChangeLessonRoomResponse receipt(Map<String, Object> row) {
        return new ChangeLessonRoomResponse(
                row.get("operation_id").toString(),
                "COMPLETED",
                row.get("lesson_id").toString(),
                row.get("occurrence_id").toString(),
                row.get("after_occurrence_revision").toString(),
                (String) row.get("after_room"));
    }

    private static long number(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).longValue();
    }

    private static ConflictException conflict() {
        return new ConflictException(
                "Пара изменилась или недоступна для смены аудитории; обнови расписание");
    }

    private static String normalizeRoom(String value) {
        String room = value == null ? null : value.trim();
        if (room != null && room.isEmpty()) room = null;
        if (room != null && room.codePointCount(0, room.length()) > 64)
            throw new ValidationException("Аудитория не длиннее 64 символов");
        return room;
    }

    private static long positiveRevision(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]*")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException malformed) {
            throw new ValidationException("Нужна положительная expectedRevision");
        }
    }
}
