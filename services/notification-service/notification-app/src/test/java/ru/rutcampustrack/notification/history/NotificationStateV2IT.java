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
