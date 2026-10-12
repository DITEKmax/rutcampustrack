package ru.rutcampustrack.attendance.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;

/**
 * Verifies INFRA-02: AttendanceStatus and AttendanceSource are stored as lowercase strings in
 * MongoDB.
 */
class EnumSerializationIT extends AbstractAttendanceIntegrationTest {

    @AfterEach
    void cleanup() {
        attendanceRepository.deleteAll();
        mongoTemplate.remove(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id")
                                .in("locale-ticket", "locale-null-ticket")),
                ExcuseTicket.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"write", "read"})
    @ResourceLock("default-locale")
    void localeKeepsCanonicalAttendanceAndExcuseCodes(String boundary) {
        AttendanceDocument doc =
                AttendanceDocument.builder()
                        .lessonId(6001L)
                        .userId(6002L)
                        .groupId(6003L)
                        .subjectId(6004L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 3, 5))
                        .status(AttendanceStatus.FREE_ATTENDANCE)
                        .source(AttendanceSource.LATE_CHECKIN)
                        .createdAt(Instant.parse("2026-03-05T10:00:00Z"))
                        .updatedAt(Instant.parse("2026-03-05T10:00:00Z"))
                        .build();
        ExcuseTicket ticket =
                ExcuseTicket.builder()
                        .id("locale-ticket")
                        .studentId(6002L)
                        .groupId(6003L)
                        .semesterId(1L)
                        .lessonIds(List.of(6001L))
                        .excuseType(ExcuseType.ILLNESS)
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .build();
        // Seed canonical native data before exercising the reader under Turkish locale.
        attendanceRepository.save(doc);
        mongoTemplate.save(ticket);
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            if (boundary.equals("write")) {
                attendanceRepository.save(doc);
                mongoTemplate.save(ticket);
                Document raw =
                        mongoTemplate
                                .getCollection("attendances")
                                .find(new Document("lesson_id", 6001L))
                                .first();
                Document rawTicket =
                        mongoTemplate
                                .getCollection("excuse_tickets")
                                .find(new Document("_id", "locale-ticket"))
                                .first();
                assertNotNull(raw);
                assertNotNull(rawTicket);
                org.junit.jupiter.api.Assertions.assertAll(
                        () -> assertEquals("free_attendance", raw.getString("status")),
                        () -> assertEquals("late_checkin", raw.getString("source")),
                        () -> assertEquals("illness", rawTicket.getString("excuse_type")),
                        () -> assertEquals("submitted", rawTicket.getString("status")));
            } else {
                var loaded = attendanceRepository.findById(doc.getId()).orElseThrow();
                var loadedTicket = mongoTemplate.findById("locale-ticket", ExcuseTicket.class);
                assertEquals(AttendanceStatus.FREE_ATTENDANCE, loaded.getStatus());
                assertEquals(AttendanceSource.LATE_CHECKIN, loaded.getSource());
                assertNotNull(loadedTicket);
                assertEquals(ExcuseType.ILLNESS, loadedTicket.getExcuseType());
                assertEquals(ExcuseTicketStatus.SUBMITTED, loadedTicket.getStatus());
                assertEquals(doc.getLessonDate(), loaded.getLessonDate());
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void unknownCodeFailsClosedAndOptionalNullKeepsItsMeaning() {
        AttendanceDocument doc =
                AttendanceDocument.builder()
                        .lessonId(6201L)
                        .userId(6202L)
                        .groupId(6203L)
                        .subjectId(6204L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 3, 5))
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.HEADMAN)
                        .createdAt(Instant.parse("2026-03-05T10:00:00Z"))
                        .updatedAt(Instant.parse("2026-03-05T10:00:00Z"))
                        .build();
        attendanceRepository.save(doc);
        Document raw =
                mongoTemplate
                        .getCollection("attendances")
                        .find(new Document("lesson_id", 6201L))
                        .first();
        assertNotNull(raw);
        mongoTemplate
                .getCollection("attendances")
                .updateOne(
                        new Document("_id", raw.get("_id")),
                        new Document("$set", new Document("status", "future_status")));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> attendanceRepository.findById(doc.getId()))
                .isInstanceOf(org.springframework.core.convert.ConversionFailedException.class);
        ExcuseTicket optional =
                ExcuseTicket.builder()
                        .id("locale-null-ticket")
                        .studentId(6202L)
                        .groupId(6203L)
                        .semesterId(1L)
                        .lessonIds(List.of(6201L))
                        .excuseType(null)
                        .status(ExcuseTicketStatus.DRAFT)
                        .build();
        mongoTemplate.save(optional);
        ExcuseTicket loaded = mongoTemplate.findById(optional.getId(), ExcuseTicket.class);
        assertNotNull(loaded);
        org.junit.jupiter.api.Assertions.assertNull(loaded.getExcuseType());
        assertEquals(ExcuseTicketStatus.DRAFT, loaded.getStatus());
    }

    @Test
    void attendanceStatus_storedAsLowercaseString() {
        AttendanceDocument doc =
                AttendanceDocument.builder()
                        .lessonId(2L)
                        .userId(200L)
                        .groupId(20L)
                        .subjectId(10L)
                        .semesterId(1L)
                        .lessonNumber(2)
                        .lessonDate(LocalDate.now())
                        .status(AttendanceStatus.FREE_ATTENDANCE)
                        .source(AttendanceSource.HEADMAN)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        attendanceRepository.save(doc);

        // Read raw document from MongoDB to verify lowercase storage
        Document raw =
                mongoTemplate
                        .getCollection("attendances")
                        .find(new Document("lesson_id", 2L))
                        .first();

        assertNotNull(raw);
        assertEquals("free_attendance", raw.getString("status"));
        assertEquals("headman", raw.getString("source"));

        // Verify round-trip through Spring Data
        AttendanceDocument loaded =
                attendanceRepository.findAll().stream()
                        .filter(d -> d.getLessonId().equals(2L))
                        .findFirst()
                        .orElseThrow();
        assertEquals(AttendanceStatus.FREE_ATTENDANCE, loaded.getStatus());
        assertEquals(AttendanceSource.HEADMAN, loaded.getSource());
    }

    @Test
    void attendanceStatus_absent_storedAsLowercaseString() {
        AttendanceDocument doc =
                AttendanceDocument.builder()
                        .lessonId(3L)
                        .userId(300L)
                        .groupId(30L)
                        .subjectId(15L)
                        .semesterId(1L)
                        .lessonNumber(3)
                        .lessonDate(LocalDate.now())
                        .status(AttendanceStatus.ABSENT)
                        .source(AttendanceSource.AUTO_SCHEDULER)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        attendanceRepository.save(doc);

        Document raw =
                mongoTemplate
                        .getCollection("attendances")
                        .find(new Document("lesson_id", 3L))
                        .first();

        assertNotNull(raw);
        assertEquals("absent", raw.getString("status"));
        assertEquals("auto_scheduler", raw.getString("source"));
    }
}
