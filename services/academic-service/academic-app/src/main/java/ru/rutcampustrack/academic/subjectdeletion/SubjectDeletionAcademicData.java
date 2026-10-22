package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Local immutable identity and snapshots. A legacy subject is never guessed into a semester. */
@Service
public class SubjectDeletionAcademicData {
    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final AcademicIdentityReadFence fences;

    public SubjectDeletionAcademicData(
            JdbcTemplate jdbc, RequestContext context, AcademicIdentityReadFence fences) {
        this.jdbc = jdbc;
        this.context = context;
        this.fences = fences;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Target target(long subjectId) {
        InternalJwtClaims actor = actor(null);
        var rows =
                jdbc.query(
                        "SELECT group_id,semester_id,name FROM subjects WHERE id=?",
                        (rs, i) ->
                                new Target(
                                        subjectId,
                                        rs.getObject("semester_id", Long.class),
                                        rs.getLong("group_id"),
                                        rs.getString("name"),
                                        actor),
                        subjectId);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Предмет не найден");
        Target target = rows.getFirst();
        actor(target.group());
        if (target.semester() == null) throw conflict();
        writable(target.semester());
        return target;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InternalJwtClaims actor(Long group) {
        var claims = context.getClaims();
        if (claims == null
                || !"HEADMAN".equals(claims.role())
                || !"ACTIVE".equals(claims.status())
                || !claims.isHeadman()
                || claims.readOnly()
                || context.getAdmittedAdminProfile() == null
                || claims.sessionId() == null
                || claims.sessionVersion() <= 0
                || claims.rolesVersion() <= 0
                || claims.groupId() == null
                || group != null && !Objects.equals(group, claims.groupId())) throw denied();
        localActor(claims.userId(), claims.groupId());
        return claims;
    }

    void localActor(long user, long group) {
        fences.requireGroupReadable(group);
        fences.requireUserReadable(user);
        Boolean active =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM groups g JOIN academic_role_eligibility h ON h.group_id=g.id
    JOIN academic_profiles p ON p.id=h.user_id
    JOIN academic_role_eligibility s ON s.user_id=p.id AND s.role='STUDENT'
    WHERE g.id=? AND g.is_active AND h.user_id=? AND h.role='HEADMAN' AND h.status='ACTIVE'
      AND p.archived_at IS NULL AND s.status='ACTIVE' AND s.group_id=g.id)
""",
                        Boolean.class,
                        group,
                        user);
        if (!Boolean.TRUE.equals(active)) throw denied();
    }

    public void writable(long semester) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM semesters WHERE id=? AND is_active AND NOT is_archived
    AND archive_transition='NONE' AND NOT archive_release_pending)
""",
                        Boolean.class,
                        semester))) throw conflict();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot(Target target) {
        // A preview checks live local eligibility. An already COMMITTING operation only
        // revalidates its frozen domain digest so later revocation cannot strand accepted effects.
        if (target.actor() != null) localActor(target.actor().userId(), target.group());
        long id = target.subject();
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM subjects WHERE id=? AND semester_id=? AND"
                            + " group_id=?)",
                        Boolean.class,
                        target.subject(),
                        target.semester(),
                        target.group()))) throw conflict();
        Boolean invalid =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM (
    SELECT group_id,semester_id FROM assignments WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM teacher_subject_groups WHERE subject_id=?
    UNION ALL SELECT group_id,semester_id FROM homeworks WHERE subject_id=?
) scope WHERE group_id IS DISTINCT FROM ? OR semester_id IS DISTINCT FROM ?)
""",
                        Boolean.class,
                        id,
                        id,
                        id,
                        target.group(),
                        target.semester());
        if (Boolean.TRUE.equals(invalid)) throw conflict();
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM homework_edit_operations WHERE subject_id=?"
                                + " AND state NOT IN ('ACKNOWLEDGED','CANCELLED'))",
                        Boolean.class,
                        id))) throw conflict();
        String rows =
                jdbc.queryForObject(
                        """
WITH domain_rows AS (
    SELECT 'subjects' AS kind,to_jsonb(s) AS data FROM subjects s WHERE id=?
    UNION ALL SELECT 'subject_lesson_types',to_jsonb(t) FROM subject_lesson_types t WHERE subject_id=?
    UNION ALL SELECT 'assignments',to_jsonb(a) FROM assignments a WHERE subject_id=?
    UNION ALL SELECT 'teacher_subject_groups',to_jsonb(t) FROM teacher_subject_groups t WHERE subject_id=?
    UNION ALL SELECT 'homeworks',to_jsonb(h) FROM homeworks h WHERE subject_id=?
    UNION ALL SELECT 'homework_completions',to_jsonb(c) FROM homework_completions c
        JOIN homeworks h ON h.id=c.homework_id WHERE h.subject_id=?
    UNION ALL SELECT 'homework_edit_history',to_jsonb(e) FROM homework_edit_history e JOIN homeworks h ON h.id=e.homework_id WHERE h.subject_id=?
    UNION ALL SELECT 'homework_edit_receipts',to_jsonb(e) FROM homework_edit_receipts e
        JOIN homeworks h ON h.id=e.homework_id WHERE h.subject_id=?
    UNION ALL SELECT 'homework_edit_operations',to_jsonb(e) FROM homework_edit_operations e WHERE subject_id=?
    UNION ALL SELECT 'homework_edit_archive_admissions',to_jsonb(a) FROM homework_edit_archive_admissions a
        JOIN homework_edit_operations e ON e.operation_id=a.edit_operation_id WHERE e.subject_id=?
    UNION ALL SELECT 'homework_binding_archives',to_jsonb(a) FROM homework_binding_archives a
        JOIN homeworks h ON h.id=a.homework_id WHERE h.subject_id=?
    UNION ALL SELECT 'homework_binding_transfer_markers',to_jsonb(m) FROM homework_binding_transfer_markers m WHERE subject_id=?
    UNION ALL SELECT 'attendance_thresholds' ,to_jsonb(t) FROM attendance_thresholds t WHERE subject_id=?
) SELECT coalesce(jsonb_agg(jsonb_build_object('kind',kind,'row_hash',encode(public.digest(convert_to(data::TEXT,'UTF8'),'sha256'),'hex')) ORDER BY kind,data::TEXT),'[]'::JSONB)::TEXT
  FROM domain_rows
""",
                        String.class,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id,
                        id);
        Long count = jdbc.queryForObject("SELECT jsonb_array_length(?::JSONB)", Long.class, rows);
        return new Snapshot(hash(rows), rows, count == null ? 0 : count);
    }

    public static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Состав или семестр предмета не подтверждён");
    }

    public static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Удаление предмета недоступно");
    }

    public record Target(
            long subject, Long semester, long group, String name, InternalJwtClaims actor) {}

    public record Snapshot(String digest, String manifest, long rows) {}
}
