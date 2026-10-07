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
import ru.rutcampustrack.attendance.events.EventSchemaValidator;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.studentrequest.entity.ExcuseDecisionSelectionDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("status").asText()).isEqualTo("approved");
        assertThat(payload.get("decision_comment").asText()).isEqualTo("справка предоставлена");
        assertThat(payload.get("decided_at").asText()).isEqualTo(decidedAt.toString());
    }

    @Test
    void publishDecided_partialApprovalCarriesExactExcludedReceiptSnapshots() throws Exception {
        var included = StudentLessonSnapshotDocument.builder().lessonId(1L).occurrenceId("101").build();
        var excluded = StudentLessonSnapshotDocument.builder().lessonId(2L).occurrenceId("102")
                .date(LocalDate.of(2026, 10, 7)).lessonNumber(3).subjectName("Математика")
                .subjectType("lecture").groupId(10L).attendanceStatus("ABSENT").build();
        var legacy = StudentLessonSnapshotDocument.builder().lessonId(3L).occurrenceId("103").build();
        var selection = new ExcuseDecisionSelectionDocument("f13db6a3-4e76-4f8a-8703-4871f23fbdcb",
                "a".repeat(64), List.of(included), List.of(excluded, legacy));
        var ticket = ExcuseTicket.builder().id("partial-ticket").studentId(100L).groupId(10L)
                .status(ExcuseTicketStatus.APPROVED).lessonIds(List.of(1L))
                .decisionAt(Instant.parse("2026-10-07T12:00:00Z")).decisionSelection(selection).build();

        publisher().publishDecided(ticket);

        JsonNode envelope = captureEnvelope("excuse.decided"); // Exactly one outbox event.
        assertThat(EventSchemaValidator.validate("excuse.decided.json", envelope.toString())).isEmpty();
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("included_lesson_ids")).isEqualTo(objectMapper.readTree("[1]"));
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("excluded_lesson_ids")).isEqualTo(objectMapper.readTree("[2,3]"));
        assertThat(payload.get("excluded_occurrence_ids")).isEqualTo(objectMapper.readTree("[\"102\",\"103\"]"));
        assertThat(payload.get("excluded_lessons")).isEqualTo(objectMapper.readTree("""
                [{"lesson_id":2,"occurrence_id":"102","date":"2026-10-07","lesson_number":3,
                  "subject_name":"Математика","lesson_type":"lecture"},
                 {"lesson_id":3,"occurrence_id":"103","date":null,"lesson_number":null,
                  "subject_name":null,"lesson_type":null}]
                """));
        // Receipt metadata is projected without group/attendance/reason/attachment details.
        assertThat(payload.get("excluded_lessons").get(0).size()).isEqualTo(6);
    }

    @Test
    void publishDecided_allTerminalVariantsWithNullCommentMatchSchema() throws Exception {
        Instant decidedAt = Instant.parse("2026-04-14T13:00:00Z");
        for (ExcuseTicketStatus status : List.of(ExcuseTicketStatus.APPROVED,
                ExcuseTicketStatus.REJECTED, ExcuseTicketStatus.CANCELLED)) {
            publisher().publishDecided(ExcuseTicket.builder()
                    .id("terminal-" + status.name().toLowerCase())
                    .studentId(100L).groupId(10L).lessonIds(List.of(1L))
                    .excuseType(ExcuseType.ILLNESS).status(status)
                    .decisionBy(status == ExcuseTicketStatus.CANCELLED ? null : 777L)
                    .decisionAt(decidedAt).createdAt(decidedAt).updatedAt(decidedAt)
                    .build());
        }

        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(outboxStorage, times(3)).save(eq("excuse.decided"), payloads.capture());
        assertThat(payloads.getAllValues()).allSatisfy(payload -> {
            try {
                assertThat(EventSchemaValidator.validate("excuse.decided.json", payload)).isEmpty();
            } catch (Exception error) {
                throw new AssertionError("excuse.decided must validate", error);
            }
        });
        assertThat(payloads.getAllValues()).extracting(payload -> {
            try {
                return objectMapper.readTree(payload).get("payload").get("status").asText();
            } catch (Exception error) {
                throw new AssertionError("valid JSON expected", error);
            }
        }).containsExactlyInAnyOrder("approved", "rejected", "cancelled");
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
