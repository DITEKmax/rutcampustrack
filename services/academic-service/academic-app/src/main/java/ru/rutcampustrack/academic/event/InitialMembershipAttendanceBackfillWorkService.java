package ru.rutcampustrack.academic.event;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction;

/** Admission, work and the event outbox share the enrolling PostgreSQL transaction. */
@Service
public class InitialMembershipAttendanceBackfillWorkService {
    private final JdbcTemplate jdbc;
    private final AcademicSemesterArchiveBarrierTransaction barrier;

    public InitialMembershipAttendanceBackfillWorkService(
            JdbcTemplate jdbc, AcademicSemesterArchiveBarrierTransaction barrier) {
        this.jdbc = jdbc;
        this.barrier = barrier;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void admit(StudentInitialMembershipRecordedEvent event) {
        Map<String, Object> tuple = event.membership();
        long semesterId = ((Number) tuple.get("semester_id")).longValue();
        barrier.lockOrdinaryWrite(semesterId);
        LocalDate end =
                jdbc.queryForObject(
                        "SELECT date_to FROM semesters WHERE id = ?", LocalDate.class, semesterId);
        event.captureSemesterEnd(end);
        Boolean exact =
                jdbc.queryForObject(
                        """
                SELECT EXISTS(SELECT 1 FROM student_group_history h JOIN semesters s ON s.id = ?
                  WHERE h.id = ? AND h.user_id = ? AND h.group_id = ?
                    AND h.reason = 'initial-enrollment' AND h.joined_at = ? AND h.joined_at = s.date_from)
                """,
                        Boolean.class,
                        semesterId,
                        tuple.get("history_id"),
                        tuple.get("student_id"),
                        tuple.get("group_id"),
                        LocalDate.parse((String) tuple.get("joined_on")));
        if (!Boolean.TRUE.equals(exact))
            throw new IllegalArgumentException("Initial membership identity is not authoritative");
        jdbc.update(
                """
                INSERT INTO academic_initial_membership_backfills
                    (event_id, history_id, student_id, group_id, semester_id, joined_on, semester_ends_on, payload_hash, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """,
                event.getEventId(),
                tuple.get("history_id"),
                tuple.get("student_id"),
                tuple.get("group_id"),
                semesterId,
                LocalDate.parse((String) tuple.get("joined_on")),
                end,
                event.payloadHash());
    }
}
