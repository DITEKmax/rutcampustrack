package ru.rutcampustrack.schedule.subjectdeletion;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.schedule.event.LessonDeletedEvent;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Own subject only; retained ledgers/replays are not removed by the domain cascade. */
@Service
public class ScheduleSubjectDeletionParticipant {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public ScheduleSubjectDeletionParticipant(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public SubjectDeletionReceipt apply(SubjectDeletionCommand command) {
        validate(command);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(5452097,?)",
                Object.class,
                (int) (command.getSemesterId() % 2147483647));
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(5452098,?)",
                Object.class,
                (int) (command.getSubjectId() % 2147483647));
        var rows =
                jdbc.query(
                        "SELECT *,jsonb_array_length(manifest) AS row_count FROM"
                                + " schedule_subject_deletion_participants WHERE operation_id=? FOR"
                                + " UPDATE",
                        (rs, i) ->
                                new Epoch(
                                        rs.getLong("subject_id"),
                                        rs.getLong("semester_id"),
                                        rs.getLong("group_id"),
                                        rs.getLong("actor_id"),
                                        rs.getString("participant_digest"),
                                        rs.getString("manifest_digest"),
                                        rs.getString("state"),
                                        rs.getBytes("command_manifest"),
                                        rs.getLong("row_count")),
                        UUID.fromString(command.getOperationId()));
        Epoch epoch = rows.isEmpty() ? null : rows.getFirst();
        if (epoch != null
                && (epoch.subject() != command.getSubjectId()
                        || epoch.semester() != command.getSemesterId()
                        || epoch.group() != command.getGroupId()
                        || epoch.actor() != command.getActorId()
                        || !epoch.digest().equals(command.getParticipantDigest())
                        || !epoch.manifestDigest().equals(command.getManifestDigest())
                        || !manifest(epoch.manifest())
                                .equals(
                                        command.toBuilder()
                                                .setAction(SubjectDeletionAction.PREPARE)
                                                .build()))) throw conflict();
        if (command.getAction() == SubjectDeletionAction.PREVIEW) return preview(command);
        if (epoch != null && "DELETED".equals(epoch.state())) {
            if (command.getAction() != SubjectDeletionAction.COMMIT) throw conflict();
            return receipt(
                    command,
                    epoch.digest(),
                    manifest(epoch.manifest()).getLessonsList(),
                    epoch.rows());
        }
        if (command.getAction() == SubjectDeletionAction.RELEASE) {
            if (epoch != null && "COMMITTING".equals(epoch.state())) throw conflict();
            if (epoch != null)
                jdbc.update(
                        "UPDATE schedule_subject_deletion_participants SET"
                                + " state='RELEASED',updated_at=NOW() WHERE operation_id=?",
                        UUID.fromString(command.getOperationId()));
            return receipt(command, command.getParticipantDigest(), command.getLessonsList(), 0);
        }
        if (epoch != null && "RELEASED".equals(epoch.state())) throw conflict();
        requireWritable(command.getSemesterId());
        SubjectDeletionReceipt current = preview(command);
        if (!current.getParticipantDigest().equals(command.getParticipantDigest())
                || !current.getLessonsList().equals(command.getLessonsList())) throw conflict();
        if (command.getAction() == SubjectDeletionAction.PREPARE) {
            if (epoch == null)
                jdbc.update(
                        """
INSERT INTO schedule_subject_deletion_participants(operation_id,subject_id,semester_id,group_id,actor_id,
    participant_digest,manifest,command_manifest,manifest_digest,state)
VALUES (?,?,?,?,?,?,?::JSONB,?,?,'PREPARED')
""",
                        UUID.fromString(command.getOperationId()),
                        command.getSubjectId(),
                        command.getSemesterId(),
                        command.getGroupId(),
                        command.getActorId(),
                        command.getParticipantDigest(),
                        rows(command.getSubjectId()),
                        command.toByteArray(),
                        command.getManifestDigest());
        } else if (command.getAction() == SubjectDeletionAction.SEAL) {
            if (epoch == null || !List.of("PREPARED", "SEALED").contains(epoch.state()))
                throw conflict();
            jdbc.update(
                    "UPDATE schedule_subject_deletion_participants SET"
                            + " state='SEALED',updated_at=NOW() WHERE operation_id=?",
                    UUID.fromString(command.getOperationId()));
        } else if (command.getAction() == SubjectDeletionAction.COMMIT) {
            if (epoch == null || !"SEALED".equals(epoch.state())) throw conflict();
            jdbc.update(
                    "UPDATE schedule_subject_deletion_participants SET state='COMMITTING' WHERE"
                            + " operation_id=?",
                    UUID.fromString(command.getOperationId()));
            jdbc.queryForObject(
                    "SELECT set_config('rutcampustrack.subject_delete_operation_id',?,TRUE)",
                    String.class,
                    command.getOperationId());
            jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
            jdbc.update(
                    """
UPDATE lesson_homework_bindings SET state='ARCHIVED',homework_id=NULL,revision=revision+1,updated_at=NOW()
WHERE subject_id=? AND (state<>'ARCHIVED' OR homework_id IS NOT NULL)
""",
                    command.getSubjectId());
            jdbc.update(
                    "UPDATE schedule_one_off_lessons SET physical_lesson_id=NULL WHERE subject_id=?"
                            + " AND physical_lesson_id IS NOT NULL",
                    command.getSubjectId());
            jdbc.update(
                    "DELETE FROM lesson_lifecycle_entries entry USING lesson_occurrences occurrence"
                        + " WHERE entry.occurrence_id=occurrence.id AND occurrence.subject_id=?",
                    command.getSubjectId());
            jdbc.update("DELETE FROM lessons WHERE subject_id=?", command.getSubjectId());
            jdbc.update(
                    "DELETE FROM lesson_occurrences WHERE subject_id=?", command.getSubjectId());
            jdbc.update("DELETE FROM schedule_items WHERE subject_id=?", command.getSubjectId());
            jdbc.update(
                    "DELETE FROM schedule_one_off_lessons WHERE subject_id=?",
                    command.getSubjectId());
            jdbc.update(
                    "UPDATE schedule_subject_deletion_participants SET"
                            + " state='DELETED',updated_at=NOW() WHERE operation_id=?",
                    UUID.fromString(command.getOperationId()));
            events.publishEvent(
                    new LessonDeletedEvent(
                            this,
                            command.getLessonsList().stream()
                                    .map(SubjectDeletionLesson::getLessonId)
                                    .toList(),
                            command.getSemesterId()));
        } else throw conflict();
        return receipt(
                command,
                current.getParticipantDigest(),
                current.getLessonsList(),
                current.getAffectedRows());
    }

    private SubjectDeletionReceipt preview(SubjectDeletionCommand c) {
        // This is a validation of all rows, not a filter that could hide a legacy foreign semester.
        Boolean invalid =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM (
    SELECT group_id,semester_id FROM schedule_items WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM schedule_one_off_lessons WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM lesson_occurrences WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM lessons WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM schedule_assignment_fences WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM lesson_homework_bindings WHERE subject_id=?
) scope WHERE group_id IS DISTINCT FROM ? OR semester_id IS DISTINCT FROM ?)
""",
                        Boolean.class,
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getGroupId(),
                        c.getSemesterId());
        if (Boolean.TRUE.equals(invalid)) throw conflict();
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM homework_placement_operations WHERE subject_id=? AND state NOT IN ('ACKNOWLEDGED','NOT_ACCEPTED'))
OR EXISTS(SELECT 1 FROM lesson_homework_bindings WHERE subject_id=? AND state='PENDING')
OR EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations WHERE subject_id=? AND state IN ('PREPARED','APPLIED'))
OR EXISTS(SELECT 1 FROM lesson_transfer_operations operation JOIN lesson_occurrences occurrence ON occurrence.id=operation.occurrence_id
    WHERE occurrence.subject_id=? AND operation.state<>'COMPLETED')
""",
                        Boolean.class,
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getSubjectId(),
                        c.getSubjectId()))) throw conflict();
        var lessons =
                jdbc.query(
                        """
SELECT lesson.id,lesson.occurrence_id,occurrence.current_lesson_id
FROM lessons lesson JOIN lesson_occurrences occurrence ON occurrence.id=lesson.occurrence_id
WHERE lesson.subject_id=? ORDER BY lesson.id
""",
                        (rs, i) ->
                                SubjectDeletionLesson.newBuilder()
                                        .setLessonId(rs.getLong(1))
                                        .setOccurrenceId(Long.toString(rs.getLong(2)))
                                        .setCurrentLessonId(rs.getLong(3))
                                        .setSubjectId(c.getSubjectId())
                                        .setSemesterId(c.getSemesterId())
                                        .setGroupId(c.getGroupId())
                                        .build(),
                        c.getSubjectId());
        String rows = rows(c.getSubjectId());
        Long count = jdbc.queryForObject("SELECT jsonb_array_length(?::JSONB)", Long.class, rows);
        return receipt(c, hash(rows), lessons, count == null ? 0 : count);
    }

    private String rows(long subject) {
        return jdbc.queryForObject(
                """
WITH domain_rows AS (
    SELECT 'schedule_items' AS kind,to_jsonb(entry) AS data FROM schedule_items entry WHERE subject_id=?
    UNION ALL SELECT 'schedule_one_off_lessons',to_jsonb(entry) FROM schedule_one_off_lessons entry WHERE subject_id=?
    UNION ALL SELECT 'lesson_occurrences',to_jsonb(entry) FROM lesson_occurrences entry WHERE subject_id=?
    UNION ALL SELECT 'lessons',to_jsonb(entry) FROM lessons entry WHERE subject_id=?
    UNION ALL SELECT 'lesson_lifecycle_entries',to_jsonb(entry) FROM lesson_lifecycle_entries entry
        JOIN lesson_occurrences occurrence ON occurrence.id=entry.occurrence_id WHERE occurrence.subject_id=?
    UNION ALL SELECT 'lesson_homework_bindings',to_jsonb(entry) FROM lesson_homework_bindings entry WHERE subject_id=?
    UNION ALL SELECT 'schedule_assignment_fences',to_jsonb(entry) FROM schedule_assignment_fences entry WHERE subject_id=?
    UNION ALL SELECT kind,data FROM schedule_subject_retained_rows(?)
) SELECT coalesce(jsonb_agg((jsonb_build_object('kind',kind,'row_hash',encode(public.digest(convert_to(data::TEXT,'UTF8'),'sha256'),'hex')) || CASE WHEN kind='schedule_one_off_lessons' THEN jsonb_build_object('detached_hash',encode(public.digest(convert_to((data || jsonb_build_object('physical_lesson_id',NULL))::TEXT,'UTF8'),'sha256'),'hex')) ELSE '{}'::JSONB END) ORDER BY kind,data::TEXT),'[]'::JSONB)::TEXT FROM domain_rows
""",
                String.class,
                subject,
                subject,
                subject,
                subject,
                subject,
                subject,
                subject,
                subject);
    }

    private void requireWritable(long semester) {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM schedule_semester_archive_barriers WHERE"
                                + " semester_id=? AND participant_state<>'RELEASED')",
                        Boolean.class,
                        semester))) throw conflict();
    }

    private static SubjectDeletionReceipt receipt(
            SubjectDeletionCommand c,
            String digest,
            List<SubjectDeletionLesson> lessons,
            long rows) {
        return SubjectDeletionReceipt.newBuilder()
                .setOperationId(c.getOperationId())
                .setSubjectId(c.getSubjectId())
                .setSemesterId(c.getSemesterId())
                .setGroupId(c.getGroupId())
                .setParticipantDigest(digest)
                .setAction(c.getAction())
                .setManifestDigest(c.getManifestDigest())
                .setActorId(c.getActorId())
                .addAllLessons(lessons)
                .setAffectedRows(rows)
                .build();
    }

    private static void validate(SubjectDeletionCommand c) {
        if (c.getSubjectId() <= 0
                || c.getSemesterId() <= 0
                || c.getGroupId() <= 0
                || c.getActorId() <= 0) throw conflict();
        UUID.fromString(c.getOperationId());
        if (c.getAction() == SubjectDeletionAction.UNRECOGNIZED
                || c.getAction() == SubjectDeletionAction.SUBJECT_DELETION_ACTION_UNSPECIFIED)
            throw conflict();
        if (c.getAction() != SubjectDeletionAction.PREVIEW
                && (!c.getParticipantDigest().matches("[0-9a-f]{64}")
                        || !c.getManifestDigest().matches("[0-9a-f]{64}"))) throw conflict();
    }

    private static SubjectDeletionCommand manifest(byte[] bytes) {
        try {
            return SubjectDeletionCommand.parseFrom(bytes);
        } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
            throw conflict();
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static IllegalStateException conflict() {
        return new IllegalStateException("Subject deletion snapshot or phase conflict");
    }

    private record Epoch(
            long subject,
            long semester,
            long group,
            long actor,
            String digest,
            String manifestDigest,
            String state,
            byte[] manifest,
            long rows) {}
}
