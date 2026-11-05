package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionAction;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionCommand;
import ru.rutcampustrack.constructor.grpc.ConstructorParticipantReceipt;
import ru.rutcampustrack.constructor.grpc.ConstructorProtectedReason;
import ru.rutcampustrack.schedule.constructor.ConstructorDeletionRecoveryJob;
import ru.rutcampustrack.schedule.constructor.ConstructorHashes;
import ru.rutcampustrack.schedule.constructor.ConstructorParticipantClients;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real SQL manifests, durable phase boundaries and public replay authorization. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "server.address=127.0.0.1")
class ConstructorDeletionIT extends AbstractScheduleIntegrationTest {
    @MockitoBean AcademicGrpcClient academic;
    @MockitoBean ConstructorParticipantClients participants;
    @MockitoBean ConstructorDeletionRecoveryJob recovery;
    @MockitoBean Clock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private final List<ConstructorDeletionAction> actions = new ArrayList<>();
    private final AtomicBoolean loseCommitReply = new AtomicBoolean();
    private boolean protect;
    private boolean foreignReceipt;

    @BeforeEach
    void prepare() {
        resetScheduleData();
        actions.clear();
        protect = false;
        foreignReceipt = false;
        loseCommitReply.set(false);
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Moscow"));
        when(clock.instant()).thenReturn(Instant.parse("2026-02-01T09:00:00Z"));
        when(clock.withZone(any()))
                .thenAnswer(call -> Clock.fixed(clock.instant(), call.getArgument(0)));
        when(academic.getActiveSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(30)
                                .setDateFrom("2026-02-02")
                                .setDateTo("2026-02-22")
                                .setFirstWeekType("odd")
                                .build());
        when(academic.getSemesterArchiveAuthorityState(30L))
                .thenReturn(
                        SemesterStateResponse.newBuilder().setId(30).setStateVersion(1).build());
        when(academic.validateGroup(10L))
                .thenReturn(GroupResponse.newBuilder().setId(10).setIsActive(true).build());
        when(academic.isHeadman(42L, 10L)).thenReturn(true);
        when(academic.getAssignmentsByIds(List.of(501L)))
                .thenReturn(
                        List.of(
                                AssignmentInfo.newBuilder()
                                        .setId(501)
                                        .setTeacherId(700)
                                        .setGroupId(10)
                                        .setSubjectId(20)
                                        .setSemesterId(30)
                                        .setLessonType("lecture")
                                        .setValidFrom("2026-02-02")
                                        .setValidUntilExclusive("2026-02-23")
                                        .build()));
        when(participants.attendance(any(), nullable(String.class)))
                .thenAnswer(call -> reply(call.getArgument(0)));
        when(participants.academic(any(), nullable(String.class)))
                .thenAnswer(call -> reply(call.getArgument(0)));
    }

    @Test
    void deletesOnlyBusinessRowsAndReauthorizesLostReplyReplayBeforeMissingLookup()
            throws Exception {
        UUID createKey = UUID.randomUUID();
        long item = create(createKey);
        String revision = preview(item).path("revision").asText();
        UUID deleteKey = UUID.randomUUID();
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", deleteKey)
                                .header("If-Match", revision))
                .andExpect(status().isNoContent());
        for (String table :
                List.of(
                        "schedule_items",
                        "lesson_occurrences",
                        "lessons",
                        "lesson_lifecycle_entries")) assertThat(count(table)).as(table).isZero();
        assertThat(count("schedule_recurring_create_replay")).isOne();
        assertThat(count("constructor_deletion_operations")).isOne();
        assertThat(count("constructor_system_audit_proofs")).isEqualTo(3);
        mvc.perform(actor(get("/schedule/items/{id}", item))).andExpect(status().isNotFound());
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", deleteKey)
                                .header("If-Match", revision))
                .andExpect(status().isNoContent());
        mvc.perform(createRequest(createKey)).andExpect(status().isGone());
        when(academic.isHeadman(42L, 10L)).thenReturn(false);
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", deleteKey)
                                .header("If-Match", revision))
                .andExpect(status().isForbidden());
        UUID operation =
                jdbcTemplate.queryForObject(
                        "SELECT operation_id FROM constructor_deletion_operations", UUID.class);
        mvc.perform(actor(get("/schedule/items/operations/{id}", operation)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lostCommitReplyRollsForwardWithSameKeyAndNeverReleases() throws Exception {
        long item = create(UUID.randomUUID());
        String revision = preview(item).path("revision").asText();
        UUID key = UUID.randomUUID();
        loseCommitReply.set(true);
        var accepted =
                mvc.perform(
                                actor(delete("/schedule/items/{id}", item))
                                        .header("Idempotency-Key", key)
                                        .header("If-Match", revision))
                        .andExpect(status().isAccepted())
                        .andReturn();
        assertThat(
                        json.readTree(accepted.getResponse().getContentAsString())
                                .path("state")
                                .asText())
                .isEqualTo("COMMITTING");
        assertThat(count("lessons")).isEqualTo(3);
        for (String barrier : List.of("READY", "DELETE_PREPARING")) {
            assertThatThrownBy(
                            () ->
                                    jdbcTemplate.update(
                                            "INSERT INTO"
                                                + " schedule_semester_archive_barriers(semester_id,operation_id,state_version,participant_state)"
                                                + " VALUES(30,?,1,?)",
                                            UUID.randomUUID(),
                                            barrier))
                    .isInstanceOf(DataAccessException.class);
        }
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", key)
                                .header("If-Match", revision))
                .andExpect(status().isNoContent());
        assertThat(actions).doesNotContain(ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE);
        assertThat(count("lessons")).isZero();
    }

    @Test
    void overlappingParticipantProtectionCountsLogicalOccurrenceOnceAndBlocksAdmission()
            throws Exception {
        long item = create(UUID.randomUUID());
        protect = true;
        JsonNode preview = preview(item);
        assertThat(preview.path("deletable").asBoolean()).isFalse();
        assertThat(preview.path("protectedCount").asLong()).isOne();
        assertThat(preview.path("removableCount").asLong()).isEqualTo(2);
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", UUID.randomUUID())
                                .header("If-Match", preview.path("revision").asText()))
                .andExpect(status().isConflict());
        assertThat(count("constructor_deletion_operations")).isZero();
        assertThat(count("lessons")).isEqualTo(3);
    }

    @Test
    void foreignReceiptAndForgedSqlDeletionFailClosed() throws Exception {
        long item = create(UUID.randomUUID());
        foreignReceipt = true;
        mvc.perform(
                        actor(post("/schedule/items/{id}/lifecycle-preview", item))
                                .param("delete", "true"))
                .andExpect(status().isServiceUnavailable());
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "DELETE FROM lessons WHERE schedule_item_id=?", item))
                .isInstanceOf(DataAccessException.class);
        assertThat(count("lessons")).isEqualTo(3);
    }

    @Test
    void legacyDeactivationKeyCannotClaimNewHardDelete() throws Exception {
        long item = create(UUID.randomUUID());
        UUID oldKey = UUID.randomUUID();
        jdbcTemplate.update(
                """
INSERT INTO schedule_recurring_lifecycle_replay
  (operation_id,actor_id,request_key,payload_hash,schedule_item_id,action,response_snapshot,accepted_at)
VALUES(?,42,?,decode(repeat('00',32),'hex'),?,'DELETE','{}'::jsonb,NOW())
""",
                UUID.randomUUID(),
                oldKey,
                item);
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", oldKey)
                                .header("If-Match", preview(item).path("revision").asText()))
                .andExpect(status().isConflict());
        assertThat(count("constructor_deletion_operations")).isZero();
        assertThat(count("lessons")).isEqualTo(3);
    }

    @Test
    void pendingLocalUserAuditProtectsBeforeAttendanceReceivesIt() throws Exception {
        long item = create(UUID.randomUUID());
        long lesson =
                jdbcTemplate.queryForObject(
                        "SELECT min(id) FROM lessons WHERE schedule_item_id=?", Long.class, item);
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(
                        transaction ->
                                ru.rutcampustrack.schedule.event.ScheduleActionRecordedEvent
                                        .publish(
                                                jdbcTemplate,
                                                events,
                                                "LESSON_EDITED",
                                                lesson,
                                                42L,
                                                ru.rutcampustrack.schedule.event
                                                        .ScheduleActionRecordedEvent.snapshot(
                                                        jdbcTemplate, lesson),
                                                java.time.OffsetDateTime.now(clock)));
        JsonNode preview = preview(item);
        assertThat(preview.path("protectedCount").asLong()).isOne();
        assertThat(preview.path("protectedReasons").toString()).contains("USER_ACTION");
        mvc.perform(
                        actor(delete("/schedule/items/{id}", item))
                                .header("Idempotency-Key", UUID.randomUUID())
                                .header("If-Match", preview.path("revision").asText()))
                .andExpect(status().isConflict());
        assertThat(count("constructor_deletion_operations")).isZero();
        assertThat(count("lessons")).isEqualTo(3);
    }

    @Test
    void recordedTemplateUpdateProtectsEvenWithoutPhysicalMutationAudit() throws Exception {
        long item = create(UUID.randomUUID());
        jdbcTemplate.update(
                """
INSERT INTO schedule_recurring_lifecycle_replay
  (operation_id,actor_id,request_key,payload_hash,schedule_item_id,action,response_snapshot,accepted_at)
VALUES(?,42,?,decode(repeat('01',32),'hex'),?,'UPDATE','{}'::jsonb,NOW())
""",
                UUID.randomUUID(),
                UUID.randomUUID(),
                item);
        JsonNode preview = preview(item);
        assertThat(preview.path("deletable").asBoolean()).isFalse();
        assertThat(preview.path("protectedCount").asLong()).isEqualTo(3);
        assertThat(preview.path("removableCount").asLong()).isZero();
        assertThat(count("constructor_deletion_operations")).isZero();
        assertThat(count("lessons")).isEqualTo(3);
    }

    private ConstructorParticipantReceipt reply(ConstructorDeletionCommand command) {
        actions.add(command.getAction());
        if (command.getAction() == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE
                || command.getAction() == ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT) {
            String stored =
                    jdbcTemplate.queryForObject(
                            "SELECT state FROM constructor_deletion_operations WHERE"
                                    + " operation_id=?",
                            String.class,
                            UUID.fromString(command.getOperationId()));
            assertThat(stored)
                    .isEqualTo(
                            command.getAction()
                                            == ConstructorDeletionAction
                                                    .CONSTRUCTOR_DELETION_PREPARE
                                    ? "PREPARING"
                                    : "COMMITTING");
        }
        if (command.getAction() == ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT
                && loseCommitReply.getAndSet(false))
            throw Status.DEADLINE_EXCEEDED.asRuntimeException();
        String state =
                switch (command.getAction()) {
                    case CONSTRUCTOR_DELETION_PREVIEW -> "PREVIEW";
                    case CONSTRUCTOR_DELETION_PREPARE -> "PREPARED";
                    case CONSTRUCTOR_DELETION_SEAL -> "SEALED";
                    case CONSTRUCTOR_DELETION_COMMIT -> "DELETED";
                    case CONSTRUCTOR_DELETION_RELEASE -> "RELEASED";
                    default -> throw new IllegalArgumentException();
                };
        var result =
                ConstructorParticipantReceipt.newBuilder()
                        .setOperationId(command.getOperationId())
                        .setRequestHash(command.getRequestHash())
                        .setScopeHash(command.getScopeHash())
                        .setParticipantDigest(
                                ByteString.copyFrom(ConstructorHashes.sha256(new byte[] {1})))
                        .setState(state);
        if (protect || foreignReceipt) {
            result.setProtectedCount(1)
                    .addProtectedOccurrenceIds(
                            foreignReceipt
                                    ? Long.MAX_VALUE
                                    : command.getScope().getLessons(0).getOccurrenceId())
                    .addProtectedReasons(
                            ConstructorProtectedReason.newBuilder()
                                    .setCode("USER_ACTION")
                                    .setCount(1));
        }
        return result.build();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long create(UUID key) throws Exception {
        var result = mvc.perform(createRequest(key)).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private MockHttpServletRequestBuilder createRequest(UUID key) throws Exception {
        return actor(post("/schedule/items"))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        json.writeValueAsString(
                                new CreateScheduleItemRequest(
                                        501L,
                                        10L,
                                        20L,
                                        30L,
                                        (short) 1,
                                        (short) 1,
                                        null,
                                        null,
                                        WeekType.ALL,
                                        "A-101")));
    }

    private JsonNode preview(long item) throws Exception {
        var result =
                mvc.perform(
                                actor(post("/schedule/items/{id}/lifecycle-preview", item))
                                        .param("delete", "true"))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 42)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", 10)
                .header("X-Is-Headman", "true");
    }
}
