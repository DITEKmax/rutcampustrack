package ru.rutcampustrack.attendance.shared.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
     * Find all marks for a bounded set of lessons in one read.  Stats/report
     * callers must not turn a semester aggregate into one database round-trip
     * per lesson; adapters should override this with their native batch query.
     */
    default List<AttendanceRecord> findByLessonIds(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return List.of();
        return lessonIds.stream().distinct()
                .flatMap(id -> findByLessonId(id).stream())
                .toList();
    }

    /**
     * Find all attendance records for a student in a semester.
     */
    List<AttendanceRecord> findByUserId(Long userId, Long semesterId);

    /**
     * Find marks for several roster members in one semester.  The default
     * implementation keeps existing adapters and focused mocks source
     * compatible; the Mongo adapter overrides it with one $in query.
     */
    default Map<Long, List<AttendanceRecord>> findByUserIds(List<Long> userIds, Long semesterId) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userIds.stream().distinct().collect(Collectors.toUnmodifiableMap(
                userId -> userId,
                userId -> List.copyOf(findByUserId(userId, semesterId))));
    }

    /**
     * Find attendance records for a group/subject within a date range (both boundaries inclusive).
     */
    List<AttendanceRecord> findByGroupAndSubject(Long groupId, Long subjectId, LocalDate from, LocalDate to);

    /**
     * Find all attendance records for a group within a date range (both boundaries inclusive).
     */
    List<AttendanceRecord> findByGroupAndDateRange(Long groupId, LocalDate from, LocalDate to);

    Optional<AttendanceRecord> findByLessonIdAndUserId(Long lessonId, Long userId);
}
