package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies INFRA-01: unique compound index on (lesson_id, user_id) rejects duplicates.
 */
class MongoIndexTest extends AbstractAttendanceIntegrationTest {

    @AfterEach
    void cleanup() {
        attendanceRepository.deleteAll();
    }

    @Test
    void uniqueIndex_rejectsDuplicateLessonUser() {
        AttendanceDocument doc1 = AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        attendanceRepository.save(doc1);

        AttendanceDocument doc2 = AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        assertThrows(DuplicateKeyException.class, () -> attendanceRepository.save(doc2));
    }

    @Test
    void uniqueIndex_allowsDifferentLessonSameUser() {
        AttendanceDocument doc1 = AttendanceDocument.builder()
                .lessonId(11L).userId(200L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        AttendanceDocument doc2 = AttendanceDocument.builder()
                .lessonId(12L).userId(200L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(2).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        attendanceRepository.save(doc1);
        AttendanceDocument saved = attendanceRepository.save(doc2);

        assertNotNull(saved.getId());
    }
}
