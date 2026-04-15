package ru.rutcampustrack.attendance.shared.port;

import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Write port for cross-domain attendance mutations.
 *
 * Lives in shared/port/ — has ZERO imports from checkin/ package so other
 * domains (excuse/, latecheckin/) can depend on it without breaking the
 * isolation rule (CLAUDE.md).
 *
 * Implemented by AttendanceWritePortImpl in checkin/ package.
 */
public interface AttendanceWritePort {

    /**
     * Upsert an attendance record for a student/lesson with the given status.
     * Convenience overload — equivalent to {@link #mark(Long, Long, Long, AttendanceStatus, AttendanceSource)}
     * with {@code source = HEADMAN_EXCUSE} (excuse approve cascade, D-16).
     */
    void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status);

    /**
     * Upsert an attendance record for a student/lesson with explicit source.
     *
     * If no document exists for (lessonId, studentId) a fresh document is inserted;
     * otherwise the existing document's status/source/updatedAt are overwritten.
     *
     * @param studentId the student whose attendance is being marked
     * @param lessonId  the lesson
     * @param groupId   the student's group (only used when inserting a fresh document)
     * @param status    target AttendanceStatus (typically PRESENT for late-checkin approve,
     *                  EXCUSED / FREE_ATTENDANCE for the excuse cascade)
     * @param source    who/what is claiming this write (LATE_CHECKIN, HEADMAN_EXCUSE, ...)
     */
    void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status, AttendanceSource source);
}
