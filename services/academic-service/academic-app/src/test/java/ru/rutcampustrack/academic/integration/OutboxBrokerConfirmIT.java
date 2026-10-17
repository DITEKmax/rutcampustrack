package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Connection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.RabbitConfig;
import ru.rutcampustrack.academic.event.RabbitOutboxEventSender;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.shared.outbox.OutboxDeliveryException;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

/** Real broker refusal must not turn the committed user operation's outbox into SENT. */
@AutoConfigureMockMvc
class OutboxBrokerConfirmIT extends AbstractAcademicEventIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private GroupRepository groups;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    @Value("${spring.rabbitmq.publisher-confirm-type:none}")
    private String confirmType;

    @Test
    void blockedBrokerKeepsIntentPendingAndRecoveryPublishesOriginalEvent() throws Exception {
        String queueName = "delivery.confirm." + UUID.randomUUID();
        CachingConnectionFactory control = connectionFactory();
        CachingConnectionFactory publisher = connectionFactory();
        String publisherName = "delivery-confirm-publisher-" + UUID.randomUUID();
        publisher.setConnectionNameStrategy(ignored -> publisherName);
        publisher.setPublisherConfirmType(
                CachingConnectionFactory.ConfirmType.valueOf(confirmType.toUpperCase(Locale.ROOT)));
        RabbitTemplate receiver = new RabbitTemplate(control);
        RabbitAdmin admin = new RabbitAdmin(control);
        Queue queue = new Queue(queueName, true, false, false);
        Group group = new Group();
        group.setName("ТРН-121");
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        group = groups.save(group);
        Connection connection = null;
        ExecutorService sends = Executors.newSingleThreadExecutor();
        try {
            admin.declareQueue(queue);
            admin.declareBinding(
                    BindingBuilder.bind(queue).to(new FanoutExchange("rut-uit.events")));
            RabbitTemplate transport =
                    new RabbitConfig()
                            .rabbitTemplate(publisher, new Jackson2JsonMessageConverter(mapper));
            RabbitOutboxEventSender sender = new RabbitOutboxEventSender(transport);
            OutboxPublisherJob job = new OutboxPublisherJob(outboxStorage, sender);
            TransactionTemplate tx = new TransactionTemplate(transactions);

            // Establish a real publishing connection before the resource alarm.
            sender.send(
                    "delivery.test.warmup",
                    mapper.writeValueAsString(
                            java.util.Map.of(
                                    "event_type",
                                    "delivery.test.warmup",
                                    "event_id",
                                    UUID.randomUUID().toString(),
                                    "event_version",
                                    1,
                                    "source",
                                    "academic-service",
                                    "occurred_at",
                                    java.time.Instant.now().toString(),
                                    "payload",
                                    java.util.Map.of())));
            assertThat(receiver.receive(queueName, 5000)).isNotNull();
            connection = publisher.createConnection().getDelegate();
            CountDownLatch blocked = new CountDownLatch(1);
            connection.addBlockedListener(
                    new BlockedListener() {
                        @Override
                        public void handleBlocked(String reason) {
                            blocked.countDown();
                        }

                        @Override
                        public void handleUnblocked() {}
                    });

            long adminId =
                    jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
            mvc.perform(
                            put("/academic/groups/{id}", group.getId())
                                    .header("X-User-Id", adminId)
                                    .with(signedAdmin())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"name\":\"Тно-121\",\"active\":true,\"trainingDurationYears\":4}"))
                    .andExpect(status().isOk());
            List<OutboxRecord> original = outboxStorage.findPending(100);
            assertThat(original).isNotEmpty();
            assertThat(original).anyMatch(record -> "group.renamed".equals(record.eventType()));

            setDiskLimit("1000GB");
            var attempt =
                    sends.submit(
                            () ->
                                    catchThrowable(
                                            () ->
                                                    tx.executeWithoutResult(
                                                            ignored -> job.publishBatch())));
            // Rabbit sends Connection.Blocked after a publication under the alarm.
            assertThat(blocked.await(15, TimeUnit.SECONDS))
                    .as("actual publisher Connection.Blocked")
                    .isTrue();
            // Leave enough time for the configured confirm timeout, but avoid
            // hanging the test on a channel-close RPC while the broker is blocked.
            Throwable failure = attempt.get(20, TimeUnit.SECONDS);

            // Kill the broker-side reader while it is still blocked. Merely
            // closing the client socket could leave TCP-buffered frames to be
            // processed when the resource alarm clears.
            closeBrokerConnection(publisherName);
            connection.abort(1000);
            connection = null;
            setDiskLimit("50MB");
            assertThat(receiver.receive(queueName, 1000))
                    .as("unaccepted event must not reach the queue")
                    .isNull();
            List<String> states =
                    original.stream()
                            .map(
                                    record ->
                                            jdbc.queryForObject(
                                                    "SELECT status FROM academic_outbox WHERE id=?",
                                                    String.class,
                                                    record.id()))
                            .toList();
            System.out.printf(
                    "broker-confirm counterexample originalRows=%d states=%s failure=%s%n",
                    original.size(),
                    states,
                    failure == null ? "none" : failure.getClass().getSimpleName());
            assertThat(states)
                    .as("unconfirmed original intents remain retryable")
                    .containsOnly("pending");
            assertThat(failure)
                    .as("broker refusal must propagate to the publisher transaction")
                    .isNotNull();

            tx.executeWithoutResult(ignored -> job.publishBatch());
            assertThat(outboxStorage.findPending(100)).isEmpty();
            List<String> envelopes = new ArrayList<>();
            for (int index = 0; index < original.size(); index++) {
                Message delivered = receiver.receive(queueName, 5000);
                assertThat(delivered).isNotNull();
                // The original envelope (including event_id) survives the retry.
                envelopes.add(
                        new String(delivered.getBody(), java.nio.charset.StandardCharsets.UTF_8));
            }
            assertThat(envelopes)
                    .containsExactlyInAnyOrderElementsOf(
                            original.stream().map(OutboxRecord::payload).toList());
            assertThat(receiver.receive(queueName, 250)).isNull();
        } finally {
            setDiskLimit("50MB");
            if (connection != null) connection.abort(1000);
            publisher.destroy();
            admin.deleteQueue(queueName);
            control.destroy();
            sends.shutdownNow();
            assertThat(sends.awaitTermination(5, TimeUnit.SECONDS))
                    .as("owned publisher worker cleanup")
                    .isTrue();
            groups.deleteById(group.getId());
        }
    }

    @Test
    void negativeBrokerConfirmKeepsIntentPendingUntilQueueRecovers() throws Exception {
        String queueName = "delivery.nack." + UUID.randomUUID();
        CachingConnectionFactory publisher = connectionFactory();
        publisher.setPublisherConfirmType(
                CachingConnectionFactory.ConfirmType.valueOf(confirmType.toUpperCase(Locale.ROOT)));
        RabbitAdmin admin = new RabbitAdmin(publisher);
        RabbitTemplate transport =
                new RabbitConfig()
                        .rabbitTemplate(publisher, new Jackson2JsonMessageConverter(mapper));
        RabbitOutboxEventSender sender = new RabbitOutboxEventSender(transport);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        // Observe the finite transport reason before the publisher deliberately removes causes.
        AtomicReference<OutboxDeliveryException.Reason> refusal = new AtomicReference<>();
        OutboxPublisherJob job =
                new OutboxPublisherJob(
                        outboxStorage,
                        (eventType, payload) -> {
                            try {
                                sender.send(eventType, payload);
                            } catch (OutboxDeliveryException failure) {
                                refusal.set(failure.reason());
                                throw failure;
                            }
                        });
        Group group = new Group();
        group.setName("ТРН-121");
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        group = groups.save(group);
        try {
            admin.declareQueue(
                    QueueBuilder.durable(queueName)
                            .maxLength(1)
                            .overflow(QueueBuilder.Overflow.rejectPublish)
                            .build());
            admin.declareBinding(
                    BindingBuilder.bind(new Queue(queueName))
                            .to(new FanoutExchange("rut-uit.events")));
            // This accepted warmup fills the queue; further publishes receive a nack.
            sender.send(
                    "delivery.test.warmup",
                    mapper.writeValueAsString(
                            java.util.Map.of(
                                    "event_type",
                                    "delivery.test.warmup",
                                    "event_id",
                                    UUID.randomUUID().toString(),
                                    "event_version",
                                    1,
                                    "source",
                                    "academic-service",
                                    "occurred_at",
                                    java.time.Instant.now().toString(),
                                    "payload",
                                    java.util.Map.of())));
            long adminId =
                    jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
            mvc.perform(
                            put("/academic/groups/{id}", group.getId())
                                    .header("X-User-Id", adminId)
                                    .with(signedAdmin())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"name\":\"Тно-121\",\"active\":true,\"trainingDurationYears\":4}"))
                    .andExpect(status().isOk());
            List<OutboxRecord> original = outboxStorage.findPending(100);
            assertThat(original).isNotEmpty();

            Throwable failure =
                    catchThrowable(() -> tx.executeWithoutResult(ignored -> job.publishBatch()));
            assertThat(failure)
                    .as("broker nack rejects the publisher transaction")
                    .isInstanceOf(IllegalStateException.class)
                    .hasNoCause();
            assertThat(refusal.get()).isEqualTo(OutboxDeliveryException.Reason.BROKER_NACK);
            assertThat(
                            original.stream()
                                    .map(
                                            record ->
                                                    jdbc.queryForObject(
                                                            "SELECT status FROM academic_outbox"
                                                                + " WHERE id=?",
                                                            String.class,
                                                            record.id()))
                                    .toList())
                    .containsOnly("pending");
            Message retained = transport.receive(queueName, 5000);
            assertThat(retained).isNotNull();
            assertThat(mapper.readTree(retained.getBody()).path("event_type").asText())
                    .isEqualTo("delivery.test.warmup");
            assertThat(transport.receive(queueName, 250)).isNull();

            admin.deleteQueue(queueName);
            admin.declareQueue(new Queue(queueName, true, false, false));
            admin.declareBinding(
                    BindingBuilder.bind(new Queue(queueName))
                            .to(new FanoutExchange("rut-uit.events")));
            tx.executeWithoutResult(ignored -> job.publishBatch());
            assertThat(outboxStorage.findPending(100)).isEmpty();
            List<String> envelopes = new ArrayList<>();
            for (int index = 0; index < original.size(); index++) {
                Message delivered = transport.receive(queueName, 5000);
                assertThat(delivered).isNotNull();
                envelopes.add(
                        new String(delivered.getBody(), java.nio.charset.StandardCharsets.UTF_8));
            }
            assertThat(envelopes)
                    .containsExactlyInAnyOrderElementsOf(
                            original.stream().map(OutboxRecord::payload).toList());
            assertThat(transport.receive(queueName, 250)).isNull();
        } finally {
            admin.deleteQueue(queueName);
            publisher.destroy();
            groups.deleteById(group.getId());
        }
    }

    private CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory =
                new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        factory.setUsername(RABBITMQ.getAdminUsername());
        factory.setPassword(RABBITMQ.getAdminPassword());
        return factory;
    }

    private void setDiskLimit(String limit) throws Exception {
        var result = RABBITMQ.execInContainer("rabbitmqctl", "set_disk_free_limit", limit);
        assertThat(result.getExitCode()).as("owned broker disk-limit command").isZero();
    }

    private void closeBrokerConnection(String name) throws Exception {
        var listing =
                RABBITMQ.execInContainer(
                        "rabbitmqctl", "--quiet", "list_connections", "pid", "client_properties");
        assertThat(listing.getExitCode()).as("owned broker connection inventory").isZero();
        String pid = null;
        // The JSON formatter emits Elixir #PID syntax, which close_connection
        // does not accept. The standard table uses Rabbit's node-qualified PID.
        for (String line : listing.getStdout().lines().toList()) {
            if (line.contains(name)) {
                assertThat(pid).as("publisher connection name must be unique").isNull();
                pid = line.strip().split("\\s+", 2)[0];
            }
        }
        assertThat(pid).as("exact owned publisher broker PID").isNotBlank();
        System.out.printf("exact synthetic publisher PID=%s%n", pid);
        var closed =
                RABBITMQ.execInContainer(
                        "rabbitmqctl",
                        "close_connection",
                        pid,
                        "synthetic outbox acceptance fault");
        assertThat(closed.getExitCode())
                .as(
                        "close exact publisher: stdout=%s stderr=%s",
                        closed.getStdout(), closed.getStderr())
                .isZero();
    }
}
