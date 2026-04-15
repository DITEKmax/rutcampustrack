package ru.rutcampustrack.attendance.checkin;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of {@link AttendanceWritePort} for the excuse approve cascade (D-16).
 *
 * Lives in checkin/ package — allowed to import AttendanceDocument / AttendanceRepository.
 * The port interface (shared/port/AttendanceWritePort) has zero checkin imports,
 * so the excuse/ domain depends only on the port and isolation is preserved.
 *
 * Upsert semantics: if a document already exists for (lessonId, studentId) its
 * status/source/updatedAt are overwritten; otherwise a fresh document is inserted
 * with the minimal field set required by the excuse flow. Fields that are only
 * meaningful for a real check-in (semesterId, subjectId, lessonNumber, lessonDate)
 * are left null — they will be populated whenever the real attendance flow
 * (checkin / auto-close / marking) touches the document.
 */
@Component
public class AttendanceWritePortImpl implements AttendanceWritePort {

    private final AttendanceRepository attendanceRepository;

    public AttendanceWritePortImpl(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status) {
        mark(studentId, lessonId, groupId, status, AttendanceSource.HEADMAN_EXCUSE);
    }

    @Override
    public void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status, AttendanceSource source) {
        Instant now = Instant.now();
        Optional<AttendanceDocument> existing =
                attendanceRepository.findByLessonIdAndUserId(lessonId, studentId);

        if (existing.isPresent()) {
            AttendanceDocument doc = existing.get();
            doc.setStatus(status);
            doc.setSource(source);
            doc.setUpdatedAt(now);
            attendanceRepository.save(doc);
            return;
        }

        AttendanceDocument fresh = AttendanceDocument.builder()
                .lessonId(lessonId)
                .userId(studentId)
                .groupId(groupId)
                .status(status)
                .source(source)
                .createdAt(now)
                .updatedAt(now)
                .build();
        attendanceRepository.save(fresh);
    }
}
