package ru.rutcampustrack.academic.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** End-to-end Academic operation, authorization, and actor/key replay evidence. */
@AutoConfigureMockMvc
class AssignmentReplacementIT extends AbstractAssignmentAuthorityIT {

    @Autowired private MockMvc mockMvc;

    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private ScheduleGrpcClient scheduleGrpcClient;

    private Fixture fixture;

    @Autowired
    private ru.rutcampustrack.academic.repository.AssignmentReplacementOperationRepository
            lifecycleOperations;

    @BeforeEach
    void createFixture() {
        fixture = newFixture("assignment-replacement");
        successfulAdoptionReceipts(scheduleGrpcClient);
    }

    @Test
    void standaloneTerminalCloseKeepsHistoryAndAllowsNewSameTeacherAdmission() throws Exception {
        long subject = insertSubject(fixture.groupId(), "closure history", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1), end = from.plusDays(15);
        long source = createSourceAssignment(subject, from, end);
        LocalDate cutoff = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        UUID key = UUID.randomUUID();
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", cutoff.toString())))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT lifecycle_state FROM assignments WHERE id=?",
                                String.class,
                                source))
                .isEqualTo("CLOSED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT valid_until_exclusive FROM assignments WHERE id=?",
                                LocalDate.class,
                                source))
                .isEqualTo(end);
        JsonNode projection =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.get(
                                                        "/academic/subjects/{id}", subject)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(projection.get("assignments")).isEmpty();
        long newTarget = createSourceAssignment(subject, from, end);
        assertThat(newTarget).isNotEqualTo(source);
        assertThat(assignmentCount(subject)).isEqualTo(2);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param(
                                                "validUntilExclusive",
                                                cutoff.plusDays(1).toString())))
                .andExpect(status().isConflict());
    }

    @Test
    void admittedClosureReplaysOriginalPastDateWithoutNewAdmission() throws Exception {
        long subject = insertSubject(fixture.groupId(), "dated receipt", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1), end = from.plusDays(15);
        long source = createSourceAssignment(subject, from, end);
        // Isolated fixture represents a previously admitted durable command,
        // whose original Moscow date is now past. The public replay must find
        // this receipt before applying today's NEW-command date validation.
        LocalDate originalDate = LocalDate.now(java.time.ZoneId.of("Europe/Moscow")).minusDays(1);
        UUID key = UUID.randomUUID(), operation = UUID.randomUUID();
        byte[] hash =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(
                                ("CLOSE_UNASSIGN|" + source + "|" + originalDate)
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                lifecycleOperations.saveAndFlush(
                                        ru.rutcampustrack.academic.entity
                                                .AssignmentReplacementOperation.lifecycle(
                                                operation,
                                                fixture.headmanId(),
                                                key,
                                                hash,
                                                source,
                                                null,
                                                from,
                                                end,
                                                AssignmentOperationKind.CLOSE_UNASSIGN,
                                                originalDate)));
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", originalDate.toString())))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", originalDate.toString())))
                .andExpect(status().isNoContent());
        assertThat(lifecycleOperations.findById(operation).orElseThrow().getState())
                .isEqualTo(AssignmentOperationState.COMMITTED);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM assignment_replacement_operations WHERE"
                                        + " actor_id=? AND request_key=?",
                                Long.class,
                                fixture.headmanId(),
                                key))
                .isEqualTo(1L);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", UUID.randomUUID())
                                        .param("validUntilExclusive", originalDate.toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void partialCreateExposesExactOwnPendingHandleAndOptionalResumeCommitsExistingTarget()
            throws Exception {
        long subject = insertSubject(fixture.groupId(), "pending adoption", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1), end = from.plusDays(15);
        org.mockito.Mockito.when(scheduleGrpcClient.commitAssignmentClose(any(UUID.class), any()))
                .thenThrow(
                        new ru.rutcampustrack.academic.exception
                                .ScheduleServiceUnavailableException(
                                "isolated receipt unavailable"));
        String body =
                """
{"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":"%s"}
"""
                        .formatted(
                                fixture.employee1(),
                                subject,
                                fixture.groupId(),
                                fixture.semesterId(),
                                from,
                                end);
        JsonNode pending =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.post("/academic/assignments")
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(body)))
                                .andExpect(status().isServiceUnavailable())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(pending.get("type").asText()).endsWith("assignment-adoption-pending");
        assertThat(pending.get("extras").get("subjectId").asLong()).isEqualTo(subject);
        JsonNode handle = pending.get("extras").get("pendingAdoptions").get(0);
        long target = handle.get("targetAssignmentId").asLong();
        UUID operation = UUID.fromString(handle.get("adoptionOperationId").asText());
        JsonNode own =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.get(
                                                        "/academic/subjects/{id}", subject)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(own.get("pendingAdoptions").get(0).get("state").asText()).isEqualTo("APPLIED");
        JsonNode admitted = own.get("pendingAdoptions").get(0);
        assertThat(admitted.get("targetAssignmentId").asLong()).isEqualTo(target);
        assertThat(admitted.get("teacherId").asLong()).isEqualTo(fixture.teacher1Id());
        assertThat(admitted.get("subjectId").asLong()).isEqualTo(subject);
        assertThat(admitted.get("groupId").asLong()).isEqualTo(fixture.groupId());
        assertThat(admitted.get("semesterId").asLong()).isEqualTo(fixture.semesterId());
        assertThat(admitted.get("lessonType").asText()).isEqualTo("LECTURE");
        assertThat(admitted.get("validFrom").asText()).isEqualTo(from.toString());
        assertThat(admitted.get("validUntilExclusive").asText()).isEqualTo(end.toString());
        assertThat(own.get("assignments")).isEmpty();
        assertThat(own.get("teacherIds")).isEmpty();
        assertThat(own.get("createdAssignmentIds")).isEmpty();
        Fixture foreign = newFixture("foreign-adoption");
        mockMvc.perform(
                        signedHeadman(
                                MockMvcRequestBuilders.post(
                                                "/academic/assignments/{id}/resume-adoption",
                                                target)
                                        .param("operationId", operation.toString()),
                                foreign.headmanId(),
                                foreign.groupId()))
                .andExpect(status().isForbidden());
        successfulAdoptionReceipts(scheduleGrpcClient);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                        "/academic/assignments/{id}/resume-adoption", target)))
                .andExpect(status().isOk());
        assertThat(assignmentCount(subject)).isEqualTo(1);
        assertThat(lifecycleOperations.findById(operation).orElseThrow().getState())
                .isEqualTo(AssignmentOperationState.COMMITTED);
        JsonNode complete =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.get(
                                                        "/academic/subjects/{id}", subject)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(complete.get("pendingAdoptions")).isEmpty();
        assertThat(complete.get("assignments")).hasSize(1);
        assertThat(complete.get("assignments").get(0).get("id").asLong()).isEqualTo(target);
        assertThat(complete.get("teacherIds")).hasSize(1);
        assertThat(complete.get("teacherIds").get(0).asLong()).isEqualTo(fixture.teacher1Id());
        assertThat(complete.get("createdAssignmentIds")).hasSize(1);
        assertThat(complete.get("createdAssignmentIds").get(0).asLong()).isEqualTo(target);
    }

    @Test
    void openTupleBlocksNewDifferentTeacherAndCloseButAcceptedCloseReceiptContinues()
            throws Exception {
        long subject = insertSubject(fixture.groupId(), "open closure barrier", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1), end = from.plusDays(15);
        long source = createSourceAssignment(subject, from, end);
        UUID key = UUID.randomUUID();
        LocalDate cutoff = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        org.mockito.Mockito.when(scheduleGrpcClient.commitAssignmentClose(any(UUID.class), any()))
                .thenThrow(
                        new ru.rutcampustrack.academic.exception
                                .ScheduleServiceUnavailableException(
                                "isolated receipt unavailable"));
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", cutoff.toString())))
                .andExpect(status().isServiceUnavailable());
        String body =
                """
{"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":"%s"}
"""
                        .formatted(
                                fixture.employee2(),
                                subject,
                                fixture.groupId(),
                                fixture.semesterId(),
                                from,
                                end);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post("/academic/assignments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)))
                .andExpect(status().isConflict());
        assertThat(assignmentCount(subject)).isEqualTo(1);
        successfulAdoptionReceipts(scheduleGrpcClient);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", cutoff.toString())))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post("/academic/assignments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)))
                .andExpect(status().isCreated());

        long secondSubject = insertSubject(fixture.groupId(), "open adoption barrier", "lecture");
        long old = createSourceAssignment(secondSubject, from, end);
        org.mockito.Mockito.when(scheduleGrpcClient.commitAssignmentClose(any(UUID.class), any()))
                .thenThrow(
                        new ru.rutcampustrack.academic.exception
                                .ScheduleServiceUnavailableException(
                                "isolated receipt unavailable"));
        JsonNode pending =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.post("/academic/assignments")
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                body.replace(
                                                                        "\"subjectId\":" + subject,
                                                                        "\"subjectId\":"
                                                                                + secondSubject))))
                                .andExpect(status().isServiceUnavailable())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        long target =
                pending.get("extras")
                        .get("pendingAdoptions")
                        .get(0)
                        .get("targetAssignmentId")
                        .asLong();
        UUID newClose = UUID.randomUUID();
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", old)
                                        .header("Idempotency-Key", newClose)
                                        .param("validUntilExclusive", cutoff.toString())))
                .andExpect(status().isConflict());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT lifecycle_state FROM assignments WHERE id=?",
                                String.class,
                                old))
                .isEqualTo("ACTIVE");
        successfulAdoptionReceipts(scheduleGrpcClient);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                        "/academic/assignments/{id}/resume-adoption", target)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", old)
                                        .header("Idempotency-Key", newClose)
                                        .param("validUntilExclusive", cutoff.toString())))
                .andExpect(status().isNoContent());
    }

    @Test
    void newOwnHeadmanDiscoversAndContinuesOriginalClosureWithoutChangingCreator()
            throws Exception {
        long subject = insertSubject(fixture.groupId(), "closure owner recovery", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1),
                end = from.plusDays(15),
                cutoff = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        long source = createSourceAssignment(subject, from, end);
        UUID key = UUID.randomUUID();
        org.mockito.Mockito.when(scheduleGrpcClient.commitAssignmentClose(any(UUID.class), any()))
                .thenThrow(
                        new ru.rutcampustrack.academic.exception
                                .ScheduleServiceUnavailableException(
                                "isolated receipt unavailable"));
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", cutoff.toString())))
                .andExpect(status().isServiceUnavailable());
        var original =
                jdbcTemplate.queryForMap(
                        "SELECT operation_id,actor_id,payload_hash FROM"
                            + " assignment_replacement_operations WHERE source_assignment_id=? AND"
                            + " request_key=?",
                        source,
                        key);
        UUID operation = (UUID) original.get("operation_id");
        long currentHeadman =
                insertUser(
                        "Headman",
                        "Current",
                        ru.rutcampustrack.academic.contract.enums.UserRole.STUDENT,
                        fixture.groupId(),
                        null);
        assignHeadman(currentHeadman, fixture.groupId());
        JsonNode detail =
                objectMapper.readTree(
                        mockMvc.perform(
                                        signedHeadman(
                                                MockMvcRequestBuilders.get(
                                                        "/academic/subjects/{id}", subject),
                                                currentHeadman,
                                                fixture.groupId()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        JsonNode handle = detail.get("pendingClosures").get(0);
        assertThat(handle.get("operationId").asText()).isEqualTo(operation.toString());
        assertThat(handle.get("assignmentId").asLong()).isEqualTo(source);
        assertThat(handle.get("requestKey").asText()).isEqualTo(key.toString());
        assertThat(handle.get("validUntilExclusive").asText()).isEqualTo(cutoff.toString());
        assertThat(handle.get("teacherId").asLong()).isEqualTo(fixture.teacher1Id());
        assertThat(handle.get("subjectId").asLong()).isEqualTo(subject);
        assertThat(handle.get("groupId").asLong()).isEqualTo(fixture.groupId());
        assertThat(handle.get("semesterId").asLong()).isEqualTo(fixture.semesterId());
        assertThat(handle.get("lessonType").asText()).isEqualTo("LECTURE");
        assertThat(handle.has("actorId")).isFalse();
        assertThat(detail.get("assignments")).isEmpty();
        Fixture foreign = newFixture("foreign closure recovery");
        mockMvc.perform(
                        signedHeadman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param("validUntilExclusive", cutoff.toString()),
                                foreign.headmanId(),
                                foreign.groupId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        signedHeadman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", key)
                                        .param(
                                                "validUntilExclusive",
                                                cutoff.plusDays(1).toString()),
                                currentHeadman,
                                fixture.groupId()))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        signedHeadman(
                                MockMvcRequestBuilders.delete("/academic/assignments/{id}", source)
                                        .header("Idempotency-Key", UUID.randomUUID())
                                        .param("validUntilExclusive", cutoff.toString()),
                                currentHeadman,
                                fixture.groupId()))
                .andExpect(status().isConflict());
        successfulAdoptionReceipts(scheduleGrpcClient);
        for (int replay = 0; replay < 2; replay++)
            mockMvc.perform(
                            signedHeadman(
                                    MockMvcRequestBuilders.delete(
                                                    "/academic/assignments/{id}", source)
                                            .header("Idempotency-Key", key)
                                            .param("validUntilExclusive", cutoff.toString()),
                                    currentHeadman,
                                    fixture.groupId()))
                    .andExpect(status().isNoContent());
        var after = lifecycleOperations.findById(operation).orElseThrow();
        assertThat(after.getActorId()).isEqualTo(fixture.headmanId());
        assertThat(after.getRequestKey()).isEqualTo(key);
        assertThat(after.getPayloadHash()).isEqualTo((byte[]) original.get("payload_hash"));
        assertThat(after.getState()).isEqualTo(AssignmentOperationState.COMMITTED);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM assignment_replacement_operations WHERE"
                                    + " source_assignment_id=? AND operation_kind='CLOSE_UNASSIGN'",
                                Long.class,
                                source))
                .isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT valid_until_exclusive FROM assignments WHERE id=?",
                                LocalDate.class,
                                source))
                .isEqualTo(end);
        String payload =
                """
{"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":"%s"}
"""
                        .formatted(
                                fixture.employee2(),
                                subject,
                                fixture.groupId(),
                                fixture.semesterId(),
                                from,
                                end);
        mockMvc.perform(
                        signedHeadman(
                                MockMvcRequestBuilders.post("/academic/assignments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload),
                                currentHeadman,
                                fixture.groupId()))
                .andExpect(status().isCreated());
        assertThat(assignmentCount(subject)).isEqualTo(2);
    }

    @Test
    void pendingActivationIsFencedAndExactReplayCompletesAfterScheduleCommit() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A teacher replacement", "lecture");
        LocalDate sourceFrom = fixture.dateFrom().plusDays(1);
        LocalDate sourceEnd = fixture.dateFrom().plusDays(20);
        LocalDate effectiveFrom = sourceFrom.plusDays(7);
        long sourceAssignmentId = createSourceAssignment(subjectId, sourceFrom, sourceEnd);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
INSERT INTO assignments
    (teacher_id, subject_id, group_id, semester_id, lesson_type,
     valid_from, valid_until_exclusive, lifecycle_state)
VALUES (?, ?, ?, ?, 'lecture'::subject_type, ?, ?, 'ACTIVE')
""",
                                        fixture.teacher1Id(),
                                        subjectId,
                                        fixture.groupId(),
                                        fixture.semesterId(),
                                        effectiveFrom,
                                        effectiveFrom))
                .isInstanceOf(DataAccessException.class);
        configureScheduleReceipts();

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                                "/academic/assignments/{id}/replace",
                                                sourceAssignmentId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                replacementBody(
                                                        fixture.teacherWithoutGrantId(),
                                                        effectiveFrom,
                                                        UUID.randomUUID()))))
                .andExpect(status().isForbidden());

        UUID requestKey = UUID.randomUUID();
        String replacementBody = replacementBody(fixture.teacher2Id(), effectiveFrom, requestKey);
        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                                "/academic/assignments/{id}/replace",
                                                sourceAssignmentId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(replacementBody)))
                .andExpect(status().isConflict());

        UUID operationId =
                jdbcTemplate.queryForObject(
                        """
SELECT operation_id FROM assignment_replacement_operations WHERE request_key = ?
""",
                        UUID.class,
                        requestKey);
        long targetAssignmentId =
                jdbcTemplate.queryForObject(
                        """
SELECT target_assignment_id FROM assignment_replacement_operations WHERE operation_id = ?
""",
                        Long.class,
                        operationId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
SELECT state FROM assignment_replacement_operations WHERE operation_id = ?
""",
                                String.class,
                                operationId))
                .isEqualTo("APPLIED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT valid_until_exclusive FROM assignments WHERE id = ?
                                """,
                                LocalDate.class,
                                sourceAssignmentId))
                .isEqualTo(effectiveFrom);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT lifecycle_state FROM assignments WHERE id = ?
                                """,
                                String.class,
                                targetAssignmentId))
                .isEqualTo("ACTIVE");
        assertRawReplacementUpdatesRejected(
                operationId, sourceAssignmentId, targetAssignmentId, effectiveFrom.plusDays(1));

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                                "/academic/assignments/{id}/replace",
                                                targetAssignmentId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                replacementBody(
                                                        fixture.teacher1Id(),
                                                        effectiveFrom.plusDays(1),
                                                        UUID.randomUUID()))))
                .andExpect(status().isConflict());

        JsonNode replaced =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.post(
                                                                "/academic/assignments/{id}/replace",
                                                                sourceAssignmentId)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(replacementBody)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(replaced.get("operationId").asText()).isEqualTo(operationId.toString());
        assertThat(replaced.get("targetAssignmentId").asLong()).isEqualTo(targetAssignmentId);
        assertThat(replaced.get("state").asText()).isEqualTo("COMMITTED");
        assertThat(replaced.get("scheduleReceiptState").asText()).isEqualTo("COMMITTED");
        assertThat(replaced.get("sourceValidUntilExclusive").asText())
                .isEqualTo(effectiveFrom.toString());
        assertThat(replaced.get("targetValidUntilExclusive").asText())
                .isEqualTo(sourceEnd.toString());
        assertThat(replaced.get("movedCount").asLong()).isEqualTo(2L);
        assertThat(replaced.get("skippedCount").asLong()).isEqualTo(3L);

        JsonNode status =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.get(
                                                        "/academic/assignments/replacements/{id}",
                                                        operationId)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(status.get("operationId").asText()).isEqualTo(operationId.toString());
        assertThat(status.get("state").asText()).isEqualTo("COMMITTED");
        assertThat(status.get("targetAssignmentId").asLong()).isEqualTo(targetAssignmentId);
        mockMvc.perform(
                        nonOwner(
                                MockMvcRequestBuilders.get(
                                        "/academic/assignments/replacements/{id}", operationId)))
                .andExpect(status().isForbidden());

        JsonNode replay =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.post(
                                                                "/academic/assignments/{id}/replace",
                                                                sourceAssignmentId)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(replacementBody)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(replay.get("operationId").asText()).isEqualTo(operationId.toString());
        assertThat(replay.get("state").asText()).isEqualTo("COMMITTED");
        verify(scheduleGrpcClient, times(1))
                .installAssignmentCloseCap(
                        eq(operationId),
                        eq(sourceAssignmentId),
                        eq(targetAssignmentId),
                        eq(effectiveFrom),
                        any(byte[].class));
        verify(scheduleGrpcClient, times(2))
                .commitAssignmentClose(eq(operationId), any(byte[].class));

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                                "/academic/assignments/{id}/replace",
                                                sourceAssignmentId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                replacementBody(
                                                        fixture.teacherWithoutGrantId(),
                                                        effectiveFrom,
                                                        requestKey))))
                .andExpect(status().isConflict());

        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*) FROM assignment_replacement_operations
                                 WHERE operation_id = ? AND actor_id = ? AND state = 'COMMITTED'
                                """,
                                Long.class,
                                operationId,
                                fixture.headmanId()))
                .isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT valid_until_exclusive FROM assignments WHERE id = ?
                                """,
                                LocalDate.class,
                                sourceAssignmentId))
                .isEqualTo(effectiveFrom);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT lifecycle_state FROM assignments WHERE id = ?
                                """,
                                String.class,
                                targetAssignmentId))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT valid_until_exclusive FROM assignments WHERE id = ?
                                """,
                                LocalDate.class,
                                targetAssignmentId))
                .isEqualTo(sourceEnd);
    }

    private long createSourceAssignment(long subjectId, LocalDate validFrom, LocalDate validUntil)
            throws Exception {
        String payload =
                """
                {"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,
                 "lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":"%s"}
                """
                        .formatted(
                                fixture.employee1(),
                                subjectId,
                                fixture.groupId(),
                                fixture.semesterId(),
                                validFrom,
                                validUntil);
        String response =
                mockMvc.perform(
                                headman(
                                        MockMvcRequestBuilders.post("/academic/assignments")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(payload)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void configureScheduleReceipts() {
        doAnswer(invocation -> receipt(invocation.getArgument(0), "APPLIED", 0L, 0L))
                .when(scheduleGrpcClient)
                .installAssignmentCloseCap(
                        any(UUID.class),
                        anyLong(),
                        anyLong(),
                        any(LocalDate.class),
                        any(byte[].class));
        AtomicInteger commitAttempts = new AtomicInteger();
        doAnswer(
                        invocation ->
                                commitAttempts.getAndIncrement() == 0
                                        ? receipt(invocation.getArgument(0), "APPLIED", 0L, 0L)
                                        : receipt(invocation.getArgument(0), "COMMITTED", 2L, 3L))
                .when(scheduleGrpcClient)
                .commitAssignmentClose(any(UUID.class), any(byte[].class));
    }

    private void assertRawReplacementUpdatesRejected(
            UUID operationId,
            long sourceAssignmentId,
            long targetAssignmentId,
            LocalDate wrongEnd) {
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    jdbcTemplate.queryForObject(
                                                            "SELECT set_config(?, ?, true)",
                                                            String.class,
                                                            "rutcampustrack.assignment_replacement_operation_id",
                                                            operationId.toString());
                                                    jdbcTemplate.update(
                                                            "UPDATE assignments SET"
                                                                + " valid_until_exclusive = ? WHERE"
                                                                + " id = ?",
                                                            wrongEnd,
                                                            sourceAssignmentId);
                                                }))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    jdbcTemplate.queryForObject(
                                                            "SELECT set_config(?, ?, true)",
                                                            String.class,
                                                            "rutcampustrack.assignment_replacement_operation_id",
                                                            operationId.toString());
                                                    jdbcTemplate.update(
                                                            "UPDATE assignments SET lifecycle_state"
                                                                    + " = 'PREPARED' WHERE id = ?",
                                                            targetAssignmentId);
                                                }))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    jdbcTemplate.queryForObject(
                                                            "SELECT set_config(?, ?, true)",
                                                            String.class,
                                                            "rutcampustrack.assignment_replacement_operation_id",
                                                            UUID.randomUUID().toString());
                                                    jdbcTemplate.update(
                                                            "UPDATE assignments SET"
                                                                + " valid_until_exclusive = ? WHERE"
                                                                + " id = ?",
                                                            wrongEnd.minusDays(2),
                                                            sourceAssignmentId);
                                                }))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status ->
                                                        jdbcTemplate.update(
                                                                "UPDATE"
                                                                    + " assignment_replacement_operations"
                                                                    + " SET payload_hash = ? WHERE"
                                                                    + " operation_id = ?",
                                                                new byte[32],
                                                                operationId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status ->
                                                        jdbcTemplate.update(
                                                                "UPDATE"
                                                                    + " assignment_replacement_operations"
                                                                    + " SET state = 'PREPARED'"
                                                                    + " WHERE operation_id = ?",
                                                                operationId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status ->
                                                        jdbcTemplate.update(
                                                                "DELETE FROM"
                                                                    + " assignment_replacement_operations"
                                                                    + " WHERE operation_id = ?",
                                                                operationId)))
                .isInstanceOf(DataAccessException.class);
    }

    private static AssignmentCloseReceipt receipt(
            UUID operationId, String state, long moved, long skipped) {
        return AssignmentCloseReceipt.newBuilder()
                .setOperationId(operationId.toString())
                .setState(state)
                .setMovedCount(moved)
                .setSkippedCount(skipped)
                .build();
    }

    private static String replacementBody(
            long teacherId, LocalDate effectiveFrom, UUID requestKey) {
        return "{\"replacementTeacherId\":\""
                + teacherId
                + "\",\"effectiveFrom\":\""
                + effectiveFrom
                + "\",\"requestKey\":\""
                + requestKey
                + "\"}";
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request) {
        return signedHeadman(request, fixture.headmanId(), fixture.groupId());
    }

    private MockHttpServletRequestBuilder nonOwner(MockHttpServletRequestBuilder request) {
        return signedTeacher(request, fixture.teacher2Id())
                .header("X-User-Id", fixture.teacher2Id())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", fixture.groupId())
                .header("X-Is-Headman", "true");
    }
}
