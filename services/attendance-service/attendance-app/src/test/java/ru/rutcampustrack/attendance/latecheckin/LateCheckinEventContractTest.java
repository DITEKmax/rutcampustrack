package ru.rutcampustrack.attendance.latecheckin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.events.EventSchemaValidator;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract-тесты publisher'а late-checkin events (M09 G3.4, 14 P0-1) —
 * проверяют, что envelope.payload укладывается в JSON Schema'ы
 * {@code event-schemas/late_checkin.requested.json} и {@code .decided.json}.
 * Bot-consumer доверяет этим schema'ам, поэтому любое расхождение здесь
 * ломает prod-flow.
 *
 * <p>Чистый unit-тест: используется in-memory {@link OutboxStorage} + прямая
 * валидация через {@link EventSchemaValidator} (networknt json-schema). Без
 * Spring context / testcontainers / Rabbit.
 */
class LateCheckinEventContractTest {

    private CapturingOutbox outbox;
    private LateCheckinEventPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        outbox = new CapturingOutbox();
        publisher = new LateCheckinEventPublisher(outbox, mapper);
    }

    @Test
    void publishRequested_producesEnvelopeMatchingSchema() throws Exception {
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id("req-1")
                .studentId(100L)
                .groupId(10L)
                .lessonId(42L)
                .studentName("Иванов И.")
                .status(LateCheckinRequestStatus.PENDING)
                .createdAt(Instant.parse("2026-04-23T09:00:00Z"))
                .updatedAt(Instant.parse("2026-04-23T09:00:00Z"))
                .build();

        publisher.publishRequested(request, LocalDate.of(2026, 4, 23), 3, 7L, "Математика");

        assertThat(outbox.captured).hasSize(1);
        OutboxRecord record = outbox.captured.get(0);
        assertThat(record.eventType()).isEqualTo("late_checkin.requested");

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "late_checkin.requested.json", record.payload());
        assertThat(errors)
                .as("late_checkin.requested payload должен соответствовать schema")
                .isEmpty();

        // Bot-critical fields (headman_alerts.py)
        var root = mapper.readTree(record.payload());
        var payload = root.get("payload");
        assertThat(root.get("event_type").asText()).isEqualTo("late_checkin.requested");
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("lesson_id").asLong()).isEqualTo(42L);
        assertThat(payload.get("student_name").asText()).isEqualTo("Иванов И.");
        assertThat(payload.get("lesson_number").asInt()).isEqualTo(3);
        assertThat(payload.get("subject_id").asLong()).isEqualTo(7L);
        assertThat(payload.get("subject_name").asText()).isEqualTo("Математика");
    }

    @Test
    void publishDecided_approved_producesEnvelopeMatchingSchema() throws Exception {
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id("req-1")
                .studentId(100L)
                .groupId(10L)
                .lessonId(42L)
                .studentName("Иванов И.")
                .status(LateCheckinRequestStatus.APPROVED)
                .decisionBy(777L)
                .decisionAt(Instant.parse("2026-04-23T10:30:00Z"))
                .createdAt(Instant.parse("2026-04-23T09:00:00Z"))
                .updatedAt(Instant.parse("2026-04-23T10:30:00Z"))
                .build();

        publisher.publishDecided(request, LocalDate.of(2026, 4, 23), 3, 7L, "Математика");

        assertThat(outbox.captured).hasSize(1);
        OutboxRecord record = outbox.captured.get(0);
        assertThat(record.eventType()).isEqualTo("late_checkin.decided");

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "late_checkin.decided.json", record.payload());
        assertThat(errors)
                .as("late_checkin.decided (approved) payload должен соответствовать schema")
                .isEmpty();

        var root = mapper.readTree(record.payload());
        var payload = root.get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("approved");
        assertThat(payload.get("decision_by").asLong()).isEqualTo(777L);
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("lesson_id").asLong()).isEqualTo(42L);
    }

    @Test
    void publishDecided_rejected_producesEnvelopeMatchingSchema() throws Exception {
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id("req-2")
                .studentId(100L)
                .groupId(10L)
                .lessonId(42L)
                .studentName("Иванов И.")
                .status(LateCheckinRequestStatus.REJECTED)
                .decisionBy(777L)
                .decisionAt(Instant.parse("2026-04-23T10:30:00Z"))
                .createdAt(Instant.parse("2026-04-23T09:00:00Z"))
                .updatedAt(Instant.parse("2026-04-23T10:30:00Z"))
                .build();

        publisher.publishDecided(request, LocalDate.of(2026, 4, 23), 3, 7L, "Математика");

        OutboxRecord record = outbox.captured.get(0);
        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "late_checkin.decided.json", record.payload());
        assertThat(errors)
                .as("late_checkin.decided (rejected) payload должен соответствовать schema")
                .isEmpty();

        var payload = mapper.readTree(record.payload()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("rejected");
    }

    // =========================================================== helper

    private static final class CapturingOutbox implements OutboxStorage {
        final List<OutboxRecord> captured = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public void save(String eventType, String payload) {
            captured.add(new OutboxRecord(nextId++, eventType, payload, Instant.now()));
        }

        @Override public List<OutboxRecord> findPending(int limit) { return List.copyOf(captured); }
        @Override public void markSent(Object id) {}
        @Override public void markFailed(Object id, String errorMsg) {}
        @Override public long deleteSentBefore(Instant before) { return 0; }
        @Override public long countPending() { return captured.size(); }
        @Override public long oldestPendingAgeSeconds() { return 0; }
    }
}
