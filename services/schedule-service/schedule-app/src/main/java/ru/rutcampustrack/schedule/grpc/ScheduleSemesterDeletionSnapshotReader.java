package ru.rutcampustrack.schedule.grpc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only snapshot for schedule entities removed by a semester deletion. */
@Repository
public class ScheduleSemesterDeletionSnapshotReader {

    private final JdbcTemplate jdbc;

    public ScheduleSemesterDeletionSnapshotReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot read(long semesterId) {
        return jdbc.query("""
                WITH domain_rows AS (
                    SELECT 'schedule_item' AS kind, item.id AS row_id, to_jsonb(item)::TEXT AS value
                      FROM schedule_items item WHERE item.semester_id = ?
                    UNION ALL
                    SELECT 'one_off_lesson', item.id, to_jsonb(item)::TEXT
                      FROM schedule_one_off_lessons item WHERE item.semester_id = ?
                    UNION ALL
                    SELECT 'occurrence', occurrence.id, to_jsonb(occurrence)::TEXT
                      FROM lesson_occurrences occurrence WHERE occurrence.semester_id = ?
                    UNION ALL
                    SELECT 'lesson', lesson.id, to_jsonb(lesson)::TEXT
                      FROM lessons lesson WHERE lesson.semester_id = ?
                    UNION ALL
                    SELECT 'lesson_lifecycle', entry.id, to_jsonb(entry)::TEXT
                      FROM lesson_lifecycle_entries entry
                      JOIN lesson_occurrences occurrence ON occurrence.id = entry.occurrence_id
                     WHERE occurrence.semester_id = ?
                )
                SELECT count(*) FILTER (WHERE kind = 'schedule_item') AS schedule_templates,
                       count(*) FILTER (WHERE kind = 'one_off_lesson') AS one_off_lessons,
                       count(*) FILTER (WHERE kind = 'lesson') AS lessons,
                       encode(public.digest(convert_to(
                           coalesce(string_agg(kind || ':' || row_id::TEXT || ':' || value,
                                               E'\\n' ORDER BY kind, row_id), ''), 'UTF8'),
                           'sha256'), 'hex') AS participant_digest,
                       coalesce((SELECT state_version FROM schedule_semester_archive_barriers
                                  WHERE semester_id = ?), 0) AS observed_fence_version
                  FROM domain_rows
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Schedule deletion snapshot returned no row");
            return new Snapshot(rs.getLong("schedule_templates"), rs.getLong("one_off_lessons"),
                    rs.getLong("lessons"), rs.getString("participant_digest"),
                    rs.getLong("observed_fence_version"));
        }, semesterId, semesterId, semesterId, semesterId, semesterId, semesterId);
    }

    public record Snapshot(long scheduleTemplates, long oneOffLessons, long lessons,
                           String participantDigest, long observedFenceVersion) { }
}
