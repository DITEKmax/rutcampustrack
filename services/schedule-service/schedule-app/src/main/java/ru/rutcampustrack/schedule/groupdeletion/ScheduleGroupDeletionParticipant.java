package ru.rutcampustrack.schedule.groupdeletion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.groupdeletion.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Short local transactions; remote calls and current actor admission remain with the coordinator.
 */
@Service
public class ScheduleGroupDeletionParticipant {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ScheduleGroupDeletionParticipant(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public GroupDeletionReceipt apply(GroupDeletionCommand command) {
        validate(command);
        UUID operation = UUID.fromString(command.getOperationId());

        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(4670544,?)",
                (int) (command.getGroupId() % 2147483647));
        var saved =
                jdbc.queryForList(
                        "SELECT * FROM group_deletion_participants WHERE operation_id=? FOR UPDATE",
                        operation);
        Map<String, Object> epoch = saved.isEmpty() ? null : saved.getFirst();
        if (epoch != null
                && ((Long) epoch.get("group_id") != command.getGroupId()
                        || (Long) epoch.get("actor_id") != command.getActorId()
                        || !epoch.get("participant_digest").equals(command.getParticipantDigest())
                        || !epoch.get("preview_digest").equals(command.getPreviewDigest())
                        || !java.util.Arrays.equals(
                                (byte[]) epoch.get("command_manifest"),
                                command.toBuilder()
                                        .setAction(GroupDeletionAction.PREPARE)
                                        .build()
                                        .toByteArray()))) throw conflict();
        if (command.getAction() == GroupDeletionAction.PREVIEW) return snapshot(command);
        if (epoch != null && "DELETED".equals(epoch.get("state"))) {
            if (command.getAction() != GroupDeletionAction.COMMIT) throw conflict();
            return stored(command, epoch);
        }
        if (command.getAction() == GroupDeletionAction.RELEASE) {
            if (epoch != null && "COMMITTING".equals(epoch.get("state"))) throw conflict();
            if (epoch != null && "RELEASED".equals(epoch.get("state")))
                return stored(command, epoch);
            if (epoch == null) insert(command, "[]", "{}", "RELEASED");
            else
                jdbc.update(
                        "UPDATE group_deletion_participants SET state='RELEASED' WHERE"
                                + " operation_id=?",
                        operation);
            return epoch == null
                    ? GroupDeletionReceipt.newBuilder()
                            .setCommand(command)
                            .setParticipantDigest(command.getParticipantDigest())
                            .addAllLessons(command.getLessonsList())
                            .build()
                    : stored(command, epoch);
        }
        if (epoch != null && "RELEASED".equals(epoch.get("state"))) throw conflict();
        if (epoch != null && command.getAction() == GroupDeletionAction.PREPARE)
            return stored(command, epoch);
        var current = snapshot(command);
        if (!current.getParticipantDigest().equals(command.getParticipantDigest())
                || !current.getLessonsList().equals(command.getLessonsList())) throw conflict();
        if (command.getAction() == GroupDeletionAction.PREPARE) {
            if (epoch == null)
                insert(
                        command,
                        manifest(command.getGroupId()),
                        json(current.getCountsMap()),
                        "PREPARED");
        } else if (command.getAction() == GroupDeletionAction.SEAL) {
            if (epoch == null || !List.of("PREPARED", "SEALED").contains(epoch.get("state")))
                throw conflict();
            jdbc.update(
                    "UPDATE group_deletion_participants SET state='SEALED' WHERE operation_id=?",
                    operation);
        } else if (command.getAction() == GroupDeletionAction.COMMIT) {
            if (epoch == null || !"SEALED".equals(epoch.get("state"))) throw conflict();
            jdbc.update(
                    "UPDATE group_deletion_participants SET state='COMMITTING' WHERE"
                            + " operation_id=?",
                    operation);
            jdbc.queryForObject(
                    "SELECT set_config('rutcampustrack.group_delete_operation_id',?,true)",
                    String.class,
                    operation.toString());
            jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
            for (String table :
                    List.of(
                            "lesson_lifecycle_entries",
                            "lesson_homework_bindings",
                            "lessons",
                            "lesson_occurrences",
                            "schedule_one_off_lessons",
                            "schedule_items",
                            "schedule_assignment_fences"))
                jdbc.update(
                        "DELETE FROM "
                                + table
                                + " t WHERE group_deletion_row_authorized(?,to_jsonb(t))",
                        table);
            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
            jdbc.update(
                    "UPDATE group_deletion_participants SET state='DELETED' WHERE operation_id=?",
                    operation);
        } else throw conflict();
        return current;
    }

    private GroupDeletionReceipt snapshot(GroupDeletionCommand c) {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM schedule_subject_deletion_participants WHERE group_id=? AND state NOT IN ('DELETED','RELEASED'))
 OR EXISTS(SELECT 1 FROM homework_placement_operations WHERE group_id=? AND state NOT IN ('ACKNOWLEDGED','NOT_ACCEPTED'))
 OR EXISTS(SELECT 1 FROM lesson_homework_bindings WHERE group_id=? AND state='PENDING')
 OR EXISTS(SELECT 1 FROM lesson_transfer_operations o JOIN lesson_occurrences l ON l.id=o.occurrence_id WHERE l.group_id=? AND o.state<>'COMPLETED')
""",
                        Boolean.class,
                        c.getGroupId(),
                        c.getGroupId(),
                        c.getGroupId(),
                        c.getGroupId()))) throw conflict();
        Map<String, Long> counts = new TreeMap<>();
        jdbc.query(
                "SELECT kind,count(*) FROM group_deletion_rows(?) GROUP BY kind",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                },
                c.getGroupId());
        return GroupDeletionReceipt.newBuilder()
                .setCommand(c)
                .setParticipantDigest(hash(manifest(c.getGroupId())))
                .putAllCounts(counts)
                .addAllLessons(
                        jdbc.query(
                                "SELECT id,semester_id FROM lessons WHERE group_id=? ORDER BY id",
                                (rs, i) ->
                                        GroupDeletionLesson.newBuilder()
                                                .setLessonId(rs.getLong(1))
                                                .setSemesterId(rs.getLong(2))
                                                .build(),
                                c.getGroupId()))
                .build();
    }

    private String manifest(long group) {
        return jdbc.queryForObject("SELECT group_deletion_manifest(?)::text", String.class, group);
    }

    private void insert(GroupDeletionCommand c, String manifest, String counts, String state) {
        jdbc.update(
                "INSERT INTO"
                    + " group_deletion_participants(operation_id,group_id,actor_id,participant_digest,preview_digest,manifest,counts,state,command_manifest)"
                    + " VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
                UUID.fromString(c.getOperationId()),
                c.getGroupId(),
                c.getActorId(),
                c.getParticipantDigest(),
                c.getPreviewDigest(),
                manifest,
                counts,
                state,
                c.toBuilder().setAction(GroupDeletionAction.PREPARE).build().toByteArray());
    }

    private GroupDeletionReceipt stored(GroupDeletionCommand c, Map<String, Object> e) {
        try {
            return GroupDeletionReceipt.newBuilder()
                    .setCommand(c)
                    .setParticipantDigest((String) e.get("participant_digest"))
                    .putAllCounts(
                            mapper.readValue(
                                    e.get("counts").toString(),
                                    new TypeReference<Map<String, Long>>() {}))
                    .addAllLessons(c.getLessonsList())
                    .build();
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validate(GroupDeletionCommand c) {
        if (c.getGroupId() <= 0
                || c.getActorId() <= 0
                || !c.getUnknownFields().asMap().isEmpty()
                || c.getAction() == GroupDeletionAction.UNRECOGNIZED
                || c.getAction() == GroupDeletionAction.GROUP_DELETION_ACTION_UNSPECIFIED)
            throw conflict();
        if (!UUID.fromString(c.getOperationId()).toString().equals(c.getOperationId()))
            throw conflict();
        if (c.getAction() != GroupDeletionAction.PREVIEW
                && (!c.getParticipantDigest().matches("[0-9a-f]{64}")
                        || !c.getPreviewDigest().matches("[0-9a-f]{64}"))) throw conflict();
    }

    private static RuntimeException conflict() {
        return io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Group deletion exact scope or phase changed")
                .asRuntimeException();
    }
}
