package ru.rutcampustrack.academic.groupdeletion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import ru.rutcampustrack.academic.contract.dto.group.GroupDeletionModels.*;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.subjectdeletion.SubjectDeletionAcademicData;
import ru.rutcampustrack.authsecurity.grpc.PasswordStepUpReceipt;
import ru.rutcampustrack.groupdeletion.grpc.*;

import java.util.*;

@Service
public class GroupDeletionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RequestContext context;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicIdentityCacheInvalidator caches;

    public GroupDeletionStore(JdbcTemplate jdbc, ObjectMapper mapper, RequestContext context) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.context = context;
    }

    public record Local(
            long group, String name, String manifest, String digest, Map<String, Long> counts) {}

    public record Saved(
            UUID id,
            long group,
            long actor,
            UUID sid,
            long sv,
            long rv,
            String digest,
            String localDigest,
            byte[] receipts,
            byte[] proof,
            Phase phase,
            Map<String, Long> counts,
            String failure) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Local snapshot(long group) {
        var names = jdbc.queryForList("SELECT name FROM groups WHERE id=?", String.class, group);
        if (names.isEmpty()) throw error(404, "group_not_found");
        checkDependencies(group);
        String manifest =
                jdbc.queryForObject("SELECT group_deletion_manifest(?)::text", String.class, group);
        Map<String, Long> counts = new TreeMap<>();
        jdbc.query(
                "SELECT kind,count(*) FROM group_deletion_rows(?) GROUP BY kind",
                rs -> {
                    counts.put("academic." + rs.getString(1), rs.getLong(2));
                },
                group);
        counts.put(
                "memberships",
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_role_eligibility WHERE group_id=? AND"
                                + " role='STUDENT'",
                        Long.class,
                        group));
        counts.put(
                "headmanAssignments",
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_role_eligibility WHERE group_id=? AND"
                                + " role='HEADMAN'",
                        Long.class,
                        group));
        counts.put("assistantAssignments", counts.getOrDefault("academic.headman_assistants", 0L));
        counts.put(
                "academicRows",
                jdbc.queryForObject(
                        "SELECT count(*) FROM group_deletion_rows(?)", Long.class, group));
        return new Local(
                group,
                names.getFirst(),
                manifest,
                SubjectDeletionAcademicData.hash(manifest),
                counts);
    }

    private void checkDependencies(long group) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT group_deletion_manifest_closed(?)", Boolean.class, group)))
            throw error(409, "group_deletion_cross_scope_reference");
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM semesters WHERE archive_transition<>'NONE' OR archive_release_pending)
 OR EXISTS(SELECT 1 FROM academic_identity_operation_groups g JOIN academic_identity_operations o USING(operation_id) WHERE g.group_id=? AND o.phase NOT IN ('SUCCEEDED','REJECTED'))
 OR EXISTS(SELECT 1 FROM academic_group_availability_guards WHERE group_id=?)
 OR EXISTS(SELECT 1 FROM subject_deletion_operations WHERE group_id=? AND phase NOT IN ('COMPLETED','CANCELLED'))
 OR EXISTS(SELECT 1 FROM homework_edit_operations WHERE group_id=? AND state NOT IN ('ACKNOWLEDGED','CANCELLED'))
 OR EXISTS(SELECT 1 FROM assignments a JOIN assignment_replacement_operations o ON o.source_assignment_id=a.id OR o.target_assignment_id=a.id WHERE a.group_id=? AND o.state<>'COMMITTED')
""",
                        Boolean.class,
                        group,
                        group,
                        group,
                        group,
                        group))) throw error(409, "group_operation_pending");
    }

    public Saved get(UUID id) {
        var values =
                jdbc.query(
                        "SELECT * FROM group_deletion_operations WHERE operation_id=?",
                        (rs, i) ->
                                new Saved(
                                        id,
                                        rs.getLong("group_id"),
                                        rs.getLong("actor_id"),
                                        rs.getObject("session_id", UUID.class),
                                        rs.getLong("session_version"),
                                        rs.getLong("roles_version"),
                                        rs.getString("preview_digest"),
                                        rs.getString("local_digest"),
                                        rs.getBytes("participant_receipts"),
                                        rs.getBytes("proof_receipt"),
                                        Phase.valueOf(rs.getString("phase")),
                                        readCounts(rs.getString("counts")),
                                        rs.getString("failure")),
                        id);
        return values.isEmpty() ? null : values.getFirst();
    }

    /**
     * The same unique operation PK arbitrates acceptance versus this permanent no-effects
     * tombstone. A request that is still proving its password cannot accept after this insertion
     * wins. Existing accepted operations are returned unchanged, including another actor's opaque
     * row; the service checks ownership before it may disclose or advance anything.
     */
    @Transactional
    public Saved cancelUnaccepted(UUID id, long group) {
        var actor = context.getClaims();
        String tombstone =
                SubjectDeletionAcademicData.hash(
                        "group-delete-not-accepted:" + id + ":" + group + ":" + actor.userId());
        Map<String, Long> zero =
                Map.of(
                        "accountsAffected",
                        0L,
                        "memberships",
                        0L,
                        "headmanAssignments",
                        0L,
                        "assistantAssignments",
                        0L,
                        "academicRows",
                        0L,
                        "scheduleRows",
                        0L,
                        "attendanceRows",
                        0L,
                        "authGrants",
                        0L);
        jdbc.update(
                """
INSERT INTO group_deletion_operations(operation_id,group_id,actor_id,session_id,session_version,roles_version,
  preview_digest,local_digest,academic_manifest,participant_receipts,proof_receipt,counts,phase,failure)
VALUES(?,?,?,?,?,?,?,?, '[]'::jsonb,?,?,?::jsonb,'CANCELLED','NOT_ACCEPTED')
ON CONFLICT(operation_id) DO NOTHING
""",
                id,
                group,
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                tombstone,
                tombstone,
                new byte[0],
                new byte[0],
                json(zero));
        return get(id);
    }

    public List<UUID> pending() {
        return jdbc.queryForList(
                "SELECT operation_id FROM group_deletion_operations WHERE phase NOT IN"
                        + " ('COMPLETED','CANCELLED') ORDER BY created_at LIMIT 20",
                UUID.class);
    }

    @Transactional
    public void accept(
            UUID id,
            Local expected,
            String digest,
            GroupDeletionManifest receipts,
            PasswordStepUpReceipt proof,
            Map<String, Long> counts) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?,?)", 0x53454D, 1);
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(4670544,?)", (int) (expected.group() % 2147483647));
        jdbc.queryForList("SELECT id FROM groups WHERE id=? FOR UPDATE", expected.group());
        if (get(id) != null) throw error(409, "group_deletion_key_conflict");
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM group_deletion_operations WHERE group_id=? AND"
                                + " phase NOT IN ('COMPLETED','CANCELLED'))",
                        Boolean.class,
                        expected.group()))) throw error(409, "group_operation_pending");
        Local current = snapshot(expected.group());
        var actor = context.getClaims();
        var p = proof.getActor();
        if (!expected.digest().equals(current.digest())
                || actor == null
                || !proof.getBinding().getOperationId().equals(id.toString())
                || proof.getBinding().getTargetId() != expected.group()
                || !proof.getBinding().getPreviewDigest().equals(digest)
                || p.getUserId() != actor.userId()
                || !p.getSessionId().equals(actor.sessionId().toString())
                || p.getSessionVersion() != actor.sessionVersion()
                || p.getRolesVersion() != actor.rolesVersion()
                || !java.time.Instant.parse(proof.getExpiresAt()).isAfter(java.time.Instant.now()))
            throw error(409, "group_deletion_stale_preview");
        jdbc.update(
                """
INSERT INTO group_deletion_operations(operation_id,group_id,actor_id,session_id,session_version,roles_version,preview_digest,local_digest,academic_manifest,participant_receipts,proof_receipt,counts,phase)
VALUES(?,?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb,'PREPARING')
""",
                id,
                expected.group(),
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                digest,
                expected.digest(),
                expected.manifest(),
                receipts.toByteArray(),
                proof.toByteArray(),
                json(counts));
    }

    @Transactional
    public void beginCommit(UUID id) {
        Saved current = lock(id);
        if (current == null || current.phase() != Phase.PREPARING) return;
        var local = snapshot(current.group());
        if (!local.digest().equals(current.localDigest()))
            throw error(409, "group_deletion_stale_preview");
        jdbc.update(
                "UPDATE group_deletion_operations SET"
                        + " phase='COMMITTING',failure=NULL,updated_at=now() WHERE operation_id=?",
                id);
    }

    /** Only the explicit durable Auth zero-effect rejection permits undoing commit intent. */
    @Transactional
    public void rejectCommit(UUID id) {
        Saved current = lock(id);
        if (current == null || current.phase() != Phase.COMMITTING) return;
        jdbc.update(
                "UPDATE group_deletion_operations SET"
                        + " phase='RELEASING',failure='AUTHORITY_REVOKED',updated_at=now() WHERE"
                        + " operation_id=?",
                id);
    }

    @Transactional
    public void release(UUID id, String reason) {
        Saved current = lock(id);
        if (current == null || current.phase() != Phase.PREPARING) return;
        jdbc.update(
                "UPDATE group_deletion_operations SET phase='RELEASING',failure=?,updated_at=now()"
                        + " WHERE operation_id=?",
                reason,
                id);
    }

    @Transactional
    public void cancel(UUID id) {
        jdbc.update(
                "UPDATE group_deletion_operations SET phase='CANCELLED',updated_at=now() WHERE"
                        + " operation_id=? AND phase='RELEASING'",
                id);
    }

    @Transactional
    public void failure(UUID id) {
        jdbc.update(
                "UPDATE group_deletion_operations SET"
                    + " failure='DEPENDENCY_UNAVAILABLE',updated_at=now() WHERE operation_id=? AND"
                    + " phase NOT IN ('COMPLETED','CANCELLED')",
                id);
    }

    @Transactional
    public void finish(UUID id) {
        Saved current = lock(id);
        if (current == null || current.phase() != Phase.COMMITTING) return;
        if (!snapshot(current.group()).digest().equals(current.localDigest()))
            throw error(409, "group_deletion_local_conflict");
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.group_delete_operation_id',?,true)",
                String.class,
                id.toString());
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        var affectedUsers =
                jdbc.queryForList(
                        "SELECT user_id FROM academic_role_eligibility WHERE group_id=? UNION"
                                + " SELECT student_id FROM headman_assistants WHERE group_id=?",
                        Long.class,
                        current.group(),
                        current.group());
        caches.afterCommit(affectedUsers, List.of(current.group()));
        // This command changes group-owned eligibility/history, not the profile aggregate.
        // Preserve the profile and its Auth-mirrored revision so later identity CAS stays aligned.
        for (String table :
                List.of(
                        "homework_edit_archive_admissions",
                        "homework_completions",
                        "homework_edit_history",
                        "homework_edit_receipts",
                        "homework_binding_archives",
                        "homework_binding_transfer_markers",
                        "homework_binding_transfer_history",
                        "homework_edit_operations",
                        "homeworks",
                        "teacher_subject_groups",
                        "assignments",
                        "subject_lesson_types",
                        "attendance_thresholds",
                        "subjects",
                        "headman_assistants",
                        "student_group_history",
                        "group_history_coverage",
                        "homework_weekly_digest_runs",
                        "academic_role_eligibility",
                        "groups"))
            jdbc.update(
                    "DELETE FROM "
                            + table
                            + " t WHERE group_deletion_row_authorized(?,to_jsonb(t))",
                    table);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        jdbc.update(
                "UPDATE group_deletion_operations SET"
                        + " phase='COMPLETED',failure=NULL,updated_at=now() WHERE operation_id=?",
                id);
        events.publishEvent(
                new ru.rutcampustrack.academic.event.GroupUpdatedEvent(this, current.group()));
        events.publishEvent(new ru.rutcampustrack.academic.event.DashboardInvalidatedEvent(this));
    }

    private Saved lock(UUID id) {
        jdbc.queryForList(
                "SELECT operation_id FROM group_deletion_operations WHERE operation_id=? FOR"
                        + " UPDATE",
                id);
        return get(id);
    }

    private Map<String, Long> readCounts(String value) {
        try {
            return mapper.readValue(value, new TypeReference<Map<String, Long>>() {});
        } catch (Exception invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    static org.springframework.web.server.ResponseStatusException error(int status, String code) {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.valueOf(status), code);
    }
}
