package ru.rutcampustrack.notification.preferences;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateBotNotificationPreferencesRequest;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static ru.rutcampustrack.notification.contract.dto.preferences.UpdateBotNotificationPreferencesRequest.Operation.*;

/** Actual Mongo transactions/Redis legacy state, no provider or upstream service calls. */
@Testcontainers
@SpringJUnitConfig(DurableNotificationPreferencesIT.Config.class)
class DurableNotificationPreferencesIT {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean MongoDatabaseFactory factory() { return new SimpleMongoClientDatabaseFactory(MONGO.getReplicaSetUrl("prefs_test")); }
        @Bean MongoTemplate mongo(MongoDatabaseFactory factory) {
            MongoTemplate mongo = new MongoTemplate(factory);
            new NotificationPreferencesMongoConfig(mongo).initialize();
            mongo.createCollection(ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION);
            mongo.createCollection("prefs_claim_effects");
            new ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore(mongo).ensureIndexes();
            return mongo;
        }
        @Bean MongoTransactionManager transactionManager(MongoDatabaseFactory factory) { return new MongoTransactionManager(factory); }
        @Bean LettuceConnectionFactory redisConnection() { return new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379)); }
        @Bean StringRedisTemplate redis(LettuceConnectionFactory connection) { return new StringRedisTemplate(connection); }
        @Bean NotificationPreferencesStore store(MongoTemplate mongo, StringRedisTemplate redis) { return new NotificationPreferencesStore(mongo, redis); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean NotificationPreferencesService canonical(NotificationPreferencesStore store, Clock clock) { return new NotificationPreferencesService(store, clock); }
        @Bean AcademicTelegramBindingClient binding() { return mock(AcademicTelegramBindingClient.class); }
        @Bean BotNotificationPreferencesService bot(NotificationPreferencesStore store, AcademicTelegramBindingClient binding, Clock clock) {
            return new BotNotificationPreferencesService(store, binding, clock);
        }
        @Bean org.springframework.messaging.simp.SimpMessagingTemplate messaging() {
            return mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);
        }
        @Bean ru.rutcampustrack.notification.push.WebPushDeliveryService webPush(MongoTemplate mongo,
                Clock clock, NotificationPreferencesService canonical) {
            return spy(new ru.rutcampustrack.notification.push.WebPushDeliveryService(
                    mock(ru.rutcampustrack.notification.push.PushSubscriptionRepository.class),
                    mock(nl.martijndwars.webpush.PushService.class), new com.fasterxml.jackson.databind.ObjectMapper(),
                    mongo, clock, canonical, null, mock(ru.rutcampustrack.notification.history.AcademicGroupMemberClient.class)));
        }
        @Bean ru.rutcampustrack.notification.event.EventConsumer consumer(MongoTemplate mongo,
                org.springframework.messaging.simp.SimpMessagingTemplate messaging,
                ru.rutcampustrack.notification.push.WebPushDeliveryService webPush) {
            return new ru.rutcampustrack.notification.event.EventConsumer(messaging, webPush,
                    new ru.rutcampustrack.shared.events.IdempotencyGuard(
                            new ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore(mongo)));
        }
    }

    @Autowired NotificationPreferencesService canonical;
    @Autowired BotNotificationPreferencesService bot;
    @MockitoSpyBean StringRedisTemplate redis;
    @Autowired AcademicTelegramBindingClient binding;
    @MockitoSpyBean MongoTemplate mongo;
    @Autowired ru.rutcampustrack.notification.event.EventConsumer consumer;
    @Autowired ru.rutcampustrack.notification.push.WebPushDeliveryService webPush;
    @Autowired org.springframework.messaging.simp.SimpMessagingTemplate messaging;
    @Autowired MongoTransactionManager transactionManager;

    @BeforeEach void clearOwnedSyntheticState() {
        reset(mongo, binding, redis);
        reset(webPush, messaging);
        doReturn(java.util.concurrent.CompletableFuture.completedFuture(null)).when(webPush)
                .sendToGroup(anyLong(), anyString(), anyMap(), anySet());
        for (String collection : List.of(NotificationPreferencesStore.USERS, NotificationPreferencesStore.TELEGRAM,
                NotificationPreferencesStore.LEGACY_OWNERS, NotificationPreferencesStore.RECEIPTS,
                ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION, "prefs_claim_effects")) {
            mongo.remove(new Query(), collection);
        }
        redis.delete(List.of("notif:prefs:user:11", "notif:prefs:user:12", "bot:notif:101",
                "bot:notif:cat:101", "bot:notif:mute:101"));
    }

    @Test void authorityOutageRollsBackEventClaimBeforeEnqueueAndReplayAdmitsOnce() {
        Map<String, Object> payload = Map.of("user_id", 11L, "group_id", 7L);
        Map<String, Object> envelope = Map.of("event_id", java.util.UUID.randomUUID().toString(),
                "event_type", "homework.due_reminder", "payload", payload);
        doThrow(new TransientDataAccessResourceException("synthetic authority outage"))
                .when(mongo).findById(11L, Document.class, NotificationPreferencesStore.USERS);
        assertThatThrownBy(() -> consumer.onEvent(envelope)).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(mongo.getCollection(ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION)
                .countDocuments()).isZero();
        verifyNoInteractions(messaging);
        verify(webPush, never()).sendToGroup(anyLong(), anyString(), anyMap(), anySet());
        reset(mongo);
        consumer.onEvent(envelope);
        consumer.onEvent(envelope);
        assertThat(mongo.getCollection(ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION)
                .countDocuments()).isEqualTo(1);
        verify(webPush, times(1)).sendToGroup(7L, "homework.due_reminder", payload, java.util.Set.of(11L));
    }

    @Test void concurrentSameEventClaimCommitsOneEffectAndLoserRetriesSafely() throws Exception {
        var eventId = java.util.UUID.randomUUID();
        String claims = ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION;
        var store = new ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore(mongo);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var simultaneousSnapshots = new java.util.concurrent.CyclicBarrier(2);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        Callable<Object> claimant = () -> {
            var localAttempts = new java.util.concurrent.atomic.AtomicInteger();
            return retryTransaction(() -> transactions.execute(status -> {
                attempts.incrementAndGet();
                if (localAttempts.getAndIncrement() == 0) {
                    // Real first read fixes both transaction snapshots before either insert.
                    // No mocked query/claim result and no provider effect outside the transaction.
                    mongo.count(new Query(), claims);
                    try { simultaneousSnapshots.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }
                boolean won = store.tryClaim("prefs-race", eventId);
                if (won) mongo.insert(new Document("event_id", eventId.toString()), "prefs_claim_effects");
                return won;
            }));
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(claimant, claimant));
            assertThat(List.of(futures.get(0).get(), futures.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        assertThat(mongo.getCollection(claims).countDocuments()).isEqualTo(1);
        assertThat(mongo.getCollection("prefs_claim_effects").countDocuments()).isEqualTo(1);
        Boolean duplicate = transactions.execute(status -> store.tryClaim("prefs-race", eventId));
        assertThat(duplicate).isFalse();
        assertThat(mongo.getCollection("prefs_claim_effects").countDocuments()).isEqualTo(1);
    }

    @Test void adoptedChoicesSurviveRedisLossAndDoNotReimportOverMongo() {
        redis.opsForHash().put("notif:prefs:user:11", "homework", "off");
        redis.opsForValue().set("bot:notif:101", "off");
        redis.opsForHash().put("bot:notif:cat:101", "lessons", "off");
        redis.opsForValue().set("bot:notif:mute:101", Instant.now().plusSeconds(3600).toString());
        var before = bot.get(11, 101, "homework");
        assertThat(before.eligible()).isFalse();
        assertThat(before.globalEnabled()).isFalse();
        redis.delete(List.of("notif:prefs:user:11", "bot:notif:101", "bot:notif:cat:101", "bot:notif:mute:101"));
        var after = bot.get(11, 101, "homework");
        assertThat(after).isEqualTo(before);
        assertThat(canonical.isEnabledForUser(11L, "homework.published")).isFalse();
    }

    @Test void neededLegacyOutageNeverDefaultsButExistingMongoDoesNotConsultRedis() {
        canonical.updateForUser(11, new UpdateNotificationPreferencesRequest(Map.of("homework", false), null));
        var hashes = mock(org.springframework.data.redis.core.HashOperations.class);
        when(hashes.entries(any())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("synthetic"));
        doReturn(hashes).when(redis).opsForHash();
        assertThat(canonical.getForUser(11).categories()).containsEntry("homework", false);
        assertThatThrownBy(() -> canonical.getForUser(12)).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(mongo.findById(12L, Document.class, NotificationPreferencesStore.USERS)).isNull();
    }

    @Test void privateRouteRejectsMissingCredentialWrongBindingAndUnavailableAuthority() throws Exception {
        String token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new BotNotificationPreferencesController(bot, token)).build();
        String route = "/internal/bot/notification-preferences/11/101";
        mvc.perform(get(route)).andExpect(status().isUnauthorized());
        mvc.perform(get(route).header("X-Bot-Preferences-Token", token + "=")).andExpect(status().isUnauthorized());
        verifyNoInteractions(binding);
        assertThat(mongo.getCollection(NotificationPreferencesStore.TELEGRAM).countDocuments()).isZero();
        mvc.perform(get(route).header("X-Bot-Preferences-Token", token).param("category", "homework"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(11))
                .andExpect(jsonPath("$.eligible").value(true));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT)).when(binding).validate(12, 101);
        mvc.perform(get("/internal/bot/notification-preferences/12/101").header("X-Bot-Preferences-Token", token))
                .andExpect(status().isConflict());
        doThrow(new TransientDataAccessResourceException("synthetic DB outage"))
                .when(mongo).findById(11L, Document.class, NotificationPreferencesStore.USERS);
        mvc.perform(get(route).header("X-Bot-Preferences-Token", token).param("category", "homework"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test void concurrentCallbackTogglesAndReplaysLinearizeWithoutLostUpdates() throws Exception {
        bot.get(11, 101, null);
        var one = command("concurrent-1", TOGGLE_GLOBAL, null, null, null);
        var two = command("concurrent-2", TOGGLE_GLOBAL, null, null, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.<Callable<Object>>of(
                    () -> retryTransaction(() -> bot.update(11, 101, one)),
                    () -> retryTransaction(() -> bot.update(11, 101, two))));
            for (var future : futures) future.get();
        }
        assertThat(bot.get(11, 101, null).globalEnabled()).isTrue();
        bot.update(11, 101, one);
        bot.update(11, 101, two);
        assertThat(bot.get(11, 101, null).globalEnabled()).isTrue();
        assertThat(mongo.getCollection(NotificationPreferencesStore.RECEIPTS).countDocuments()).isEqualTo(2);
    }

    @Test void receiptIsAtomicAndReplayDoesNotToggleOrExtendMuteAndRechecksBinding() {
        var toggle = command("callback-1", TOGGLE_CATEGORY, "homework", null, null);
        var first = bot.update(11, 101, toggle);
        assertThat(first.categories().get("homework")).isFalse();
        assertThat(bot.update(11, 101, toggle)).isEqualTo(first);
        assertThatThrownBy(() -> bot.update(11, 101, command("callback-1", TOGGLE_CATEGORY, "lessons", null, null)))
                .isInstanceOf(ResponseStatusException.class);
        var mute = command("callback-2", MUTE_FOR, null, null, 86400L);
        var muted = bot.update(11, 101, mute);
        assertThat(bot.update(11, 101, mute).mutedUntil()).isEqualTo(muted.mutedUntil());
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT)).when(binding).validate(11, 101);
        assertThatThrownBy(() -> bot.update(11, 101, toggle)).isInstanceOf(ResponseStatusException.class);
        assertThat(mongo.findById("11:101", Document.class, NotificationPreferencesStore.TELEGRAM).get("mutedUntil")).isNotNull();
    }

    @Test void failedBootstrapAndReceiptInsertionRollbackMarkerFacetAndMutation() {
        doThrow(new TransientDataAccessResourceException("synthetic canonical outage"))
                .when(mongo).findById(11L, Document.class, NotificationPreferencesStore.USERS);
        assertThatThrownBy(() -> bot.get(11, 101, "lessons")).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(mongo.findById(101L, Document.class, NotificationPreferencesStore.LEGACY_OWNERS)).isNull();
        assertThat(mongo.findById("11:101", Document.class, NotificationPreferencesStore.TELEGRAM)).isNull();
        reset(mongo);
        bot.get(11, 101, null);
        doThrow(new TransientDataAccessResourceException("synthetic receipt outage"))
                .when(mongo).insert(any(Document.class), eq(NotificationPreferencesStore.RECEIPTS));
        assertThatThrownBy(() -> bot.update(11, 101, command("callback-3", TOGGLE_GLOBAL, null, null, null)))
                .isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(mongo.findById("11:101", Document.class, NotificationPreferencesStore.TELEGRAM).getBoolean("globalEnabled")).isTrue();
    }

    @Test void rebindCannotCopyLegacyFacetAndPartialConcurrentPatchesDoNotLoseFields() throws Exception {
        redis.opsForValue().set("bot:notif:101", "off");
        assertThat(bot.get(11, 101, null).globalEnabled()).isFalse();
        assertThat(bot.get(12, 101, null).globalEnabled()).isTrue();
        assertThat(bot.get(11, 101, null).globalEnabled()).isFalse();
        canonical.getForUser(11);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.<Callable<Object>>of(
                    () -> canonical.updateForUser(11, new UpdateNotificationPreferencesRequest(Map.of("lessons", false), null)),
                    () -> canonical.updateForUser(11, new UpdateNotificationPreferencesRequest(Map.of("homework", false), null))));
            for (var future : futures) future.get();
        }
        assertThat(canonical.getForUser(11).categories()).containsEntry("lessons", false).containsEntry("homework", false);
    }

    private static UpdateBotNotificationPreferencesRequest command(String key, UpdateBotNotificationPreferencesRequest.Operation operation,
                                                                  String category, Boolean enabled, Long duration) {
        return new UpdateBotNotificationPreferencesRequest(key, operation, category, enabled, null, duration);
    }

    private static Object retryTransaction(Callable<Object> mutation) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try { return mutation.call(); }
            catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | com.mongodb.MongoException failure) {
                if (attempt >= 9) throw failure;
                Thread.sleep(20);
            }
        }
    }
}
