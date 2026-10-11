package ru.rutcampustrack.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateDocument;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;
import ru.rutcampustrack.notification.history.NotificationHistoryConsumer;
import ru.rutcampustrack.notification.history.NotificationHistoryDocument;
import ru.rutcampustrack.notification.history.NotificationHistoryRepository;
import ru.rutcampustrack.notification.history.AcademicGroupMemberClient;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;

/**
 * M13 G8 — IT для consumer-side dedup в notification-app.
 *
 * <p>Публикует {@code excuse.requested} с одинаковым {@code event_id}
 * дважды; обе очереди ({@code notification-web.events} и
 * {@code notification-web.history}) получают сообщение, но guard'ы
 * пропускают только первое — history-document создаётся один раз,
 * claim-records по двум consumerId — по одному каждой.
 */
@SpringBootTest(properties = {
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
            Path keyFile = Path.of(
                    EventIdempotentIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String COLLECTION = "event_consumer_processed";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationHistoryRepository repository;

    @MockitoSpyBean
    private MongoTemplate mongoTemplate;

    @Autowired private RabbitAdmin rabbitAdmin;
    @MockitoBean private ru.rutcampustrack.notification.history.NotificationAudienceClient audienceClient;
    @MockitoBean private NotificationPreferencesService notificationPreferences;
    @MockitoBean private WebPushDeliveryService webPushDeliveryService;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;
    @Autowired private ReminderAttendanceStateService reminderAttendanceStateService;
    private StringRedisTemplate reminderRedis;
    private HashOperations<String, Object, Object> reminderPreferences;

    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

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
        when(notificationPreferences.isEnabledForUser(anyLong(), anyString(), any())).thenReturn(true);
        when(audienceClient.resolve(any())).thenAnswer(call -> {
            Map<String, Object> event = call.getArgument(0);
            if ("excuse.requested".equals(event.get("event_type"))) return Map.of(42L, Set.of("STUDENT"), 43L, Set.of("HEADMAN"));
            return Map.of(42L, Set.of("STUDENT"));
        });
        for (String collection : List.of(ru.rutcampustrack.notification.history.NotificationAudienceService.COLLECTION,
                ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION, "user_notification_preferences"))
            mongoTemplate.remove(new Query(), collection);
    }

    private Map<String, Object> envelope(UUID eventId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "excuse.requested");
        envelope.put("event_id", eventId.toString());
        envelope.put("trace_id", "trace-" + eventId);
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload", Map.of(
                "user_id", 42,
                "group_id", 7,
                "ticket_id", "request-it-1",
                "excuse_type", "illness"));
        return envelope;
    }

    private void publish(Map<String, Object> env) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", env);
    }

    private long claimsForConsumer(String consumerId, UUID eventId) {
        return mongoTemplate.count(
                new Query(Criteria.where("consumer_id").is(consumerId)
                        .and("event_id").is(eventId.toString())),
                COLLECTION);
    }

    @Test void duplicateDeliveryHistoryAndDurableIntentCommitOnceAcrossBothConsumerQueues() {
        UUID id = UUID.randomUUID();
        var event = envelope(id);
        publish(event); publish(event);
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(repository.findAll()).hasSize(2);
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isEqualTo(1);
            assertThat(claimsForConsumer(NotificationHistoryConsumer.CONSUMER_ID, id)).isEqualTo(1);
            var intent = mongoTemplate.findById(id.toString(), org.bson.Document.class, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION);
            assertThat(intent).isNotNull();
            assertThat(ru.rutcampustrack.notification.history.NotificationAudienceService.decode(intent)).containsOnlyKeys(43L);
            verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any(), any());
        });
        publish(event);
        await().during(ofSeconds(1)).atMost(ofSeconds(3)).untilAsserted(() -> {
            assertThat(repository.findAll()).hasSize(2);
            assertThat(mongoTemplate.getCollection(ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION).countDocuments()).isEqualTo(1);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"lesson.started", "homework.published", "excuse.requested"})
    void authorityFailureRetriesBeforeClaimThenSealedIntentCommitsOnce(String type) {
        UUID id = UUID.randomUUID();
        var event = envelope(id); event.put("event_type", type);
        org.mockito.Mockito.doThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException())
                .doReturn(type.equals("excuse.requested") ? Map.of(42L, Set.of("STUDENT"), 43L, Set.of("HEADMAN")) : Map.of(42L, Set.of("STUDENT"))).when(audienceClient).resolve(any());
        publish(event);
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isEqualTo(1);
            assertThat(mongoTemplate.findById(id.toString(), org.bson.Document.class, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION)).isNotNull();
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isZero();
        });
        publish(event);
        await().during(ofSeconds(1)).atMost(ofSeconds(3)).untilAsserted(() -> assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isEqualTo(1));
    }

    @ParameterizedTest @ValueSource(strings = {"group.archived", "excuse.requested"})
    void exhaustedAuthorityFailureRollsBackClaimAndRetainsOriginalEventInDlq(String type) {
        UUID id = UUID.randomUUID();
        var event = envelope(id); event.put("event_type", type);
        org.mockito.Mockito.doThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException()).when(audienceClient).resolve(any());
        publish(event);
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isZero();
            assertThat(rabbitAdmin.getQueueInfo("notification-web.events.dlq").getMessageCount()).isEqualTo(1);
        });
        assertThat(repository.findAll()).isEmpty();
        assertThat(mongoTemplate.getCollection(ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION).countDocuments()).isZero();
        Map<?, ?> retained = (Map<?, ?>) rabbitTemplate.receiveAndConvert("notification-web.events.dlq");
        assertThat(retained.get("event_id")).isEqualTo(id.toString());
    }

    @ParameterizedTest @ValueSource(strings = {"marks", "preferences"})
    void reminderDependencyFailureRetriesAndRetainsOnlyUnmarkedHistoryAndEnabledExternalIntent(String dependency) {
        UUID id = UUID.randomUUID();
        var event = envelope(id); event.put("event_type", "lesson.reminder");
        event.put("payload", Map.of("group_id", 7L, "lesson_id", 101L));
        org.mockito.Mockito.doReturn(Map.of(42L, Set.of("STUDENT"), 43L, Set.of("STUDENT"), 44L, Set.of("STUDENT"))).when(audienceClient).resolve(any());
        mongoTemplate.insert(ReminderAttendanceStateDocument.builder().lessonId(101L).userId(42L).status("present").markedAt(Instant.now()).build());
        when(notificationPreferences.isEnabledForUser(eq(43L), anyString(), any())).thenReturn(false);
        if (dependency.equals("marks")) {
            doThrow(new org.springframework.dao.TransientDataAccessResourceException("synthetic marks unavailable"))
                    .doCallRealMethod().when(mongoTemplate).find(any(Query.class), eq(ReminderAttendanceStateDocument.class));
        } else {
            when(notificationPreferences.isEnabledForUser(eq(43L), anyString(), any()))
                    .thenThrow(new org.springframework.dao.TransientDataAccessResourceException("synthetic preferences unavailable"))
                    .thenReturn(false);
        }
        publish(event);
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(claimsForConsumer(EventConsumer.CONSUMER_ID, id)).isEqualTo(1);
            var intent = mongoTemplate.findById(id.toString(), org.bson.Document.class, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION);
            assertThat(intent).isNotNull();
            assertThat(ru.rutcampustrack.notification.history.NotificationAudienceService.decode(intent)).containsOnlyKeys(44L);
            assertThat(claimsForConsumer(NotificationHistoryConsumer.CONSUMER_ID, id)).isEqualTo(1);
            assertThat(repository.findAll()).extracting(NotificationHistoryDocument::getUserId).containsExactlyInAnyOrder(43L, 44L);
        });
    }
}
