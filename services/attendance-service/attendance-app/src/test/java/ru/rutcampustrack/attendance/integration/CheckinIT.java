package ru.rutcampustrack.attendance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for geo-checkin write path.
 * Proves CHKN-01 through CHKN-07 and INFRA-06 requirements.
 * Uses real MongoDB, Redis, and RabbitMQ Testcontainers via AbstractAttendanceIntegrationTest.
 *
 * GeofenceService is replaced with @MockitoBean to prevent @PostConstruct gRPC call to Academic Service.
 */
class CheckinIT extends AbstractAttendanceIntegrationTest {

    private static final String EXCHANGE = "rut-uit.events";
    private static final String TEST_QUEUE = "test-checkin-queue";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        // Clean MongoDB between tests (preserve indexes)
        mongoTemplate.remove(new Query(), AttendanceDocument.class);

        // Flush all Redis keys
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // Reset all mocks
        Mockito.reset(scheduleGrpcClient, semesterCacheService, geofenceService);

        // Declare test queue bound to fanout exchange for event verification
        Queue testQueue = new Queue(TEST_QUEUE, false, false, true);
        amqpAdmin.declareQueue(testQueue);
        FanoutExchange exchange = new FanoutExchange(EXCHANGE, true, false);
        amqpAdmin.declareExchange(exchange);
        Binding binding = BindingBuilder.bind(testQueue).to(exchange);
        amqpAdmin.declareBinding(binding);

        // Purge any leftover messages in the test queue
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    // -------------------------------------------------------------------------
    // Helper: build a mock LessonResponse with a wide time window (today)
    // -------------------------------------------------------------------------

    private LessonResponse buildActiveLesson(boolean isGeoBlocked) {
        String today = LocalDate.now(ZoneId.of("Europe/Moscow")).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return LessonResponse.newBuilder()
                .setId(1L)
                .setGroupId(10L)
                .setSubjectId(5L)
                .setDate(today)
                .setStartTime("00:00")
                .setEndTime("23:59")
                .setIsGeoBlocked(isGeoBlocked)
                .setLessonNumber(1)
                .setStatus("active")
                .build();
    }

    private String checkinJson() throws Exception {
        return objectMapper.writeValueAsString(new CheckinRequest(55.7558, 37.6173));
    }

    // -------------------------------------------------------------------------
    // Test 1 — CHKN-01 + CHKN-02: Happy path — writes document with PRESENT
    // -------------------------------------------------------------------------

    @Test
    void checkin_happyPath_returns201AndWritesDocument() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(buildActiveLesson(false));
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(true);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.lessonId").value(1))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$._links.self").exists());

        List<AttendanceDocument> docs = attendanceRepository.findAll();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(docs.get(0).getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
        assertThat(docs.get(0).getUserId()).isEqualTo(100L);
        assertThat(docs.get(0).getGroupId()).isEqualTo(10L);
    }

    // -------------------------------------------------------------------------
    // Test 2 — CHKN-01: Outside geofence returns 422
    // -------------------------------------------------------------------------

    @Test
    void checkin_outsideGeofence_returns422() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(buildActiveLesson(false));
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(false);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(
                        "https://api.rutcampustrack.ru/problems/geofence-violation"));

        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 3 — CHKN-02: No active lesson returns 404
    // -------------------------------------------------------------------------

    @Test
    void checkin_noActiveLesson_returns404() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString()))
                .thenThrow(new ResourceNotFoundException("Lesson", "groupId/timestamp", "10/now"));

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isNotFound());

        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 4 — CHKN-04: Geo-blocked lesson returns 403
    // -------------------------------------------------------------------------

    @Test
    void checkin_lessonGeoBlocked_returns403() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(buildActiveLesson(true));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(
                        "https://api.rutcampustrack.ru/problems/geo-blocked"));

        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 5 — CHKN-05: Repeated checkin upserts onto the same (lesson, user) row
    // -------------------------------------------------------------------------

    @Test
    void checkin_duplicateCheckin_upsertsSameRow() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(buildActiveLesson(false));
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(true);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        // First request succeeds
        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated());

        // Flush Redis dedup so second request reaches MongoDB
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // Second identical request succeeds too (upsert semantics) — it just refreshes the doc.
        // The compound unique index on (lesson_id, user_id) prevents a duplicate row.
        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated());

        // MongoDB must have exactly 1 document — the upsert collapses both requests onto one row.
        assertThat(attendanceRepository.findAll()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Test 6 — CHKN-06: Redis dedup within 5s returns 409 (no document written)
    // -------------------------------------------------------------------------

    @Test
    void checkin_redisDedupAlreadySet_returns409WithNoDocumentWritten() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(buildActiveLesson(false));
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(true);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        // Pre-set the Redis dedup key to simulate a lock already held
        String dedupKey = "attendance:dedup:1:100";
        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofSeconds(5));

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(
                        "https://api.rutcampustrack.ru/problems/conflict"));

        // No document should be written (dedup fires before save)
        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 7 — CHKN-07: Rate limit exceeded returns 429
    // -------------------------------------------------------------------------

    @Test
    void checkin_rateLimitExceeded_returns429() throws Exception {
        // Pre-set rate key at max attempts (3)
        String rateKey = "attendance:rate:100";
        redisTemplate.opsForValue().set(rateKey, "3", Duration.ofSeconds(60));

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value(
                        "https://api.rutcampustrack.ru/problems/rate-limit-exceeded"));

        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 8 — INFRA-06: Successful checkin publishes attendance.marked event
    // -------------------------------------------------------------------------

    @Test
    void checkin_successfulCheckin_publishesAttendanceMarkedEvent() throws Exception {
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(buildActiveLesson(false));
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(true);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinJson())
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated());

        // Verify event was published to the test queue
        flushOutbox();
        org.springframework.amqp.core.Message message = rabbitTemplate.receive(TEST_QUEUE, 5000);
        assertThat(message).isNotNull();

        // M02: raw JSON bytes в body (RabbitOutboxEventSender.send).
        com.fasterxml.jackson.databind.JsonNode envelope =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(message.getBody());
        assertThat(envelope.get("event_type").asText()).isEqualTo("attendance.marked");
        assertThat(envelope.get("event_id").asText()).isNotBlank();
        assertThat(envelope.get("occurred_at").asText()).isNotBlank();

        com.fasterxml.jackson.databind.JsonNode payload = envelope.get("payload");
        assertThat(payload.get("lesson_id")).isNotNull();
        assertThat(payload.get("user_id")).isNotNull();
        assertThat(payload.get("status").asText()).isEqualTo("present");
    }
}
