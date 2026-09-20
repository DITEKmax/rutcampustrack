package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Real RabbitMQ proof for the decision-event retry and DLQ boundary.
 *
 * <p>Malformed decision envelopes are rejected immediately. Transient
 * dependency failures are retried three times, then rejected to the queue's
 * configured dead-letter exchange. Both cases use the application listener
 * container, not a direct EventConsumer invocation.
 */
class RabbitDecisionRetryIT extends AbstractAttendanceIntegrationTest {

    private static final String DLQ = "attendance-service.events.dlq";
    private static final String RABBIT_VHOST = "rct_requests_isolation_rabbit_decision_retry";

    static {
        ensureRabbitVhost(RABBIT_VHOST);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @org.springframework.test.context.DynamicPropertySource
    static void overrideRabbitVhost(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.virtual-host", () -> RABBIT_VHOST);
    }

    @MockitoBean
    private StudentRequestService studentRequestService;

    @BeforeEach
    void cleanQueues() {
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getContainerId()).isNotBlank();
        System.out.printf("requests-isolation runtime rabbitContainer=%s rabbitPort=%s rabbitVhost=%s labels=%s%n",
                RABBITMQ.getContainerId(), RABBITMQ.getMappedPort(5672), RABBIT_VHOST,
                RABBITMQ.getLabels());
        amqpAdmin.purgeQueue("attendance-service.events", false);
        amqpAdmin.purgeQueue("attendance-service.events.dlq", false);
        reset(studentRequestService);
    }

    @Test
    void malformedDecisionEnvelope_isBoundedAndDeadLetters() {
        String eventId = UUID.randomUUID().toString();
        publish(Map.of(
                "event_type", "late_checkin.decision",
                "event_id", eventId,
                "event_version", 1,
                "source", "untrusted-source",
                "payload", Map.of("request_id", "507f1f77bcf86cd799439011")
        ));

        Message deadLetter = rabbitTemplate.receive(DLQ, 10_000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(new String(deadLetter.getBody()))
                .contains(eventId)
                .contains("late_checkin.decision");
    }

    @Test
    void transientDecisionFailure_retriesThreeTimesThenDeadLetters() {
        String requestId = "507f1f77bcf86cd799439011";
        long actorId = 42L;
        doThrow(new AcademicServiceUnavailableException("simulated dependency outage"))
                .when(studentRequestService)
                .decideLateCheckinFromBot(requestId, actorId, true);

        String eventId = UUID.randomUUID().toString();
        publish(Map.of(
                "event_type", "late_checkin.decision",
                "event_id", eventId,
                "event_version", 1,
                "source", "notification-bot",
                "payload", Map.of(
                        "request_id", requestId,
                        "decision_by", actorId,
                        "approved", true
                )
        ));

        Message deadLetter = rabbitTemplate.receive(DLQ, 10_000);

        assertThat(deadLetter).isNotNull();
        verify(studentRequestService, org.mockito.Mockito.times(3))
                .decideLateCheckinFromBot(requestId, actorId, true);
        assertThat(new String(deadLetter.getBody())).contains(eventId);
    }

    private void publish(Map<String, Object> envelope) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
    }

    private static void ensureRabbitVhost(String vhost) {
        try {
            org.testcontainers.containers.Container.ExecResult result =
                    RABBITMQ.execInContainer("rabbitmqctl", "add_vhost", vhost);
            if (result.getExitCode() != 0 && !result.getStderr().contains("already exists")) {
                throw new IllegalStateException("Cannot create Rabbit vhost " + vhost
                        + ": " + result.getStderr());
            }
            org.testcontainers.containers.Container.ExecResult permissions =
                    RABBITMQ.execInContainer("rabbitmqctl", "set_permissions", "-p", vhost,
                            RABBITMQ.getAdminUsername(), ".*", ".*", ".*");
            if (permissions.getExitCode() != 0) {
                throw new IllegalStateException("Cannot grant Rabbit vhost permissions for " + vhost
                        + ": " + permissions.getStderr());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot initialize Rabbit vhost " + vhost, ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot initialize Rabbit vhost " + vhost, ex);
        }
    }
}
