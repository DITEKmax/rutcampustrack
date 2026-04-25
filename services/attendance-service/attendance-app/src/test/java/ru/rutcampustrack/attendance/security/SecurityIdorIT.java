package ru.rutcampustrack.attendance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G9 (NEW-31) — IDOR-проверки attendance-service.
 *
 * <p>Каждый сценарий: студент/headman A → endpoint, оперирующий ресурсом
 * группы/студента B → 403.
 */
class SecurityIdorIT extends AbstractAttendanceIntegrationTest {

    @Autowired private ObjectMapper objectMapper;

    private final Long groupAId = 4001L;
    private final Long groupBId = 4002L;
    private final Long studentAId = 7001L;
    private final Long headmanAId = 7002L;
    private final Long studentBId = 7003L;
    private final Long headmanBId = 7004L;

    private String excuseTicketBId;
    private String lateCheckinRequestBId;

    @BeforeEach
    void seed() {
        mongoTemplate.remove(new Query(), ExcuseTicket.class);
        mongoTemplate.remove(new Query(), LateCheckinRequest.class);

        // Excuse-тикет группы B (автор — studentB, SUBMITTED)
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(studentBId)
                .groupId(groupBId)
                .lessonIds(List.of(8001L))
                .excuseType(ru.rutcampustrack.attendance.contract.enums.ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        excuseTicketBId = mongoTemplate.save(ticket).getId();

        // Late-checkin запрос группы B (автор — studentB, PENDING)
        LateCheckinRequest req = LateCheckinRequest.builder()
                .studentId(studentBId)
                .groupId(groupBId)
                .lessonId(8001L)
                .studentName("Студ Б.")
                .status(ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        lateCheckinRequestBId = mongoTemplate.save(req).getId();
    }

    private MockHttpServletRequestBuilder asStudentA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", studentAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", groupAId)
                .header("X-Is-Headman", "false");
    }

    private MockHttpServletRequestBuilder asHeadmanA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", headmanAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", groupAId)
                .header("X-Is-Headman", "true");
    }

    // ============================================================
    // Excuse IDOR
    // ============================================================

    @Test
    void getTicketById_foreignGroup_returns403() throws Exception {
        // Student A не автор и не headman группы B → 403
        mockMvc.perform(asStudentA(get("/attendance/excuses/{id}", excuseTicketBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTicketById_foreignGroupHeadman_returns403() throws Exception {
        // Headman A — староста группы A, не группы B → 403
        mockMvc.perform(asHeadmanA(get("/attendance/excuses/{id}", excuseTicketBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupTickets_foreignGroupId_returns403() throws Exception {
        // path-param: /excuses/group/{groupId}
        mockMvc.perform(asHeadmanA(
                        get("/attendance/excuses/group/{gid}", groupBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateExcuseStatus_foreignGroup_returns403() throws Exception {
        UpdateExcuseStatusRequest body = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, null);
        mockMvc.perform(asHeadmanA(
                        patch("/attendance/excuses/{id}/status", excuseTicketBId)
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(body))))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Late-checkin IDOR
    // ============================================================

    @Test
    void decideLateCheckin_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(
                        post("/attendance/late-checkin/{id}/decision", lateCheckinRequestBId)
                                .contentType("application/json")
                                .content("{\"approved\":true}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPendingLateCheckin_plainStudent_returns403() throws Exception {
        // Plain student (не headman) → AccessDenied (только headman может листать pending)
        mockMvc.perform(asStudentA(get("/attendance/late-checkin/pending")))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Marking IDOR — headman A → ученик/lesson группы B
    // ============================================================

    @Test
    void markAttendance_plainStudentNotHeadman_returns403() throws Exception {
        // Plain student (не headman) → AccessDenied (только headman может ставить mark)
        mockMvc.perform(asStudentA(
                        put("/attendance/lessons/{lid}/students/{uid}", 8001L, studentBId)
                                .contentType("application/json")
                                .content("{\"status\":\"PRESENT\"}")))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Report IDOR
    // ============================================================

    @Test
    void getJournal_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(
                        get("/attendance/reports/journal")
                                .param("groupId", groupBId.toString())
                                .param("subjectId", "100")
                                .param("dateFrom", "2041-09-01")
                                .param("dateTo", "2041-12-31")))
                .andExpect(status().isForbidden());
    }

    @Test
    void plainStudent_cannotViewJournal() throws Exception {
        mockMvc.perform(asStudentA(
                        get("/attendance/reports/journal")
                                .param("groupId", groupAId.toString())
                                .param("subjectId", "100")
                                .param("dateFrom", "2041-09-01")
                                .param("dateTo", "2041-12-31")))
                .andExpect(status().isForbidden());
    }
}
