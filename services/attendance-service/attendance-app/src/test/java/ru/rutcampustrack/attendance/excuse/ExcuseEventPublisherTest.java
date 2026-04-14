package ru.rutcampustrack.attendance.excuse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for ExcuseEventPublisher (Phase 59-05).
 * Covers D-19 (excuse.requested envelope) and D-20 (excuse.decided envelope).
 * D-27: payload shape must match what notification-bot headman_alerts.py reads
 *       (payload.user_id, payload.group_id, payload.student_name, payload.excuse_type lowercase).
 */
@ExtendWith(MockitoExtension.class)
class ExcuseEventPublisherTest {

    private static final String EXCHANGE = "rut-uit.events";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ExcuseEventPublisher publisher;

    // ---------------------------------------------------------------------
    // D-19: publishRequested envelope & payload shape
    // ---------------------------------------------------------------------
    @Test
    void publishRequested_sendsCorrectEnvelopeToFanoutExchange() {
        Instant now = Instant.parse("2026-04-14T12:00:00Z");
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("65f0a1b2c3d4e5f6a7b8c9d0")
                .studentId(100L)
                .groupId(10L)
                .studentName("Иванов Иван")
                .lessonIds(List.of(1L, 2L, 3L))
                .excuseType(ExcuseType.ILLNESS)
                .comment("Болею, справка будет")
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        publisher.publishRequested(ticket);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(""), captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) captor.getValue();
        assertThat(envelope).containsKey("event_type").containsKey("event_id")
                .containsKey("occurred_at").containsKey("payload");
        assertThat(envelope.get("event_type")).isEqualTo("excuse.requested");
        assertThat(envelope.get("event_id")).isInstanceOf(String.class);
        assertThat(envelope.get("occurred_at")).isInstanceOf(String.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload.get("ticket_id")).isEqualTo("65f0a1b2c3d4e5f6a7b8c9d0");
        assertThat(payload.get("student_id")).isEqualTo(100L);
        // D-27: bot reads payload.user_id (not student_id)
        assertThat(payload.get("user_id")).isEqualTo(100L);
        assertThat(payload.get("student_name")).isEqualTo("Иванов Иван");
        assertThat(payload.get("group_id")).isEqualTo(10L);
        assertThat(payload.get("lesson_ids")).isEqualTo(List.of(1L, 2L, 3L));
        // D-19: excuse_type must be lowercase (schema enum)
        assertThat(payload.get("excuse_type")).isEqualTo("illness");
        assertThat(payload.get("comment")).isEqualTo("Болею, справка будет");
        assertThat(payload.get("created_at")).isEqualTo(now.toString());
    }

    // ---------------------------------------------------------------------
    // D-19: FREE_ATTENDANCE enum → "free_attendance" lowercase
    // ---------------------------------------------------------------------
    @Test
    void publishRequested_freeAttendanceType_serializesLowercaseWithUnderscore() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("t-free")
                .studentId(200L)
                .groupId(20L)
                .studentName("Петров Пётр")
                .lessonIds(List.of(5L))
                .excuseType(ExcuseType.FREE_ATTENDANCE)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .build();

        publisher.publishRequested(ticket);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(""), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload.get("excuse_type")).isEqualTo("free_attendance");
    }

    // ---------------------------------------------------------------------
    // D-20: publishDecided envelope & payload shape
    // ---------------------------------------------------------------------
    @Test
    void publishDecided_approvedTicket_sendsCorrectEnvelope() {
        Instant decidedAt = Instant.parse("2026-04-14T13:00:00Z");
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("65f0a1b2c3d4e5f6a7b8c9d0")
                .studentId(100L)
                .groupId(10L)
                .studentName("Иванов Иван")
                .lessonIds(List.of(1L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.APPROVED)
                .decisionBy(777L)
                .decisionComment("справка предоставлена")
                .decisionAt(decidedAt)
                .createdAt(Instant.now())
                .updatedAt(decidedAt)
                .build();

        publisher.publishDecided(ticket);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(""), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) captor.getValue();

        assertThat(envelope.get("event_type")).isEqualTo("excuse.decided");
        assertThat(envelope.get("event_id")).isInstanceOf(String.class);
        assertThat(envelope.get("occurred_at")).isInstanceOf(String.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload.get("ticket_id")).isEqualTo("65f0a1b2c3d4e5f6a7b8c9d0");
        assertThat(payload.get("student_id")).isEqualTo(100L);
        assertThat(payload.get("user_id")).isEqualTo(100L);
        assertThat(payload.get("decision_by")).isEqualTo(777L);
        assertThat(payload.get("status")).isEqualTo("approved");
        assertThat(payload.get("decision_comment")).isEqualTo("справка предоставлена");
        assertThat(payload.get("decided_at")).isEqualTo(decidedAt.toString());
    }

    // ---------------------------------------------------------------------
    // D-27 regression: payload.user_id MUST equal ticket.studentId (bot reads user_id)
    // ---------------------------------------------------------------------
    @Test
    void publishRequested_userIdEqualsStudentId() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("t-x")
                .studentId(42L)
                .groupId(10L)
                .studentName("X")
                .lessonIds(List.of(1L))
                .excuseType(ExcuseType.OTHER)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .build();

        publisher.publishRequested(ticket);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(""), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload.get("user_id")).isEqualTo(payload.get("student_id"));
    }
}
