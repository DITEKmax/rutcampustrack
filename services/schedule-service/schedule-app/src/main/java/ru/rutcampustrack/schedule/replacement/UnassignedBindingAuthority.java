package ru.rutcampustrack.schedule.replacement;

import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.schedule.exception.ConflictException;

/** Absence is admitted only through the durable closure history of this exact occurrence. */
public final class UnassignedBindingAuthority {
    private UnassignedBindingAuthority() {}

    public static long retainedSource(JdbcTemplate jdbc, long occurrenceId) {
        var sources =
                jdbc.queryForList(
                        """
                SELECT closure.source_assignment_id
                  FROM lesson_occurrences occurrence
                  JOIN schedule_assignment_binding_ledger ledger ON ledger.occurrence_id=occurrence.id
                  JOIN schedule_assignment_replacement_operations closure ON closure.operation_id=ledger.operation_id
                 WHERE occurrence.id=? AND occurrence.assignment_id IS NULL AND occurrence.assigned_teacher_id IS NULL
                   AND ledger.after_assignment_id IS NULL AND ledger.after_teacher_id IS NULL
                   AND closure.operation_kind='CLOSE_UNASSIGN' AND closure.state='COMMITTED'
                   AND closure.group_id=occurrence.group_id AND closure.subject_id=occurrence.subject_id
                   AND closure.semester_id=occurrence.semester_id AND closure.lesson_type=occurrence.lesson_type
                 ORDER BY ledger.accepted_at DESC,ledger.operation_id LIMIT 1
                """,
                        Long.class,
                        occurrenceId);
        if (sources.size() != 1 || sources.get(0) <= 0)
            throw new ConflictException("Не подтверждено снятое назначение этой пары");
        return sources.get(0);
    }
}
