package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Real RabbitMQ proof for the decision-event retry and DLQ boundary.
 *
 * <p>Malformed decision envelopes are rejected immediately. Transient dependency failures are
 * retried three times, then rejected to the queue's configured dead-letter exchange. Both cases use
 * the application listener container, not a direct EventConsumer invocation.
 */
class RabbitDecisionRetryIT extends AbstractAttendanceIntegrationTest {

    private static final String DLQ = "attendance-service.events.dlq";
    private static final String RABBIT_VHOST = "rct_requests_isolation_rabbit_decision_retry";

    static {
        ensureRabbitVhost(RABBIT_VHOST);
    }

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private AmqpAdmin amqpAdmin;

    @org.springframework.test.context.DynamicPropertySource
    static void overrideRabbitVhost(
            org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.virtual-host", () -> RABBIT_VHOST);
    }

    @MockitoBean private StudentRequestService studentRequestService;

    @BeforeEach
    void cleanQueues() {
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getContainerId()).isNotBlank();
        System.out.printf(
                "requests-isolation runtime rabbitContainer=%s rabbitPort=%s rabbitVhost=%s"
                        + " labels=%s%n",
                RABBITMQ.getContainerId(),
                RABBITMQ.getMappedPort(5672),
                RABBIT_VHOST,
                RABBITMQ.getLabels());
        amqpAdmin.purgeQueue("attendance-service.events", false);
        amqpAdmin.purgeQueue("attendance-service.events.dlq", false);
        amqpAdmin.purgeQueue("attendance-service.events.retry", false);
        reset(studentRequestService);
    }

    @Test
    void occupiedPublisherChannelHasBoundedCheckoutAndOriginalPendingIntentRecovers()
            throws Exception {
        var connection =
                new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(
                        RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connection.setUsername(RABBITMQ.getAdminUsername());
        connection.setPassword(RABBITMQ.getAdminPassword());
        connection.setVirtualHost(RABBIT_VHOST);
        connection.setChannelCacheSize(1);
        connection.setPublisherConfirmType(
                org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType
                        .SIMPLE);
        var sender =
                new ru.rutcampustrack.shared.outbox.ConfirmedRabbitOutboxSender(
                        new RabbitTemplate(connection));
        var storage = org.mockito.Mockito.mock(ru.rutcampustrack.shared.outbox.OutboxStorage.class);
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"event_id\":\"" + eventId + "\",\"event_type\":\"delivery.proof\"}";
        org.mockito.Mockito.when(storage.findPending(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(
                        java.util.List.of(
                                new ru.rutcampustrack.shared.outbox.OutboxRecord(
                                        1L, "delivery.proof", payload, java.time.Instant.now())));
        var job = new ru.rutcampustrack.shared.outbox.OutboxPublisherJob(storage, sender);
        org.springframework.amqp.rabbit.connection.Connection owned = connection.createConnection();
        com.rabbitmq.client.Channel held = owned.createChannel(false);
        try {
            long started = System.nanoTime();
            assertThatThrownBy(job::publishBatch).isInstanceOf(IllegalStateException.class);
            long elapsed =
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(elapsed).isBetween(2900L, 7000L);
            verify(storage, org.mockito.Mockito.never())
                    .markSent(org.mockito.ArgumentMatchers.any());
            held.close();
            held = null;
            assertThat(job.publishBatch()).isEqualTo(1);
            verify(storage).markSent(1L);
        } finally {
            if (held != null) held.close();
            owned.close();
            connection.destroy();
        }
    }

    @Test
    void actualBrokerAckReturnAndNackDoNotConfuseAcceptanceWithRouting() throws Exception {
        String vhost = "rct_report_delivery_" + UUID.randomUUID();
        ensureRabbitVhost(vhost);
        var connection =
                new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(
                        RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connection.setUsername(RABBITMQ.getAdminUsername());
        connection.setPassword(RABBITMQ.getAdminPassword());
        connection.setVirtualHost(vhost);
        connection.setPublisherConfirmType(
                org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType
                        .SIMPLE);
        var admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(connection);
        var transport = new RabbitTemplate(connection);
        var sender = new ru.rutcampustrack.shared.outbox.ConfirmedRabbitOutboxSender(transport);
        var exchange =
                new org.springframework.amqp.core.FanoutExchange("rut-uit.events", true, false);
        var queue =
                org.springframework.amqp.core.QueueBuilder.durable("report.delivery.proof")
                        .maxLength(1)
                        .overflow(org.springframework.amqp.core.QueueBuilder.Overflow.rejectPublish)
                        .build();
        var binding = org.springframework.amqp.core.BindingBuilder.bind(queue).to(exchange);
        String eventId = UUID.randomUUID().toString();
        String payload =
                "{\"event_id\":\""
                        + eventId
                        + "\",\"event_type\":\"delivery.proof\",\"payload\":{}}";
        try {
            admin.declareExchange(exchange);
            admin.declareQueue(queue);
            admin.declareBinding(binding);
            sender.send("delivery.proof", payload);
            Message accepted = transport.receive(queue.getName(), 5000);
            assertThat(accepted).isNotNull();
            assertThat(accepted.getMessageProperties().getMessageId()).isEqualTo(eventId);
            assertThat(new String(accepted.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(payload);

            admin.removeBinding(binding);
            assertThatThrownBy(() -> sender.send("delivery.proof", payload))
                    .isInstanceOf(org.springframework.amqp.AmqpException.class)
                    .hasMessageContaining("BROKER_UNROUTABLE");
            admin.declareBinding(binding);
            sender.send("delivery.proof", payload);
            assertThatThrownBy(() -> sender.send("delivery.proof", payload))
                    .isInstanceOf(org.springframework.amqp.AmqpException.class);
            assertThat(admin.getQueueInfo(queue.getName()).getMessageCount()).isEqualTo(1);
            var facts =
                    RABBITMQ.execInContainer(
                            "rabbitmqctl",
                            "-p",
                            vhost,
                            "list_queues",
                            "name",
                            "type",
                            "--formatter=json");
            assertThat(facts.getExitCode()).isZero();
            assertThat(facts.getStdout()).contains("classic", queue.getName());
            var version = RABBITMQ.execInContainer("rabbitmq-diagnostics", "server_version");
            assertThat(version.getExitCode()).isZero();
            assertThat(version.getStdout()).contains("3.13.");
            System.out.printf(
                    "report Rabbit proof ack=accepted return=rejected nack=rejected type=classic"
                            + " version=%s%n",
                    version.getStdout().trim());
        } finally {
            connection.destroy();
            var cleanup = RABBITMQ.execInContainer("rabbitmqctl", "delete_vhost", vhost);
            assertThat(cleanup.getExitCode()).isZero();
        }
    }

    @Test
    void missingEventTypeIsRetainedInDlqInsteadOfAcknowledgedAndLost() {
        String eventId = UUID.randomUUID().toString();
        publish(Map.of("event_id", eventId, "payload", Map.of("synthetic", true)));

        Message deadLetter = rabbitTemplate.receive(DLQ, 10_000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(new String(deadLetter.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains(eventId);
        org.mockito.Mockito.verifyNoInteractions(studentRequestService);
    }

    @Test
    void transientFailureRecoversTheOriginalDeliveryAndDuplicateHasNoSecondEffect() {
        String requestId = "507f1f77bcf86cd799439012";
        long actorId = 42L;
        doThrow(
                        new AcademicServiceUnavailableException(
                                "synthetic transient dependency outage",
                                io.grpc.Status.Code.UNAVAILABLE))
                .doNothing()
                .when(studentRequestService)
                .decideLateCheckinFromBot(requestId, actorId, true);
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = decision(eventId, requestId, actorId);

        publish(envelope);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                verify(studentRequestService, org.mockito.Mockito.times(2))
                                        .decideLateCheckinFromBot(requestId, actorId, true));
        assertThat(rabbitTemplate.receive(DLQ, 500)).isNull();
        publish(envelope);
        String barrierRequest = "507f1f77bcf86cd799439015";
        publish(decision(UUID.randomUUID().toString(), barrierRequest, actorId));
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                verify(studentRequestService)
                                        .decideLateCheckinFromBot(barrierRequest, actorId, true));
        // Prefetch=1 and one consumer: the subsequent ordinary command is a delivery barrier.
        verify(studentRequestService, org.mockito.Mockito.times(2))
                .decideLateCheckinFromBot(requestId, actorId, true);
        assertThat(rabbitTemplate.receive(DLQ, 500)).isNull();
    }

    @Test
    void poisonIsRetainedWithRejectionReasonAndNextOrdinaryDeliveryWorks() {
        String poisonId = UUID.randomUUID().toString();
        publish(
                Map.of(
                        "event_type",
                        "late_checkin.decision",
                        "event_id",
                        poisonId,
                        "event_version",
                        1,
                        "source",
                        "untrusted-source",
                        "payload",
                        Map.of("request_id", "507f1f77bcf86cd799439013")));
        Message deadLetter = rabbitTemplate.receive(DLQ, 10_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains(poisonId);
        java.util.List<?> deaths = deadLetter.getMessageProperties().getHeader("x-death");
        assertThat(deaths).hasSize(1);
        assertThat(((Map<?, ?>) deaths.get(0)).get("reason").toString()).isEqualTo("rejected");
        // Return the inspected retained message to this isolated terminal queue. There is no
        // automatic DLQ consumer or DLQ->main binding, so it cannot cycle into the worker.
        rabbitTemplate.send("", DLQ, deadLetter);

        String requestId = "507f1f77bcf86cd799439014";
        doNothing().when(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);
        publish(decision(UUID.randomUUID().toString(), requestId, 42L));

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                verify(studentRequestService)
                                        .decideLateCheckinFromBot(requestId, 42L, true));
        assertThat(amqpAdmin.getQueueInfo(DLQ).getMessageCount()).isEqualTo(1);
    }

    @Test
    void transientOutageAfterThreeAttemptsAutomaticallyRedrivesOriginalAfterThirtySeconds()
            throws Exception {
        var topology =
                RABBITMQ.execInContainer(
                        "rabbitmqctl",
                        "-p",
                        RABBIT_VHOST,
                        "list_queues",
                        "name",
                        "type",
                        "arguments",
                        "policy",
                        "--formatter=json");
        assertThat(topology.getExitCode()).isZero();
        assertThat(topology.getStdout())
                .contains(
                        "attendance-service.events.retry",
                        "classic",
                        "x-message-ttl",
                        "30000",
                        "x-dead-letter-routing-key",
                        "attendance-service.events");
        String requestId = "507f1f77bcf86cd799439016";
        doThrow(
                        new AcademicServiceUnavailableException(
                                "synthetic transient outage", io.grpc.Status.Code.UNAVAILABLE))
                .doThrow(
                        new AcademicServiceUnavailableException(
                                "synthetic transient outage", io.grpc.Status.Code.UNAVAILABLE))
                .doThrow(
                        new AcademicServiceUnavailableException(
                                "synthetic transient outage", io.grpc.Status.Code.UNAVAILABLE))
                .doNothing()
                .when(studentRequestService)
                .decideLateCheckinFromBot(requestId, 42L, true);
        long started = System.nanoTime();
        publish(decision(UUID.randomUUID().toString(), requestId, 42L));

        Awaitility.await()
                .atMost(Duration.ofSeconds(45))
                .untilAsserted(
                        () ->
                                verify(studentRequestService, org.mockito.Mockito.times(4))
                                        .decideLateCheckinFromBot(requestId, 42L, true));

        assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .isGreaterThanOrEqualTo(30_000);
        assertThat(rabbitTemplate.receive(DLQ, 500)).isNull();
        assertThat(amqpAdmin.getQueueInfo("attendance-service.events.retry").getMessageCount())
                .isZero();
    }

    @Test
    void malformedDecisionEnvelope_isBoundedAndDeadLetters() {
        String eventId = UUID.randomUUID().toString();
        publish(
                Map.of(
                        "event_type",
                        "late_checkin.decision",
                        "event_id",
                        eventId,
                        "event_version",
                        1,
                        "source",
                        "untrusted-source",
                        "payload",
                        Map.of("request_id", "507f1f77bcf86cd799439011")));

        Message deadLetter = rabbitTemplate.receive(DLQ, 10_000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(new String(deadLetter.getBody()))
                .contains(eventId)
                .contains("late_checkin.decision");
    }

    @Test
    void persistentDependencyFailureExhaustsThreeAttemptsAndOneDelayedCycleThenDeadLetters() {
        String requestId = "507f1f77bcf86cd799439011";
        long actorId = 42L;
        doThrow(
                        new AcademicServiceUnavailableException(
                                "simulated dependency outage", io.grpc.Status.Code.UNAVAILABLE))
                .when(studentRequestService)
                .decideLateCheckinFromBot(requestId, actorId, true);

        String eventId = UUID.randomUUID().toString();
        publish(
                Map.of(
                        "event_type",
                        "late_checkin.decision",
                        "event_id",
                        eventId,
                        "event_version",
                        1,
                        "source",
                        "notification-bot",
                        "payload",
                        Map.of(
                                "request_id", requestId,
                                "decision_by", actorId,
                                "approved", true)));

        Message deadLetter = rabbitTemplate.receive(DLQ, 45_000);

        assertThat(deadLetter).isNotNull();
        verify(studentRequestService, org.mockito.Mockito.times(6))
                .decideLateCheckinFromBot(requestId, actorId, true);
        assertThat(new String(deadLetter.getBody())).contains(eventId);
        assertThat(deadLetter.getMessageProperties().getHeaders())
                .containsEntry("rct_redrive_count", 1);
        assertThat(deadLetter.getMessageProperties().getMessageId()).isEqualTo(eventId);
    }

    private void publish(Map<String, Object> envelope) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
    }

    private static Map<String, Object> decision(String eventId, String requestId, long actorId) {
        return Map.of(
                "event_type",
                "late_checkin.decision",
                "event_id",
                eventId,
                "event_version",
                1,
                "source",
                "notification-bot",
                "payload",
                Map.of("request_id", requestId, "decision_by", actorId, "approved", true));
    }

    private static void ensureRabbitVhost(String vhost) {
        try {
            org.testcontainers.containers.Container.ExecResult result =
                    RABBITMQ.execInContainer("rabbitmqctl", "add_vhost", vhost);
            if (result.getExitCode() != 0 && !result.getStderr().contains("already exists")) {
                throw new IllegalStateException(
                        "Cannot create Rabbit vhost " + vhost + ": " + result.getStderr());
            }
            org.testcontainers.containers.Container.ExecResult permissions =
                    RABBITMQ.execInContainer(
                            "rabbitmqctl",
                            "set_permissions",
                            "-p",
                            vhost,
                            RABBITMQ.getAdminUsername(),
                            ".*",
                            ".*",
                            ".*");
            if (permissions.getExitCode() != 0) {
                throw new IllegalStateException(
                        "Cannot grant Rabbit vhost permissions for "
                                + vhost
                                + ": "
                                + permissions.getStderr());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot initialize Rabbit vhost " + vhost, ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot initialize Rabbit vhost " + vhost, ex);
        }
    }
}
