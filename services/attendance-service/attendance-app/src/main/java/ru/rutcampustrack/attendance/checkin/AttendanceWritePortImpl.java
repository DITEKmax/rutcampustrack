package ru.rutcampustrack.attendance.checkin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
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
    private final JournalAttachmentPort journalAttachmentPort;

    @Autowired
    public AttendanceWritePortImpl(AttendanceRepository attendanceRepository,
                                   PairWriteCoordinator pairWriteCoordinator,
                                   Clock clock,
                                   JournalAttachmentPort journalAttachmentPort) {
        this.attendanceRepository = attendanceRepository;
        this.pairWriteCoordinator = pairWriteCoordinator;
        this.clock = clock;
        this.journalAttachmentPort = journalAttachmentPort;
    }

    /** Source-compatible constructor for focused tests without attachment storage. */
    public AttendanceWritePortImpl(AttendanceRepository attendanceRepository,
                                   PairWriteCoordinator pairWriteCoordinator,
                                   Clock clock) {
        this(attendanceRepository, pairWriteCoordinator, clock, null);
    }

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public void mark(Long studentId, Long lessonId, Long groupId, Long semesterId, AttendanceStatus status) {
        mark(studentId, lessonId, groupId, semesterId, status, AttendanceSource.HEADMAN_EXCUSE, null);
    }

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public void mark(Long studentId, Long lessonId, Long groupId, Long semesterId,
                     AttendanceStatus status, AttendanceSource source) {
        mark(studentId, lessonId, groupId, semesterId, status, source, null);
    }

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public void mark(Long studentId, Long lessonId, Long groupId, Long semesterId, AttendanceStatus status,
                     AttendanceSource source, String excuseReason) {
        Instant now = clock.instant();
        requireSemesterId(semesterId);
        pairWriteCoordinator.lock(semesterId, studentId, lessonId, groupId, now);
        Optional<AttendanceDocument> existing =
                attendanceRepository.findByLessonIdAndUserId(lessonId, studentId);

        if (existing.isPresent()) {
            AttendanceDocument doc = existing.get();
            requireExistingScope(doc, groupId, semesterId);
            if (preserveExistingPresent(doc, status, source)) {
                return;
            }
            applyJournalAttachmentTransition(doc, status, studentId, lessonId, groupId, semesterId);
            doc.setStatus(status);
            doc.setSource(source);
            doc.setGroupId(groupId);
            doc.setSemesterId(semesterId);
            doc.setExcuseReason(excuseReason);
            doc.setExcuseType(null);
            doc.setExcuseComment(null);
            doc.setUpdatedAt(now);
            attendanceRepository.save(doc);
            return;
        }

        AttendanceDocument fresh = AttendanceDocument.builder()
                .lessonId(lessonId)
                .userId(studentId)
                .groupId(groupId)
                .semesterId(semesterId)
                .status(status)
                .source(source)
                .excuseReason(excuseReason)
                .excuseType(null)
                .excuseComment(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        applyJournalAttachmentTransition(fresh, status, studentId, lessonId, groupId, semesterId);
        attendanceRepository.save(fresh);
    }

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public void markWithLesson(Long studentId, Long lessonId, Long groupId, Long subjectId,
                               Long semesterId, Integer lessonNumber, LocalDate lessonDate,
                               AttendanceStatus status, AttendanceSource source, Long markedBy) {
        Instant now = clock.instant();
        requireSemesterId(semesterId);
        pairWriteCoordinator.lock(semesterId, studentId, lessonId, groupId, now);
        AttendanceDocument doc = attendanceRepository.findByLessonIdAndUserId(lessonId, studentId)
                .orElseGet(AttendanceDocument::new);
        if (doc.getId() != null) requireExistingScope(doc, groupId, semesterId);
        if (preserveExistingPresent(doc, status, source)) {
            return;
        }
        if (doc.getCreatedAt() == null) doc.setCreatedAt(now);
        doc.setLessonId(lessonId);
        doc.setUserId(studentId);
        doc.setGroupId(groupId);
        doc.setSubjectId(subjectId);
        doc.setSemesterId(semesterId);
        doc.setLessonNumber(lessonNumber);
        doc.setLessonDate(lessonDate);
        applyJournalAttachmentTransition(doc, status, studentId, lessonId, groupId, semesterId);
        doc.setStatus(status);
        doc.setSource(source);
        doc.setMarkedBy(markedBy);
        doc.setExcuseReason(null);
        doc.setExcuseType(null);
        doc.setExcuseComment(null);
        doc.setUpdatedAt(now);
        attendanceRepository.save(doc);
    }

    private static void requireSemesterId(Long semesterId) {
        if (semesterId == null || semesterId <= 0) {
            throw new IllegalArgumentException("semesterId must be positive");
        }
    }

    private static void requireExistingScope(AttendanceDocument document, Long groupId, Long semesterId) {
        if ((document.getGroupId() != null && !document.getGroupId().equals(groupId))
                || (document.getSemesterId() != null && !document.getSemesterId().equals(semesterId))) {
            throw new ConflictException("Семестр или группа отметки не совпадает с уроком");
        }
    }

    /**
     * A headman excuse/late decision is a conditional write.  A prior PRESENT
     * from a real check-in wins the pair race.  Manual marking uses its own
     * writer and remains able to change the attendance state.
     */
    private static boolean preserveExistingPresent(AttendanceDocument document,
                                                    AttendanceStatus requestedStatus,
                                                    AttendanceSource source) {
        return document.getStatus() == AttendanceStatus.PRESENT
                && (source == AttendanceSource.HEADMAN_EXCUSE || source == AttendanceSource.LATE_CHECKIN);
    }

    private void applyJournalAttachmentTransition(AttendanceDocument document,
                                                  AttendanceStatus nextStatus,
                                                  Long studentId,
                                                  Long lessonId,
                                                  Long groupId,
                                                  Long semesterId) {
        boolean retain = nextStatus == AttendanceStatus.EXCUSED
                && document.getStatus() == AttendanceStatus.EXCUSED
                && document.getAttachmentId() != null
                && journalAttachmentPort != null
                && journalAttachmentPort.isAvailable(lessonId, studentId, document.getAttachmentId());
        if (!retain) {
            if (journalAttachmentPort != null) {
                journalAttachmentPort.delete(semesterId, studentId, lessonId, groupId);
            }
            document.setAttachmentId(null);
            document.setAttachmentName(null);
            document.setAttachmentContentType(null);
            document.setAttachmentSize(null);
        }
    }

}
