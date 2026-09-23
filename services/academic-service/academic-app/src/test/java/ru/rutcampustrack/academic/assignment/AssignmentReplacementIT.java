package ru.rutcampustrack.academic.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end Academic operation, authorization, and actor/key replay evidence. */
@AutoConfigureMockMvc
class AssignmentReplacementIT extends AbstractAssignmentAuthorityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ScheduleGrpcClient scheduleGrpcClient;

    private Fixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = newFixture("assignment-replacement");
    }

    @Test
    void pendingActivationIsFencedAndExactReplayCompletesAfterScheduleCommit()
            throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A teacher replacement", "lecture");
        LocalDate sourceFrom = fixture.dateFrom().plusDays(1);
        LocalDate sourceEnd = fixture.dateFrom().plusDays(20);
        LocalDate effectiveFrom = sourceFrom.plusDays(7);
        long sourceAssignmentId = createSourceAssignment(subjectId, sourceFrom, sourceEnd);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO assignments
                    (teacher_id, subject_id, group_id, semester_id, lesson_type,
                     valid_from, valid_until_exclusive, lifecycle_state)
                VALUES (?, ?, ?, ?, 'lecture'::subject_type, ?, ?, 'ACTIVE')
                """, fixture.teacher1Id(), subjectId, fixture.groupId(), fixture.semesterId(),
                effectiveFrom, effectiveFrom)).isInstanceOf(DataAccessException.class);
        configureScheduleReceipts();

        mockMvc.perform(headman(MockMvcRequestBuilders.post(
                        "/academic/assignments/{id}/replace", sourceAssignmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(replacementBody(fixture.teacherWithoutGrantId(), effectiveFrom, UUID.randomUUID()))))
                .andExpect(status().isForbidden());

        UUID requestKey = UUID.randomUUID();
        String replacementBody = replacementBody(fixture.teacher2Id(), effectiveFrom, requestKey);
        mockMvc.perform(headman(
                        MockMvcRequestBuilders.post("/academic/assignments/{id}/replace", sourceAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON).content(replacementBody)))
                .andExpect(status().isConflict());

        UUID operationId = jdbcTemplate.queryForObject("""
                SELECT operation_id FROM assignment_replacement_operations WHERE request_key = ?
                """, UUID.class, requestKey);
        long targetAssignmentId = jdbcTemplate.queryForObject("""
                SELECT target_assignment_id FROM assignment_replacement_operations WHERE operation_id = ?
                """, Long.class, operationId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT state FROM assignment_replacement_operations WHERE operation_id = ?
                """, String.class, operationId)).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT valid_until_exclusive FROM assignments WHERE id = ?
                """, LocalDate.class, sourceAssignmentId)).isEqualTo(effectiveFrom);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT lifecycle_state FROM assignments WHERE id = ?
                """, String.class, targetAssignmentId)).isEqualTo("ACTIVE");
        assertRawReplacementUpdatesRejected(operationId, sourceAssignmentId, targetAssignmentId,
                effectiveFrom.plusDays(1));

        mockMvc.perform(headman(MockMvcRequestBuilders.post(
                        "/academic/assignments/{id}/replace", targetAssignmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(replacementBody(fixture.teacher1Id(), effectiveFrom.plusDays(1), UUID.randomUUID()))))
                .andExpect(status().isConflict());

        JsonNode replaced = objectMapper.readTree(mockMvc.perform(headman(
                        MockMvcRequestBuilders.post("/academic/assignments/{id}/replace", sourceAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON).content(replacementBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(replaced.get("operationId").asText()).isEqualTo(operationId.toString());
        assertThat(replaced.get("targetAssignmentId").asLong()).isEqualTo(targetAssignmentId);
        assertThat(replaced.get("state").asText()).isEqualTo("COMMITTED");
        assertThat(replaced.get("scheduleReceiptState").asText()).isEqualTo("COMMITTED");
        assertThat(replaced.get("sourceValidUntilExclusive").asText()).isEqualTo(effectiveFrom.toString());
        assertThat(replaced.get("targetValidUntilExclusive").asText()).isEqualTo(sourceEnd.toString());
        assertThat(replaced.get("movedCount").asLong()).isEqualTo(2L);
        assertThat(replaced.get("skippedCount").asLong()).isEqualTo(3L);

        JsonNode status = objectMapper.readTree(mockMvc.perform(headman(
                        MockMvcRequestBuilders.get("/academic/assignments/replacements/{id}", operationId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(status.get("operationId").asText()).isEqualTo(operationId.toString());
        assertThat(status.get("state").asText()).isEqualTo("COMMITTED");
        assertThat(status.get("targetAssignmentId").asLong()).isEqualTo(targetAssignmentId);
        mockMvc.perform(nonOwner(MockMvcRequestBuilders.get(
                        "/academic/assignments/replacements/{id}", operationId)))
                .andExpect(status().isForbidden());

        JsonNode replay = objectMapper.readTree(mockMvc.perform(headman(
                        MockMvcRequestBuilders.post("/academic/assignments/{id}/replace", sourceAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON).content(replacementBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(replay.get("operationId").asText()).isEqualTo(operationId.toString());
        assertThat(replay.get("state").asText()).isEqualTo("COMMITTED");
        verify(scheduleGrpcClient, times(1)).installAssignmentCloseCap(
                eq(operationId), eq(sourceAssignmentId), eq(targetAssignmentId),
                eq(effectiveFrom), any(byte[].class));
        verify(scheduleGrpcClient, times(2)).commitAssignmentClose(eq(operationId), any(byte[].class));

        mockMvc.perform(headman(MockMvcRequestBuilders.post(
                        "/academic/assignments/{id}/replace", sourceAssignmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(replacementBody(fixture.teacherWithoutGrantId(), effectiveFrom, requestKey))))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM assignment_replacement_operations
                 WHERE operation_id = ? AND actor_id = ? AND state = 'COMMITTED'
                """, Long.class, operationId, fixture.headmanId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT valid_until_exclusive FROM assignments WHERE id = ?
                """, LocalDate.class, sourceAssignmentId)).isEqualTo(effectiveFrom);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT lifecycle_state FROM assignments WHERE id = ?
                """, String.class, targetAssignmentId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT valid_until_exclusive FROM assignments WHERE id = ?
                """, LocalDate.class, targetAssignmentId)).isEqualTo(sourceEnd);
    }

    private long createSourceAssignment(long subjectId, LocalDate validFrom, LocalDate validUntil)
            throws Exception {
        String payload = """
                {"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,
                 "lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":"%s"}
                """.formatted(fixture.employee1(), subjectId, fixture.groupId(),
                fixture.semesterId(), validFrom, validUntil);
        String response = mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void configureScheduleReceipts() {
        doAnswer(invocation -> receipt(invocation.getArgument(0), "APPLIED", 0L, 0L))
                .when(scheduleGrpcClient).installAssignmentCloseCap(any(UUID.class), anyLong(),
                        anyLong(), any(LocalDate.class), any(byte[].class));
        AtomicInteger commitAttempts = new AtomicInteger();
        doAnswer(invocation -> commitAttempts.getAndIncrement() == 0
                        ? receipt(invocation.getArgument(0), "APPLIED", 0L, 0L)
                        : receipt(invocation.getArgument(0), "COMMITTED", 2L, 3L))
                .when(scheduleGrpcClient).commitAssignmentClose(any(UUID.class), any(byte[].class));
    }

    private void assertRawReplacementUpdatesRejected(UUID operationId,
                                                     long sourceAssignmentId,
                                                     long targetAssignmentId,
                                                     LocalDate wrongEnd) {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT set_config(?, ?, true)", String.class,
                    "rutcampustrack.assignment_replacement_operation_id", operationId.toString());
            jdbcTemplate.update("UPDATE assignments SET valid_until_exclusive = ? WHERE id = ?",
                    wrongEnd, sourceAssignmentId);
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT set_config(?, ?, true)", String.class,
                    "rutcampustrack.assignment_replacement_operation_id", operationId.toString());
            jdbcTemplate.update("UPDATE assignments SET lifecycle_state = 'PREPARED' WHERE id = ?",
                    targetAssignmentId);
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT set_config(?, ?, true)", String.class,
                    "rutcampustrack.assignment_replacement_operation_id", UUID.randomUUID().toString());
            jdbcTemplate.update("UPDATE assignments SET valid_until_exclusive = ? WHERE id = ?",
                    wrongEnd.minusDays(2), sourceAssignmentId);
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                jdbcTemplate.update("UPDATE assignment_replacement_operations SET payload_hash = ? WHERE operation_id = ?",
                        new byte[32], operationId))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                jdbcTemplate.update("UPDATE assignment_replacement_operations SET state = 'PREPARED' WHERE operation_id = ?",
                        operationId))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                jdbcTemplate.update("DELETE FROM assignment_replacement_operations WHERE operation_id = ?",
                        operationId))).isInstanceOf(DataAccessException.class);
    }

    private static AssignmentCloseReceipt receipt(UUID operationId,
                                                  String state,
                                                  long moved,
                                                  long skipped) {
        return AssignmentCloseReceipt.newBuilder().setOperationId(operationId.toString())
                .setState(state).setMovedCount(moved).setSkippedCount(skipped).build();
    }

    private static String replacementBody(long teacherId, LocalDate effectiveFrom, UUID requestKey) {
        return "{\"replacementTeacherId\":\"" + teacherId + "\",\"effectiveFrom\":\""
                + effectiveFrom + "\",\"requestKey\":\"" + requestKey + "\"}";
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", fixture.headmanId())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", fixture.groupId())
                .header("X-Is-Headman", "true");
    }

    private MockHttpServletRequestBuilder nonOwner(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", fixture.teacher2Id())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", fixture.groupId())
                .header("X-Is-Headman", "true");
    }
}
