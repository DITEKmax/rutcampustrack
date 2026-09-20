package ru.rutcampustrack.attendance.checkin;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private final PairWriteCoordinator pairWriteCoordinator;
    private final Clock clock;

    public AttendanceWritePortImpl(AttendanceRepository attendanceRepository,
                                   PairWriteCoordinator pairWriteCoordinator,
                                   Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.pairWriteCoordinator = pairWriteCoordinator;
        this.clock = clock;
    }

    @Override
    public void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status) {
        mark(studentId, lessonId, groupId, status, AttendanceSource.HEADMAN_EXCUSE, null);
    }

    @Override
    public void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status, AttendanceSource source) {
        mark(studentId, lessonId, groupId, status, source, null);
    }

    @Override
    public void mark(Long studentId, Long lessonId, Long groupId, AttendanceStatus status,
                     AttendanceSource source, String excuseReason) {
        Instant now = clock.instant();
        pairWriteCoordinator.lock(studentId, lessonId, groupId, now);
        Optional<AttendanceDocument> existing =
                attendanceRepository.findByLessonIdAndUserId(lessonId, studentId);

        if (existing.isPresent()) {
            AttendanceDocument doc = existing.get();
            doc.setStatus(status);
            doc.setSource(source);
            doc.setExcuseReason(excuseReason);
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
                .excuseReason(excuseReason)
                .createdAt(now)
                .updatedAt(now)
                .build();
        attendanceRepository.save(fresh);
    }

    @Override
    public void markWithLesson(Long studentId, Long lessonId, Long groupId, Long subjectId,
                               Long semesterId, Integer lessonNumber, LocalDate lessonDate,
                               AttendanceStatus status, AttendanceSource source, Long markedBy) {
        Instant now = clock.instant();
        pairWriteCoordinator.lock(studentId, lessonId, groupId, now);
        AttendanceDocument doc = attendanceRepository.findByLessonIdAndUserId(lessonId, studentId)
                .orElseGet(AttendanceDocument::new);
        if (doc.getCreatedAt() == null) doc.setCreatedAt(now);
        doc.setLessonId(lessonId);
        doc.setUserId(studentId);
        doc.setGroupId(groupId);
        doc.setSubjectId(subjectId);
        doc.setSemesterId(semesterId);
        doc.setLessonNumber(lessonNumber);
        doc.setLessonDate(lessonDate);
        doc.setStatus(status);
        doc.setSource(source);
        doc.setMarkedBy(markedBy);
        doc.setExcuseReason(null);
        doc.setUpdatedAt(now);
        attendanceRepository.save(doc);
    }
}
