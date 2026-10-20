package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Integration tests for manual attendance marking write path. Proves MARK-01, MARK-02, INFRA-06
 * requirements via full HTTP stack with real MongoDB.
 *
 * <p>All tests use MockMvc performing HTTP PUT to /attendance/lessons/{lessonId}/students/{userId}.
 * GeofenceService is mocked to prevent @PostConstruct gRPC calls. AttendanceEventPublisher is spied
 * to verify event publication.
 */
class MarkingIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "markingit", 3);
    }

    @Autowired private ObjectMapper objectMapper;

    @Autowired private RequestAttachmentRepository requestAttachmentRepository;

    @MockitoSpyBean protected AttendanceEventPublisher attendanceEventPublisher;

    private static final Long LESSON_ID = 42L;
    private static final Long USER_ID = 99L;
    private static final Long HEADMAN_USER_ID = 50L;
    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 5L;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        requestAttachmentRepository.deleteAll();
        mongoTemplate.getCollection("attendance_outbox").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("lesson_actions").deleteMany(new org.bson.Document());
        mongoTemplate.getCollection("student_checkin_pairs").deleteMany(new org.bson.Document());
        Mockito.reset(
                scheduleGrpcClient, academicGrpcClient, semesterCacheService, geofenceService);
        when(scheduleGrpcClient.requireAttendanceMutationReady(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.eq(GROUP_ID)))
                .thenAnswer(inv -> scheduleGrpcClient.getLessonById(inv.getArgument(0)));
        when(academicGrpcClient.isHeadman(HEADMAN_USER_ID, GROUP_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.HeadmanCheckResponse.newBuilder()
                                .setIsHeadman(true)
                                .build());
        when(academicGrpcClient.hasAssistantPermission(
                        org.mockito.ArgumentMatchers.eq(GROUP_ID),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private LessonResponse buildLesson(Long groupId) {
        return LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(groupId)
                .setSubjectId(SUBJECT_ID)
                .setSemesterId(1L)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStartTime("08:00")
                .setEndTime("09:30")
                .setStatus("started")
                .build();
    }

    private LessonResponse buildHistoricalLesson(Long groupId, Long semesterId) {
        return LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(groupId)
                .setSubjectId(SUBJECT_ID)
                .setSemesterId(semesterId)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStartTime("08:00")
                .setEndTime("09:30")
                .setStatus("closed")
                .build();
    }

    private GroupMembersResponse buildGroupMembers(Long... userIds) {
        GroupMembersResponse.Builder builder = GroupMembersResponse.newBuilder();
        for (Long uid : userIds) {
            builder.addStudents(StudentInfo.newBuilder().setUserId(uid).build());
        }
        return builder.build();
    }

    private GroupMembersResponse buildHistoricalGroupMembers(Long semesterId, Long... userIds) {
        GroupMembersResponse.Builder builder =
                GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-04-01")
                        .setSemesterId(semesterId);
        for (Long uid : userIds) {
            builder.addStudents(StudentInfo.newBuilder().setUserId(uid).build());
        }
        return builder.build();
    }

    private void mockHappyPath() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(GROUP_ID));
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(buildHistoricalGroupMembers(1L, USER_ID, 100L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            headmanRequest(AttendanceStatus status) throws Exception {
        return headmanRequest(LESSON_ID, USER_ID, status);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            headmanRequest(Long lessonId, Long userId, AttendanceStatus status) throws Exception {
        MarkRequest request =
                status == AttendanceStatus.EXCUSED
                        ? new MarkRequest(status, ExcuseType.ILLNESS, "test reason")
                        : new MarkRequest(status);
        return put("/attendance/lessons/{lessonId}/students/{userId}", lessonId, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-Id", HEADMAN_USER_ID.toString())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID.toString())
                .header("X-Is-Headman", "true");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            headmanHeaders(
                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                            request) {
        return request.header("X-User-Id", HEADMAN_USER_ID.toString())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID.toString())
                .header("X-Is-Headman", "true");
    }

    // -------------------------------------------------------------------------
    // Test 1 — MARK-01: Happy path — headman marks student in own group
    // -------------------------------------------------------------------------

    @Test
    void mark_headmanMarksStudentInGroup_returns200WithHateoas() throws Exception {
        mockHappyPath();

        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.lessonId").value(LESSON_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$._links.self").exists());

        // Verify document saved in MongoDB
        List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
        assertThat(docs).hasSize(1);
        AttendanceDocument doc = docs.get(0);
        assertThat(doc.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(doc.getSource()).isEqualTo(AttendanceSource.HEADMAN);
        assertThat(doc.getMarkedBy()).isEqualTo(HEADMAN_USER_ID);
        assertThat(doc.getLessonId()).isEqualTo(LESSON_ID);
        assertThat(doc.getUserId()).isEqualTo(USER_ID);
    }

    // -------------------------------------------------------------------------
    // Test 2 — MARK-01: Student not in headman's group returns 403
    // -------------------------------------------------------------------------

    @Test
    void mark_studentNotInGroup_returns403() throws Exception {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(GROUP_ID));
        // Group members does NOT include userId 99
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(buildHistoricalGroupMembers(1L, 100L, 101L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT)).andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 3 — MARK-01: Non-headman cannot access marking endpoint (403)
    // -------------------------------------------------------------------------

    @Test
    void mark_notHeadman_returns403() throws Exception {
        mockHappyPath();
        when(academicGrpcClient.hasAssistantPermission(GROUP_ID, "MARK_ATTENDANCE"))
                .thenReturn(false);
        when(academicGrpcClient.hasAssistantPermission(GROUP_ID, "MANAGE_EXCUSES"))
                .thenReturn(false);

        mockMvc.perform(
                        put("/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new MarkRequest(AttendanceStatus.PRESENT)))
                                .header("X-User-Id", HEADMAN_USER_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Отсутствует право MARK_ATTENDANCE"));

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
        assertThat(requestAttachmentRepository.findAll()).isEmpty();
        verify(attendanceEventPublisher, Mockito.never())
                .publishMarked(
                        Mockito.any(AttendanceDocument.class),
                        org.mockito.ArgumentMatchers.nullable(String.class));
    }

    // -------------------------------------------------------------------------
    // Test 4 — MARK-01: Wrong group lesson returns 403
    // -------------------------------------------------------------------------

    @Test
    void mark_lessonBelongsToDifferentGroup_returns403() throws Exception {
        // Headman is in group 10, but lesson belongs to group 20
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(20L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT)).andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 5 — MARK-02: Upsert updates status — second mark replaces first
    // -------------------------------------------------------------------------

    @Test
    void mark_secondMark_updatesStatusNotDuplicate() throws Exception {
        mockHappyPath();

        // First mark: PRESENT
        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT)).andExpect(status().isOk());

        // Second mark: ABSENT
        mockMvc.perform(headmanRequest(AttendanceStatus.ABSENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABSENT"));

        // Verify MongoDB: still exactly 1 document (upsert, not insert)
        List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 6 — D-14: CANCELLED status is rejected with 400
    // -------------------------------------------------------------------------

    @Test
    void mark_cancelledStatus_returns400() throws Exception {
        mockMvc.perform(headmanRequest(AttendanceStatus.CANCELLED))
                .andExpect(status().isBadRequest());

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 7 — INFRA-06: Event published after successful mark
    // -------------------------------------------------------------------------

    @Test
    void mark_successfulMark_publishesAttendanceMarkedEvent() throws Exception {
        mockHappyPath();

        var response = socketMark(USER_ID, AttendanceStatus.PRESENT);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var stored = attendanceRepository.findByLessonIdAndUserId(LESSON_ID, USER_ID).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(stored.getSource()).isEqualTo(AttendanceSource.HEADMAN);
        var intents = markedIntents();
        assertThat(intents).hasSize(1);
        for (var intent : intents)
            assertThat(
                            ru.rutcampustrack.attendance.events.EventSchemaValidator.validate(
                                    "attendance.marked.json", intent.toString()))
                    .isEmpty();
        assertThat(intents)
                .allSatisfy(
                        envelope -> {
                            assertThat(envelope.path("event_type").asText())
                                    .isEqualTo("attendance.marked");
                            assertThat(envelope.path("event_id").asText()).isNotBlank();
                            assertThat(envelope.path("payload").path("lesson_id").asLong())
                                    .isEqualTo(LESSON_ID);
                            assertThat(envelope.path("payload").path("user_id").asLong())
                                    .isEqualTo(USER_ID);
                            assertThat(envelope.path("payload").path("group_id").asLong())
                                    .isEqualTo(GROUP_ID);
                            assertThat(envelope.path("payload").path("subject_id").asLong())
                                    .isEqualTo(SUBJECT_ID);
                            assertThat(envelope.path("payload").path("lesson_date").asText())
                                    .isEqualTo("2026-04-01");
                            assertThat(envelope.path("payload").path("status").asText())
                                    .isEqualTo("present");
                            assertThat(envelope.path("payload").path("marked_by").asText())
                                    .isEqualTo("headman");
                        });
    }

    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate socket;
    @Autowired private ru.rutcampustrack.shared.security.InternalJwtTestFactory tokens;
    @MockitoSpyBean private ru.rutcampustrack.shared.outbox.OutboxStorage transactionalOutbox;

    private org.springframework.http.HttpHeaders signedHeadman() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(
                "X-Internal-Token",
                tokens.validToken(
                        HEADMAN_USER_ID,
                        java.util.UUID.randomUUID(),
                        1L,
                        1L,
                        "HEADMAN",
                        "ACTIVE",
                        GROUP_ID,
                        true,
                        false));
        return headers;
    }

    private org.springframework.http.ResponseEntity<String> socketMark(
            long user, AttendanceStatus status) {
        return socket.exchange(
                "/attendance/lessons/" + LESSON_ID + "/students/" + user,
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new MarkRequest(status), signedHeadman()),
                String.class);
    }

    private List<com.fasterxml.jackson.databind.JsonNode> markedIntents() {
        return mongoTemplate
                .getCollection("attendance_outbox")
                .find(new org.bson.Document("event_type", "attendance.marked"))
                .into(new java.util.ArrayList<>())
                .stream()
                .map(
                        row -> {
                            try {
                                return objectMapper.readTree(row.getString("payload"));
                            } catch (Exception failure) {
                                throw new AssertionError(failure);
                            }
                        })
                .toList();
    }

    private ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest batch() {
        return new ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest(
                List.of(
                        new ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchItem(
                                LESSON_ID, 99L, AttendanceStatus.PRESENT),
                        new ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchItem(
                                LESSON_ID, 100L, AttendanceStatus.ABSENT),
                        new ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchItem(
                                LESSON_ID, 101L, AttendanceStatus.PRESENT)));
    }

    private void batchAuthority() {
        mockHappyPath();
        when(academicGrpcClient.getGroupMembers(GROUP_ID))
                .thenReturn(buildGroupMembers(99L, 100L, 101L));
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(buildHistoricalGroupMembers(1L, 99L, 100L, 101L));
    }

    @Test
    void batchHttpPersistsExactPairsStableIdsAndDurableIntents() throws Exception {
        batchAuthority();
        var response =
                socket.postForEntity(
                        "/attendance/marks/batch",
                        new org.springframework.http.HttpEntity<>(batch(), signedHeadman()),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var stored = attendanceRepository.findAll();
        assertThat(stored)
                .extracting(
                        row -> row.getLessonId() + "/" + row.getUserId() + "/" + row.getStatus())
                .containsExactlyInAnyOrder("42/99/PRESENT", "42/100/ABSENT", "42/101/PRESENT");
        var intentsBeforeReplay = markedIntents();
        assertThat(intentsBeforeReplay).hasSize(3);
        for (var envelope : intentsBeforeReplay) {
            assertThat(
                            ru.rutcampustrack.attendance.events.EventSchemaValidator.validate(
                                    "attendance.marked.json", envelope.toString()))
                    .isEmpty();
            java.util.UUID.fromString(envelope.path("event_id").asText());
            var payload = envelope.path("payload");
            var mark =
                    stored.stream()
                            .filter(row -> row.getUserId() == payload.path("user_id").asLong())
                            .findFirst()
                            .orElseThrow();
            assertThat(envelope.path("event_type").asText()).isEqualTo("attendance.marked");
            assertThat(payload.path("lesson_id").asLong()).isEqualTo(mark.getLessonId());
            assertThat(payload.path("user_id").asLong()).isEqualTo(mark.getUserId());
            assertThat(payload.path("status").asText())
                    .isEqualTo(mark.getStatus().name().toLowerCase(java.util.Locale.ROOT));
            assertThat(payload.path("marked_by").asText()).isEqualTo("headman");
            assertThat(mark.getSource()).isEqualTo(AttendanceSource.HEADMAN);
            assertThat(mark.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(mark.getSubjectId()).isEqualTo(SUBJECT_ID);
            assertThat(mark.getSemesterId()).isEqualTo(1L);
            assertThat(mark.getLessonNumber()).isEqualTo(1);
            assertThat(mark.getLessonDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(payload.path("group_id").asLong()).isEqualTo(GROUP_ID);
            assertThat(payload.path("subject_id").asLong()).isEqualTo(SUBJECT_ID);
            assertThat(payload.path("lesson_number").asInt()).isEqualTo(1);
            assertThat(payload.path("lesson_date").asText()).isEqualTo("2026-04-01");
        }
        assertThat(intentsBeforeReplay)
                .extracting(envelope -> envelope.path("payload").path("user_id").asLong())
                .containsExactlyInAnyOrder(99L, 100L, 101L);
        assertThat(
                        socket.postForEntity(
                                        "/attendance/marks/batch",
                                        new org.springframework.http.HttpEntity<>(
                                                batch(), signedHeadman()),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        assertThat(attendanceRepository.findAll())
                .extracting(AttendanceDocument::getId)
                .containsExactlyInAnyOrderElementsOf(
                        stored.stream().map(AttendanceDocument::getId).toList());
        assertThat(markedIntents()).containsExactlyInAnyOrderElementsOf(intentsBeforeReplay);
    }

    @Test
    void batchHttpFailureAfterRealOutboxInsertRollsBackAndSameCommandRecovers() {
        batchAuthority();
        var inserted = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(
                        call -> {
                            call.callRealMethod();
                            if ("attendance.marked".equals(call.getArgument(0))
                                    && inserted.incrementAndGet() == 2)
                                throw new IllegalStateException(
                                        "owned failure after second actual event insert");
                            return null;
                        })
                .when(transactionalOutbox)
                .save(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        try {
            var failed =
                    socket.postForEntity(
                            "/attendance/marks/batch",
                            new org.springframework.http.HttpEntity<>(batch(), signedHeadman()),
                            String.class);
            assertThat(failed.getStatusCode().is5xxServerError()).isTrue();
            assertThat(inserted.get()).isEqualTo(2);
            for (String collection :
                    List.of(
                            "attendances",
                            "attendance_outbox",
                            "lesson_actions",
                            "student_checkin_pairs"))
                assertThat(mongoTemplate.getCollection(collection).countDocuments())
                        .as(collection)
                        .isZero();
        } finally {
            org.mockito.Mockito.reset(transactionalOutbox);
        }
        assertThat(
                        socket.postForEntity(
                                        "/attendance/marks/batch",
                                        new org.springframework.http.HttpEntity<>(
                                                batch(), signedHeadman()),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        assertThat(attendanceRepository.findAll()).hasSize(3);
        assertThat(markedIntents()).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // Test 8 — MARK-01: Immutable fields preserved on second upsert
    // -------------------------------------------------------------------------

    @Test
    void mark_secondMark_preservesImmutableFields() throws Exception {
        mockHappyPath();

        // First mark
        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT)).andExpect(status().isOk());

        AttendanceDocument first =
                mongoTemplate.findOne(
                        Query.query(
                                Criteria.where("lesson_id")
                                        .is(LESSON_ID)
                                        .and("user_id")
                                        .is(USER_ID)),
                        AttendanceDocument.class);
        assertThat(first).isNotNull();
        String originalId = first.getId();

        // Second mark with different status
        mockMvc.perform(headmanRequest(AttendanceStatus.EXCUSED)).andExpect(status().isOk());

        AttendanceDocument second =
                mongoTemplate.findOne(
                        Query.query(
                                Criteria.where("lesson_id")
                                        .is(LESSON_ID)
                                        .and("user_id")
                                        .is(USER_ID)),
                        AttendanceDocument.class);
        assertThat(second).isNotNull();
        // Same MongoDB _id — same document updated, not new one created
        assertThat(second.getId()).isEqualTo(originalId);
        // Status updated
        assertThat(second.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
        // Immutable fields preserved
        assertThat(second.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(second.getSubjectId()).isEqualTo(SUBJECT_ID);
    }

    @Test
    void journal_historicalRoster_authorizedFileAndClear() throws Exception {
        long semesterId = 7L;
        byte[] pdf = "%PDF-1.7\nheadman-journal\n".getBytes(StandardCharsets.US_ASCII);
        when(academicGrpcClient.getHeadmanJournalSemesterState(GROUP_ID, semesterId))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(semesterId)
                                .build());
        when(scheduleGrpcClient.getLessonById(LESSON_ID))
                .thenReturn(buildHistoricalLesson(GROUP_ID, semesterId));
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), semesterId))
                .thenReturn(buildHistoricalGroupMembers(semesterId, USER_ID, 100L));

        // The server checks the historical roster before any write.
        mockMvc.perform(headmanRequest(LESSON_ID, 101L, AttendanceStatus.PRESENT))
                .andExpect(status().isForbidden());

        MockMultipartFile requestPart =
                new MockMultipartFile(
                        "request",
                        "request.json",
                        MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(
                                new MarkRequest(
                                        AttendanceStatus.EXCUSED,
                                        ExcuseType.ILLNESS,
                                        "doctor note")));
        MockMultipartFile file =
                new MockMultipartFile("file", "medical.pdf", MediaType.APPLICATION_PDF_VALUE, pdf);
        MockMultipartHttpServletRequestBuilder mark =
                multipart("/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID);
        mark.file(requestPart);
        mark.file(file);
        mark.with(
                request -> {
                    request.setMethod("PUT");
                    return request;
                });
        mockMvc.perform(headmanHeaders(mark))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCUSED"));

        mockMvc.perform(headmanHeaders(get("/attendance/reports/lesson/{lessonId}", LESSON_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonStatus").value("CLOSED"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].status").value("excused"))
                .andExpect(jsonPath("$.entries[0].excuseType").value("ILLNESS"))
                .andExpect(jsonPath("$.entries[0].attachmentName").value("medical.pdf"))
                .andExpect(jsonPath("$.entries[1].status").doesNotExist());

        mockMvc.perform(
                        headmanHeaders(
                                get(
                                        "/attendance/lessons/{lessonId}/students/{userId}/attachment",
                                        LESSON_ID,
                                        USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));

        mockMvc.perform(
                        headmanHeaders(
                                delete(
                                        "/attendance/lessons/{lessonId}/students/{userId}",
                                        LESSON_ID,
                                        USER_ID)))
                .andExpect(status().isNoContent());
        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
        assertThat(requestAttachmentRepository.findAll()).isEmpty();
    }
}
