package ru.rutcampustrack.academic.semester;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the Academic domain rows covered by a final-deletion preview. */
@Repository
public class AcademicSemesterDeletionSnapshotReader {

    private final JdbcTemplate jdbc;

    public AcademicSemesterDeletionSnapshotReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot read(long semesterId) {
        return jdbc.query("""
                WITH domain_rows AS (
                    SELECT 'assignment' AS kind, a.id::TEXT AS row_id, to_jsonb(a)::TEXT AS value
                      FROM assignments a WHERE a.semester_id = ?
                    UNION ALL
                    SELECT 'homework', h.id::TEXT, to_jsonb(h)::TEXT
                      FROM homeworks h WHERE h.semester_id = ?
                    UNION ALL
                    SELECT 'homework_completion', c.id::TEXT, to_jsonb(c)::TEXT
                      FROM homework_completions c
                      JOIN homeworks h ON h.id = c.homework_id
                     WHERE h.semester_id = ?
                    UNION ALL
                    SELECT 'teacher_subject_group', t.id::TEXT, to_jsonb(t)::TEXT
                      FROM teacher_subject_groups t WHERE t.semester_id = ?
                    UNION ALL
                    SELECT 'homework_edit_history', e.id::TEXT, to_jsonb(e)::TEXT
                      FROM homework_edit_history e WHERE e.semester_id = ?
                    UNION ALL
                    SELECT 'homework_edit_receipt', e.homework_id || ':' || e.actor_id || ':' || e.request_key, to_jsonb(e)::TEXT
                      FROM homework_edit_receipts e WHERE e.semester_id = ?
                    UNION ALL
                    SELECT 'homework_edit_command', e.operation_id::TEXT,
                           (to_jsonb(e) - ARRAY['state','attempts','next_attempt_at','last_failure','finalized_at'])::TEXT
                      FROM homework_edit_operations e WHERE e.semester_id = ?
                )
                SELECT count(*) FILTER (WHERE kind = 'assignment') AS assignments,
                       count(*) FILTER (WHERE kind = 'homework') AS homeworks,
                       encode(public.digest(convert_to(
                           coalesce(string_agg(kind || ':' || row_id::TEXT || ':' || value,
                                               E'\\n' ORDER BY kind, row_id), ''), 'UTF8'),
                           'sha256'), 'hex') AS participant_digest
                  FROM domain_rows
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Academic deletion snapshot returned no row");
            return new Snapshot(rs.getLong("assignments"), rs.getLong("homeworks"),
                    rs.getString("participant_digest"));
        }, semesterId, semesterId, semesterId, semesterId, semesterId, semesterId, semesterId);
    }

    public record Snapshot(long assignments, long homeworks, String participantDigest) { }
}
