package ru.rutcampustrack.attendance.integration;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies INFRA-02: AttendanceStatus and AttendanceSource are stored as lowercase strings in MongoDB.
 */
class EnumSerializationTest extends AbstractAttendanceIntegrationTest {

    @AfterEach
    void cleanup() {
        attendanceRepository.deleteAll();
    }

    @Test
    void attendanceStatus_storedAsLowercaseString() {
        AttendanceDocument doc = AttendanceDocument.builder()
                .lessonId(2L).userId(200L).groupId(20L).subjectId(10L)
                .semesterId(1L).lessonNumber(2).lessonDate(LocalDate.now())
                .status(AttendanceStatus.FREE_ATTENDANCE).source(AttendanceSource.HEADMAN)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        attendanceRepository.save(doc);

        // Read raw document from MongoDB to verify lowercase storage
        Document raw = mongoTemplate.getCollection("attendances")
                .find(new Document("lesson_id", 2L))
                .first();

        assertNotNull(raw);
        assertEquals("free_attendance", raw.getString("status"));
        assertEquals("headman", raw.getString("source"));

        // Verify round-trip through Spring Data
        AttendanceDocument loaded = attendanceRepository.findAll().stream()
                .filter(d -> d.getLessonId().equals(2L))
                .findFirst().orElseThrow();
        assertEquals(AttendanceStatus.FREE_ATTENDANCE, loaded.getStatus());
        assertEquals(AttendanceSource.HEADMAN, loaded.getSource());
    }

    @Test
    void attendanceStatus_absent_storedAsLowercaseString() {
        AttendanceDocument doc = AttendanceDocument.builder()
                .lessonId(3L).userId(300L).groupId(30L).subjectId(15L)
                .semesterId(1L).lessonNumber(3).lessonDate(LocalDate.now())
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        attendanceRepository.save(doc);

        Document raw = mongoTemplate.getCollection("attendances")
                .find(new Document("lesson_id", 3L))
                .first();

        assertNotNull(raw);
        assertEquals("absent", raw.getString("status"));
        assertEquals("auto_scheduler", raw.getString("source"));
    }
}
