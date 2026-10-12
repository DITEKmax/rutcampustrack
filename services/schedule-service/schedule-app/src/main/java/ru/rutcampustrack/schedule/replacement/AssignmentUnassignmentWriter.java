package ru.rutcampustrack.schedule.replacement;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.PreparedAssignmentCloseResponse;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;
import ru.rutcampustrack.schedule.grpc.CommitAssignmentCloseRequest;
import ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

/** The no-target branch retains physical identities and changes only their current binding. */
@Service
public class AssignmentUnassignmentWriter {
    private final JdbcTemplate jdbc;
    private final AcademicGrpcClient academic;
    private final ScheduleSemesterArchiveWriteFence archive;
    private final TransactionTemplate transaction;

    public AssignmentUnassignmentWriter(
            JdbcTemplate jdbc,
            AcademicGrpcClient academic,
            ScheduleSemesterArchiveWriteFence archive,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.academic = academic;
        this.archive = archive;
        this.transaction = new TransactionTemplate(manager);
    }

    public AssignmentCloseReceipt install(InstallAssignmentCloseCapRequest request) {
        UUID id = UUID.fromString(request.getOperationId());
        Long source = request.hasSourceAssignmentId() ? request.getSourceAssignmentId() : null;
        PreparedAssignmentCloseResponse authority =
                academic.getPreparedAssignmentLifecycle(id, source);
        verify(authority, id, request.getPayloadHash().toByteArray());
        if (!authority.getOperationKind().equals(request.getOperationKind())
                || request.hasSourceAssignmentId() != authority.hasSourceAssignmentId()
                || source != null && source != authority.getSourceAssignmentId()
                || request.hasTargetAssignmentId() != authority.hasTargetAssignmentId()
                || request.hasTargetAssignmentId()
                        && request.getTargetAssignmentId() != authority.getTargetAssignmentId()
                || !request.getEffectiveFrom().equals(authority.getEffectiveFrom())) conflict();
        var write = archive.prepareBusinessWrite(authority.getSemesterId());
        return transaction.execute(tx -> installLocal(authority, write));
    }

    public AssignmentCloseReceipt commit(CommitAssignmentCloseRequest request) {
        UUID id = UUID.fromString(request.getOperationId());
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT source_assignment_id FROM schedule_assignment_replacement_operations WHERE operation_id = ?",
                        id);
        if (rows.isEmpty()) throw new ConflictException("Барьер назначения не установлен");
        PreparedAssignmentCloseResponse authority =
                academic.getPreparedAssignmentLifecycle(
                        id, nullable(rows.get(0).get("source_assignment_id")));
        verify(authority, id, request.getPayloadHash().toByteArray());
        var write = archive.prepareBusinessWrite(authority.getSemesterId());
        return transaction.execute(tx -> commitLocal(authority, write));
    }

    private AssignmentCloseReceipt installLocal(
            PreparedAssignmentCloseResponse authority,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite write) {
        lockScope(authority, write);
        UUID id = UUID.fromString(authority.getOperationId());
        jdbc.update(
                """
                INSERT INTO schedule_assignment_replacement_operations
                    (operation_id,payload_hash,source_assignment_id,target_assignment_id,source_teacher_id,target_teacher_id,
                     group_id,subject_id,semester_id,lesson_type,source_valid_from,valid_until_exclusive,effective_from,
                     state,operation_kind,requested_cutoff)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'PREPARED',?,?) ON CONFLICT(operation_id) DO NOTHING
                """,
                id,
                authority.getPayloadHash().toByteArray(),
                source(authority),
                target(authority),
                authority.hasSourceTeacherId() ? authority.getSourceTeacherId() : null,
                authority.hasTargetTeacherId() ? authority.getTargetTeacherId() : null,
                authority.getGroupId(),
                authority.getSubjectId(),
                authority.getSemesterId(),
                authority.getLessonType(),
                LocalDate.parse(authority.getSourceValidFrom()),
                LocalDate.parse(authority.getTargetValidUntilExclusive()),
                LocalDate.parse(authority.getEffectiveFrom()),
                authority.getOperationKind(),
                LocalDate.parse(authority.getRequestedCutoff()));
        Map<String, Object> row = lockOperation(authority);
        if (!"PREPARED".equals(row.get("state"))) return receipt(row);
        if (!"PREPARED".equals(authority.getState())) conflict();
        lockFence(authority);
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.schedule_assignment_rebind_operation_id', ?, true)",
                String.class,
                id.toString());
        if (source(authority) != null) {
            jdbc.update(
                    "UPDATE schedule_assignment_fences SET creation_cap_until_exclusive = ?, revision = revision + 1 WHERE assignment_id = ? AND creation_cap_until_exclusive > ?",
                    LocalDate.parse(authority.getEffectiveFrom()),
                    source(authority),
                    LocalDate.parse(authority.getEffectiveFrom()));
            List<Map<String, Object>> items =
                    jdbc.queryForList(
                            """
                    SELECT id,lifecycle_revision FROM schedule_items
                     WHERE assignment_id=? AND group_id=? AND subject_id=? AND semester_id=? AND is_active
                     ORDER BY id FOR UPDATE
                    """,
                            source(authority),
                            authority.getGroupId(),
                            authority.getSubjectId(),
                            authority.getSemesterId());
            for (Map<String, Object> item : items) {
                long revision = number(item.get("lifecycle_revision"));
                jdbc.update(
                        """
                        INSERT INTO schedule_assignment_template_close_ledger
                         (operation_id,semester_id,schedule_item_id,expected_revision,after_revision)
                        VALUES (?,?,?,?,?)
                        """,
                        id,
                        authority.getSemesterId(),
                        item.get("id"),
                        revision,
                        revision + 1);
                if (jdbc.update(
                                """
                        UPDATE schedule_items SET is_active=false,lifecycle_revision=lifecycle_revision+1,deactivated_at=NOW()
                         WHERE id=? AND lifecycle_revision=? AND is_active
                        """,
                                item.get("id"),
                                revision)
                        != 1) conflict();
            }
        }
        jdbc.update(
                "UPDATE schedule_assignment_replacement_operations SET state='APPLIED', updated_at=NOW() WHERE operation_id=? AND state='PREPARED'",
                id);
        return AssignmentCloseReceipt.newBuilder()
                .setOperationId(id.toString())
                .setState("APPLIED")
                .build();
    }

    private AssignmentCloseReceipt commitLocal(
            PreparedAssignmentCloseResponse authority,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite write) {
        lockScope(authority, write);
        Map<String, Object> operation = lockOperation(authority);
        if ("COMMITTED".equals(operation.get("state"))) return receipt(operation);
        if (!"APPLIED".equals(operation.get("state"))
                || !("APPLIED".equals(authority.getState())
                        || "COMMITTED".equals(authority.getState()))) conflict();
        boolean close = source(authority) != null;
        lockFence(authority);
        UUID id = UUID.fromString(authority.getOperationId());
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.assignment_binding_operation_id', ?, true)",
                String.class,
                id.toString());
        List<Map<String, Object>> occurrences =
                jdbc.queryForList(
                        """
                SELECT occurrence.id, occurrence.revision, occurrence.current_lesson_id,
                       occurrence.assignment_id, occurrence.assigned_teacher_id, occurrence.occurrence_date
                  FROM lesson_occurrences occurrence
                 WHERE occurrence.group_id=? AND occurrence.subject_id=? AND occurrence.semester_id=? AND occurrence.lesson_type=?
                   AND occurrence.occurrence_date>=? AND occurrence.occurrence_date<?
                   AND ((? AND occurrence.assignment_id=?) OR (NOT ? AND occurrence.assignment_id IS NULL
                        AND occurrence.assigned_teacher_id IS NULL AND EXISTS (
                         SELECT 1 FROM schedule_assignment_binding_ledger ledger
                         JOIN schedule_assignment_replacement_operations closure ON closure.operation_id=ledger.operation_id
                         WHERE ledger.occurrence_id=occurrence.id AND ledger.after_assignment_id IS NULL
                           AND closure.operation_kind='CLOSE_UNASSIGN' AND closure.state='COMMITTED')))
                 ORDER BY occurrence.id FOR UPDATE OF occurrence
                """,
                        authority.getGroupId(),
                        authority.getSubjectId(),
                        authority.getSemesterId(),
                        authority.getLessonType(),
                        LocalDate.parse(authority.getRequestedCutoff()),
                        LocalDate.parse(authority.getTargetValidUntilExclusive()),
                        close,
                        source(authority),
                        close);
        long changed = 0;
        long held = 0;
        for (Map<String, Object> occurrence : occurrences) {
            Long lessonId = nullable(occurrence.get("current_lesson_id"));
            Map<String, Object> lesson =
                    lessonId == null
                            ? null
                            : jdbc.queryForMap(
                                    "SELECT * FROM lessons WHERE id=? FOR UPDATE", lessonId);
            Boolean eligible =
                    lesson == null
                            ? jdbc.queryForObject(
                                    "SELECT ?::date > (NOW() AT TIME ZONE 'Europe/Moscow')::date",
                                    Boolean.class,
                                    occurrence.get("occurrence_date"))
                            : jdbc.queryForObject(
                                    "SELECT status::text NOT IN ('active','closed') AND (date + start_time) AT TIME ZONE 'Europe/Moscow' > NOW() FROM lessons WHERE id=?",
                                    Boolean.class,
                                    lessonId);
            if (!Boolean.TRUE.equals(eligible)) {
                held++;
                continue;
            }
            long revision = number(occurrence.get("revision"));
            Long lessonRevision = lesson == null ? null : number(lesson.get("revision"));
            Long afterAssignment = close ? null : target(authority);
            Long afterTeacher = close ? null : authority.getTargetTeacherId();
            jdbc.update(
                    """
                    INSERT INTO schedule_assignment_binding_ledger
                      (operation_id,semester_id,occurrence_id,lesson_id,occurrence_date,expected_occurrence_revision,after_occurrence_revision,
                       expected_lesson_revision,after_lesson_revision,before_assignment_id,before_teacher_id,after_assignment_id,after_teacher_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    id,
                    authority.getSemesterId(),
                    number(occurrence.get("id")),
                    lessonId,
                    occurrence.get("occurrence_date"),
                    revision,
                    revision + 1,
                    lessonRevision,
                    lessonRevision == null ? null : lessonRevision + 1,
                    occurrence.get("assignment_id"),
                    occurrence.get("assigned_teacher_id"),
                    afterAssignment,
                    afterTeacher);
            if (jdbc.update(
                            "UPDATE lesson_occurrences SET assignment_id=?,assigned_teacher_id=?,revision=revision+1 WHERE id=? AND revision=?",
                            afterAssignment,
                            afterTeacher,
                            occurrence.get("id"),
                            revision)
                    != 1) conflict();
            if (lessonId != null
                    && jdbc.update(
                                    "UPDATE lessons SET assignment_id=?,assigned_teacher_id=?,revision=revision+1 WHERE id=? AND revision=?",
                                    afterAssignment,
                                    afterTeacher,
                                    lessonId,
                                    lessonRevision)
                            != 1) conflict();
            changed++;
        }
        jdbc.update(
                "UPDATE schedule_assignment_replacement_operations SET state='COMMITTED',moved_count=?,skipped_count=?,updated_at=NOW() WHERE operation_id=? AND state='APPLIED'",
                changed,
                held,
                id);
        return AssignmentCloseReceipt.newBuilder()
                .setOperationId(id.toString())
                .setState("COMMITTED")
                .setMovedCount(changed)
                .setSkippedCount(held)
                .build();
    }

    private void lockScope(
            PreparedAssignmentCloseResponse authority,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite write) {
        archive.lockPreparedBusinessWrite(write);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 549281))",
                Object.class,
                authority.getGroupId()
                        + "|"
                        + authority.getSubjectId()
                        + "|"
                        + authority.getSemesterId()
                        + "|"
                        + authority.getLessonType());
    }

    private void lockFence(PreparedAssignmentCloseResponse authority) {
        Long assignment = source(authority) != null ? source(authority) : target(authority);
        long teacher =
                source(authority) != null
                        ? authority.getSourceTeacherId()
                        : authority.getTargetTeacherId();
        jdbc.update(
                """
                INSERT INTO schedule_assignment_fences(assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,lesson_type,valid_from,cap_until_exclusive,creation_cap_until_exclusive)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(assignment_id) DO NOTHING
                """,
                assignment,
                authority.getGroupId(),
                authority.getSubjectId(),
                authority.getSemesterId(),
                teacher,
                authority.getLessonType(),
                LocalDate.parse(authority.getSourceValidFrom()),
                LocalDate.parse(authority.getTargetValidUntilExclusive()),
                LocalDate.parse(authority.getTargetValidUntilExclusive()));
        Map<String, Object> fence =
                jdbc.queryForMap(
                        "SELECT * FROM schedule_assignment_fences WHERE assignment_id=? FOR UPDATE",
                        assignment);
        if (number(fence.get("group_id")) != authority.getGroupId()
                || number(fence.get("subject_id")) != authority.getSubjectId()
                || number(fence.get("semester_id")) != authority.getSemesterId()
                || number(fence.get("assigned_teacher_id")) != teacher
                || !Objects.equals(fence.get("lesson_type"), authority.getLessonType())
                || !Objects.equals(
                        fence.get("valid_from"),
                        java.sql.Date.valueOf(authority.getSourceValidFrom()))) conflict();
        LocalDate retained = ((java.sql.Date) fence.get("cap_until_exclusive")).toLocalDate();
        LocalDate end = LocalDate.parse(authority.getTargetValidUntilExclusive());
        if (!retained.equals(end)) {
            if (source(authority) == null || retained.isBefore(end)) conflict();
            Boolean priorClose =
                    jdbc.queryForObject(
                            """
                    SELECT EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations operation
                     WHERE operation.source_assignment_id=? AND operation.state='COMMITTED'
                       AND operation.operation_kind IN ('REPLACE','CLOSE_UNASSIGN')
                       AND operation.effective_from=? AND operation.group_id=? AND operation.subject_id=?
                       AND operation.semester_id=? AND operation.lesson_type=? AND operation.source_teacher_id=?)
                    """,
                            Boolean.class,
                            assignment,
                            end,
                            authority.getGroupId(),
                            authority.getSubjectId(),
                            authority.getSemesterId(),
                            authority.getLessonType(),
                            teacher);
            if (!Boolean.TRUE.equals(priorClose)) conflict();
        }
    }

    private Map<String, Object> lockOperation(PreparedAssignmentCloseResponse authority) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT * FROM schedule_assignment_replacement_operations WHERE operation_id=? FOR UPDATE",
                        UUID.fromString(authority.getOperationId()));
        if (!MessageDigest.isEqual(
                        (byte[]) row.get("payload_hash"), authority.getPayloadHash().toByteArray())
                || !Objects.equals(nullable(row.get("source_assignment_id")), source(authority))
                || !Objects.equals(nullable(row.get("target_assignment_id")), target(authority))
                || !Objects.equals(
                        nullable(row.get("source_teacher_id")),
                        authority.hasSourceTeacherId() ? authority.getSourceTeacherId() : null)
                || !Objects.equals(
                        nullable(row.get("target_teacher_id")),
                        authority.hasTargetTeacherId() ? authority.getTargetTeacherId() : null)
                || !Objects.equals(
                        row.get("source_valid_from"),
                        java.sql.Date.valueOf(authority.getSourceValidFrom()))
                || !Objects.equals(
                        row.get("valid_until_exclusive"),
                        java.sql.Date.valueOf(authority.getTargetValidUntilExclusive()))
                || !Objects.equals(row.get("operation_kind"), authority.getOperationKind())
                || !Objects.equals(
                        row.get("effective_from"),
                        java.sql.Date.valueOf(authority.getEffectiveFrom()))
                || !Objects.equals(
                        row.get("requested_cutoff"),
                        java.sql.Date.valueOf(authority.getRequestedCutoff()))
                || number(row.get("group_id")) != authority.getGroupId()
                || number(row.get("subject_id")) != authority.getSubjectId()
                || number(row.get("semester_id")) != authority.getSemesterId()
                || !Objects.equals(row.get("lesson_type"), authority.getLessonType())) conflict();
        return row;
    }

    private static void verify(PreparedAssignmentCloseResponse authority, UUID id, byte[] hash) {
        if (authority == null) conflict();
        boolean close = "CLOSE_UNASSIGN".equals(authority.getOperationKind());
        if (!(close || "ADOPT_UNASSIGNED".equals(authority.getOperationKind()))
                || authority.hasSourceAssignmentId() != close
                || authority.hasSourceTeacherId() != close
                || authority.hasTargetAssignmentId() == close
                || authority.hasTargetTeacherId() == close
                || close
                        && (authority.getSourceAssignmentId() <= 0
                                || authority.getSourceTeacherId() <= 0)
                || !close
                        && (authority.getTargetAssignmentId() <= 0
                                || authority.getTargetTeacherId() <= 0)
                || !id.toString().equals(authority.getOperationId())
                || hash.length != 32
                || !MessageDigest.isEqual(hash, authority.getPayloadHash().toByteArray())
                || authority.getGroupId() <= 0
                || authority.getSubjectId() <= 0
                || authority.getSemesterId() <= 0
                || !List.of("lecture", "practice", "lab").contains(authority.getLessonType()))
            conflict();
        LocalDate from = LocalDate.parse(authority.getSourceValidFrom());
        LocalDate end = LocalDate.parse(authority.getTargetValidUntilExclusive());
        LocalDate cutoff = LocalDate.parse(authority.getRequestedCutoff());
        LocalDate effective = LocalDate.parse(authority.getEffectiveFrom());
        if (!from.isBefore(end)
                || effective.isBefore(from)
                || effective.isAfter(end)
                || close && !effective.equals(cutoff.isBefore(from) ? from : cutoff)
                || !close && (!effective.equals(from) || !cutoff.equals(from))) conflict();
    }

    private static Long source(PreparedAssignmentCloseResponse a) {
        return a.hasSourceAssignmentId() ? a.getSourceAssignmentId() : null;
    }

    private static Long target(PreparedAssignmentCloseResponse a) {
        return a.hasTargetAssignmentId() ? a.getTargetAssignmentId() : null;
    }

    private static Long nullable(Object value) {
        return value == null ? null : number(value);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static void conflict() {
        throw new ConflictException("Назначение изменилось; обнови данные");
    }

    private static AssignmentCloseReceipt receipt(Map<String, Object> row) {
        return AssignmentCloseReceipt.newBuilder()
                .setOperationId(row.get("operation_id").toString())
                .setState(row.get("state").toString())
                .setMovedCount(number(row.get("moved_count")))
                .setSkippedCount(number(row.get("skipped_count")))
                .build();
    }
}
