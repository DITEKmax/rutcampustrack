package ru.rutcampustrack.attendance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration proof that the retired legacy endpoint is fail-closed.
 *
 * <p>The seeded HEADMAN/ABSENT document is deliberately observed before and
 * after the request so a future controller change cannot silently resurrect the
 * old overwrite, deduplication or event path.</p>
 */
class CheckinIT extends AbstractAttendanceIntegrationTest {

    private static final long LESSON_ID = 1L;
    private static final long STUDENT_ID = 100L;
    private static final long HEADMAN_ID = 900L;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        mongoTemplate.getCollection("student_checkin_receipts").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("student_checkin_pairs").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("attendance_outbox").deleteMany(new org.bson.Document());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        Mockito.reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService, geofenceService);
    }

    @Test
    void legacyCheckin_returns410AndPreservesManualAbsenceWithoutSideEffects() throws Exception {
        Instant markedAt = Instant.parse("2026-09-06T07:00:00Z");
        AttendanceDocument seeded = attendanceRepository.save(AttendanceDocument.builder()
                .lessonId(LESSON_ID)
                .userId(STUDENT_ID)
                .groupId(10L)
                .subjectId(5L)
                .semesterId(1L)
                .lessonNumber(1)
                .lessonDate(LocalDate.of(2026, 9, 6))
                .status(AttendanceStatus.ABSENT)
                .source(AttendanceSource.HEADMAN)
                .markedBy(HEADMAN_ID)
                .createdAt(markedAt)
                .updatedAt(markedAt)
                .build());
        long attendanceCountBefore = attendanceRepository.count();
        long receiptsBefore = mongoTemplate.getCollection("student_checkin_receipts").countDocuments();
        long pairsBefore = mongoTemplate.getCollection("student_checkin_pairs").countDocuments();
        long outboxBefore = mongoTemplate.getCollection("attendance_outbox").countDocuments();

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckinRequest(55.7558, 37.6173)))
                        .header("X-User-Id", Long.toString(STUDENT_ID))
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.type").value(
                        "https://api.rutcampustrack.ru/problems/legacy-checkin-retired"))
                .andExpect(jsonPath("$.detail").value(
                        "Используйте канонический student check-in API"));

        AttendanceDocument after = attendanceRepository
                .findByLessonIdAndUserId(LESSON_ID, STUDENT_ID)
                .orElseThrow();
        assertThat(after.getId()).isEqualTo(seeded.getId());
        assertThat(after.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(after.getSource()).isEqualTo(AttendanceSource.HEADMAN);
        assertThat(after.getMarkedBy()).isEqualTo(HEADMAN_ID);
        assertThat(after.getCreatedAt()).isEqualTo(markedAt);
        assertThat(after.getUpdatedAt()).isEqualTo(markedAt);
        assertThat(attendanceRepository.count()).isEqualTo(attendanceCountBefore);
        assertThat(mongoTemplate.getCollection("student_checkin_receipts").countDocuments())
                .isEqualTo(receiptsBefore);
        assertThat(mongoTemplate.getCollection("student_checkin_pairs").countDocuments())
                .isEqualTo(pairsBefore);
        assertThat(mongoTemplate.getCollection("attendance_outbox").countDocuments())
                .isEqualTo(outboxBefore);
        assertThat(redisTemplate.getConnectionFactory().getConnection().serverCommands().dbSize())
                .isZero();
        assertThat(outboxStorage.findPending(100)).isEmpty();
        verifyNoInteractions(scheduleGrpcClient, academicGrpcClient, semesterCacheService, geofenceService);
    }
}
