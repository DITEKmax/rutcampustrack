package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.Phase;
import ru.rutcampustrack.academic.event.SubjectDeletedEvent;
import ru.rutcampustrack.subjectdeletion.grpc.SubjectDeletionAction;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Every phase transition is a short local transaction; no RPC runs while PG locks are held. */
@Service
public class SubjectDeletionCommands {
    private final JdbcTemplate jdbc;
    private final SubjectDeletionAcademicData data;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubjectDeletionCommands(
            JdbcTemplate jdbc,
            SubjectDeletionAcademicData data,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.data = data;
        this.events = events;
        this.clock = clock;
    }

    public SubjectDeletionOperation get(UUID operation) {
        var rows =
                jdbc.query(
                        "SELECT * FROM subject_deletion_operations WHERE operation_id=?",
                        (rs, i) ->
                                new SubjectDeletionOperation(
                                        rs.getObject("operation_id", UUID.class),
                                        rs.getObject("idempotency_key", UUID.class),
                                        rs.getLong("subject_id"),
                                        rs.getLong("semester_id"),
                                        rs.getLong("group_id"),
                                        rs.getLong("actor_id"),
                                        rs.getObject("session_id", UUID.class),
                                        rs.getString("preview_digest"),
                                        rs.getString("confirmation_hash"),
                                        rs.getString("academic_digest"),
                                        rs.getString("schedule_digest"),
                                        rs.getString("attendance_digest"),
                                        rs.getBytes("manifest"),
                                        Phase.valueOf(rs.getString("phase")),
                                        rs.getString("schedule_state"),
                                        rs.getString("attendance_state"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getString("last_failure"),
                                        rs.getObject("proof_receipt_id", UUID.class)),
                        operation);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public UUID byKey(UUID key) {
        var rows =
                jdbc.query(
                        "SELECT operation_id FROM subject_deletion_operations WHERE"
                                + " idempotency_key=?",
                        (rs, i) -> rs.getObject(1, UUID.class),
                        key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<UUID> pending() {
        return jdbc.query(
                "SELECT operation_id FROM subject_deletion_operations WHERE phase NOT IN"
                        + " ('COMPLETED','CANCELLED') ORDER BY created_at LIMIT 20",
                (rs, i) -> rs.getObject(1, UUID.class));
    }

    @Transactional
    public void accept(
            UUID operation,
            UUID key,
            AuthSubjectDeletionClient.Admission admission,
            String confirmationHash,
            SubjectDeletionSnapshot snapshot) {
        UUID proof = admission.proof();
        // Same global state-transition lock as SemesterService, BEFORE accepting a durable fence.
        // Once inserted, the pending operation blocks activation/archive until completion/release.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?, ?)", 0x53454D, 1);
        var target = snapshot.target();
        var actor = target.actor();
        lockActor(actor.userId(), target.group());
        var accepted = get(operation);
        UUID keyed = byKey(key);
        if (accepted != null || keyed != null) {
            if (accepted != null
                    && accepted.id().equals(keyed)
                    && accepted.key().equals(key)
                    && accepted.subject() == target.subject()
                    && accepted.semester() == target.semester()
                    && accepted.group() == target.group()
                    && accepted.actor() == actor.userId()
                    && accepted.sid().equals(actor.sessionId())
                    && accepted.proof().equals(proof)
                    && accepted.confirmationHash().equals(confirmationHash)) return;
            throw SubjectDeletionAcademicData.conflict();
        }
        lockTarget(target.subject(), target.semester());
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM subject_deletion_operations WHERE subject_id=?"
                                + " AND phase NOT IN ('COMPLETED','CANCELLED'))",
                        Boolean.class,
                        target.subject()))) throw SubjectDeletionAcademicData.conflict();
        if (!admission.matches(
                        proof,
                        operation,
                        snapshot.digest(),
                        target.subject(),
                        target.semester(),
                        target.group(),
                        actor.userId(),
                        actor.sessionId(),
                        clock.instant())
                || !data.snapshot(target).digest().equals(snapshot.academic().digest()))
            throw SubjectDeletionAcademicData.conflict();
        jdbc.update(
                """
INSERT INTO subject_deletion_admission_receipts(receipt_id,operation_id,actor_id,session_id,
    subject_id,semester_id,group_id,preview_digest,checked_at) VALUES (?,?,?,?,?,?,?,?,?)
""",
                proof,
                operation,
                actor.userId(),
                actor.sessionId(),
                target.subject(),
                target.semester(),
                target.group(),
                snapshot.digest(),
                Timestamp.from(admission.checkedAt()));
        var command =
                snapshot.command(
                        operation,
                        snapshot.schedule().getParticipantDigest(),
                        SubjectDeletionAction.PREPARE);
        jdbc.update(
                """
INSERT INTO subject_deletion_operations(operation_id,idempotency_key,subject_id,semester_id,group_id,actor_id,session_id,
    preview_digest,confirmation_hash,academic_digest,academic_manifest,schedule_digest,attendance_digest,manifest,phase,created_at,updated_at,proof_receipt_id)
VALUES (?,?,?,?,?,?,?,?,?,?,?::JSONB,?,?,?,'PREPARING',?,?,?)
""",
                operation,
                key,
                target.subject(),
                target.semester(),
                target.group(),
                actor.userId(),
                actor.sessionId(),
                snapshot.digest(),
                confirmationHash,
                snapshot.academic().digest(),
                snapshot.academic().manifest(),
                snapshot.schedule().getParticipantDigest(),
                snapshot.attendance().getParticipantDigest(),
                command.toByteArray(),
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()),
                proof);
    }

    @Transactional
    public void participant(UUID operation, boolean schedule, String state) {
        SubjectDeletionOperation current = lockOperation(operation);
        if (current == null
                || current.phase() == Phase.COMPLETED
                || current.phase() == Phase.CANCELLED) return;
        String previous = schedule ? current.scheduleState() : current.attendanceState();
        if (previous.equals("DELETED") || previous.equals("RELEASED")) return;
        if (state.equals("RELEASED") && current.phase() != Phase.RELEASING)
            throw SubjectDeletionAcademicData.conflict();
        if (!state.equals("RELEASED") && rank(state) < rank(previous)) return;
        String column = schedule ? "schedule_state" : "attendance_state";
        jdbc.update(
                "UPDATE subject_deletion_operations SET "
                        + column
                        + "=?,updated_at=?,last_failure=NULL WHERE operation_id=?",
                state,
                Timestamp.from(clock.instant()),
                operation);
    }

    @Transactional
    public boolean beginCommit(UUID operation, AuthSubjectDeletionClient.Admission admission) {
        // Local group -> profile -> semester -> subject -> operation order matches acceptance.
        SubjectDeletionOperation before = get(operation);
        if (before == null) return false;
        lockActor(before.actor(), before.group());
        lockTarget(before.subject(), before.semester());
        SubjectDeletionOperation current = lockOperation(operation);
        if (current.phase() == Phase.COMMITTING || current.phase() == Phase.COMPLETED) return true;
        if (current.phase() != Phase.PREPARING && current.phase() != Phase.SEALED) return false;
        if (!current.scheduleState().equals("SEALED")
                || !current.attendanceState().equals("SEALED")) return false;
        if (admission == null
                || !admission.matches(
                        current.proof(),
                        current.id(),
                        current.digest(),
                        current.subject(),
                        current.semester(),
                        current.group(),
                        current.actor(),
                        current.sid(),
                        clock.instant())
                || !validProof(
                        current.proof(),
                        current.id(),
                        current.digest(),
                        current.subject(),
                        current.semester(),
                        current.group(),
                        current.actor(),
                        current.sid())) return false;
        var target = target(current);
        if (!data.snapshot(target).digest().equals(current.academicDigest())) return false;
        jdbc.update(
                "UPDATE subject_deletion_operations SET"
                        + " phase='COMMITTING',last_failure=NULL,updated_at=? WHERE operation_id=?",
                Timestamp.from(clock.instant()),
                operation);
        return true;
    }

    @Transactional
    public void release(UUID operation, String reason) {
        var current = lockOperation(operation);
        if (current == null
                || current.phase() == Phase.COMMITTING
                || current.phase() == Phase.COMPLETED
                || current.phase() == Phase.CANCELLED) return;
        jdbc.update(
                "UPDATE subject_deletion_operations SET"
                        + " phase='RELEASING',last_failure=?,updated_at=? WHERE operation_id=?",
                reason,
                Timestamp.from(clock.instant()),
                operation);
    }

    @Transactional
    public void cancelReleased(UUID operation) {
        var current = lockOperation(operation);
        if (current != null
                && current.phase() == Phase.RELEASING
                && current.scheduleState().equals("RELEASED")
                && current.attendanceState().equals("RELEASED"))
            jdbc.update(
                    "UPDATE subject_deletion_operations SET phase='CANCELLED',updated_at=? WHERE"
                            + " operation_id=?",
                    Timestamp.from(clock.instant()),
                    operation);
    }

    @Transactional
    public void failure(UUID operation) {
        jdbc.update(
                "UPDATE subject_deletion_operations SET"
                    + " last_failure='DEPENDENCY_UNAVAILABLE',updated_at=? WHERE operation_id=? AND"
                    + " phase NOT IN ('COMPLETED','CANCELLED')",
                Timestamp.from(clock.instant()),
                operation);
    }

    @Transactional
    public void finish(UUID operation) {
        SubjectDeletionOperation before = get(operation);
        if (before == null || before.phase() == Phase.COMPLETED) return;
        lockTarget(before.subject(), before.semester());
        var current = lockOperation(operation);
        if (current.phase() != Phase.COMMITTING
                || !current.scheduleState().equals("DELETED")
                || !current.attendanceState().equals("DELETED")) return;
        if (!data.snapshot(target(current)).digest().equals(current.academicDigest()))
            throw SubjectDeletionAcademicData.conflict();
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.subject_delete_operation_id',?,TRUE)",
                String.class,
                operation.toString());
        jdbc.update(
                "UPDATE homework_binding_archives a SET homework_id=NULL FROM homeworks h WHERE"
                        + " a.homework_id=h.id AND h.subject_id=?",
                current.subject());
        jdbc.update(
                "UPDATE homework_binding_transfer_markers m SET"
                    + " superseded_by_edit_operation_id=NULL FROM homework_edit_operations e WHERE"
                    + " m.superseded_by_edit_operation_id=e.operation_id AND e.subject_id=?",
                current.subject());
        jdbc.update(
                "DELETE FROM homework_edit_archive_admissions a USING homework_edit_operations e"
                        + " WHERE a.edit_operation_id=e.operation_id AND e.subject_id=?",
                current.subject());
        jdbc.update(
                "DELETE FROM homework_completions c USING homeworks h WHERE c.homework_id=h.id AND"
                        + " h.subject_id=?",
                current.subject());
        jdbc.update("DELETE FROM homeworks WHERE subject_id=?", current.subject());
        jdbc.update("DELETE FROM teacher_subject_groups WHERE subject_id=?", current.subject());
        jdbc.update("DELETE FROM assignments WHERE subject_id=?", current.subject());
        jdbc.update("DELETE FROM subject_lesson_types WHERE subject_id=?", current.subject());
        jdbc.update("DELETE FROM attendance_thresholds WHERE subject_id=?", current.subject());
        if (jdbc.update(
                        "DELETE FROM subjects WHERE id=? AND semester_id=? AND group_id=?",
                        current.subject(),
                        current.semester(),
                        current.group())
                != 1) throw SubjectDeletionAcademicData.conflict();
        jdbc.update(
                "UPDATE subject_deletion_operations SET"
                        + " phase='COMPLETED',updated_at=?,last_failure=NULL WHERE operation_id=?",
                Timestamp.from(clock.instant()),
                operation);
        events.publishEvent(new SubjectDeletedEvent(this, current.subject()));
    }

    private SubjectDeletionOperation lockOperation(UUID operation) {
        jdbc.query(
                "SELECT operation_id FROM subject_deletion_operations WHERE operation_id=? FOR"
                        + " UPDATE",
                (rs, i) -> rs.getObject(1),
                operation);
        return get(operation);
    }

    private void lockActor(long actor, long group) {
        jdbc.query("SELECT id FROM groups WHERE id=? FOR UPDATE", (rs, i) -> rs.getLong(1), group);
        jdbc.query(
                "SELECT id FROM academic_profiles WHERE id=? FOR UPDATE",
                (rs, i) -> rs.getLong(1),
                actor);
        data.localActor(actor, group);
    }

    private void lockTarget(long subject, long semester) {
        jdbc.query(
                "SELECT id FROM semesters WHERE id=? FOR UPDATE",
                (rs, i) -> rs.getLong(1),
                semester);
        jdbc.query(
                "SELECT id FROM subjects WHERE id=? AND semester_id=? FOR UPDATE",
                (rs, i) -> rs.getLong(1),
                subject,
                semester);
        data.writable(semester);
    }

    private boolean validProof(
            UUID proof,
            UUID operation,
            String digest,
            long subject,
            long semester,
            long group,
            long actor,
            UUID sid) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM subject_deletion_admission_receipts WHERE"
                            + " receipt_id=? AND operation_id=? AND preview_digest=? AND"
                            + " subject_id=? AND semester_id=? AND group_id=? AND actor_id=? AND"
                            + " session_id=?)",
                        Boolean.class,
                        proof,
                        operation,
                        digest,
                        subject,
                        semester,
                        group,
                        actor,
                        sid));
    }

    private static int rank(String state) {
        return switch (state) {
            case "PENDING" -> 0;
            case "PREPARED" -> 1;
            case "SEALED" -> 2;
            case "DELETED", "RELEASED" -> 3;
            default -> throw SubjectDeletionAcademicData.conflict();
        };
    }

    private SubjectDeletionAcademicData.Target target(SubjectDeletionOperation operation) {
        return new SubjectDeletionAcademicData.Target(
                operation.subject(), operation.semester(), operation.group(), "", null);
    }
}
