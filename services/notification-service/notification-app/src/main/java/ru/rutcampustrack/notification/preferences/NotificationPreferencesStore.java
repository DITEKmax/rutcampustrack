package ru.rutcampustrack.notification.preferences;

import org.bson.Document;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mongo authority. Redis is consulted only when a durable record has never existed. */
@Repository
public class NotificationPreferencesStore {
    static final String USERS = "user_notification_preferences";
    static final String TELEGRAM = "telegram_notification_preferences";
    static final String LEGACY_OWNERS = "notification_preference_legacy_bindings";
    static final String RECEIPTS = "notification_preference_mutation_receipts";
    static final List<String> CATEGORIES = List.of("lessons", "reminders", "homework", "tickets", "schedule", "group");

    private final MongoTemplate mongo;
    private final StringRedisTemplate redis;

    public NotificationPreferencesStore(MongoTemplate mongo, StringRedisTemplate redis) {
        this.mongo = mongo;
        this.redis = redis;
    }

    public NotificationPreferencesDto user(long userId) {
        positive(userId);
        Document existing = mongo.findById(userId, Document.class, USERS);
        if (existing == null) {
            Map<Object, Object> legacy;
            try {
                legacy = redis.opsForHash().entries("notif:prefs:user:" + userId);
            } catch (RuntimeException error) {
                throw unavailable("Legacy user preferences are unavailable", error);
            }
            Map<String, Boolean> categories = legacyCategories(legacy);
            Instant mute = legacyInstant(legacy.get("mute_until"), false);
            Update initial = new Update().setOnInsert("categories", categories);
            if (mute != null) initial.setOnInsert("mutedUntil", Date.from(mute));
            mongo.upsert(id(userId), initial, USERS);
            existing = mongo.findById(userId, Document.class, USERS);
        }
        return userDto(existing);
    }

    public NotificationPreferencesDto patchUser(long userId, UpdateNotificationPreferencesRequest request,
                                               Instant now) {
        user(userId);
        Update patch = new Update();
        if (request.categories() != null) {
            for (String category : CATEGORIES) {
                Boolean enabled = request.categories().get(category);
                if (enabled != null) patch.set("categories." + category, enabled);
            }
        }
        if (request.mutedUntil() != null && request.mutedUntil().isAfter(now)) {
            patch.set("mutedUntil", Date.from(request.mutedUntil()));
        } else {
            patch.unset("mutedUntil");
        }
        return userDto(mongo.findAndModify(id(userId), patch,
                FindAndModifyOptions.options().returnNew(true), Document.class, USERS));
    }

    /** Caller validates the live binding; facet + legacy ownership insertion belongs to its transaction. */
    public Document telegram(long userId, long telegramId) {
        positive(userId);
        positive(telegramId);
        String key = facetKey(userId, telegramId);
        Document existing = mongo.findById(key, Document.class, TELEGRAM);
        if (existing != null) {
            validateFacet(existing, userId, telegramId);
            return existing;
        }
        String global;
        String mute;
        Map<Object, Object> categories;
        try {
            global = redis.opsForValue().get("bot:notif:" + telegramId);
            categories = redis.opsForHash().entries("bot:notif:cat:" + telegramId);
            mute = redis.opsForValue().get("bot:notif:mute:" + telegramId);
        } catch (RuntimeException error) {
            throw unavailable("Legacy Telegram preferences are unavailable", error);
        }
        boolean globalEnabled = legacyEnabled(global);
        Map<String, Boolean> flags = legacyCategories(categories);
        Instant mutedUntil = legacyInstant(mute, true);

        mongo.upsert(id(telegramId), new Update().setOnInsert("userId", userId), LEGACY_OWNERS);
        Document owner = mongo.findById(telegramId, Document.class, LEGACY_OWNERS);
        if (owner == null || !(owner.get("userId") instanceof Number ownerId) || ownerId.longValue() <= 0) {
            throw unavailable("Legacy Telegram ownership is invalid", null);
        }
        if (ownerId.longValue() != userId) {
            // This Telegram-only source already belonged to another account. Never copy it to a new facet.
            globalEnabled = true;
            flags = defaults();
            mutedUntil = null;
        }
        Update initial = new Update().setOnInsert("userId", userId).setOnInsert("telegramId", telegramId)
                .setOnInsert("globalEnabled", globalEnabled).setOnInsert("categories", flags);
        if (mutedUntil != null) initial.setOnInsert("mutedUntil", Date.from(mutedUntil));
        mongo.upsert(id(key), initial, TELEGRAM);
        existing = mongo.findById(key, Document.class, TELEGRAM);
        validateFacet(existing, userId, telegramId);
        return existing;
    }

    public Document patchTelegram(long userId, long telegramId, Update patch, String compareField,
                                  Boolean expected) {
        Query query = id(facetKey(userId, telegramId));
        if (compareField != null) query.addCriteria(Criteria.where(compareField).is(expected));
        Document result = mongo.findAndModify(query, patch,
                FindAndModifyOptions.options().returnNew(true), Document.class, TELEGRAM);
        if (result == null) throw unavailable("Concurrent preference mutation must be retried", null);
        validateFacet(result, userId, telegramId);
        return result;
    }

    public Document receipt(String key) { return mongo.findById(key, Document.class, RECEIPTS); }
    public void insertReceipt(Document receipt) { mongo.insert(receipt, RECEIPTS); }

    static NotificationPreferencesDto userDto(Document document) {
        if (document == null) throw unavailable("Durable preferences are unavailable", null);
        return new NotificationPreferencesDto(durableCategories(document.get("categories")), instant(document.get("mutedUntil")));
    }

    static Map<String, Boolean> durableCategories(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) throw unavailable("Durable preferences are invalid", null);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            if (!(values.get(category) instanceof Boolean enabled)) {
                throw unavailable("Durable preference category is invalid", null);
            }
            result.put(category, enabled);
        }
        return Map.copyOf(result);
    }

    static boolean globalEnabled(Document facet) {
        if (!(facet.get("globalEnabled") instanceof Boolean enabled)) {
            throw unavailable("Durable Telegram global preference is invalid", null);
        }
        return enabled;
    }

    static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        throw unavailable("Durable mute is invalid", null);
    }

    private static void validateFacet(Document facet, long userId, long telegramId) {
        if (facet == null || !(facet.get("userId") instanceof Number uid) || uid.longValue() != userId
                || !(facet.get("telegramId") instanceof Number tid) || tid.longValue() != telegramId) {
            throw unavailable("Durable Telegram preference identity is invalid", null);
        }
        globalEnabled(facet);
        durableCategories(facet.get("categories"));
        instant(facet.get("mutedUntil"));
    }

    private static Map<String, Boolean> legacyCategories(Map<Object, Object> values) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String category : CATEGORIES) flags.put(category, legacyEnabled(values.get(category)));
        return flags;
    }

    private static boolean legacyEnabled(Object value) {
        if (value == null || "on".equals(value)) return true;
        if ("off".equals(value)) return false;
        throw unavailable("Legacy preference value is invalid", null);
    }

    private static Instant legacyInstant(Object value, boolean allowEpoch) {
        if (value == null) return null;
        if (!(value instanceof String text) || text.isBlank()) throw unavailable("Legacy mute is invalid", null);
        try {
            return Instant.parse(text);
        } catch (RuntimeException error) {
            if (allowEpoch) {
                try {
                    double epoch = Double.parseDouble(text);
                    if (Double.isFinite(epoch)) return Instant.ofEpochMilli((long) (epoch * 1000));
                } catch (RuntimeException ignored) { /* reject malformed legacy state below */ }
            }
            throw unavailable("Legacy mute is invalid", null);
        }
    }

    static Map<String, Boolean> defaults() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        CATEGORIES.forEach(category -> values.put(category, true));
        return values;
    }
    static String facetKey(long userId, long telegramId) { return userId + ":" + telegramId; }
    private static Query id(Object id) { return Query.query(Criteria.where("_id").is(id)); }
    private static void positive(long id) { if (id <= 0) throw new IllegalArgumentException("Positive user identity is required"); }
    static TransientDataAccessResourceException unavailable(String message, Throwable cause) {
        return new TransientDataAccessResourceException(message, cause);
    }
}
