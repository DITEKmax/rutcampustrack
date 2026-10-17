package ru.rutcampustrack.attendance.shared.port;

import java.time.LocalDate;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Read-only DTO for cross-domain data transfer (D-16). Used by report/ domain to access checkin/
 * data through AttendanceReadPort. CRITICAL: Never import AttendanceDocument here — only contract
 * enums and java.*.
 */
public record AttendanceRecord(
        Long lessonId,
        Long userId,
        Long groupId,
        Long subjectId,
        LocalDate lessonDate,
        Integer lessonNumber,
        AttendanceStatus status,
        AttendanceSource source,
        String excuseReason,
        String excuseType,
        String excuseComment,
        String attachmentId,
        String attachmentName,
        String attachmentContentType,
        Long attachmentSize,
        String markRevision) {
    public AttendanceRecord(
            Long lessonId,
            Long userId,
            Long groupId,
            Long subjectId,
            LocalDate lessonDate,
            Integer lessonNumber,
            AttendanceStatus status,
            AttendanceSource source,
            String excuseReason,
            String excuseType,
            String excuseComment,
            String attachmentId,
            String attachmentName,
            String attachmentContentType,
            Long attachmentSize) {
        this(
                lessonId,
                userId,
                groupId,
                subjectId,
                lessonDate,
                lessonNumber,
                status,
                source,
                excuseReason,
                excuseType,
                excuseComment,
                attachmentId,
                attachmentName,
                attachmentContentType,
                attachmentSize,
                null);
    }

    /** Source-compatible constructor for existing read adapters and focused tests. */
    public AttendanceRecord(
            Long lessonId,
            Long userId,
            Long groupId,
            Long subjectId,
            LocalDate lessonDate,
            Integer lessonNumber,
            AttendanceStatus status,
            AttendanceSource source,
            String excuseReason) {
        this(
                lessonId,
                userId,
                groupId,
                subjectId,
                lessonDate,
                lessonNumber,
                status,
                source,
                excuseReason,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
