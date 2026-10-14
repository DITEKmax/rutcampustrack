package ru.rutcampustrack.notification.event;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.ChannelProxy;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.history.NotificationHistoryConsumer;
import ru.rutcampustrack.notification.history.NotificationHistoryDocument;
import ru.rutcampustrack.notification.history.NotificationHistoryRepository;
import ru.rutcampustrack.notification.history.NotificationInboxService;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateDocument;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;
import ru.rutcampustrack.notification.security.RequestContext;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

/**
 * M13 G8 — IT для consumer-side dedup в notification-app.
 *
 * <p>Публикует {@code excuse.requested} с одинаковым {@code event_id} дважды; обе очереди ({@code
 * notification-web.events} и {@code notification-web.history}) получают сообщение, но guard'ы
 * пропускают только первое — history-document создаётся один раз, claim-records по двум consumerId
 * — по одному каждой.
 */
@SpringBootTest(
        properties = {
            "notification.bot-preferences.token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
            "vapid.private-key=test-priv",
            "notification.history.ttl-days=30"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventIdempotentIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(EventIdempotentIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String COLLECTION = "event_consumer_processed";

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private NotificationHistoryRepository repository;

    @Autowired private NotificationInboxService inbox;

    @MockitoSpyBean private MongoTemplate mongoTemplate;

    @Autowired private RabbitAdmin rabbitAdmin;

    @Autowired private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired private ObjectMapper json;

    @MockitoBean
    private ru.rutcampustrack.notification.history.NotificationAudienceClient audienceClient;

    @MockitoBean private NotificationPreferencesService notificationPreferences;
    @MockitoBean private WebPushDeliveryService webPushDeliveryService;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;
    @Autowired private ReminderAttendanceStateService reminderAttendanceStateService;
    private StringRedisTemplate reminderRedis;
    private HashOperations<String, Object, Object> reminderPreferences;

    @MockitoBean private nl.martijndwars.webpush.PushService pushService;

    @BeforeEach
    void clean() {
        reminderRedis = mock(StringRedisTemplate.class);
        reminderPreferences = mock(HashOperations.class);
        when(reminderRedis.opsForHash()).thenReturn(reminderPreferences);
        when(reminderPreferences.entries(anyString())).thenReturn(Map.of());
        repository.deleteAll();
        mongoTemplate.remove(new Query(), COLLECTION);
        mongoTemplate.remove(new Query(), ReminderAttendanceStateDocument.class);
        rabbitAdmin.purgeQueue("notification-web.events.dlq", false);
        when(webPushDeliveryService.shouldPush(anyString())).thenReturn(true);
        when(notificationPreferences.isEnabledForUser(anyLong(), anyString(), any()))
                .thenReturn(true);
        when(audienceClient.resolve(any()))
                .thenAnswer(
                        call -> {
                            Map<String, Object> event = call.getArgument(0);
                            if ("excuse.requested".equals(event.get("event_type")))
                                return Map.of(42L, Set.of("STUDENT"), 43L, Set.of("HEADMAN"));
                            return Map.of(42L, Set.of("STUDENT"));
                        });
        for (String collection :
                List.of(
                        ru.rutcampustrack.notification.history.NotificationAudienceService
                                .COLLECTION,
                        ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION,
                        "user_notification_preferences"))
            mongoTemplate.remove(new Query(), collection);
    }

    private Map<String, Object> envelope(UUID eventId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "excuse.requested");
        envelope.put("event_id", eventId.toString());
        envelope.put("trace_id", "trace-" + eventId);
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put(
                "payload",
                Map.of(
                        "user_id",
                        42,
                        "group_id",
                        7,
                        "ticket_id",
                        "request-it-1",
                        "excuse_type",
                        "illness"));
        return envelope;
    }

    private void publish(Map<String, Object> env) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", env);
    }

    private long claimsForConsumer(String consumerId, UUID eventId) {
        return mongoTemplate.count(
                new Query(
                        Criteria.where("consumer_id")
                                .is(consumerId)
                                .and("event_id")
                                .is(eventId.toString())),
                COLLECTION);
    }

    @Test
    void duplicateDeliveryHistoryAndDurableIntentCommitOnceAcrossBothConsumerQueues() {
        UUID id = UUID.randomUUID();
        var event = envelope(id);
        publish(event);
        publish(event);
        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(repository.findAll()).hasSize(2);
                            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id))
                                    .isEqualTo(1);
                            assertThat(
                                            claimsForConsumer(
                                                    NotificationHistoryConsumer.CONSUMER_ID, id))
                                    .isEqualTo(1);
                            var intent =
                                    mongoTemplate.findById(
                                            id.toString(),
                                            org.bson.Document.class,
                                            ru.rutcampustrack.notification.push
                                                    .NotificationPushIntentStore.COLLECTION);
                            assertThat(intent).isNotNull();
                            assertThat(
                                            ru.rutcampustrack.notification.history
                                                    .NotificationAudienceService.decode(intent))
                                    .containsOnlyKeys(43L);
                            verify(webPushDeliveryService, never())
                                    .sendToGroup(anyLong(), anyString(), any(), any());
                        });
        publish(event);
        await().during(ofSeconds(1))
                .atMost(ofSeconds(3))
                .untilAsserted(
                        () -> {
                            assertThat(repository.findAll()).hasSize(2);
                            assertThat(
                                            mongoTemplate
                                                    .getCollection(
                                                            ru.rutcampustrack.notification.push
                                                                    .NotificationPushIntentStore
                                                                    .COLLECTION)
                                                    .countDocuments())
                                    .isEqualTo(1);
                        });
    }

    @Test
    void committedEffectsSurviveConsumerChannelLossBeforeAckAndRedeliverWithoutChangingReadState()
            throws Exception {
        UUID id = UUID.randomUUID();
        try (HttpClient management = HttpClient.newBuilder().connectTimeout(ofSeconds(2)).build();
                AckLossWindow delivery = new AckLossWindow("notification-web.events", id);
                AckLossWindow history = new AckLossWindow("notification-web.history", id)) {
            delivery.install();
            history.install();
            long deliveryDeadLetters =
                    rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount();
            long historyDeadLetters =
                    rabbitAdmin.getQueueInfo("notification-web.history.dlq").getMessageCount();
            publish(envelope(id));

            // Each delegate has returned from its production transaction, but its real AUTO ACK
            // cannot run until this test releases the listener wrapper.
            delivery.awaitCommitted();
            history.awaitCommitted();
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isEqualTo(1);
            assertThat(claimsForConsumer(NotificationHistoryConsumer.CONSUMER_ID, id)).isEqualTo(1);
            var rows = repository.findByEventIdAndUserIdIn(id.toString(), List.of(42L, 43L));
            assertThat(rows)
                    .extracting(NotificationHistoryDocument::getUserId)
                    .containsExactlyInAnyOrder(42L, 43L);
            var recipient =
                    rows.stream().filter(row -> row.getUserId() == 42L).findFirst().orElseThrow();
            var student = new RequestContext();
            student.setUserId(42L);
            student.setSelectedRole("STUDENT");
            student.setInternalToken("synthetic-validated-token");
            student.setSessionId(UUID.randomUUID());
            student.setSessionVersion(1);
            student.setRolesVersion(1);
            student.setReadOnly(true);
            var read =
                    inbox.readState(
                            student,
                            recipient.getId(),
                            new NotificationInboxDto.ReadState(true, "0"));
            assertThat(read.role()).isEqualTo("STUDENT");
            assertThat(read.item().readAt()).isNotNull();
            assertThat(read.item().revision()).isEqualTo("1");
            var persistedRead =
                    repository
                            .findById(recipient.getId())
                            .orElseThrow()
                            .getRoleViews()
                            .get("STUDENT");
            assertThat(persistedRead.getReadAt()).isNotNull();
            assertThat(persistedRead.getRevision()).isEqualTo(1);
            var beforeHistory =
                    repository.findByEventIdAndUserIdIn(id.toString(), List.of(42L, 43L)).stream()
                            .sorted(Comparator.comparing(NotificationHistoryDocument::getUserId))
                            .toList();
            var beforeIntent =
                    mongoTemplate.findById(
                            id.toString(),
                            org.bson.Document.class,
                            ru.rutcampustrack.notification.push.NotificationPushIntentStore
                                    .COLLECTION);
            assertThat(beforeIntent).isNotNull();
            await().atMost(ofSeconds(15))
                    .untilAsserted(
                            () -> {
                                assertThat(queueState(management, delivery.queue).unacked())
                                        .isEqualTo(1);
                                assertThat(queueState(management, history.queue).unacked())
                                        .isEqualTo(1);
                            });
            long deliveryAcks = queueState(management, delivery.queue).acknowledged();
            long historyAcks = queueState(management, history.queue).acknowledged();

            // Abort only the captured, test-owned channels before the adapters can acknowledge.
            // The real listener containers must recover on new channels and receive redelivery.
            delivery.release.countDown();
            history.release.countDown();
            delivery.awaitReplay();
            history.awaitReplay();
            await().atMost(ofSeconds(20))
                    .untilAsserted(
                            () -> {
                                QueueState delivered = queueState(management, delivery.queue);
                                QueueState persisted = queueState(management, history.queue);
                                assertThat(delivered.ready()).isZero();
                                assertThat(delivered.unacked()).isZero();
                                assertThat(delivered.acknowledged()).isGreaterThan(deliveryAcks);
                                assertThat(persisted.ready()).isZero();
                                assertThat(persisted.unacked()).isZero();
                                assertThat(persisted.acknowledged()).isGreaterThan(historyAcks);
                            });
            assertThat(delivery.deliveries.get()).isEqualTo(2);
            assertThat(history.deliveries.get()).isEqualTo(2);
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isEqualTo(1);
            assertThat(claimsForConsumer(NotificationHistoryConsumer.CONSUMER_ID, id)).isEqualTo(1);
            assertThat(repository.findByEventIdAndUserIdIn(id.toString(), List.of(42L, 43L)))
                    .containsExactlyInAnyOrderElementsOf(beforeHistory);
            var afterIntent =
                    mongoTemplate.findById(
                            id.toString(),
                            org.bson.Document.class,
                            ru.rutcampustrack.notification.push.NotificationPushIntentStore
                                    .COLLECTION);
            assertThat(afterIntent).isNotNull();
            assertThat(afterIntent.get("context")).isEqualTo(beforeIntent.get("context"));
            assertThat(afterIntent.get("roles")).isEqualTo(beforeIntent.get("roles"));
            assertThat(
                            mongoTemplate
                                    .getCollection(
                                            ru.rutcampustrack.notification.push
                                                    .NotificationPushIntentStore.COLLECTION)
                                    .countDocuments())
                    .isEqualTo(1);
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount())
                    .isEqualTo(deliveryDeadLetters);
            assertThat(rabbitAdmin.getQueueInfo("notification-web.history.dlq").getMessageCount())
                    .isEqualTo(historyDeadLetters);
        }
    }

    private QueueState queueState(HttpClient client, String queue) throws Exception {
        assertThat(Set.of("notification-web.events", "notification-web.history")).contains(queue);
        String credentials = RABBITMQ.getAdminUsername() + ":" + RABBITMQ.getAdminPassword();
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://"
                                                + RABBITMQ.getHost()
                                                + ":"
                                                + RABBITMQ.getHttpPort()
                                                + "/api/queues/%2F/"
                                                + queue))
                        .timeout(ofSeconds(2))
                        .header(
                                "Authorization",
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString(
                                                        credentials.getBytes(
                                                                StandardCharsets.UTF_8)))
                        .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().length).isLessThanOrEqualTo(64 * 1024);
        var state = json.readTree(response.body());
        assertThat(state.path("messages_ready").isIntegralNumber()).isTrue();
        assertThat(state.path("messages_unacknowledged").isIntegralNumber()).isTrue();
        return new QueueState(
                state.path("messages_ready").asLong(),
                state.path("messages_unacknowledged").asLong(),
                state.path("message_stats").path("ack").asLong());
    }

    private record QueueState(long ready, long unacked, long acknowledged) {}

    /** A gate around the existing adapter, outside the durable production transaction. */
    private final class AckLossWindow implements AutoCloseable {
        private final String queue;
        private final UUID eventId;
        private final SimpleMessageListenerContainer container;
        private final MessageListener original;
        private final boolean wasRunning;
        private final CountDownLatch committed = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch replayed = new CountDownLatch(1);
        private final AtomicInteger deliveries = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<Channel> firstChannel = new AtomicReference<>();

        private AckLossWindow(String queue, UUID eventId) {
            this.queue = queue;
            this.eventId = eventId;
            container =
                    listenerRegistry.getListenerContainers().stream()
                            .filter(SimpleMessageListenerContainer.class::isInstance)
                            .map(SimpleMessageListenerContainer.class::cast)
                            .filter(item -> Arrays.asList(item.getQueueNames()).contains(queue))
                            .findFirst()
                            .orElseThrow();
            assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
            original = container.getMessageListener();
            assertThat(original).isInstanceOf(ChannelAwareMessageListener.class);
            wasRunning = container.isRunning();
        }

        private void install() {
            container.stop();
            container.setMessageListener(
                    (ChannelAwareMessageListener)
                            (message, channel) -> {
                                boolean target =
                                        eventId.toString()
                                                .equals(
                                                        json.readTree(message.getBody())
                                                                .path("event_id")
                                                                .asText());
                                if (!target) {
                                    ((ChannelAwareMessageListener) original)
                                            .onMessage(message, channel);
                                    return;
                                }
                                try {
                                    int delivery = deliveries.incrementAndGet();
                                    Channel physical =
                                            channel instanceof ChannelProxy proxy
                                                    ? proxy.getTargetChannel()
                                                    : channel;
                                    if (delivery == 1) {
                                        assertThat(message.getMessageProperties().isRedelivered())
                                                .isFalse();
                                        firstChannel.set(physical);
                                    } else {
                                        assertThat(delivery).isEqualTo(2);
                                        assertThat(message.getMessageProperties().isRedelivered())
                                                .isTrue();
                                        assertThat(physical).isNotSameAs(firstChannel.get());
                                    }
                                    ((ChannelAwareMessageListener) original)
                                            .onMessage(message, channel);
                                    if (delivery == 1) {
                                        committed.countDown();
                                        assertThat(release.await(45, TimeUnit.SECONDS)).isTrue();
                                        physical.abort();
                                    } else {
                                        replayed.countDown();
                                    }
                                } catch (Exception | AssertionError caught) {
                                    failure.compareAndSet(null, caught);
                                    throw caught;
                                }
                            });
            container.start();
        }

        private void awaitCommitted() {
            await().atMost(ofSeconds(10))
                    .until(() -> committed.getCount() == 0 || failure.get() != null);
            assertThat(failure.get()).isNull();
            assertThat(committed.getCount()).isZero();
        }

        private void awaitReplay() {
            await().atMost(ofSeconds(15))
                    .until(() -> replayed.getCount() == 0 || failure.get() != null);
            assertThat(failure.get()).isNull();
            assertThat(replayed.getCount()).isZero();
        }

        @Override
        public void close() {
            release.countDown();
            container.stop();
            container.setMessageListener(original);
            if (wasRunning) container.start();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"lesson.started", "homework.published", "excuse.requested"})
    void authorityFailureRetriesBeforeClaimThenSealedIntentCommitsOnce(String type) {
        UUID id = UUID.randomUUID();
        var event = envelope(id);
        event.put("event_type", type);
        org.mockito.Mockito.doThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException())
                .doReturn(
                        type.equals("excuse.requested")
                                ? Map.of(42L, Set.of("STUDENT"), 43L, Set.of("HEADMAN"))
                                : Map.of(42L, Set.of("STUDENT")))
                .when(audienceClient)
                .resolve(any());
        publish(event);
        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id))
                                    .isEqualTo(1);
                            assertThat(
                                            mongoTemplate.findById(
                                                    id.toString(),
                                                    org.bson.Document.class,
                                                    ru.rutcampustrack.notification.push
                                                            .NotificationPushIntentStore
                                                            .COLLECTION))
                                    .isNotNull();
                            assertThat(
                                            rabbitAdmin
                                                    .getQueueInfo("notification-web.events.dlq")
                                                    .getMessageCount())
                                    .isZero();
                        });
        publish(event);
        await().during(ofSeconds(1))
                .atMost(ofSeconds(3))
                .untilAsserted(
                        () ->
                                assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id))
                                        .isEqualTo(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"group.archived", "excuse.requested"})
    void exhaustedAuthorityFailureRollsBackClaimAndRetainsOriginalEventInDlq(String type) {
        UUID id = UUID.randomUUID();
        var event = envelope(id);
        event.put("event_type", type);
        org.mockito.Mockito.doThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException())
                .when(audienceClient)
                .resolve(any());
        publish(event);
        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isZero();
                            assertThat(
                                            rabbitAdmin
                                                    .getQueueInfo("notification-web.events.dlq")
                                                    .getMessageCount())
                                    .isEqualTo(1);
                        });
        assertThat(repository.findAll()).isEmpty();
        assertThat(
                        mongoTemplate
                                .getCollection(
                                        ru.rutcampustrack.notification.push
                                                .NotificationPushIntentStore.COLLECTION)
                                .countDocuments())
                .isZero();
        Map<?, ?> retained =
                (Map<?, ?>) rabbitTemplate.receiveAndConvert("notification-web.events.dlq");
        assertThat(retained.get("event_id")).isEqualTo(id.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"marks", "preferences"})
    void reminderDependencyFailureRetriesAndRetainsOnlyUnmarkedHistoryAndEnabledExternalIntent(
            String dependency) {
        UUID id = UUID.randomUUID();
        var event = envelope(id);
        event.put("event_type", "lesson.reminder");
        event.put("payload", Map.of("group_id", 7L, "lesson_id", 101L));
        org.mockito.Mockito.doReturn(
                        Map.of(
                                42L,
                                Set.of("STUDENT"),
                                43L,
                                Set.of("STUDENT"),
                                44L,
                                Set.of("STUDENT")))
                .when(audienceClient)
                .resolve(any());
        mongoTemplate.insert(
                ReminderAttendanceStateDocument.builder()
                        .lessonId(101L)
                        .userId(42L)
                        .status("present")
                        .markedAt(Instant.now())
                        .build());
        when(notificationPreferences.isEnabledForUser(eq(43L), anyString(), any()))
                .thenReturn(false);
        if (dependency.equals("marks")) {
            doThrow(
                            new org.springframework.dao.TransientDataAccessResourceException(
                                    "synthetic marks unavailable"))
                    .doCallRealMethod()
                    .when(mongoTemplate)
                    .find(any(Query.class), eq(ReminderAttendanceStateDocument.class));
        } else {
            when(notificationPreferences.isEnabledForUser(eq(43L), anyString(), any()))
                    .thenThrow(
                            new org.springframework.dao.TransientDataAccessResourceException(
                                    "synthetic preferences unavailable"))
                    .thenReturn(false);
        }
        publish(event);
        await().atMost(ofSeconds(10))
                .untilAsserted(
                        () -> {
                            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id))
                                    .isEqualTo(1);
                            var intent =
                                    mongoTemplate.findById(
                                            id.toString(),
                                            org.bson.Document.class,
                                            ru.rutcampustrack.notification.push
                                                    .NotificationPushIntentStore.COLLECTION);
                            assertThat(intent).isNotNull();
                            assertThat(
                                            ru.rutcampustrack.notification.history
                                                    .NotificationAudienceService.decode(intent))
                                    .containsOnlyKeys(44L);
                            assertThat(
                                            claimsForConsumer(
                                                    NotificationHistoryConsumer.CONSUMER_ID, id))
                                    .isEqualTo(1);
                            assertThat(repository.findAll())
                                    .extracting(NotificationHistoryDocument::getUserId)
                                    .containsExactlyInAnyOrder(43L, 44L);
                        });
    }
}
