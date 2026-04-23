package ru.rutcampustrack.attendance.latecheckin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinDecisionRequest;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc + Testcontainers happy-path IT для LateCheckinController (M09 G3.2, 14 P0-1).
 *
 * <p>Покрывает три endpoint'а:
 * <ul>
 *   <li>POST /attendance/late-checkin/{lessonId} — студент создаёт запрос</li>
 *   <li>GET  /attendance/late-checkin/pending — староста видит PENDING группы</li>
 *   <li>POST /attendance/late-checkin/{requestId}/decision — staros решает</li>
 * </ul>
 *
 * <p>gRPC-клиенты — {@code @MockitoBean} из базового класса. Реальная Mongo
 * через Testcontainers ({@link AbstractAttendanceIntegrationTest}).
 * X-User-* заголовки имитируют JWT-claims от Gateway.
 */
class LateCheckinControllerIT extends AbstractAttendanceIntegrationTest {

    private static final Long STUDENT_ID = 100L;
    private static final Long HEADMAN_ID = 777L;
    private static final Long OTHER_STUDENT_ID = 200L;
    private static final Long GROUP_ID = 10L;
    private static final Long LESSON_ID = 42L;
    private static final Long SUBJECT_ID = 7L;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LateCheckinRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), LateCheckinRequest.class);
        mongoTemplate.remove(new Query(), AttendanceDocument.class);

        Mockito.reset(scheduleGrpcClient, academicGrpcClient);

        when(academicGrpcClient.getUserDisplayName(anyLong())).thenReturn("Иванов И.");
        when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID, "Математика"));
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(
                LessonResponse.newBuilder()
                        .setId(LESSON_ID)
                        .setGroupId(GROUP_ID)
                        .setSubjectId(SUBJECT_ID)
                        .setDate("2026-04-23")
                        .setLessonNumber(3)
                        .setStatus("active")
                        .build());
    }

    private String toJson(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private LateCheckinRequest seedPending(Long studentId) {
        Instant now = Instant.now();
        return repository.save(LateCheckinRequest.builder()
                .studentId(studentId)
                .groupId(GROUP_ID)
                .lessonId(LESSON_ID)
                .studentName("Иванов И.")
                .status(LateCheckinRequestStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    // ==================================================== POST /{lessonId}

    @Test
    void createRequest_asStudent_returns201AndPersistsPending() throws Exception {
        mockMvc.perform(post("/attendance/late-checkin/{lessonId}", LESSON_ID)
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID))
                .andExpect(jsonPath("$.groupId").value(GROUP_ID))
                .andExpect(jsonPath("$.lessonId").value(LESSON_ID))
                .andExpect(jsonPath("$.status").value("pending"));

        List<LateCheckinRequest> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(LateCheckinRequestStatus.PENDING);
    }

    // ==================================================== GET /pending

    @Test
    void listPending_asHeadman_returnsOnlyGroupPending() throws Exception {
        // Seed PENDING for STUDENT_ID (same group) and OTHER_STUDENT_ID (foreign group).
        seedPending(STUDENT_ID);
        LateCheckinRequest foreign = LateCheckinRequest.builder()
                .studentId(OTHER_STUDENT_ID)
                .groupId(99L)
                .lessonId(LESSON_ID)
                .studentName("Петров П.")
                .status(LateCheckinRequestStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        repository.save(foreign);

        mockMvc.perform(get("/attendance/late-checkin/pending")
                        .header("X-User-Id", HEADMAN_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.lateCheckinRequestResponseList.length()").value(1))
                .andExpect(jsonPath("$._embedded.lateCheckinRequestResponseList[0].studentId").value(STUDENT_ID));
    }

    @Test
    void listPending_asNonHeadman_returns403() throws Exception {
        mockMvc.perform(get("/attendance/late-checkin/pending")
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());
    }

    // ==================================================== POST /{requestId}/decision

    @Test
    void decideRequest_approveByHeadman_returns200AndMarksAttendance() throws Exception {
        LateCheckinRequest pending = seedPending(STUDENT_ID);
        LateCheckinDecisionRequest body = new LateCheckinDecisionRequest(true);

        mockMvc.perform(post("/attendance/late-checkin/{id}/decision", pending.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body))
                        .header("X-User-Id", HEADMAN_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.decisionBy").value(HEADMAN_ID));

        LateCheckinRequest reloaded = repository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LateCheckinRequestStatus.APPROVED);
        assertThat(reloaded.getDecisionBy()).isEqualTo(HEADMAN_ID);
    }

    @Test
    void decideRequest_asNonHeadman_returns403() throws Exception {
        LateCheckinRequest pending = seedPending(STUDENT_ID);
        LateCheckinDecisionRequest body = new LateCheckinDecisionRequest(true);

        mockMvc.perform(post("/attendance/late-checkin/{id}/decision", pending.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body))
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());

        LateCheckinRequest reloaded = repository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LateCheckinRequestStatus.PENDING);
    }
}
