package ru.rutcampustrack.attendance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mock.web.MockMultipartFile;
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

/**
 * Integration tests for manual attendance marking write path.
 * Proves MARK-01, MARK-02, INFRA-06 requirements via full HTTP stack with real MongoDB.
 *
 * All tests use MockMvc performing HTTP PUT to /attendance/lessons/{lessonId}/students/{userId}.
 * GeofenceService is mocked to prevent @PostConstruct gRPC calls.
 * AttendanceEventPublisher is spied to verify event publication.
 */
class MarkingIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestAttachmentRepository requestAttachmentRepository;

    @MockitoSpyBean
    protected AttendanceEventPublisher attendanceEventPublisher;

    private static final Long LESSON_ID = 42L;
    private static final Long USER_ID = 99L;
    private static final Long HEADMAN_USER_ID = 50L;
    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 5L;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        requestAttachmentRepository.deleteAll();
        Mockito.reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService, geofenceService);
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
        GroupMembersResponse.Builder builder = GroupMembersResponse.newBuilder()
                .setAsOfDate("2026-04-01")
                .setSemesterId(semesterId);
        for (Long uid : userIds) {
            builder.addStudents(StudentInfo.newBuilder().setUserId(uid).build());
        }
        return builder.build();
    }

    private void mockHappyPath() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(GROUP_ID));
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(buildHistoricalGroupMembers(1L, USER_ID, 100L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder headmanRequest(
            AttendanceStatus status) throws Exception {
        return headmanRequest(LESSON_ID, USER_ID, status);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder headmanRequest(
            Long lessonId, Long userId, AttendanceStatus status) throws Exception {
        MarkRequest request = status == AttendanceStatus.EXCUSED
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder headmanHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request
                .header("X-User-Id", HEADMAN_USER_ID.toString())
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
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), 1L))
                .thenReturn(buildHistoricalGroupMembers(1L, 100L, 101L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT))
                .andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 3 — MARK-01: Non-headman cannot access marking endpoint (403)
    // -------------------------------------------------------------------------

    @Test
    void mark_notHeadman_returns403() throws Exception {
        mockMvc.perform(put("/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MarkRequest(AttendanceStatus.PRESENT)))
                        .header("X-User-Id", HEADMAN_USER_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 4 — MARK-01: Wrong group lesson returns 403
    // -------------------------------------------------------------------------

    @Test
    void mark_lessonBelongsToDifferentGroup_returns403() throws Exception {
        // Headman is in group 10, but lesson belongs to group 20
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(20L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT))
                .andExpect(status().isForbidden());

        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 5 — MARK-02: Upsert updates status — second mark replaces first
    // -------------------------------------------------------------------------

    @Test
    void mark_secondMark_updatesStatusNotDuplicate() throws Exception {
        mockHappyPath();

        // First mark: PRESENT
        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT))
                .andExpect(status().isOk());

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

        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT))
                .andExpect(status().isOk());

        // Verify AttendanceEventPublisher.publishMarked was called with a valid document.
        // NOTIF unification: 2-arg form (subjectName может быть null если gRPC mock'а нет).
        verify(attendanceEventPublisher).publishMarked(
                org.mockito.ArgumentMatchers.argThat(doc ->
                        doc.getLessonId().equals(LESSON_ID)
                                && doc.getUserId().equals(USER_ID)
                                && doc.getStatus() == AttendanceStatus.PRESENT
                                && doc.getSource() == AttendanceSource.HEADMAN
                ),
                org.mockito.ArgumentMatchers.any()
        );
    }

    // -------------------------------------------------------------------------
    // Test 8 — MARK-01: Immutable fields preserved on second upsert
    // -------------------------------------------------------------------------

    @Test
    void mark_secondMark_preservesImmutableFields() throws Exception {
        mockHappyPath();

        // First mark
        mockMvc.perform(headmanRequest(AttendanceStatus.PRESENT))
                .andExpect(status().isOk());

        AttendanceDocument first = mongoTemplate.findOne(
                Query.query(Criteria.where("lesson_id").is(LESSON_ID).and("user_id").is(USER_ID)),
                AttendanceDocument.class);
        assertThat(first).isNotNull();
        String originalId = first.getId();

        // Second mark with different status
        mockMvc.perform(headmanRequest(AttendanceStatus.EXCUSED))
                .andExpect(status().isOk());

        AttendanceDocument second = mongoTemplate.findOne(
                Query.query(Criteria.where("lesson_id").is(LESSON_ID).and("user_id").is(USER_ID)),
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
        when(scheduleGrpcClient.getLessonById(LESSON_ID))
                .thenReturn(buildHistoricalLesson(GROUP_ID, semesterId));
        when(academicGrpcClient.getGroupMembers(
                GROUP_ID, LocalDate.of(2026, 4, 1), semesterId))
                .thenReturn(buildHistoricalGroupMembers(semesterId, USER_ID, 100L));

        // The server checks the historical roster before any write.
        mockMvc.perform(headmanRequest(LESSON_ID, 101L, AttendanceStatus.PRESENT))
                .andExpect(status().isForbidden());

        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(
                        new MarkRequest(AttendanceStatus.EXCUSED, ExcuseType.ILLNESS, "doctor note")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "medical.pdf", MediaType.APPLICATION_PDF_VALUE, pdf);
        MockMultipartHttpServletRequestBuilder mark = multipart(
                "/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID);
        mark.file(requestPart);
        mark.file(file);
        mark.with(request -> {
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

        mockMvc.perform(headmanHeaders(get(
                        "/attendance/lessons/{lessonId}/students/{userId}/attachment",
                        LESSON_ID, USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));

        mockMvc.perform(headmanHeaders(delete(
                        "/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID)))
                .andExpect(status().isNoContent());
        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
        assertThat(requestAttachmentRepository.findAll()).isEmpty();
    }
}
