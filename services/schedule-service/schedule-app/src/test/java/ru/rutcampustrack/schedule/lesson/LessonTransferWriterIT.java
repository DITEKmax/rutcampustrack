package ru.rutcampustrack.schedule.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;
import ru.rutcampustrack.schedule.event.EventConsumer;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL proof for the transfer transaction, replay, fixed batches and wire ACKs. */
@AutoConfigureMockMvc
class LessonTransferWriterIT extends AbstractScheduleIntegrationTest {

    private static final AtomicLong FIXTURE_SEQUENCE = new AtomicLong(System.currentTimeMillis() * 1000L);

    @MockitoBean private AcademicGrpcClient academic;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LessonTransferWriter transferWriter;
    @Autowired private EventConsumer eventConsumer;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxStorage outboxStorage;
    @Autowired private MockMvc mockMvc;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private ru.rutcampustrack.schedule.recurring.RecurringScheduleItemLifecycleWriter lifecycle;
    @Autowired private ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence archiveFence;

    private final List<UUID> ackEventIds = new ArrayList<>();
    private Fixture fixture;

    @Test
    void historicalRoomOnlyTemplateUpdateKeepsLegacyTimes() {
        fixture = insertFixture();
        when(academic.getSemesterArchiveAuthorityState(fixture.semesterId()))
                .thenReturn(SemesterStateResponse.newBuilder().setId(fixture.semesterId()).build());
        Map<String, Object> source = jdbcTemplate.queryForMap("SELECT * FROM lessons WHERE id = ?", fixture.sourceLessonId());
        long item = ((Number) source.get("schedule_item_id")).longValue();
        long assignment = ((Number) source.get("assignment_id")).longValue();
        var authority = new ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority(assignment,
                ((Number) source.get("assigned_teacher_id")).longValue(), fixture.subjectId(), fixture.groupId(),
                fixture.semesterId(), "lecture", fixture.sourceDate().minusDays(2), fixture.targetDate().plusDays(5));
        var change = new ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest(fixture.subjectId(),
                (short) fixture.sourceDate().getDayOfWeek().getValue(), (short) 2,
                LocalTime.of(9, 0), LocalTime.of(10, 30),
                ru.rutcampustrack.schedule.contract.enums.WeekType.ALL, "B-205");
        var authorities = Map.of(assignment, authority);
        var preview = lifecycle.preview(item, change, false, authorities, fixture.sourceDate(), fixture.sourceDate());
        var result = lifecycle.mutate(item, change, false, preview.revision(), UUID.randomUUID(), fixture.actorId(),
                authorities, fixture.sourceDate(), fixture.sourceDate(), archiveFence.prepareBusinessWrite(fixture.semesterId()));
        long target = jdbcTemplate.queryForObject("SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?",
                Long.class, fixture.occurrenceId());
        assertThat(target).isNotEqualTo(fixture.sourceLessonId());
        assertThat(jdbcTemplate.queryForObject("SELECT start_time FROM lessons WHERE id = ?", LocalTime.class, target))
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT end_time FROM lessons WHERE id = ?", LocalTime.class, target))
                .isEqualTo(LocalTime.of(10, 30));
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @AfterEach
    void cleanup() {
        if (fixture != null) {
            jdbcTemplate.update("""
                    UPDATE lesson_homework_bindings
                       SET state = 'ARCHIVED', revision = revision + 1, updated_at = NOW()
                     WHERE occurrence_id = ? AND state <> 'ARCHIVED'
                    """, fixture.occurrenceId());
        }
        for (UUID eventId : ackEventIds) {
            jdbcTemplate.update("DELETE FROM event_consumer_processed WHERE consumer_id = 'schedule' AND event_id = ?",
                    eventId);
        }
        ackEventIds.clear();
    }

    @Test
    void requestReplayUsesCanonicalOutboxAndFixedReceiptsCompleteOnce() throws Exception {
        fixture = insertFixture();
        when(academic.getSemesterArchiveAuthorityState(fixture.semesterId()))
                .thenReturn(SemesterStateResponse.newBuilder().setId(fixture.semesterId()).build());
        assertThatThrownBy(() -> transferWriter.transfer(fixture.sourceLessonId(), fixture.actorId(),
                new TransferLessonRequest(fixture.targetDate(), 3, LocalTime.of(8, 30), LocalTime.of(9, 50),
                        null, "1", UUID.randomUUID())))
                .isInstanceOf(ru.rutcampustrack.schedule.contract.enums.LessonSlot.ValidationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_transfer_operations WHERE occurrence_id = ?",
                Long.class, fixture.occurrenceId())).isZero();
        jdbcTemplate.update("""
                INSERT INTO lesson_homework_bindings
                    (occurrence_id, current_lesson_id, homework_id, actor_id, request_key,
                     payload_hash, state, revision)
                SELECT ?, ?, NULL, ?, gen_random_uuid(), decode(lpad(to_hex(n), 64, '0'), 'hex'),
                       'PENDING', 1
                  FROM generate_series(1, 65) n
                """, fixture.occurrenceId(), fixture.sourceLessonId(), fixture.actorId());

        TransferLessonRequest request = new TransferLessonRequest(
                fixture.targetDate(), 3, null, null, null, "1", fixture.requestKey());
        TransferLessonResponse pending = transferWriter.transfer(fixture.sourceLessonId(), fixture.actorId(), request);
        assertThat(pending.state()).isEqualTo("PENDING");
        assertThat(transferWriter.transfer(fixture.sourceLessonId(), fixture.actorId(), request).operationId())
                .isEqualTo(pending.operationId());
        assertThatThrownBy(() -> transferWriter.transfer(fixture.sourceLessonId(), fixture.actorId(),
                new TransferLessonRequest(fixture.targetDate().plusDays(1), 3, null, null, null,
                        "1", fixture.requestKey())))
                .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);

        UUID operationId = UUID.fromString(pending.operationId());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT batch_count FROM lesson_transfer_operations WHERE operation_id = ?
                """, Integer.class, operationId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                SELECT binding_count FROM lesson_transfer_binding_batches
                 WHERE operation_id = ? ORDER BY batch_index
                """, Integer.class, operationId)).containsExactly(64, 1);

        List<Map<String, Object>> emitted = outboxStorage.findPending(1000).stream()
                .filter(row -> "lesson.transfer.requested".equals(row.eventType()))
                .map(this::eventEnvelope)
                .filter(event -> pending.operationId().equals(
                        String.valueOf(((Map<?, ?>) event.get("payload")).get("operation_id"))))
                .toList();
        assertThat(emitted).hasSize(2);
        List<Map<String, Object>> ordered = emitted.stream()
                .sorted((left, right) -> Integer.compare(batchIndex(left), batchIndex(right))).toList();
        for (int index = 0; index < ordered.size(); index++) {
            Map<String, Object> payload = payload(ordered.get(index));
            assertThat(payload.get("transfer_payload_hash")).isInstanceOf(String.class);
            assertThat(payload.get("transfer_revision")).isEqualTo(2);
            assertThat(payload).doesNotContainKeys("payload_hash", "expected_occurrence_revision");
            Map<?, ?> source = (Map<?, ?>) payload.get("source");
            Map<?, ?> target = (Map<?, ?>) payload.get("target");
            assertThat(source.containsKey("lesson_revision")).isTrue();
            assertThat(source.containsKey("occurrence_revision")).isTrue();
            assertThat(source.containsKey("room")).isTrue();
            assertThat(source.containsKey("revision")).isFalse();
            assertThat(source.containsKey("room_snapshot")).isFalse();
            assertThat(target.containsKey("lesson_revision")).isTrue();
            assertThat(target.containsKey("occurrence_revision")).isTrue();
            assertThat(target.containsKey("room")).isTrue();
            assertThat(target.containsKey("revision")).isFalse();
            assertThat(target.containsKey("room_snapshot")).isFalse();
            assertThat(payload.get("batch_count")).isEqualTo(2);
            assertThat(payload.get("batch_index")).isEqualTo(index);
            assertThat(((List<?>) payload.get("bindings")).size()).isEqualTo(index == 0 ? 64 : 1);
        }

        Map<String, Object> batch0 = payload(ordered.get(0));
        Map<String, Object> batch1 = payload(ordered.get(1));
        ack(batch0, "attendance-service", "ATTENDANCE", -1, "APPLIED");
        ack(batch1, "academic-service", "ACADEMIC", 1, "APPLIED");
        ack(batch1, "academic-service", "ACADEMIC", 1, "APPLIED");
        assertThat(transferWriter.status(operationId).state()).isEqualTo("PENDING");
        ack(batch0, "academic-service", "ACADEMIC", 0, "APPLIED");

        TransferLessonResponse completed = transferWriter.status(operationId);
        assertThat(completed.state()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_participant_receipts WHERE operation_id = ?
                """, Integer.class, operationId)).isEqualTo(3);
        assertThat(transferWriter.transfer(fixture.sourceLessonId(), fixture.actorId(), request).state())
                .isEqualTo("COMPLETED");

        long firstTargetId = Long.parseLong(pending.targetLessonId());
        assertThat(jdbcTemplate.queryForObject("SELECT start_time FROM lessons WHERE id = ?", LocalTime.class, firstTargetId))
                .isEqualTo(LocalTime.of(11, 40));
        assertThat(jdbcTemplate.queryForObject("SELECT end_time FROM lessons WHERE id = ?", LocalTime.class, firstTargetId))
                .isEqualTo(LocalTime.of(13, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT start_time FROM lessons WHERE id = ?", LocalTime.class, fixture.sourceLessonId()))
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT end_time FROM lessons WHERE id = ?", LocalTime.class, fixture.sourceLessonId()))
                .isEqualTo(LocalTime.of(10, 30));
        var transferredPage = lessonRepository.pageByGroupIdAndDateBetweenAndStatusIn(
                fixture.groupId(), fixture.sourceDate(), fixture.targetDate(),
                List.of("transferred"), PageRequest.of(0, 10));
        assertThat(transferredPage.getContent().stream().map(lesson -> lesson.getId()).toList())
                .containsExactly(fixture.sourceLessonId());
        assertThat(transferredPage.getTotalElements()).isEqualTo(1);

        var plannedPage = lessonRepository.pageByGroupIdAndDateBetweenAndStatusIn(
                fixture.groupId(), fixture.sourceDate(), fixture.targetDate(),
                List.of("planned"), PageRequest.of(0, 10));
        assertThat(plannedPage.getContent().stream().map(lesson -> lesson.getId()).toList())
                .containsExactly(firstTargetId);
        assertThat(plannedPage.getTotalElements()).isEqualTo(1);

        var firstHistoryPage = lessonRepository.pageByGroupIdAndDateBetweenAndStatusIn(
                fixture.groupId(), fixture.sourceDate(), fixture.targetDate(),
                List.of("transferred", "planned"), PageRequest.of(0, 1));
        var secondHistoryPage = lessonRepository.pageByGroupIdAndDateBetweenAndStatusIn(
                fixture.groupId(), fixture.sourceDate(), fixture.targetDate(),
                List.of("transferred", "planned"), PageRequest.of(1, 1));
        assertThat(firstHistoryPage.getTotalElements()).isEqualTo(2);
        assertThat(firstHistoryPage.getTotalPages()).isEqualTo(2);
        assertThat(firstHistoryPage.getContent().stream().map(lesson -> lesson.getId()).toList())
                .containsExactly(fixture.sourceLessonId());
        assertThat(secondHistoryPage.getContent().stream().map(lesson -> lesson.getId()).toList())
                .containsExactly(firstTargetId);

        JsonNode reloadedTarget = reloadLesson(firstTargetId, fixture.targetDate(),
                fixture.targetDate().plusDays(4));
        assertThat(reloadedTarget.path("revision").asLong()).isEqualTo(1);
        assertThat(reloadedTarget.path("occurrenceRevision").asLong()).isEqualTo(2);

        LocalDate nextDate = fixture.targetDate().plusDays(2);
        if (nextDate.getDayOfWeek().getValue() == 7) nextDate = nextDate.plusDays(1);
        TransferLessonRequest nextRequest = new TransferLessonRequest(
                nextDate, 4, null, null, null,
                reloadedTarget.path("occurrenceRevision").asText(), UUID.randomUUID());
        TransferLessonResponse nextPending = transferWriter.transfer(
                firstTargetId, fixture.actorId(), nextRequest);
        assertThat(nextPending.state()).isEqualTo("PENDING");
        assertThat(nextPending.sourceLessonId()).isEqualTo(Long.toString(firstTargetId));
        assertThat(nextPending.revision()).isEqualTo("3");

        UUID nextOperationId = UUID.fromString(nextPending.operationId());
        List<Map<String, Object>> nextBatches = outboxStorage.findPending(1000).stream()
                .filter(row -> "lesson.transfer.requested".equals(row.eventType()))
                .map(this::eventEnvelope)
                .map(LessonTransferWriterIT::payload)
                .filter(event -> nextPending.operationId().equals(String.valueOf(event.get("operation_id"))))
                .sorted((left, right) -> Integer.compare(batchIndexFromPayload(left), batchIndexFromPayload(right)))
                .toList();
        assertThat(nextBatches).hasSize(2);
        ack(nextBatches.get(0), "attendance-service", "ATTENDANCE", -1, "APPLIED");
        ack(nextBatches.get(0), "academic-service", "ACADEMIC", 0, "APPLIED");
        ack(nextBatches.get(1), "academic-service", "ACADEMIC", 1, "APPLIED");
        assertThat(transferWriter.status(nextOperationId).state()).isEqualTo("COMPLETED");
    }

    private JsonNode reloadLesson(long lessonId, LocalDate from, LocalDate to) throws Exception {
        String json = mockMvc.perform(get("/schedule/groups/{groupId}/lessons", fixture.groupId())
                        .param("dateFrom", from.toString())
                        .param("dateTo", to.toString())
                        .header("X-User-Id", Long.toString(fixture.actorId()))
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", Long.toString(fixture.groupId()))
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rows = objectMapper.readTree(json).path("_embedded").path("lessonResponseList");
        for (JsonNode row : rows) {
            if (row.path("id").asLong() == lessonId) return row;
        }
        throw new AssertionError("Reloaded schedule did not include target lesson " + lessonId);
    }

    @Test
    void oneOffPublicTransferKeepsOriginAndBindingsAcrossRaceReplayAckAndRestore() throws Exception {
        long seed = FIXTURE_SEQUENCE.incrementAndGet();
        long group = seed + 10, subject = seed + 20, semester = seed + 30, actor = seed + 40;
        LocalDate sourceDate = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(30);
        LocalDate targetDate = sourceDate.plusDays(1);
        if (targetDate.getDayOfWeek().getValue() == 7) targetDate = targetDate.plusDays(1);
        LocalDate until = targetDate.plusDays(5);
        when(academic.isHeadman(actor, group)).thenReturn(true);
        when(academic.validateGroup(group)).thenReturn(GroupResponse.newBuilder().setId(group).setIsActive(true).build());
        when(academic.getActiveSemester()).thenReturn(SemesterResponse.newBuilder().setId(semester)
                .setDateFrom(sourceDate.minusDays(2).toString()).setDateTo(until.minusDays(1).toString()).build());
        when(academic.getSemesterArchiveAuthorityState(semester))
                .thenReturn(SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        when(academic.getAssignmentsByIds(List.of(seed))).thenReturn(List.of(AssignmentInfo.newBuilder()
                .setId(seed).setTeacherId(seed + 50).setGroupId(group).setSubjectId(subject).setSemesterId(semester)
                .setLessonType("lecture").setValidFrom(sourceDate.minusDays(2).toString())
                .setValidUntilExclusive(until.toString()).build()));
        UUID createKey = UUID.randomUUID();
        String createBody = objectMapper.writeValueAsString(new CreateOneOffLessonRequest(group, subject, seed,
                sourceDate, (short) 2, null, null, "original"));
        JsonNode created = objectMapper.readTree(mockMvc.perform(oneOffActor(post("/schedule/one-off-lessons"), actor, group)
                .header("Idempotency-Key", createKey).contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long origin = created.path("id").asLong(), source = created.path("physicalLessonId").asLong();
        long occurrence = jdbcTemplate.queryForObject("SELECT occurrence_id FROM lessons WHERE id = ?", Long.class, source);
        UUID bindingKey = UUID.randomUUID();
        long binding = jdbcTemplate.queryForObject("""
                INSERT INTO lesson_homework_bindings (occurrence_id, current_lesson_id, homework_id,
                    actor_id, request_key, payload_hash, state, revision)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 1) RETURNING binding_id
                """, Long.class, occurrence, source, seed + 60, actor, bindingKey, new byte[32]);
        TransferLessonRequest request = new TransferLessonRequest(targetDate, 3, null, null, "moved", "1", UUID.randomUUID());
        String body = objectMapper.writeValueAsString(request);
        mockMvc.perform(oneOffActor(post("/schedule/lessons/{id}/transfer", source), actor + 1, group + 1)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        JsonNode accepted;
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            java.util.function.Supplier<JsonNode> command = () -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("transfer race start timed out");
                    return objectMapper.readTree(mockMvc.perform(oneOffActor(post("/schedule/lessons/{id}/transfer", source), actor, group)
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted())
                            .andReturn().getResponse().getContentAsString());
                } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
            };
            var first = CompletableFuture.supplyAsync(command, executor);
            var second = CompletableFuture.supplyAsync(command, executor);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            accepted = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS).path("operationId").asText())
                    .isEqualTo(accepted.path("operationId").asText());
        }
        long target = accepted.path("targetLessonId").asLong();
        assertThat(target).isPositive().isNotEqualTo(source);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons WHERE occurrence_id = ?", Long.class, occurrence)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_transfer_operations WHERE occurrence_id = ?", Long.class, occurrence)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("SELECT date, lesson_number, classroom, physical_lesson_id FROM schedule_one_off_lessons WHERE id = ?", origin))
                .containsEntry("date", java.sql.Date.valueOf(sourceDate))
                .containsEntry("classroom", "original").containsEntry("physical_lesson_id", target);
        assertThat(jdbcTemplate.queryForObject("SELECT lesson_number FROM schedule_one_off_lessons WHERE id = ?", Integer.class, origin)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForMap("SELECT occurrence_id, current_lesson_id, actor_id, request_key, revision, state FROM lesson_homework_bindings WHERE binding_id = ?", binding))
                .containsEntry("occurrence_id", occurrence).containsEntry("current_lesson_id", target)
                .containsEntry("actor_id", actor).containsEntry("request_key", bindingKey).containsEntry("revision", 2L).containsEntry("state", "ACTIVE");
        mockMvc.perform(oneOffActor(post("/schedule/lessons/{id}/transfer", source), actor, group)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new TransferLessonRequest(
                        targetDate, 4, null, null, "moved", "1", request.requestKey())))).andExpect(status().isConflict());
        // A stale source cancellation must not cancel the new current physical generation.
        mockMvc.perform(oneOffActor(patch("/schedule/lessons/{id}/cancel", source), actor, group)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"late old source\"}"))
                .andExpect(status().isConflict());
        JsonNode replay = objectMapper.readTree(mockMvc.perform(oneOffActor(post("/schedule/one-off-lessons"), actor, group)
                .header("Idempotency-Key", createKey).contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(replay.path("id").asLong()).isEqualTo(origin);
        assertThat(replay.path("physicalLessonId").asLong()).isEqualTo(target);
        assertThat(replay.path("date").asText()).isEqualTo(targetDate.toString());
        JsonNode list = objectMapper.readTree(mockMvc.perform(oneOffActor(get("/schedule/one-off-lessons"), actor, group)
                .param("groupId", Long.toString(group)).param("dateFrom", targetDate.toString()).param("dateTo", targetDate.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list.toString()).contains("\"physicalLessonId\":" + target).contains(targetDate.toString()).contains("moved");
        JsonNode oldDay = objectMapper.readTree(mockMvc.perform(oneOffActor(get("/schedule/one-off-lessons"), actor, group)
                .param("groupId", Long.toString(group)).param("dateFrom", sourceDate.toString()).param("dateTo", sourceDate.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(oldDay.toString()).doesNotContain("\"physicalLessonId\":" + target);
        var nextRequest = oneOffActor(get("/schedule/lessons/next"), actor, group).param("groupId", Long.toString(group))
                .param("semesterId", Long.toString(semester)).param("subjectId", Long.toString(subject))
                .param("lessonType", "LECTURE").param("fromDate", targetDate.toString());
        mockMvc.perform(nextRequest).andExpect(status().isNoContent());
        List<Map<String, Object>> batches = outboxStorage.findPending(1000).stream()
                .filter(row -> row.eventType().equals("lesson.transfer.requested")).map(this::eventEnvelope)
                .filter(event -> accepted.path("operationId").asText().equals(String.valueOf(payload(event).get("operation_id"))))
                .toList();
        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().get("event_version")).isEqualTo(2);
        Map<String, Object> batch = payload(batches.getFirst());
        for (String side : List.of("source", "target")) {
            Map<?, ?> snapshot = (Map<?, ?>) batch.get(side);
            assertThat(snapshot.get("schedule_item_id")).isNull();
            assertThat(((Number) snapshot.get("one_off_lesson_id")).longValue()).isEqualTo(origin);
        }
        transferWriter.republishPendingBatches();
        ack(batch, "attendance-service", "ATTENDANCE", -1, "APPLIED");
        ack(batch, "academic-service", "ACADEMIC", 0, "APPLIED");
        assertThat(transferWriter.status(UUID.fromString(accepted.path("operationId").asText())).state()).isEqualTo("COMPLETED");
        JsonNode next = objectMapper.readTree(mockMvc.perform(oneOffActor(get("/schedule/lessons/next"), actor, group)
                .param("groupId", Long.toString(group)).param("semesterId", Long.toString(semester))
                .param("subjectId", Long.toString(subject)).param("lessonType", "LECTURE").param("fromDate", targetDate.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(next.path("lessonId").asLong()).isEqualTo(target);
        assertThat(next.path("occurrenceId").asLong()).isEqualTo(occurrence);
        mockMvc.perform(oneOffActor(patch("/schedule/lessons/{id}/cancel", target), actor, group)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"current canceled\"}")).andExpect(status().isOk());
        mockMvc.perform(oneOffActor(patch("/schedule/lessons/{id}/restore", target), actor, group)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, target)).isEqualTo("planned");
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, source)).isEqualTo("transferred");
        assertThat(jdbcTemplate.queryForObject("SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?", Long.class, occurrence)).isEqualTo(target);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons WHERE occurrence_id = ?", Long.class, occurrence)).isEqualTo(2);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder oneOffActor(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, long actor, long group) {
        return request.header("X-User-Id", actor).header("X-User-Role", "STUDENT")
                .header("X-Group-Id", group).header("X-Is-Headman", "true");
    }

    private void ack(Map<String, Object> requestPayload, String source, String participant,
                     int batchIndex, String result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", requestPayload.get("operation_id"));
        payload.put("participant", participant);
        payload.put("batch_index", batchIndex);
        payload.put("result", result);
        payload.put("retryable", false);
        payload.put("transfer_payload_hash", requestPayload.get("transfer_payload_hash"));
        payload.put("source_lesson_id", requestPayload.get("source_lesson_id"));
        payload.put("target_lesson_id", requestPayload.get("target_lesson_id"));
        UUID eventId = UUID.randomUUID();
        ackEventIds.add(eventId);
        eventConsumer.onEvent(Map.of(
                "event_type", "lesson.transfer.participant.applied",
                "event_version", 1,
                "event_id", eventId.toString(),
                "trace_id", UUID.randomUUID().toString(),
                "source", source,
                "payload", payload));
    }

    private Map<String, Object> eventEnvelope(OutboxRecord row) {
        try {
            return objectMapper.readValue(row.payload(), new TypeReference<>() { });
        } catch (Exception error) {
            throw new AssertionError("transfer outbox envelope must be valid JSON", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    private static int batchIndex(Map<String, Object> event) {
        return ((Number) payload(event).get("batch_index")).intValue();
    }

    private static int batchIndexFromPayload(Map<String, Object> payload) {
        return ((Number) payload.get("batch_index")).intValue();
    }

    private Fixture insertFixture() {
        long seed = FIXTURE_SEQUENCE.incrementAndGet();
        LocalDate sourceDate = LocalDate.now().plusDays(30);
        if (sourceDate.getDayOfWeek().getValue() == 7) sourceDate = sourceDate.plusDays(1);
        LocalDate targetDate = sourceDate.plusDays(1);
        if (targetDate.getDayOfWeek().getValue() == 7) targetDate = targetDate.plusDays(1);
        long assignmentId = seed;
        long groupId = seed + 10;
        long subjectId = seed + 20;
        long semesterId = seed + 30;
        long actorId = seed + 40;
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """, assignmentId, groupId, subjectId, semesterId, seed + 50,
                sourceDate.minusDays(2), targetDate.plusDays(5), targetDate.plusDays(5));
        Long itemId = jdbcTemplate.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room)
                VALUES (?, ?, ?, ?, ?, 2, '09:00'::time, '10:30'::time, 'all', 'A-204')
                RETURNING id
                """, Long.class, assignmentId, groupId, subjectId, semesterId,
                sourceDate.getDayOfWeek().getValue());
        Long occurrenceId = jdbcTemplate.queryForObject("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture') RETURNING id
                """, Long.class, itemId, sourceDate, assignmentId, groupId, subjectId,
                semesterId, seed + 50);
        Long lessonId = jdbcTemplate.queryForObject("""
                INSERT INTO lessons
                    (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type, lesson_number, day_of_week,
                     start_time, end_time, room_snapshot, week_type_snapshot,
                     generation, revision, date, status, is_geo_blocked)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 2, ?, '09:00'::time, '10:30'::time,
                        'A-204', 'all', 1, 1, ?, 'planned'::lesson_status, false)
                RETURNING id
                """, Long.class, itemId, occurrenceId, assignmentId, groupId, subjectId,
                semesterId, seed + 50, sourceDate.getDayOfWeek().getValue(), sourceDate);
        jdbcTemplate.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                lessonId, occurrenceId);
        jdbcTemplate.update("""
                INSERT INTO lesson_lifecycle_entries
                    (occurrence_id, revision, action, lesson_id, generation, actor_id, occurred_at)
                VALUES (?, 1, 'CREATED', ?, 1, ?, NOW())
                """, occurrenceId, lessonId, actorId);
        return new Fixture(occurrenceId, lessonId, groupId, subjectId, semesterId, actorId,
                sourceDate, targetDate, UUID.randomUUID());
    }

    private record Fixture(long occurrenceId, long sourceLessonId, long groupId, long subjectId,
                          long semesterId, long actorId, LocalDate sourceDate, LocalDate targetDate,
                          UUID requestKey) { }
}
