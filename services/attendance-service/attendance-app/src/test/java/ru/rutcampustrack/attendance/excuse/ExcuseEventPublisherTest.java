package ru.rutcampustrack.attendance.excuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for ExcuseEventPublisher (Phase 59-05 + M02 Группа 5).
 *
 * <p>После M02: publisher пишет в OutboxStorage (не в Rabbit напрямую).
 * Mockito мочит OutboxStorage; сериализацию делает реальный ObjectMapper
 * (чтобы проверить envelope + payload на JSON уровне).
 *
 * Covers D-19 (excuse.requested envelope) and D-20 (excuse.decided envelope).
 * D-27: payload shape must match what notification-bot headman_alerts.py reads.
 */
@ExtendWith(MockitoExtension.class)
class ExcuseEventPublisherTest {

    @Mock
    private OutboxStorage outboxStorage;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExcuseEventPublisher publisher() {
        return new ExcuseEventPublisher(outboxStorage, objectMapper);
    }

    private JsonNode captureEnvelope(String eventType) throws Exception {
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxStorage).save(eq(eventType), payloadCaptor.capture());
        return objectMapper.readTree(payloadCaptor.getValue());
    }

    // D-19
    @Test
    void publishRequested_sendsCorrectEnvelopeToOutbox() throws Exception {
        Instant now = Instant.parse("2026-04-14T12:00:00Z");
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("65f0a1b2c3d4e5f6a7b8c9d0")
                .studentId(100L).groupId(10L)
                .studentName("Иванов Иван")
                .lessonIds(List.of(1L, 2L, 3L))
                .excuseType(ExcuseType.ILLNESS)
                .comment("Болею, справка будет")
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now).updatedAt(now)
                .build();

        publisher().publishRequested(ticket, List.of());

        JsonNode envelope = captureEnvelope("excuse.requested");
        assertThat(envelope.get("event_type").asText()).isEqualTo("excuse.requested");
        assertThat(envelope.has("event_id")).isTrue();
        assertThat(envelope.has("occurred_at")).isTrue();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("ticket_id").asText()).isEqualTo("65f0a1b2c3d4e5f6a7b8c9d0");
        assertThat(payload.get("student_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("student_name").asText()).isEqualTo("Иванов Иван");
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("excuse_type").asText()).isEqualTo("illness");
        assertThat(payload.get("comment").asText()).isEqualTo("Болею, справка будет");
        assertThat(payload.get("created_at").asText()).isEqualTo(now.toString());
    }

    // D-19: FREE_ATTENDANCE → "free_attendance"
    @Test
    void publishRequested_freeAttendanceType_serializesLowercaseWithUnderscore() throws Exception {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("t-free").studentId(200L).groupId(20L)
                .studentName("Петров Пётр")
                .lessonIds(List.of(5L))
                .excuseType(ExcuseType.FREE_ATTENDANCE)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .build();

        publisher().publishRequested(ticket, List.of());

        JsonNode envelope = captureEnvelope("excuse.requested");
        assertThat(envelope.get("payload").get("excuse_type").asText())
                .isEqualTo("free_attendance");
    }

    // D-20
    @Test
    void publishDecided_approvedTicket_sendsCorrectEnvelope() throws Exception {
        Instant decidedAt = Instant.parse("2026-04-14T13:00:00Z");
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("65f0a1b2c3d4e5f6a7b8c9d0")
                .studentId(100L).groupId(10L)
                .studentName("Иванов Иван")
                .lessonIds(List.of(1L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.APPROVED)
                .decisionBy(777L)
                .decisionComment("справка предоставлена")
                .decisionAt(decidedAt)
                .createdAt(Instant.now()).updatedAt(decidedAt)
                .build();

        publisher().publishDecided(ticket);

        JsonNode envelope = captureEnvelope("excuse.decided");
        assertThat(envelope.get("event_type").asText()).isEqualTo("excuse.decided");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("ticket_id").asText()).isEqualTo("65f0a1b2c3d4e5f6a7b8c9d0");
        assertThat(payload.get("student_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("decision_by").asLong()).isEqualTo(777L);
        assertThat(payload.get("status").asText()).isEqualTo("approved");
        assertThat(payload.get("decision_comment").asText()).isEqualTo("справка предоставлена");
        assertThat(payload.get("decided_at").asText()).isEqualTo(decidedAt.toString());
    }

    // D-27 regression
    @Test
    void publishRequested_userIdEqualsStudentId() throws Exception {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("t-x").studentId(42L).groupId(10L)
                .studentName("X")
                .lessonIds(List.of(1L))
                .excuseType(ExcuseType.OTHER)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .build();

        publisher().publishRequested(ticket, List.of());

        JsonNode payload = captureEnvelope("excuse.requested").get("payload");
        assertThat(payload.get("user_id").asLong())
                .isEqualTo(payload.get("student_id").asLong());
    }
}
