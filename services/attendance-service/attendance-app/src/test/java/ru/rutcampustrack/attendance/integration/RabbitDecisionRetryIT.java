package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.shared.events.EventTraceContext;
import ru.rutcampustrack.shared.outbox.ConfirmedRabbitOutboxSender;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real RabbitMQ proof for the decision-event retry and DLQ boundary.
 *
 * <p>Malformed decision envelopes are rejected immediately. Transient dependency failures are
 * retried three times, then rejected to the queue's configured dead-letter exchange. Both cases use
 * the application listener container, not a direct EventConsumer invocation.
 */
@AutoConfigureObservability
@Import(RabbitDecisionRetryIT.TelemetryCaptureConfiguration.class)
class RabbitDecisionRetryIT extends AbstractAttendanceIntegrationTest {

    private static final String DLQ = "attendance-service.events.dlq";
    private static final String RABBIT_VHOST = "rct_requests_isolation_rabbit_decision_retry";

    static {
        ensureRabbitVhost(RABBIT_VHOST);
    }

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private AmqpAdmin amqpAdmin;

    @Autowired private OpenTelemetry telemetry;

    @Autowired private SdkTracerProvider tracerProvider;

    @Autowired private CapturedSpans capturedSpans;

    @org.springframework.test.context.DynamicPropertySource
    static void overrideRabbitVhost(
            org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.virtual-host", () -> RABBIT_VHOST);
        // Keep actual application sampling/baggage defaults. Standalone CI captures the real
        // SDK exporter locally; the existing collector cohort may provide its owned endpoint.
        String endpoint = collectorEndpoint();
        if (endpoint == null) {
            registry.add("management.otlp.tracing.export.enabled", () -> false);
        } else {
            registry.add("management.otlp.tracing.endpoint", () -> endpoint);
        }
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
                .doReturn(null)
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
        doReturn(null).when(studentRequestService).decideLateCheckinFromBot(requestId, 42L, true);
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
        String businessRequest = "507f1f77bcf86cd799439016";
        String ordinaryRequest = "507f1f77bcf86cd799439017";
        String eventId = UUID.randomUUID().toString();
        String ordinaryEventId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String ordinaryRequestId = UUID.randomUUID().toString();
        List<WorkerAttempt> attempts = new CopyOnWriteArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            attempts.add(workerAttempt(businessRequest));
                            if (failures.incrementAndGet() <= 3) {
                                throw new AcademicServiceUnavailableException(
                                        "synthetic transient outage",
                                        io.grpc.Status.Code.UNAVAILABLE);
                            }
                            return null;
                        })
                .when(studentRequestService)
                .decideLateCheckinFromBot(businessRequest, 42L, true);
        doAnswer(
                        invocation -> {
                            attempts.add(workerAttempt(ordinaryRequest));
                            return null;
                        })
                .when(studentRequestService)
                .decideLateCheckinFromBot(ordinaryRequest, 42L, true);

        var previousSpan = Span.current().getSpanContext();
        String previousRequest = Baggage.current().getEntryValue("requestId");
        TraceOrigin origin = acceptedOrigin(eventId, businessRequest, requestId, true);
        TraceOrigin ordinary =
                acceptedOrigin(ordinaryEventId, ordinaryRequest, ordinaryRequestId, false);
        assertThat(ordinary.traceId()).isNotEqualTo(origin.traceId());
        // The origin request has ended before the outbox publisher executes on a background
        // context. The sender must use durable metadata, rather than the test thread's span.
        assertThat(Span.current().getSpanContext()).isEqualTo(previousSpan);
        assertThat(Baggage.current().getEntryValue("requestId")).isEqualTo(previousRequest);
        var sender = new ConfirmedRabbitOutboxSender(rabbitTemplate);
        List<Message> published = new CopyOnWriteArrayList<>();
        MessagePostProcessor wireCapture =
                message -> {
                    // RabbitTemplate injects observation headers after this callback into this
                    // same Message. Inspect the actual sent carrier after confirms/worker receipt.
                    published.add(message);
                    return message;
                };
        rabbitTemplate.addBeforePublishPostProcessors(wireCapture);
        try {
            String payload = new ObjectMapper().writeValueAsString(origin.envelope());
            long started = System.nanoTime();
            sender.send("late_checkin.decision", payload);
            Awaitility.await()
                    .atMost(Duration.ofSeconds(45))
                    .untilAsserted(
                            () ->
                                    verify(studentRequestService, org.mockito.Mockito.times(4))
                                            .decideLateCheckinFromBot(businessRequest, 42L, true));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    .isGreaterThanOrEqualTo(30_000);

            // Replay the same durable command, then a distinct command on the same single
            // listener/prefetch=1. This checks the real Mongo dedup boundary and worker cleanup.
            sender.send("late_checkin.decision", payload);
            sender.send(
                    "late_checkin.decision",
                    new ObjectMapper().writeValueAsString(ordinary.envelope()));
            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> {
                                verify(studentRequestService)
                                        .decideLateCheckinFromBot(ordinaryRequest, 42L, true);
                                assertThat(
                                                tracerProvider
                                                        .forceFlush()
                                                        .join(5, TimeUnit.SECONDS)
                                                        .isSuccess())
                                        .isTrue();
                                assertThat(attempts).hasSize(5);
                                assertExportedWorker(attempts.get(4));
                            });
            verify(studentRequestService, org.mockito.Mockito.times(4))
                    .decideLateCheckinFromBot(businessRequest, 42L, true);

            List<Message> originalCopies =
                    published.stream()
                            .filter(m -> eventId.equals(m.getMessageProperties().getMessageId()))
                            .toList();
            assertThat(originalCopies).hasSize(3); // original, delayed redrive, duplicate replay
            for (Message copy : originalCopies) {
                assertThat(copy.getBody())
                        .isEqualTo(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertThat(copy.getMessageProperties().getHeaders())
                        .containsEntry("requestId", requestId)
                        .containsEntry("request_id", requestId);
            }
            assertThat(originalCopies.get(1).getMessageProperties().getTimestamp())
                    .isEqualTo(originalCopies.get(0).getMessageProperties().getTimestamp())
                    .isNotNull();
            assertThat(originalCopies.get(1).getMessageProperties().getHeaders())
                    .containsEntry("rct_redrive_count", 1);
            SpanData initialProducer = producer(originalCopies.get(0), origin, origin.spanId());
            assertThat(exportedSpan(origin.spanId()).getKind()).isEqualTo(SpanKind.INTERNAL);
            SpanData firstWorker = assertExportedWorker(attempts.get(0));
            assertThat(firstWorker.getParentSpanId()).isEqualTo(initialProducer.getSpanId());
            for (int i = 0; i < 3; i++) {
                assertWorkerCorrelation(
                        attempts.get(i), origin, eventId, requestId, origin.traceId());
                assertThat(attempts.get(i).spanId()).isEqualTo(firstWorker.getSpanId());
            }
            SpanData retryProducer =
                    producer(originalCopies.get(1), origin, firstWorker.getSpanId());
            SpanData recoveredWorker = assertExportedWorker(attempts.get(3));
            assertThat(recoveredWorker.getParentSpanId()).isEqualTo(retryProducer.getSpanId());
            assertWorkerCorrelation(attempts.get(3), origin, eventId, requestId, origin.traceId());

            Message ordinaryMessage =
                    published.stream()
                            .filter(
                                    m ->
                                            ordinaryEventId.equals(
                                                    m.getMessageProperties().getMessageId()))
                            .findFirst()
                            .orElseThrow();
            SpanData ordinaryProducer = producer(ordinaryMessage, ordinary, ordinary.spanId());
            SpanData ordinaryWorker = assertExportedWorker(attempts.get(4));
            assertThat(ordinaryWorker.getParentSpanId()).isEqualTo(ordinaryProducer.getSpanId());
            assertWorkerCorrelation(
                    attempts.get(4), ordinary, ordinaryEventId, ordinaryRequestId, null);
            assertThat(Span.current().getSpanContext()).isEqualTo(previousSpan);
            assertThat(Baggage.current().getEntryValue("requestId")).isEqualTo(previousRequest);
            assertThat(rabbitTemplate.receive(DLQ, 500)).isNull();
            assertThat(amqpAdmin.getQueueInfo("attendance-service.events.retry").getMessageCount())
                    .isZero();
            writeCollectorReceipt(origin, ordinary, attempts.get(3), attempts.get(4));
        } finally {
            assertThat(rabbitTemplate.removeBeforePublishPostProcessor(wireCapture)).isTrue();
        }
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

    private TraceOrigin acceptedOrigin(
            String eventId, String businessRequest, String requestId, boolean legacyTrace) {
        Span span =
                telemetry
                        .getTracer("rct.synthetic.accepted-intent")
                        .spanBuilder("synthetic.accepted-intent")
                        .startSpan();
        assertThat(span.isRecording()).as("actual application SDK sampling").isTrue();
        Map<String, Object> envelope = new HashMap<>(decision(eventId, businessRequest, 42L));
        try (var ignored =
                Baggage.builder()
                        .put("requestId", requestId)
                        .build()
                        .storeInContext(Context.current().with(span))
                        .makeCurrent()) {
            EventTraceContext.capture(envelope);
            if (legacyTrace) {
                envelope.put("trace_id", span.getSpanContext().getTraceId());
            }
        } finally {
            span.end();
        }
        return new TraceOrigin(
                span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId(), envelope);
    }

    private static WorkerAttempt workerAttempt(String businessRequest) {
        var active = Span.current().getSpanContext();
        return new WorkerAttempt(
                businessRequest,
                active.getTraceId(),
                active.getSpanId(),
                Baggage.current().getEntryValue("requestId"),
                MDC.get("requestId"),
                MDC.get("eventId"),
                MDC.get("traceId"),
                MDC.get("spanId"),
                MDC.get("originTraceId"));
    }

    private SpanData assertExportedWorker(WorkerAttempt attempt) {
        SpanData worker = exportedSpan(attempt.spanId());
        assertThat(worker.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(worker.getResource().getAttribute(AttributeKey.stringKey("service.name")))
                .isEqualTo("attendance-service");
        assertThat(worker.getTraceId()).isEqualTo(attempt.traceId());
        assertThat(worker.getAttributes().get(AttributeKey.stringKey("requestId")))
                .isEqualTo(attempt.requestId());
        return worker;
    }

    private SpanData producer(Message carrier, TraceOrigin origin, String parentSpanId) {
        Object header = carrier.getMessageProperties().getHeaders().get("traceparent");
        assertThat(header).isInstanceOf(String.class);
        String traceparent = (String) header;
        assertThat(traceparent).matches("00-" + origin.traceId() + "-[0-9a-f]{16}-01");
        SpanData producer = exportedSpan(traceparent.split("-")[2]);
        assertThat(producer.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(producer.getTraceId()).isEqualTo(origin.traceId());
        assertThat(producer.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(producer.getAttributes().get(AttributeKey.stringKey("requestId")))
                .isEqualTo(origin.envelope().get("request_id"));
        return producer;
    }

    private SpanData exportedSpan(String spanId) {
        return capturedSpans.spans.stream()
                .filter(span -> span.getSpanId().equals(spanId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SDK did not export actual span " + spanId));
    }

    private static void assertWorkerCorrelation(
            WorkerAttempt attempt,
            TraceOrigin origin,
            String eventId,
            String requestId,
            String legacyTrace) {
        assertThat(attempt.traceId()).isEqualTo(origin.traceId());
        assertThat(attempt.requestId()).isEqualTo(requestId);
        assertThat(attempt.mdcRequestId()).isEqualTo(requestId);
        assertThat(attempt.eventId()).isEqualTo(eventId);
        assertThat(attempt.mdcTraceId()).isEqualTo(attempt.traceId());
        assertThat(attempt.mdcSpanId()).isEqualTo(attempt.spanId());
        assertThat(attempt.originTraceId()).isEqualTo(legacyTrace);
    }

    private static String collectorEndpoint() {
        String endpoint = System.getenv("RCT_TEST_OTLP_ENDPOINT");
        String receipt = System.getenv("RCT_TEST_TELEMETRY_RECEIPT");
        if (endpoint == null && receipt == null) return null;
        assertThat(endpoint != null && receipt != null)
                .as("owned collector endpoint and distinct private receipt are provided together")
                .isTrue();
        var target = java.net.URI.create(endpoint);
        assertThat(
                        "http".equals(target.getScheme())
                                && "127.0.0.1".equals(target.getHost())
                                && target.getPort() > 0
                                && target.getPort() <= 65535
                                && "/v1/traces".equals(target.getPath())
                                && target.getUserInfo() == null
                                && target.getRawQuery() == null
                                && target.getRawFragment() == null)
                .as("owned loopback OTLP endpoint")
                .isTrue();
        return endpoint;
    }

    private void writeCollectorReceipt(
            TraceOrigin recovered,
            TraceOrigin ordinary,
            WorkerAttempt recoveredWorker,
            WorkerAttempt ordinaryWorker)
            throws java.io.IOException {
        if (collectorEndpoint() == null) return;
        String source = System.getenv("GITHUB_SHA");
        assertThat(source).as("exact executable CI revision").matches("[0-9a-f]{40}");
        var output = java.nio.file.Path.of(System.getenv("RCT_TEST_TELEMETRY_RECEIPT"));
        assertThat(output.isAbsolute()).isTrue();
        assertThat(java.nio.file.Files.exists(output))
                .as("never overwrite a prior receipt")
                .isFalse();
        List<String> traces = List.of(recovered.traceId(), ordinary.traceId());
        var graph =
                capturedSpans.spans.stream()
                        .filter(span -> traces.contains(span.getTraceId()))
                        .map(
                                span ->
                                        Map.of(
                                                "service",
                                                span.getResource()
                                                        .getAttribute(
                                                                AttributeKey.stringKey(
                                                                        "service.name")),
                                                "kind",
                                                span.getKind().name(),
                                                "traceId",
                                                span.getTraceId(),
                                                "spanId",
                                                span.getSpanId(),
                                                "parentSpanId",
                                                span.getParentSpanId()))
                        .toList();
        var receipt =
                Map.of(
                        "schema",
                        1,
                        "kind",
                        "actual_application_background",
                        "source_sha",
                        source,
                        "cases",
                        List.of(
                                Map.of(
                                        "outcome",
                                        "recovered",
                                        "requestId",
                                        recoveredWorker.requestId(),
                                        "traceId",
                                        recoveredWorker.traceId(),
                                        "workerSpanId",
                                        recoveredWorker.spanId()),
                                Map.of(
                                        "outcome",
                                        "ordinary",
                                        "requestId",
                                        ordinaryWorker.requestId(),
                                        "traceId",
                                        ordinaryWorker.traceId(),
                                        "workerSpanId",
                                        ordinaryWorker.spanId())),
                        "spans",
                        graph,
                        "qualification",
                        "Real application RabbitTemplate/listener and Mongo dedup; synthetic origin"
                                + " and mocked StudentRequestService boundary, not business PASS");
        java.nio.file.Files.createDirectories(output.getParent());
        java.nio.file.Files.createFile(
                output,
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        java.nio.file.Files.writeString(
                output,
                new ObjectMapper().writeValueAsString(receipt) + "\n",
                java.nio.file.StandardOpenOption.WRITE);
    }

    private record TraceOrigin(String traceId, String spanId, Map<String, Object> envelope) {}

    private record WorkerAttempt(
            String businessRequest,
            String traceId,
            String spanId,
            String requestId,
            String mdcRequestId,
            String eventId,
            String mdcTraceId,
            String mdcSpanId,
            String originTraceId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TelemetryCaptureConfiguration {
        @Bean
        CapturedSpans capturedRabbitSpans() {
            return new CapturedSpans();
        }
    }

    static final class CapturedSpans implements SpanExporter {
        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
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
