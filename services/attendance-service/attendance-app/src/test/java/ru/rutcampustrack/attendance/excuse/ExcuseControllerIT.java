package ru.rutcampustrack.attendance.excuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.schedule.grpc.LessonInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ExcuseController} covering AC-1..AC-6 of phase 59.
 *
 * <p>Full REST → service → Mongo → RabbitMQ happy and rejection paths. Uses real
 * MongoDB and RabbitMQ via {@link AbstractAttendanceIntegrationTest} Testcontainers.
 * {@link ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient} and
 * {@link ru.rutcampustrack.attendance.grpc.AcademicGrpcClient} are {@code @MockitoBean}s
 * from the base class (no live gRPC servers).
 *
 * <p><b>Trust-boundary model:</b> X-User-* headers simulate JWT claims injected by the
 * API Gateway; they populate {@link ru.rutcampustrack.attendance.security.RequestContext}
 * through the production {@link ru.rutcampustrack.attendance.security.UserContextFilter}.
 *
 * <p>AC coverage:
 * <ul>
 *   <li>AC-1 — {@link #createExcuse_asPlainStudent_returns201AndPersistsTicket}</li>
 *   <li>AC-2 — {@link #createExcuse_duplicateLessonId_returns409}</li>
 *   <li>AC-3 — {@link #createExcuse_asHeadman_returns409}</li>
 *   <li>AC-4 — {@link #getTicketById_foreignStudent_returns403}</li>
 *   <li>AC-5 — {@link #updateStatus_approvedByHeadman_returns200AndCascadesAttendance}</li>
 *   <li>AC-6 — {@link #updateStatus_headmanSelfApprove_returns409}</li>
 * </ul>
 */
class ExcuseControllerIT extends AbstractAttendanceIntegrationTest {

    private static final Long STUDENT_ID = 100L;
    private static final Long OTHER_STUDENT_ID = 200L;
    private static final Long HEADMAN_ID = 777L;
    private static final Long GROUP_ID = 10L;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExcuseRepository excuseRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), ExcuseTicket.class);
        mongoTemplate.remove(new Query(), AttendanceDocument.class);

        Mockito.reset(scheduleGrpcClient, academicGrpcClient);

        // Default: academic returns a snapshot display name for any user id.
        when(academicGrpcClient.getUserDisplayName(anyLong())).thenReturn("Иванов Иван");

        // Default: return LessonInfo matching the requested ids + student's group (10),
        // so D-25 validation passes. Tests needing a wrong-group lesson override this.
        when(scheduleGrpcClient.getLessonsByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> LessonInfo.newBuilder()
                            .setLessonId(id)
                            .setGroupId(GROUP_ID)
                            .build())
                    .toList();
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String toJson(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private ExcuseTicket seedSubmittedTicket(Long studentId,
                                             ExcuseType type,
                                             List<Long> lessonIds) {
        Instant now = Instant.now();
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(studentId)
                .groupId(GROUP_ID)
                .studentName("Иванов Иван")
                .lessonIds(lessonIds)
                .excuseType(type)
                .comment("test")
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return excuseRepository.save(ticket);
    }

    // =========================================================================
    // AC-1: POST /attendance/excuses — plain STUDENT creates a ticket -> 201
    // =========================================================================
    @Test
    void createExcuse_asPlainStudent_returns201AndPersistsTicket() throws Exception {
        CreateExcuseRequest req = new CreateExcuseRequest(
                List.of(11L, 12L), ExcuseType.ILLNESS, "Болею");

        mockMvc.perform(post("/attendance/excuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req))
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID))
                .andExpect(jsonPath("$.groupId").value(GROUP_ID))
                .andExpect(jsonPath("$.lessonIds.length()").value(2))
                .andExpect(jsonPath("$._links.self.href").exists());

        List<ExcuseTicket> all = excuseRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(ExcuseTicketStatus.SUBMITTED);
        assertThat(all.get(0).getLessonIds()).containsExactly(11L, 12L);
    }

    // =========================================================================
    // AC-2 / D-11: POST with a lessonId already in an active ticket -> 409
    // =========================================================================
    @Test
    void createExcuse_duplicateLessonId_returns409() throws Exception {
        seedSubmittedTicket(STUDENT_ID, ExcuseType.ILLNESS, List.of(21L, 22L));

        CreateExcuseRequest req = new CreateExcuseRequest(
                List.of(22L, 23L), ExcuseType.ILLNESS, "снова");

        mockMvc.perform(post("/attendance/excuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req))
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isConflict());

        // Only the seed ticket remains.
        assertThat(excuseRepository.findAll()).hasSize(1);
    }

    // =========================================================================
    // AC-3 / D-12: POST with X-Is-Headman: true -> 409
    // =========================================================================
    @Test
    void createExcuse_asHeadman_returns409() throws Exception {
        CreateExcuseRequest req = new CreateExcuseRequest(
                List.of(31L), ExcuseType.OTHER, "пропуск старосты");

        mockMvc.perform(post("/attendance/excuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req))
                        .header("X-User-Id", HEADMAN_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isConflict());

        assertThat(excuseRepository.findAll()).isEmpty();
    }

    // =========================================================================
    // AC-4 / D-14: GET /{id} from a different plain student -> 403
    // =========================================================================
    @Test
    void getTicketById_foreignStudent_returns403() throws Exception {
        ExcuseTicket owned = seedSubmittedTicket(STUDENT_ID, ExcuseType.ILLNESS, List.of(41L));

        mockMvc.perform(get("/attendance/excuses/{id}", owned.getId())
                        .header("X-User-Id", OTHER_STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // AC-5 / D-16: PATCH /{id}/status {approved} as headman -> 200 + cascade
    // =========================================================================
    @Test
    void updateStatus_approvedByHeadman_returns200AndCascadesAttendance() throws Exception {
        ExcuseTicket ticket = seedSubmittedTicket(
                STUDENT_ID, ExcuseType.ILLNESS, List.of(51L, 52L, 53L));

        UpdateExcuseStatusRequest decision = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, "справка");

        mockMvc.perform(patch("/attendance/excuses/{id}/status", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(decision))
                        .header("X-User-Id", HEADMAN_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.decisionBy").value(HEADMAN_ID))
                .andExpect(jsonPath("$.decisionComment").value("справка"));

        Optional<ExcuseTicket> persisted = excuseRepository.findById(ticket.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(ExcuseTicketStatus.APPROVED);

        List<AttendanceDocument> docs = attendanceRepository.findAll();
        assertThat(docs).hasSize(3);
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getUserId()).isEqualTo(STUDENT_ID);
            assertThat(d.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
        });
        assertThat(docs.stream().map(AttendanceDocument::getLessonId).toList())
                .containsExactlyInAnyOrder(51L, 52L, 53L);
    }

    // =========================================================================
    // AC-6 / D-13: headman approves their OWN ticket -> 409
    // =========================================================================
    @Test
    void updateStatus_headmanSelfApprove_returns409() throws Exception {
        // A plain student created the ticket, but now the same user id comes in as a headman
        // -> self-approve. We seed the ticket under HEADMAN_ID (the user approving).
        ExcuseTicket ownTicket = seedSubmittedTicket(HEADMAN_ID, ExcuseType.ILLNESS, List.of(61L));

        UpdateExcuseStatusRequest decision = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, "own");

        mockMvc.perform(patch("/attendance/excuses/{id}/status", ownTicket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(decision))
                        .header("X-User-Id", HEADMAN_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isConflict());

        // Ticket status must NOT have changed.
        Optional<ExcuseTicket> still = excuseRepository.findById(ownTicket.getId());
        assertThat(still).isPresent();
        assertThat(still.get().getStatus()).isEqualTo(ExcuseTicketStatus.SUBMITTED);
        assertThat(attendanceRepository.findAll()).isEmpty();
    }

    // =========================================================================
    // D-18: re-approving an already APPROVED ticket -> 409
    // =========================================================================
    @Test
    void updateStatus_alreadyDecided_returns409() throws Exception {
        // Seed an already-approved ticket.
        Instant now = Instant.now();
        ExcuseTicket approved = ExcuseTicket.builder()
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .studentName("Иванов Иван")
                .lessonIds(List.of(71L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.APPROVED)
                .decisionBy(HEADMAN_ID)
                .decisionAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        approved = excuseRepository.save(approved);

        UpdateExcuseStatusRequest decision = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.REJECTED, "передумал");

        mockMvc.perform(patch("/attendance/excuses/{id}/status", approved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(decision))
                        .header("X-User-Id", HEADMAN_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isConflict());
    }
}
