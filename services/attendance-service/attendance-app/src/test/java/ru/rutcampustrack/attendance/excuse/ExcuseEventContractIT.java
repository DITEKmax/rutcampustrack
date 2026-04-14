package ru.rutcampustrack.attendance.excuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.attendance.security.RequestContext;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Contract test (AC-7) for excuse event publishing.
 *
 * <p>Publishes real ExcuseTicket events through the fanout exchange using a private
 * test queue bound for the duration of the test. Parses the received JSON and asserts
 * the fields that notification-bot {@code headman_alerts.py} reads.
 *
 * <p><b>Local-env note.</b> Requires Docker for the MongoDB + RabbitMQ Testcontainers;
 * boots from {@link AbstractAttendanceIntegrationTest}. On a dev machine without
 * Docker Desktop running this class is expected to fail early in Spring context boot
 * (RyukResourceReaper) — same mode as every other *IT in this module. CI with Docker
 * available runs it normally.
 */
class ExcuseEventContractIT extends AbstractAttendanceIntegrationTest {

    private static final String EXCHANGE = "rut-uit.events";

    @Autowired
    private ExcuseService excuseService;

    @Autowired
    private ExcuseRepository excuseRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private org.springframework.amqp.core.AmqpAdmin amqpAdmin;

    // RequestContext is a request-scoped bean; the service calls getUserId/getGroupId/isHeadman.
    // Stub it via MockitoBean in the integration profile would be cleaner, but we can also
    // override per-test via Autowired + reset — for this contract test we rely on a Mockito
    // spy installed in setUp.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private RequestContext requestContext;

    private String testQueueName;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), ExcuseTicket.class);

        // Declare a temporary queue bound to the fanout exchange so we can capture events
        // emitted by the service. Name is unique per test run to avoid collisions.
        testQueueName = "excuse.contract-test." + UUID.randomUUID();
        FanoutExchange exchange = new FanoutExchange(EXCHANGE, true, false);
        Queue queue = QueueBuilder.nonDurable(testQueueName).autoDelete().build();
        Binding binding = BindingBuilder.bind(queue).to(exchange);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        // Drain anything left over (defensive).
        //noinspection StatementWithEmptyBody
        while (rabbitTemplate.receive(testQueueName, 50) != null) { /* drain */ }

        // JWT / security stub: plain student 100 in group 10.
        Mockito.reset(requestContext, academicGrpcClient);
        when(requestContext.getUserId()).thenReturn(100L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(requestContext.isHeadman()).thenReturn(false);
        when(academicGrpcClient.getUserDisplayName(anyLong())).thenReturn("Иванов Иван");
    }

    @Test
    void createExcuse_publishesRequestedEvent_matchingBotContract() throws Exception {
        CreateExcuseRequest req = new CreateExcuseRequest(
                List.of(1L, 2L),
                ExcuseType.ILLNESS,
                "Болею, справка будет"
        );

        ExcuseTicket saved = excuseService.createExcuse(req);
        assertThat(saved.getId()).isNotNull();

        Object message = rabbitTemplate.receiveAndConvert(testQueueName, Duration.ofSeconds(5).toMillis());
        assertThat(message).as("excuse.requested event must reach the test queue").isNotNull();

        // The default Jackson2JsonMessageConverter (registered in RabbitConfig) deserialises
        // the envelope into LinkedHashMap; re-serialise through ObjectMapper to traverse.
        JsonNode root = mapper.valueToTree(message);

        assertThat(root.get("event_type").asText()).isEqualTo("excuse.requested");
        assertThat(root.get("event_id").asText()).isNotBlank();
        assertThat(root.get("occurred_at").asText()).isNotBlank();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        // Bot-critical fields (headman_alerts.py reads these)
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("student_name").asText()).isEqualTo("Иванов Иван");
        // D-19: lowercase enum
        assertThat(payload.get("excuse_type").asText()).isEqualTo("illness");
        // Supporting fields
        assertThat(payload.get("student_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("ticket_id").asText()).isEqualTo(saved.getId());
        assertThat(payload.get("lesson_ids").isArray()).isTrue();
        assertThat(payload.get("lesson_ids").size()).isEqualTo(2);
        assertThat(payload.get("comment").asText()).isEqualTo("Болею, справка будет");
    }

    @Test
    void updateStatus_publishesDecidedEvent_matchingBotContract() throws Exception {
        // Seed a SUBMITTED ticket directly (skip createExcuse to avoid a second event on the queue).
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(100L)
                .groupId(10L)
                .studentName("Иванов Иван")
                .lessonIds(List.of(5L))
                .excuseType(ExcuseType.FREE_ATTENDANCE)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
        ticket = excuseRepository.save(ticket);
        // Drain anything residual (belt-and-braces).
        //noinspection StatementWithEmptyBody
        while (rabbitTemplate.receive(testQueueName, 50) != null) { /* drain */ }

        // Flip caller to headman of the same group (different user).
        Long headmanId = 777L;
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(requestContext.isHeadman()).thenReturn(true);

        UpdateExcuseStatusRequest decision = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, "справка предоставлена");

        ExcuseTicket decided = excuseService.updateStatus(ticket.getId(), decision);
        assertThat(decided.getStatus()).isEqualTo(ExcuseTicketStatus.APPROVED);

        Object message = rabbitTemplate.receiveAndConvert(testQueueName, Duration.ofSeconds(5).toMillis());
        assertThat(message).as("excuse.decided event must reach the test queue").isNotNull();

        JsonNode root = mapper.valueToTree(message);
        assertThat(root.get("event_type").asText()).isEqualTo("excuse.decided");
        JsonNode payload = root.get("payload");
        assertThat(payload.get("ticket_id").asText()).isEqualTo(ticket.getId());
        assertThat(payload.get("status").asText()).isEqualTo("approved");
        assertThat(payload.get("decision_by").asLong()).isEqualTo(headmanId);
        assertThat(payload.get("decision_comment").asText()).isEqualTo("справка предоставлена");
        assertThat(payload.get("user_id").asLong()).isEqualTo(100L);
        assertThat(payload.get("student_id").asLong()).isEqualTo(100L);
    }
}
