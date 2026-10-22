package ru.rutcampustrack.notification.preferences;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesV2Dto;
import ru.rutcampustrack.notification.history.NotificationCatalog;

/** The account's single Mongo document is also the CAS/revision authority for bot commands. */
@Service
public class NotificationPreferencesV2Service {
    private final NotificationPreferencesStore store;
    private final AcademicTelegramBindingClient binding;
    private final Clock clock;
    private ru.rutcampustrack.notification.history.NotificationProjectionSignals signals;

    public NotificationPreferencesV2Service(
            NotificationPreferencesStore store,
            AcademicTelegramBindingClient binding,
            Clock clock) {
        this.store = store;
        this.binding = binding;
        this.clock = clock;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationPreferencesV2Service(
            NotificationPreferencesStore store,
            AcademicTelegramBindingClient binding,
            Clock clock,
            ru.rutcampustrack.notification.history.NotificationProjectionSignals signals) {
        this(store, binding, clock);
        this.signals = signals;
    }

    @Transactional
    public NotificationPreferencesV2Dto get(long userId) {
        Long telegramId = binding.telegramId(userId);
        return snapshot(ensure(userId, telegramId), telegramId != null);
    }

    public NotificationPreferencesV2Dto update(
            long userId, NotificationPreferencesV2Dto.Update request) {
        validate(request);
        Long telegramId = binding.telegramId(userId);
        Document before = ensure(userId, telegramId);
        NotificationPreferencesV2Dto current = snapshot(before, telegramId != null);
        if (!current.revision().equals(request.expectedRevision()))
            throw new NotificationPreferencesConflict(current);
        Update update =
                new Update()
                        .set("v2.types", encode(request.types()))
                        .set("v2.splitChannels", request.splitChannels())
                        .set("v2.revision", UUID.randomUUID().toString());
        if (request.hasMutedUntil()) {
            if (request.mutedUntil() == null) update.unset("mutedUntil");
            else update.set("mutedUntil", Date.from(request.mutedUntil()));
        }
        Document saved = store.patchV2(userId, current.revision(), update);
        if (saved == null)
            throw new NotificationPreferencesConflict(
                    snapshot(store.durableUser(userId), telegramId != null));
        if (signals != null) signals.changed(userId);
        return snapshot(saved, telegramId != null);
    }

    /**
     * Bot's binding has already been validated; importing choices never trusts a user-supplied
     * facet.
     */
    Document ensure(long userId, Long telegramId) {
        Document user = store.durableUser(userId);
        Document profile = user.get("v2", Document.class);
        Document facet = telegramId == null ? null : store.telegram(userId, telegramId);
        if (profile == null) {
            Map<String, Boolean> app =
                    NotificationPreferencesStore.durableCategories(user.get("categories"));
            Map<String, Boolean> telegram =
                    facet == null
                            ? app
                            : NotificationPreferencesStore.durableCategories(
                                    facet.get("categories"));
            boolean global = facet == null || NotificationPreferencesStore.globalEnabled(facet);
            Map<String, NotificationPreferencesV2Dto.TypePreference> types = new LinkedHashMap<>();
            for (var meaning : NotificationCatalog.meanings()) {
                // Old Telegram used canonical AND own flags. Preserve effective explicit OFF during
                // adoption.
                boolean appEnabled = app.get(meaning.category());
                boolean telegramEnabled = appEnabled && global && telegram.get(meaning.category());
                types.put(
                        meaning.type().name(),
                        new NotificationPreferencesV2Dto.TypePreference(
                                appEnabled, telegramEnabled, "violet"));
            }
            boolean split =
                    types.values().stream().anyMatch(value -> value.appPush() != value.telegram());
            profile =
                    new Document("revision", UUID.randomUUID().toString())
                            .append("splitChannels", split)
                            .append("types", encode(types))
                            .append("telegramImported", facet != null);
            user = store.initializeV2(userId, user, profile);
            profile = user.get("v2", Document.class);
        }
        if (facet != null && !Boolean.TRUE.equals(profile.getBoolean("telegramImported"))) {
            var types = types(profile);
            var oldTelegram =
                    NotificationPreferencesStore.durableCategories(facet.get("categories"));
            boolean global = NotificationPreferencesStore.globalEnabled(facet);
            Map<String, NotificationPreferencesV2Dto.TypePreference> imported =
                    new LinkedHashMap<>();
            for (var meaning : NotificationCatalog.meanings()) {
                var value = types.get(meaning.type().name());
                imported.put(
                        meaning.type().name(),
                        new NotificationPreferencesV2Dto.TypePreference(
                                value.appPush(),
                                value.telegram() && global && oldTelegram.get(meaning.category()),
                                value.colorId()));
            }
            Update adoption =
                    new Update()
                            .set("v2.types", encode(imported))
                            .set("v2.telegramImported", true)
                            .set(
                                    "v2.splitChannels",
                                    imported.values().stream()
                                            .anyMatch(value -> value.appPush() != value.telegram()))
                            .set("v2.revision", UUID.randomUUID().toString());
            user = store.patchV2(userId, profile.getString("revision"), adoption);
            if (user == null)
                throw NotificationPreferencesStore.unavailable(
                        "Concurrent preference adoption must be retried", null);
        }
        return user;
    }

    /**
     * Existing profile controls WebPush. Before adoption, the original category authority remains
     * in force.
     */
    public boolean appEnabled(long userId, String type) {
        if (!NotificationCatalog.typeIds().contains(type))
            throw new IllegalArgumentException("Unsupported notification type");
        Document user = store.durableUser(userId);
        if (active(NotificationPreferencesStore.instant(user.get("mutedUntil"))) != null)
            return false;
        Document profile = user.get("v2", Document.class);
        return profile == null
                ? NotificationPreferencesStore.durableCategories(user.get("categories"))
                        .get(
                                NotificationCatalog.meanings().stream()
                                        .filter(meaning -> meaning.type().name().equals(type))
                                        .findFirst()
                                        .orElseThrow()
                                        .category())
                : types(profile).get(type).appPush();
    }

    public boolean telegramEnabled(long userId, long telegramId, String type) {
        if (!NotificationCatalog.typeIds().contains(type))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported notification type");
        Document user = ensure(userId, telegramId);
        Document facet = store.telegram(userId, telegramId);
        return types(user.get("v2", Document.class)).get(type).telegram()
                && active(NotificationPreferencesStore.instant(user.get("mutedUntil"))) == null
                && active(NotificationPreferencesStore.instant(facet.get("mutedUntil"))) == null;
    }

    /** Called inside the bot command/receipt transaction, after exact binding validation. */
    void applyBotFlags(long userId, long telegramId, String category, boolean enabled) {
        Document user = ensure(userId, telegramId);
        Document profile = user.get("v2", Document.class);
        boolean split = profile.getBoolean("splitChannels");
        Map<String, NotificationPreferencesV2Dto.TypePreference> changed =
                new LinkedHashMap<>(types(profile));
        for (var meaning : NotificationCatalog.meanings()) {
            if (category != null && !meaning.category().equals(category)) continue;
            var old = changed.get(meaning.type().name());
            changed.put(
                    meaning.type().name(),
                    new NotificationPreferencesV2Dto.TypePreference(
                            split ? old.appPush() : enabled, enabled, old.colorId()));
        }
        if (store.patchV2(
                        userId,
                        profile.getString("revision"),
                        new Update()
                                .set("v2.types", encode(changed))
                                .set("v2.revision", UUID.randomUUID().toString()))
                == null) {
            throw NotificationPreferencesStore.unavailable(
                    "Concurrent preference command must be retried", null);
        }
        if (signals != null) signals.changed(userId);
    }

    NotificationPreferencesV2Dto snapshot(Document user, boolean linked) {
        Document profile = user.get("v2", Document.class);
        if (profile == null
                || profile.getString("revision") == null
                || !(profile.get("splitChannels") instanceof Boolean)) {
            throw NotificationPreferencesStore.unavailable(
                    "Durable preference profile is invalid", null);
        }
        return new NotificationPreferencesV2Dto(
                profile.getString("revision"),
                profile.getBoolean("splitChannels"),
                types(profile),
                active(NotificationPreferencesStore.instant(user.get("mutedUntil"))),
                linked);
    }

    private Instant active(Instant value) {
        return value != null && value.isAfter(Instant.now(clock)) ? value : null;
    }

    static Map<String, NotificationPreferencesV2Dto.TypePreference> types(Document profile) {
        return decodeTypes(profile, NotificationCatalog.typeIds());
    }

    static Map<String, NotificationPreferencesV2Dto.TypePreference> decodeTypes(
            Document profile, Set<String> expectedIds) {
        Object raw = profile.get("types");
        if (!(raw instanceof Map<?, ?> values) || !values.keySet().equals(expectedIds)) {
            throw NotificationPreferencesStore.unavailable(
                    "Durable preference types are invalid", null);
        }
        Map<String, NotificationPreferencesV2Dto.TypePreference> result = new LinkedHashMap<>();
        for (var meaning : NotificationCatalog.meanings()) {
            if (!expectedIds.contains(meaning.type().name())) continue;
            Object entry = values.get(meaning.type().name());
            if (!(entry instanceof Map<?, ?> value)
                    || !(value.get("appPush") instanceof Boolean app)
                    || !(value.get("telegram") instanceof Boolean telegram)
                    || !(value.get("colorId") instanceof String color)
                    || !NotificationCatalog.COLORS.contains(color))
                throw NotificationPreferencesStore.unavailable(
                        "Durable type preference is invalid", null);
            result.put(
                    meaning.type().name(),
                    new NotificationPreferencesV2Dto.TypePreference(app, telegram, color));
        }
        return Collections.unmodifiableMap(result);
    }

    static Document encode(Map<String, NotificationPreferencesV2Dto.TypePreference> types) {
        Document result = new Document();
        types.forEach(
                (id, value) ->
                        result.put(
                                id,
                                new Document("appPush", value.appPush())
                                        .append("telegram", value.telegram())
                                        .append("colorId", value.colorId())));
        return result;
    }

    private static void validate(NotificationPreferencesV2Dto.Update request) {
        if (request == null
                || !request.hasSplitChannels()
                || request.splitChannels() == null
                || request.expectedRevision() == null
                || request.expectedRevision().length() > 128
                || request.types() == null
                || !request.types().keySet().equals(NotificationCatalog.typeIds())) bad();
        for (var value : request.types().values()) {
            if (value == null
                    || value.appPush() == null
                    || value.telegram() == null
                    || value.colorId() == null
                    || !NotificationCatalog.COLORS.contains(value.colorId())
                    || (!request.splitChannels() && !value.appPush().equals(value.telegram())))
                bad();
        }
    }

    private static void bad() {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid notification preferences");
    }
}
