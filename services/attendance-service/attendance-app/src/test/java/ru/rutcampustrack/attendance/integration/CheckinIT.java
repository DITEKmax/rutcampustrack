package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
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

/**
 * Integration proof that the retired legacy endpoint is fail-closed.
 *
 * <p>The seeded HEADMAN/ABSENT document is deliberately observed before and after the request so a
 * future controller change cannot silently resurrect the old overwrite, deduplication or event
 * path.
 */
class CheckinIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "checkinit", 6);
    }

    private static final long LESSON_ID = 1L;
    private static final long STUDENT_ID = 100L;
    private static final long HEADMAN_ID = 900L;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired private ru.rutcampustrack.shared.security.InternalJwtTestFactory tokens;

    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        mongoTemplate.getCollection("student_checkin_receipts").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("student_checkin_pairs").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("attendance_outbox").deleteMany(new org.bson.Document());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        Mockito.reset(
                scheduleGrpcClient, academicGrpcClient, semesterCacheService, geofenceService);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "legacy retirement seeded={0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void legacyCheckin_returns410AndPreservesManualAbsenceWithoutSideEffects(boolean seededState)
            throws Exception {
        Instant markedAt = Instant.parse("2026-09-06T07:00:00Z");
        AttendanceDocument seeded =
                seededState
                        ? attendanceRepository.save(
                                AttendanceDocument.builder()
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
                                        .build())
                        : null;
        var rawBefore =
                mongoTemplate
                        .getCollection("attendances")
                        .find()
                        .into(new java.util.ArrayList<org.bson.Document>());
        long attendanceCountBefore = attendanceRepository.count();
        long receiptsBefore =
                mongoTemplate.getCollection("student_checkin_receipts").countDocuments();
        long pairsBefore = mongoTemplate.getCollection("student_checkin_pairs").countDocuments();
        long outboxBefore = mongoTemplate.getCollection("attendance_outbox").countDocuments();

        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(
                "X-Internal-Token",
                tokens.validToken(
                        STUDENT_ID,
                        java.util.UUID.randomUUID(),
                        1L,
                        1L,
                        "STUDENT",
                        "ACTIVE",
                        10L,
                        false,
                        false));
        var response =
                http.postForEntity(
                        "/attendance/checkin",
                        new org.springframework.http.HttpEntity<>(
                                new CheckinRequest(55.7558, 37.6173), headers),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(410);
        var problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://api.rutcampustrack.ru/problems/legacy-checkin-retired");
        assertThat(problem.path("status").asInt()).isEqualTo(410);
        assertThat(problem.path("detail").asText())
                .isEqualTo("Используйте канонический student check-in API");
        assertThat(
                        mongoTemplate
                                .getCollection("attendances")
                                .find()
                                .into(new java.util.ArrayList<org.bson.Document>()))
                .containsExactlyElementsOf(rawBefore);
        if (seededState) {
            AttendanceDocument after =
                    attendanceRepository
                            .findByLessonIdAndUserId(LESSON_ID, STUDENT_ID)
                            .orElseThrow();
            assertThat(after.getId()).isEqualTo(seeded.getId());
            assertThat(after.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(after.getSource()).isEqualTo(AttendanceSource.HEADMAN);
            assertThat(after.getMarkedBy()).isEqualTo(HEADMAN_ID);
            assertThat(after.getCreatedAt()).isEqualTo(markedAt);
            assertThat(after.getUpdatedAt()).isEqualTo(markedAt);
        }
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
        verifyNoInteractions(
                scheduleGrpcClient, academicGrpcClient, semesterCacheService, geofenceService);
    }
}
