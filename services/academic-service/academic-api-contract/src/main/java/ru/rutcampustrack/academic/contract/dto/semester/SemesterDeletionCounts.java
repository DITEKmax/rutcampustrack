package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;

/** User-visible entity counts; participant digests also bind opaque identities and versions. */
@Schema(description = "Количество удаляемых записей по доменам")
public record SemesterDeletionCounts(
        long scheduleTemplates,
        long oneOffLessons,
        long lessons,
        long assignments,
        long homeworks,
        long attendanceMarks,
        long studentRequests) {
}
