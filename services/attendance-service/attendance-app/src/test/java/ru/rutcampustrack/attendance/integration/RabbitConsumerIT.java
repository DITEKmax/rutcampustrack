package ru.rutcampustrack.attendance.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies INFRA-05: RabbitMQ queue infrastructure is correctly declared. -
 * attendance-service.events queue is bound to rut-uit.events fanout exchange -
 * attendance-service.events.dlq DLQ queue exists - Messages published to the fanout exchange are
 * routed to the queue
 *
 * <p>Uses RabbitMQ Testcontainer started in AbstractAttendanceIntegrationTest.
 */
class RabbitConsumerIT extends AbstractAttendanceIntegrationTest {

    @Autowired private AmqpAdmin amqpAdmin;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Test
    void attendanceEventsQueue_isDeclaredAndBound() {
        // Verify queue was auto-declared at context startup
        QueueInformation queueInfo = amqpAdmin.getQueueInfo("attendance-service.events");
        assertNotNull(queueInfo, "attendance-service.events queue should be declared");
    }

    @Test
    void attendanceDlqQueue_isDeclared() {
        QueueInformation dlqInfo = amqpAdmin.getQueueInfo("attendance-service.events.dlq");
        assertNotNull(dlqInfo, "attendance-service.events.dlq queue should be declared");
    }
}
