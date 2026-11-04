package ru.rutcampustrack.schedule.replacement;

import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.schedule.exception.ConflictException;

/** Absence requires exact durable closure or initial semester-generation provenance. */
public final class UnassignedBindingAuthority {
    private UnassignedBindingAuthority() {}

    public static long retainedSource(JdbcTemplate jdbc, long occurrenceId) {
        var sources =
                jdbc.queryForList(
                        """
                SELECT assignment_unassigned_origin(?)
                """,
                        Long.class,
                        occurrenceId);
        if (sources.size() != 1 || sources.get(0) == null || sources.get(0) <= 0)
            throw new ConflictException("Не подтверждено снятое назначение этой пары");
        return sources.get(0);
    }
}
