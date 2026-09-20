package ru.rutcampustrack.schedule.recurring;

import java.time.LocalDate;

/** Immutable, validated copy of the Academic assignment response. */
public record RecurringAssignmentAuthority(
        long assignmentId,
        long teacherId,
        long subjectId,
        long groupId,
        long semesterId,
        String lessonType,
        LocalDate validFrom,
        LocalDate validUntilExclusive
) {
    public RecurringAssignmentAuthority {
        if (assignmentId <= 0 || teacherId <= 0 || subjectId <= 0
                || groupId <= 0 || semesterId <= 0) {
            throw new IllegalArgumentException("assignment authority ids must be positive");
        }
        if (lessonType == null || lessonType.isBlank()
                || validFrom == null || validUntilExclusive == null
                || !validFrom.isBefore(validUntilExclusive)) {
            throw new IllegalArgumentException("assignment authority interval is invalid");
        }
        lessonType = lessonType.toLowerCase(java.util.Locale.ROOT);
        if (!lessonType.equals("lecture") && !lessonType.equals("practice")
                && !lessonType.equals("lab")) {
            throw new IllegalArgumentException("unsupported assignment lesson type: " + lessonType);
        }
    }
}
