package ru.rutcampustrack.attendance.shared.port;

import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.LocalDate;

/**
 * Read-only DTO for cross-domain data transfer (D-16).
 * Used by report/ domain to access checkin/ data through AttendanceReadPort.
 * CRITICAL: Never import AttendanceDocument here — only contract enums and java.*.
 */
public record AttendanceRecord(
        Long lessonId,
        Long userId,
        Long groupId,
        Long subjectId,
        LocalDate lessonDate,
        Integer lessonNumber,
        AttendanceStatus status,
        AttendanceSource source
) {}
