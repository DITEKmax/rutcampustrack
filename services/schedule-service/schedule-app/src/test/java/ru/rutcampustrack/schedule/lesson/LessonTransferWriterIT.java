package ru.rutcampustrack.schedule.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;
import ru.rutcampustrack.schedule.event.EventConsumer;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL proof for the transfer transaction, replay, fixed batches and wire ACKs. */
@AutoConfigureMockMvc
class LessonTransferWriterIT extends AbstractScheduleIntegrationTest {

    private static final AtomicLong FIXTURE_SEQUENCE = new AtomicLong(System.currentTimeMillis() * 1000L);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LessonTransferWriter transferWriter;
    @Autowired private EventConsumer eventConsumer;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxStorage outboxStorage;
    @Autowired private MockMvc mockMvc;

    private final List<UUID> ackEventIds = new ArrayList<>();
    private Fixture fixture;

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
