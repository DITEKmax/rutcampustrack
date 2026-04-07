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
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class MarkingIntegrationTest extends AbstractAttendanceIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

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
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStatus("started")
                .build();
    }

    private GroupMembersResponse buildGroupMembers(Long... userIds) {
        GroupMembersResponse.Builder builder = GroupMembersResponse.newBuilder();
        for (Long uid : userIds) {
            builder.addStudents(StudentInfo.newBuilder().setUserId(uid).build());
        }
        return builder.build();
    }

    private void mockHappyPath() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(GROUP_ID));
        when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(buildGroupMembers(USER_ID, 100L));
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder headmanRequest(
            AttendanceStatus status) throws Exception {
        return put("/attendance/lessons/{lessonId}/students/{userId}", LESSON_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MarkRequest(status)))
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
        when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(buildGroupMembers(100L, 101L));
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

        // Verify AttendanceEventPublisher.publishMarked was called with a valid document
        verify(attendanceEventPublisher).publishMarked(
                org.mockito.ArgumentMatchers.argThat(doc ->
                        doc.getLessonId().equals(LESSON_ID)
                                && doc.getUserId().equals(USER_ID)
                                && doc.getStatus() == AttendanceStatus.PRESENT
                                && doc.getSource() == AttendanceSource.HEADMAN
                )
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
}
