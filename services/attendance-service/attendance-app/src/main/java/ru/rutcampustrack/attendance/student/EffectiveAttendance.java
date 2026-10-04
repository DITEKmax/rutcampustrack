package ru.rutcampustrack.attendance.student;

import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/** Read-only attendance value for an already authorized lesson. Never creates a stored mark. */
public record EffectiveAttendance(AttendanceStatus status, AttendanceSource source) {

    public static EffectiveAttendance resolve(
            String lessonStatus, boolean hasStoredMark,
            AttendanceStatus storedStatus, AttendanceSource storedSource) {
        if (hasStoredMark) {
            return new EffectiveAttendance(storedStatus, storedSource);
        }
        if ("closed".equalsIgnoreCase(lessonStatus)) {
            return new EffectiveAttendance(AttendanceStatus.ABSENT, AttendanceSource.AUTO_SCHEDULER);
        }
        return new EffectiveAttendance(null, null);
    }
}
