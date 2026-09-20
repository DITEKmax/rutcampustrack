package ru.rutcampustrack.schedule.recurring;

import java.time.LocalDate;

/** Durable identity/range returned by a recurring create or replay. */
public record RecurringCreateResult(
        long scheduleItemId,
        long assignmentId,
        long generatedCount,
        LocalDate generatedFrom,
        LocalDate generatedUntil
) {}
