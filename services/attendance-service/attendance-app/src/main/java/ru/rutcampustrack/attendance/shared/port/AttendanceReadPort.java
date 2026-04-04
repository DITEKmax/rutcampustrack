package ru.rutcampustrack.attendance.shared.port;

import java.time.LocalDate;
import java.util.List;

/**
 * Read port interface for cross-domain data access (D-14, D-15).
 * Lives in shared/port/ — has ZERO imports from checkin/ package.
 * Implemented by AttendanceReadPortImpl in checkin/ package.
 */
public interface AttendanceReadPort {

    /**
     * Find all attendance records for a given lesson.
     */
    List<AttendanceRecord> findByLessonId(Long lessonId);

    /**
     * Find all attendance records for a student in a semester.
     */
    List<AttendanceRecord> findByUserId(Long userId, Long semesterId);

    /**
     * Find attendance records for a group/subject within a date range (both boundaries inclusive).
     */
    List<AttendanceRecord> findByGroupAndSubject(Long groupId, Long subjectId, LocalDate from, LocalDate to);
}
