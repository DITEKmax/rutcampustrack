package ru.rutcampustrack.attendance.shared.port;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Write port for cross-domain attendance mutations (D-16 cascade).
 *
 * Lives in shared/port/ — has ZERO imports from checkin/ package so the excuse/
 * domain can depend on it without breaking the isolation rule (CLAUDE.md).
 *
 * Implemented by AttendanceWritePortImpl in checkin/ package.
 */
public interface AttendanceWritePort {

    /**
     * Upsert an attendance record for a student/lesson with the given status.
     *
     * Called from the excuse domain upon ticket approval (D-16):
     * if no document exists for (lessonId, studentId) a new one is created;
     * otherwise the existing document's status is overwritten.
     *
     * The source is always set to {@code AttendanceSource.HEADMAN_EXCUSE}.
     *
     * @param studentId the student whose attendance is being marked
     * @param lessonId  the lesson the student is being excused from
     * @param groupId   the student's group at the time of the decision
     *                  (used only when inserting a fresh document — existing docs preserve their groupId)
     * @param status    target AttendanceStatus (EXCUSED or FREE_ATTENDANCE for the approve cascade)
     */
    void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status);
}
