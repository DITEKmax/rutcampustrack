package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public SubjectDeletionAcademicData(JdbcTemplate jdbc, RequestContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    @Transactional(readOnly = true)
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

    public InternalJwtClaims actor(Long group) {
        var claims = context.getClaims();
        if (claims == null
                || !"HEADMAN".equals(claims.role())
                || !"ACTIVE".equals(claims.status())
                || !claims.isHeadman()
                || claims.readOnly()
                || claims.groupId() == null
                || group != null && !Objects.equals(group, claims.groupId())) throw denied();
        Boolean active =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM groups g JOIN user_role_grants h ON h.group_id=g.id
    JOIN users u ON u.id=h.user_id
    WHERE g.id=? AND g.is_active AND h.role='headman' AND h.status='active' AND h.user_id=?
    AND u.status<>'archived' AND NOT EXISTS(SELECT 1 FROM user_role_grants peer
        WHERE peer.group_id=g.id AND peer.role='headman' AND peer.status='active' AND peer.user_id<>h.user_id)
    AND EXISTS(SELECT 1 FROM user_role_grants student WHERE student.user_id=h.user_id
        AND student.role='student' AND student.status='active' AND student.group_id=g.id))
""",
                        Boolean.class,
                        claims.groupId(),
                        claims.userId());
        if (!Boolean.TRUE.equals(active)) throw denied();
        return claims;
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

    public Snapshot snapshot(Target target) {
        long id = target.subject();
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
