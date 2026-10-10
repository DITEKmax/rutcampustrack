package ru.rutcampustrack.notification.history;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesV2Dto;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.notification.preferences.*;
import ru.rutcampustrack.notification.security.RequestContext;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Mongo atomic updates and independent role views; no provider/server/foreign data. */
@Testcontainers
class NotificationStateV2IT {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");
    MongoTemplate mongo;
    NotificationInboxService inbox;
    NotificationPreferencesV2Service preferences;
    AcademicTelegramBindingClient binding;
    org.springframework.data.redis.core.StringRedisTemplate redis;
    static final Instant SENT = Instant.parse("2026-10-09T10:00:00Z");
    static final Instant NOW = SENT.plusSeconds(3600);
    @BeforeEach void setup() {
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(MONGO.getReplicaSetUrl("notification_v2_owned_test")));
        mongo.remove(new Query(), NotificationHistoryDocument.class);
        mongo.remove(new Query(), "user_notification_preferences");
        mongo.remove(new Query(), "telegram_notification_preferences");
        mongo.remove(new Query(), "notification_preference_legacy_bindings");
        inbox = new NotificationInboxService(mongo, new NotificationCatalog(), Clock.fixed(NOW, ZoneOffset.UTC));
        binding = mock(AcademicTelegramBindingClient.class);
        redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        // Durable synthetic records below prove Redis is not a second authority.
        preferences = new NotificationPreferencesV2Service(new NotificationPreferencesStore(mongo, redis), binding, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    @Test void roleCommandsAreIsolatedAndUnreadReplayPreservesSentAtRetentionAndOrdering() {
        var student = context(11L, "STUDENT");
        var headman = context(11L, "HEADMAN");
        var doc = row(11, "dual", Set.of("STUDENT", "HEADMAN"), SENT);
        mongo.insert(doc);
        assertThat(inbox.count(student).count()).isEqualTo(1);
        assertThat(inbox.count(headman).count()).isEqualTo(1);
        var read = inbox.readState(student, doc.getId(), new NotificationInboxDto.ReadState(true, "0"));
        assertThat(read.unreadCount()).isZero();
        assertThat(inbox.count(headman).count()).isEqualTo(1);
        var unread = inbox.readState(student, doc.getId(), new NotificationInboxDto.ReadState(false, "1"));
        var replay = inbox.readState(student, doc.getId(), new NotificationInboxDto.ReadState(false, "1"));
        assertThat(replay.item()).isEqualTo(unread.item());
        assertThat(unread.item().orderAt()).isEqualTo(NOW);
        assertThat(unread.item().sentAt()).isEqualTo(SENT);
        assertThat(mongo.findById(doc.getId(), NotificationHistoryDocument.class).getSentAt()).isEqualTo(SENT);
        assertThatThrownBy(() -> inbox.readState(student, doc.getId(), new NotificationInboxDto.ReadState(true, "1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));
        assertThat(inbox.markAllRead(headman)).isZero();
        assertThat(inbox.count(student).count()).isEqualTo(1);
        assertThatThrownBy(() -> inbox.readState(context(12L, "STUDENT"), doc.getId(), new NotificationInboxDto.ReadState(true, "2")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    }
    @Test void cursorIsBoundToUserRoleAndFilterAndLegacyNeverBackfillsHeadmanRole() {
        var first = row(11, "old-a", Set.of("STUDENT"), SENT);
        var second = row(11, "old-b", Set.of("STUDENT"), SENT.minusSeconds(1));
        mongo.insert(first); mongo.insert(second);
        var context = context(11L, "STUDENT");
        var page = inbox.list(context, 1, null, false);
        assertThat(page.items()).extracting(NotificationInboxDto.Item::id).containsExactly(first.getId());
        assertThat(inbox.list(context, 1, page.nextCursor(), false).items()).extracting(NotificationInboxDto.Item::id).containsExactly(second.getId());
        assertThatThrownBy(() -> inbox.list(context(11L, "HEADMAN"), 1, page.nextCursor(), false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> inbox.list(context(12L, "STUDENT"), 1, page.nextCursor(), false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> inbox.list(context, 1, page.nextCursor(), true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var legacy = row(11, "legacy", Set.of("STUDENT"), SENT.minusSeconds(3)); legacy.setRoleViews(null);
        mongo.insert(legacy);
        assertThat(inbox.count(context).count()).isEqualTo(3);
        assertThat(inbox.count(context(11L, "HEADMAN")).count()).isZero();
    }
    @Test void concurrentReadCommandsApplyOneRevisionAndSameCommandReplayDoesNotBump() throws Exception {
        var doc = row(11, "race", Set.of("STUDENT"), SENT); mongo.insert(doc);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<NotificationInboxDto.ReadResult> command = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return inbox.readState(context(11L, "STUDENT"), doc.getId(), new NotificationInboxDto.ReadState(true, "0"));
            };
            var results = executor.invokeAll(List.of(command, command));
            assertThat(results.get(0).get().item().revision()).isEqualTo("1");
            assertThat(results.get(1).get().item().revision()).isEqualTo("1");
            assertThat(mongo.findById(doc.getId(), NotificationHistoryDocument.class).getRoleViews().get("STUDENT").getRevision()).isEqualTo(1);
        }
    }
    @Test void legacyOffMuteSurviveAndSplitTelegramIsIndependentWithAtomicRevisionConflict() throws Exception {
        seedPreferences(11, 101, false);
        when(binding.telegramId(11)).thenReturn(101L);
        var old = preferences.get(11);
        assertThat(old.splitChannels()).isTrue();
        assertThat(old.types().get("LESSON_STARTED").telegram()).isFalse();
        var changed = new LinkedHashMap<>(old.types());
        changed.put("LESSON_STARTED", new NotificationPreferencesV2Dto.TypePreference(false, true, "blue"));
        var request = new NotificationPreferencesV2Dto.Update(old.revision(), true, changed);
        var saved = preferences.update(11, request);
        assertThat(saved.mutedUntil()).isEqualTo(NOW.plusSeconds(600));
        assertThat(preferences.appEnabled(11, "LESSON_STARTED")).isFalse();
        // Explicit unmute only, followed by Telegram eligibility through the exact validated facet.
        var unmute = new NotificationPreferencesV2Dto.Update(saved.revision(), true, saved.types()); unmute.setMutedUntil(null);
        preferences.update(11, unmute);
        assertThat(preferences.telegramEnabled(11, 101, "LESSON_STARTED")).isTrue();
        assertThatThrownBy(() -> preferences.update(11, request)).isInstanceOf(NotificationPreferencesConflict.class);
        var current = preferences.get(11);
        var unifiedInvalid = new NotificationPreferencesV2Dto.Update(current.revision(), false, current.types());
        assertThatThrownBy(() -> preferences.update(11, unifiedInvalid)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(redis);
    }
    @Test void concurrentPreferenceSavesCommitOneWholeProfileAndLoserGets409() throws Exception {
        seedPreferences(11, 101, true); when(binding.telegramId(11)).thenReturn(101L);
        var initial = preferences.get(11);
        var one = new LinkedHashMap<>(initial.types());
        var two = new LinkedHashMap<>(initial.types());
        one.put("LESSON_STARTED", new NotificationPreferencesV2Dto.TypePreference(false, false, "pink"));
        two.put("LESSON_STARTED", new NotificationPreferencesV2Dto.TypePreference(true, true, "green"));
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(one, two).stream().<Callable<Object>>map(values -> () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { return preferences.update(11, new NotificationPreferencesV2Dto.Update(initial.revision(), false, values)); }
                catch (NotificationPreferencesConflict error) { return error; }
            }).toList();
            var results = executor.invokeAll(tasks);
            var values = List.of(results.get(0).get(), results.get(1).get());
            assertThat(values.stream().filter(NotificationPreferencesV2Dto.class::isInstance)).hasSize(1);
            assertThat(values.stream().filter(NotificationPreferencesConflict.class::isInstance)).hasSize(1);
            var committed = preferences.get(11);
            assertThat(committed.types()).isIn(one, two);
            assertThat(committed.revision()).isNotEqualTo(initial.revision());
        }
    }
    @Test void preferenceLegacyOffCannotSucceedAgainstProfileAdoptedAfterItsRead() {
        seedPreferences(11, 101, true);
        when(binding.telegramId(11)).thenReturn(101L);
        var racedMongo = spy(mongo);
        var store = new NotificationPreferencesStore(racedMongo, redis);
        var legacy = new NotificationPreferencesService(store, Clock.fixed(NOW, ZoneOffset.UTC));
        var racedPreferences = new NotificationPreferencesV2Service(store, binding, Clock.fixed(NOW, ZoneOffset.UTC));
        beforeWrite(racedMongo, "categories.reminders", () -> racedPreferences.get(11));
        var off = new ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest(
                Map.of("reminders", false), NOW.plusSeconds(600));
        assertThatThrownBy(() -> legacy.updateForUser(11, off))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        assertThat(mongo.findById(11L, Document.class, "user_notification_preferences").containsKey("v2")).isTrue();
        // The rejected write cannot report success; an explicit retry uses the current V2 revision.
        assertThat(legacy.updateForUser(11, off).categories().get("reminders")).isFalse();
        var current = racedPreferences.get(11);
        assertThat(current.types().get("LESSON_STARTED").appPush()).isFalse();
        assertThat(current.types().get("LESSON_REMINDER").appPush()).isFalse();
        assertThat(current.types().get("LESSON_STARTED").telegram()).isFalse();
        assertThat(current.mutedUntil()).isEqualTo(NOW.plusSeconds(600));
        verifyNoInteractions(redis);
    }
    @Test void preferenceAdoptionCannotCommitProfileBuiltBeforeSuccessfulLegacyOff() {
        seedPreferences(11, 101, true);
        when(binding.telegramId(11)).thenReturn(101L);
        var racedMongo = spy(mongo);
        var store = new NotificationPreferencesStore(racedMongo, redis);
        var legacy = new NotificationPreferencesService(store, Clock.fixed(NOW, ZoneOffset.UTC));
        var racedPreferences = new NotificationPreferencesV2Service(store, binding, Clock.fixed(NOW, ZoneOffset.UTC));
        var legacyResponse = new java.util.concurrent.atomic.AtomicReference<ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto>();
        beforeWrite(racedMongo, "v2", () -> legacyResponse.set(legacy.updateForUser(11,
                new ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest(
                        Map.of("reminders", false), NOW.plusSeconds(600)))));
        assertThatThrownBy(() -> racedPreferences.get(11))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        assertThat(legacyResponse.get().categories().get("reminders")).isFalse();
        assertThat(mongo.findById(11L, Document.class, "user_notification_preferences").containsKey("v2")).isFalse();
        var current = racedPreferences.get(11);
        assertThat(current.types().get("LESSON_STARTED").appPush()).isFalse();
        assertThat(current.types().get("LESSON_REMINDER").appPush()).isFalse();
        assertThat(current.types().get("LESSON_STARTED").telegram()).isFalse();
        assertThat(current.mutedUntil()).isEqualTo(NOW.plusSeconds(600));
        verifyNoInteractions(redis);
    }
    @Test void preferenceLegacyGetProjectsOnlyCanonicalAppAndUnchangedSavePreservesOff() {
        seedPreferences(11, 101, true);
        when(binding.telegramId(11)).thenReturn(101L);
        var initial = preferences.get(11);
        var types = new LinkedHashMap<>(initial.types());
        types.put("LESSON_STARTED", new NotificationPreferencesV2Dto.TypePreference(false, true, "blue"));
        types.put("LESSON_CLOSED", new NotificationPreferencesV2Dto.TypePreference(true, false, "pink"));
        preferences.update(11, new NotificationPreferencesV2Dto.Update(initial.revision(), true, types));
        var legacy = new NotificationPreferencesService(new NotificationPreferencesStore(mongo, redis), Clock.fixed(NOW, ZoneOffset.UTC));
        var shown = legacy.getForUser(11);
        assertThat(shown.categories().get("reminders")).isFalse(); // mixed app category is conservative OFF
        assertThat(shown.categories().get("lessons")).isTrue(); // Telegram OFF does not change app projection
        var saved = legacy.updateForUser(11, new ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest(
                shown.categories(), shown.mutedUntil()));
        assertThat(saved.categories()).isEqualTo(shown.categories());
        var current = preferences.get(11);
        assertThat(current.types().get("LESSON_STARTED")).isEqualTo(types.get("LESSON_STARTED"));
        assertThat(current.types().get("LESSON_REMINDER").appPush()).isFalse();
        assertThat(current.types().get("LESSON_REMINDER").telegram()).isTrue();
        assertThat(current.types().get("LESSON_CLOSED")).isEqualTo(types.get("LESSON_CLOSED"));
    }
    @Test void preferenceLegacyExplicitEnableKeepsUnifiedColumnsAndColors() {
        seedPreferences(11, 101, true);
        when(binding.telegramId(11)).thenReturn(101L);
        var initial = preferences.get(11);
        var types = new LinkedHashMap<>(initial.types());
        types.put("LESSON_STARTED", new NotificationPreferencesV2Dto.TypePreference(false, false, "blue"));
        preferences.update(11, new NotificationPreferencesV2Dto.Update(initial.revision(), false, types));
        var legacy = new NotificationPreferencesService(new NotificationPreferencesStore(mongo, redis), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(legacy.getForUser(11).categories().get("reminders")).isFalse();
        assertThat(legacy.updateForUser(11, new ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest(
                Map.of("reminders", true), NOW.plusSeconds(600))).categories().get("reminders")).isTrue();
        var current = preferences.get(11);
        assertThat(current.types().get("LESSON_STARTED")).isEqualTo(new NotificationPreferencesV2Dto.TypePreference(true, true, "blue"));
        assertThat(current.types().get("LESSON_REMINDER")).isEqualTo(new NotificationPreferencesV2Dto.TypePreference(true, true, "violet"));
    }
    /** Commits the competing operation after the authority snapshot but immediately before its real Mongo CAS. */
    private static void beforeWrite(MongoTemplate mongo, String setField, Runnable competingWrite) {
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            var update = invocation.<org.springframework.data.mongodb.core.query.UpdateDefinition>getArgument(1).getUpdateObject();
            var set = update.get("$set", Document.class);
            if (set != null && set.containsKey(setField) && once.compareAndSet(false, true)) competingWrite.run();
            return invocation.callRealMethod();
        }).when(mongo).findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Document.class), eq("user_notification_preferences"));
    }
    @Test void allRoleCatalogMigrationPreservesOldSplitOffMuteAndRejectsStale21Save() {
        seedPreferences(11, 101, true); when(binding.telegramId(11)).thenReturn(101L);
        var initial = preferences.get(11);
        var types = new Document();
        initial.types().forEach((id, value) -> {
            if (NotificationCatalog.legacyTypeIds().contains(id)) types.put(id,
                    new Document("appPush", !id.equals("LESSON_CANCELLED")).append("telegram", true).append("colorId", "blue"));
        });
        mongo.updateFirst(new Query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(11L)),
                new org.springframework.data.mongodb.core.query.Update().set("v2.types", types).set("v2.splitChannels", true), "user_notification_preferences");
        var migrated = preferences.get(11);
        assertThat(migrated.types()).hasSize(32);
        assertThat(migrated.revision()).isNotEqualTo(initial.revision());
        assertThat(migrated.types().get("LESSON_CANCELLED")).isEqualTo(new NotificationPreferencesV2Dto.TypePreference(false, true, "blue"));
        assertThat(migrated.types().get("LESSON_TRANSFERRED")).isEqualTo(new NotificationPreferencesV2Dto.TypePreference(false, true, "violet"));
        assertThat(migrated.mutedUntil()).isEqualTo(initial.mutedUntil());
        var stale = new LinkedHashMap<String, NotificationPreferencesV2Dto.TypePreference>();
        migrated.types().forEach((id, value) -> { if (NotificationCatalog.legacyTypeIds().contains(id)) stale.put(id, value); });
        assertThatThrownBy(() -> preferences.update(11, new NotificationPreferencesV2Dto.Update(initial.revision(), true, stale)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(preferences.get(11)).isEqualTo(migrated);
        verifyNoInteractions(redis);
    }

    @Test void allRoleCatalogAdoptionCasNeverOverwritesConcurrentExplicitOff() {
        seedPreferences(11, 101, true); when(binding.telegramId(11)).thenReturn(101L);
        var initial = preferences.get(11);
        Document record = mongo.findById(11L, Document.class, "user_notification_preferences");
        Document oldTypes = record.get("v2", Document.class).get("types", Document.class);
        oldTypes.keySet().removeIf(id -> !NotificationCatalog.legacyTypeIds().contains(id));
        mongo.save(record, "user_notification_preferences");
        mongo = spy(mongo);
        preferences = new NotificationPreferencesV2Service(new NotificationPreferencesStore(mongo, redis), binding, Clock.fixed(NOW, ZoneOffset.UTC));
        beforeWrite(mongo, "v2.types", () -> {
            var patch = new org.springframework.data.mongodb.core.query.Update().set("v2.revision", "concurrent-off")
                    .set("v2.types.LESSON_CANCELLED.appPush", false).set("v2.types.LESSON_CANCELLED.telegram", false);
            mongo.updateFirst(new Query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(11L)), patch, "user_notification_preferences");
        });
        assertThatThrownBy(() -> preferences.get(11)).isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        var migrated = preferences.get(11);
        assertThat(migrated.types().get("LESSON_TRANSFERRED").appPush()).isFalse();
        assertThat(migrated.types().get("LESSON_TRANSFERRED").telegram()).isFalse();
        assertThat(migrated.types().get("LESSON_CANCELLED").appPush()).isFalse();
        assertThat(migrated.revision()).isNotEqualTo(initial.revision());
    }

    @Test void allRoleSnapshotAndRoleUnionRemainImmutableOnReplayWithoutBumpingReadOrSentAt() {
        deliveryCollections();
        var client = mock(NotificationAudienceClient.class);
        var audience = new NotificationAudienceService(mongo, client, mock(ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService.class));
        var event = NotificationAudienceAdmissionTest.envelope("lesson.cancelled", Map.of("lesson_id", 51L, "group_id", 7L));
        when(client.resolve(event)).thenReturn(Map.of(11L, Set.of("STUDENT", "HEADMAN"), 12L, Set.of("TEACHER")));
        var snapshot = audience.snapshot(event);
        when(client.resolve(event)).thenReturn(Map.of(11L, Set.of("STUDENT"), 13L, Set.of("TEACHER")));
        assertThat(audience.snapshot(event)).isEqualTo(snapshot);
        assertThat(audience.current(event, snapshot)).containsOnlyKeys(11L).containsEntry(11L, Set.of("STUDENT"));
        var writer = new NotificationRoleHistoryWriter(mongo);
        String eventId = (String) event.get("event_id");
        writer.persist(eventId, 11, NotificationType.LESSON_CANCELLED, Map.of("lesson_id", 51L), null, Set.of("STUDENT"), SENT);
        var saved = mongo.findOne(new Query(org.springframework.data.mongodb.core.query.Criteria.where("event_id").is(eventId)), NotificationHistoryDocument.class);
        inbox.readState(context(11L, "STUDENT"), saved.getId(), new NotificationInboxDto.ReadState(true, "0"));
        writer.persist(eventId, 11, NotificationType.LESSON_CANCELLED, Map.of("lesson_id", 999L), null, Set.of("STUDENT", "HEADMAN"), NOW);
        var replay = mongo.findById(saved.getId(), NotificationHistoryDocument.class);
        assertThat(replay.getPayload()).containsEntry("lesson_id", 51L);
        assertThat(replay.getSentAt()).isEqualTo(SENT);
        assertThat(replay.getRoleViews().get("STUDENT").getRevision()).isEqualTo(1);
        assertThat(replay.getRoleViews().get("HEADMAN").getOrderAt()).isEqualTo(SENT);
        assertThat(mongo.count(new Query(), NotificationHistoryDocument.class)).isEqualTo(1);
    }

    @Test void allRoleIntentClaimRetriesAfterOutageAndProviderLeaseCannotBeReplayedByAnotherWorker() throws Exception {
        deliveryCollections();
        var clock = new DeliveryClock(NOW);
        var intents = new ru.rutcampustrack.notification.push.NotificationPushIntentStore(mongo, clock);
        var event = NotificationAudienceAdmissionTest.envelope("semester.activated", Map.of("semester_id", 3L));
        intents.enqueue(event, Map.of(12L, Set.of("ADMIN")));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<Document>>of(intents::claim, intents::claim));
            var claimed = results.stream().map(result -> { try { return result.get(); } catch (Exception error) { throw new RuntimeException(error); } }).filter(Objects::nonNull).toList();
            assertThat(claimed).hasSize(1);
            var first = claimed.getFirst();
            intents.retry(first, "AuthorityUnavailable");
            assertThat(intents.claim()).isNull();
            clock.instant = NOW.plusSeconds(6);
            var second = intents.claim();
            assertThat(second.getInteger("attempts")).isEqualTo(2);
            assertThat(intents.startProvider(first, "12:sub")).isFalse();
            assertThat(intents.startProvider(second, "12:sub")).isTrue();
            clock.instant = NOW.plusSeconds(127);
            assertThat(intents.claim()).isNull();
            assertThat(mongo.findById(event.get("event_id"), Document.class, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION).getString("state"))
                    .isEqualTo("UNCERTAIN");
        }
    }

    @Test void allRoleDispatcherUsesUserIdentityAndRechecksAuthorityBeforeProviderAfterDurableRetry() throws Exception {
        deliveryCollections();
        var clock = new DeliveryClock(NOW);
        var intents = new ru.rutcampustrack.notification.push.NotificationPushIntentStore(mongo, clock);
        var event = NotificationAudienceAdmissionTest.envelope("lesson.cancelled", Map.of("lesson_id", 51L, "group_id", 7L));
        intents.enqueue(event, Map.of(12L, Set.of("TEACHER"), 13L, Set.of("STUDENT")));
        var client = mock(NotificationAudienceClient.class);
        var audience = new NotificationAudienceService(mongo, client, mock(ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService.class));
        var prefs = mock(NotificationPreferencesService.class);
        var subs = mock(ru.rutcampustrack.notification.push.PushSubscriptionRepository.class);
        var provider = mock(ru.rutcampustrack.notification.push.WebPushDeliveryService.class);
        var teacher = ru.rutcampustrack.notification.push.PushSubscriptionDocument.builder().id("teacher-sub").userId(12L).groupId(null).build();
        var revoked = ru.rutcampustrack.notification.push.PushSubscriptionDocument.builder().id("revoked-sub").userId(13L).groupId(7L).headman(true).build();
        when(subs.findAllByUserIdIn(any())).thenReturn(List.of(teacher, revoked));
        when(prefs.isEnabledForUser(anyLong(), eq("lesson.cancelled"), anyMap())).thenReturn(true);
        when(client.resolve(anyMap())).thenThrow(new IllegalStateException("synthetic authority unavailable"));
        var dispatcher = new ru.rutcampustrack.notification.push.NotificationPushIntentDispatcher(intents, audience, prefs, subs, provider, new NotificationCatalog());
        dispatcher.dispatchOne();
        verifyNoInteractions(provider);
        assertThat(mongo.findById(event.get("event_id"), Document.class, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION).getString("state")).isEqualTo("PENDING");
        clock.instant = NOW.plusSeconds(6);
        when(client.resolve(anyMap())).thenReturn(Map.of(12L, Set.of("TEACHER"), 13L, Set.of("HEADMAN")));
        when(provider.sendToSubscription(eq(teacher), eq("lesson.cancelled"), any())).thenReturn(201);
        dispatcher.dispatchOne();
        verify(provider).sendToSubscription(eq(teacher), eq("lesson.cancelled"), argThat(item -> item.action().role().equals("TEACHER")));
        verify(provider, never()).sendToSubscription(eq(revoked), anyString(), any());
        verify(subs, never()).findAllByGroupId(anyLong());
        assertThat(mongo.findById(event.get("event_id"), Document.class, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION).getString("state")).isEqualTo("COMPLETED");
    }

    @Test void allRoleClaimHistoryAndIntentRollbackTogetherAndReplayCommitsOneIntent() throws Exception {
        deliveryCollections();
        var client = mock(NotificationAudienceClient.class);
        var audience = new NotificationAudienceService(mongo, client, mock(ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService.class));
        var event = NotificationAudienceAdmissionTest.envelope("semester.activated", Map.of("semester_id", 3L));
        when(client.resolve(event)).thenReturn(Map.of(12L, Set.of("ADMIN")));
        var prefs = mock(NotificationPreferencesService.class);
        when(prefs.isEnabledForUser(eq(12L), anyString(), anyMap())).thenThrow(new IllegalStateException("synthetic prefs outage"));
        var guard = new ru.rutcampustrack.shared.events.IdempotencyGuard(new ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore(mongo));
        var history = new NotificationHistoryEventProcessor(mock(NotificationHistoryRepository.class), mock(NotificationHistoryService.class),
                guard, mock(AcademicGroupMemberClient.class), mock(org.springframework.messaging.simp.SimpMessagingTemplate.class), null,
                audience, new NotificationRoleHistoryWriter(mongo));
        var push = mock(ru.rutcampustrack.notification.push.WebPushDeliveryService.class);
        when(push.shouldPush("semester.activated")).thenReturn(true);
        var consumer = new ru.rutcampustrack.notification.event.EventConsumer(mock(org.springframework.messaging.simp.SimpMessagingTemplate.class), push,
                guard, null, audience, history, new ru.rutcampustrack.notification.push.NotificationPushIntentStore(mongo, Clock.fixed(NOW, ZoneOffset.UTC)), prefs);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.data.mongodb.MongoTransactionManager(mongo.getMongoDatabaseFactory()));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> consumer.onEvent(event))).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.count(new Query(), NotificationHistoryDocument.class)).isZero();
        assertThat(mongo.getCollection(NotificationAudienceService.COLLECTION).countDocuments()).isZero();
        assertThat(mongo.getCollection(ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION).countDocuments()).isZero();
        reset(prefs); when(prefs.isEnabledForUser(eq(12L), anyString(), anyMap())).thenReturn(true);
        transactions.executeWithoutResult(status -> consumer.onEvent(event));
        transactions.executeWithoutResult(status -> consumer.onEvent(event));
        assertThat(mongo.count(new Query(), NotificationHistoryDocument.class)).isEqualTo(1);
        assertThat(mongo.getCollection(ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION).countDocuments()).isEqualTo(1);
        verify(push, never()).sendToSubscription(any(), anyString(), any());
    }
    private void deliveryCollections() {
        new NotificationDeliveryMongoConfig(mongo, 30).indexes();
        for (String collection : List.of(NotificationAudienceService.COLLECTION, ru.rutcampustrack.notification.push.NotificationPushIntentStore.COLLECTION,
                ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore.DEFAULT_COLLECTION)) {
            if (!mongo.collectionExists(collection)) mongo.createCollection(collection);
            mongo.remove(new Query(), collection);
        }
        new ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore(mongo).ensureIndexes();
        mongo.indexOps(NotificationHistoryDocument.class).ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                .on("event_id", org.springframework.data.domain.Sort.Direction.ASC).on("user_id", org.springframework.data.domain.Sort.Direction.ASC).unique());
    }
    private static class DeliveryClock extends Clock {
        Instant instant;
        DeliveryClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
    private void seedPreferences(long user, long telegram, boolean global) {
        Map<String, Boolean> flags = Map.of("lessons", true, "reminders", true, "homework", true, "tickets", true, "schedule", true, "group", true);
        mongo.insert(new Document("_id", user).append("categories", flags).append("mutedUntil", Date.from(NOW.plusSeconds(600))), "user_notification_preferences");
        mongo.insert(new Document("_id", user + ":" + telegram).append("userId", user).append("telegramId", telegram)
                .append("globalEnabled", global).append("categories", flags), "telegram_notification_preferences");
    }
    private static RequestContext context(long user, String role) {
        var context = new RequestContext(); context.setUserId(user); context.setSelectedRole(role);
        context.setInternalToken("synthetic-validated-token"); context.setSessionId(UUID.randomUUID());
        context.setSessionVersion(1); context.setRolesVersion(1); context.setReadOnly(true);
        return context;
    }
    private static NotificationHistoryDocument row(long user, String event, Set<String> roles, Instant sent) {
        return NotificationHistoryDocument.builder().userId(user).eventId(event).type(NotificationType.LESSON_STARTED)
                .payload(Map.of("lesson_id", 17L)).sentAt(sent)
                .roleViews(roles.stream().collect(java.util.stream.Collectors.toMap(role -> role,
                        role -> NotificationHistoryDocument.RoleView.builder().orderAt(sent).revision(0).build()))).build();
    }
}
