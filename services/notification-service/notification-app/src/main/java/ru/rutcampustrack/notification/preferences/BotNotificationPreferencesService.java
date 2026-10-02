package ru.rutcampustrack.notification.preferences;

import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.notification.contract.dto.preferences.BotNotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateBotNotificationPreferencesRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

@Service
public class BotNotificationPreferencesService {
    private final NotificationPreferencesStore store;
    private final AcademicTelegramBindingClient binding;
    private final Clock clock;

    public BotNotificationPreferencesService(NotificationPreferencesStore store, AcademicTelegramBindingClient binding, Clock clock) {
        this.store = store;
        this.binding = binding;
        this.clock = clock;
    }

    @Transactional
    public BotNotificationPreferencesDto get(long userId, long telegramId, String category) {
        category(category, true);
        binding.validate(userId, telegramId);
        return snapshot(userId, telegramId, store.telegram(userId, telegramId), store.user(userId), category);
    }

    @Transactional
    public BotNotificationPreferencesDto update(long userId, long telegramId, UpdateBotNotificationPreferencesRequest request) {
        validate(request);
        binding.validate(userId, telegramId);
        String receiptKey = NotificationPreferencesStore.facetKey(userId, telegramId) + ":" + request.requestKey();
        String fingerprint = fingerprint(request);
        Document receipt = store.receipt(receiptKey);
        if (receipt != null) {
            if (!fingerprint.equals(receipt.getString("fingerprint"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Preference request identity conflicts");
            }
            return receiptDto(receipt);
        }
        Document facet = store.telegram(userId, telegramId);
        NotificationPreferencesDto canonical = store.user(userId);
        Update patch = new Update();
        String compareField = null;
        Boolean expected = null;
        switch (request.operation()) {
            case TOGGLE_GLOBAL -> {
                compareField = "globalEnabled";
                expected = NotificationPreferencesStore.globalEnabled(facet);
                patch.set(compareField, !expected);
            }
            case TOGGLE_CATEGORY -> {
                compareField = "categories." + request.category();
                expected = NotificationPreferencesStore.durableCategories(facet.get("categories")).get(request.category());
                patch.set(compareField, !expected);
            }
            case SET_GLOBAL -> patch.set("globalEnabled", request.enabled());
            case SET_CATEGORY -> patch.set("categories." + request.category(), request.enabled());
            case SET_MUTE -> patch.set("mutedUntil", Date.from(request.mutedUntil()));
            case MUTE_FOR -> patch.set("mutedUntil", Date.from(Instant.now(clock).plusSeconds(request.durationSeconds())));
            case CLEAR_MUTE -> patch.unset("mutedUntil");
        }
        Document updated = store.patchTelegram(userId, telegramId, patch, compareField, expected);
        BotNotificationPreferencesDto result = snapshot(userId, telegramId, updated, canonical, null);
        Document saved = new Document("_id", receiptKey).append("fingerprint", fingerprint)
                .append("userId", userId).append("telegramId", telegramId)
                .append("globalEnabled", result.globalEnabled()).append("categories", result.categories())
                .append("mutedUntil", date(result.mutedUntil())).append("canonicalCategories", result.canonicalCategories())
                .append("canonicalMutedUntil", date(result.canonicalMutedUntil()));
        store.insertReceipt(saved);
        return result;
    }

    private BotNotificationPreferencesDto snapshot(long userId, long telegramId, Document facet,
                                                   NotificationPreferencesDto canonical, String category) {
        boolean global = NotificationPreferencesStore.globalEnabled(facet);
        var flags = NotificationPreferencesStore.durableCategories(facet.get("categories"));
        Instant mute = active(NotificationPreferencesStore.instant(facet.get("mutedUntil")));
        Instant canonicalMute = active(canonical.mutedUntil());
        Boolean eligible = category == null ? null : global && flags.get(category)
                && canonical.categories().get(category) && mute == null && canonicalMute == null;
        return new BotNotificationPreferencesDto(userId, telegramId, global, flags, mute,
                canonical.categories(), canonicalMute, eligible);
    }

    private static BotNotificationPreferencesDto receiptDto(Document saved) {
        return new BotNotificationPreferencesDto(((Number) saved.get("userId")).longValue(),
                ((Number) saved.get("telegramId")).longValue(), NotificationPreferencesStore.globalEnabled(saved),
                NotificationPreferencesStore.durableCategories(saved.get("categories")),
                NotificationPreferencesStore.instant(saved.get("mutedUntil")),
                NotificationPreferencesStore.durableCategories(saved.get("canonicalCategories")),
                NotificationPreferencesStore.instant(saved.get("canonicalMutedUntil")), null);
    }

    private Instant active(Instant mute) { return mute != null && mute.isAfter(Instant.now(clock)) ? mute : null; }
    private static Date date(Instant instant) { return instant == null ? null : Date.from(instant); }

    private static void category(String category, boolean optional) {
        if (!(optional && category == null) && (category == null || !NotificationPreferencesStore.CATEGORIES.contains(category))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown notification category");
        }
    }

    private static void validate(UpdateBotNotificationPreferencesRequest request) {
        if (request == null || request.operation() == null || request.requestKey() == null
                || !request.requestKey().matches("[A-Za-z0-9_/:\\-]{1,128}")) badCommand();
        boolean hasCategory = switch (request.operation()) { case TOGGLE_CATEGORY, SET_CATEGORY -> true; default -> false; };
        boolean hasEnabled = switch (request.operation()) { case SET_GLOBAL, SET_CATEGORY -> true; default -> false; };
        boolean hasMute = request.operation() == UpdateBotNotificationPreferencesRequest.Operation.SET_MUTE;
        if (hasCategory) category(request.category(), false);
        boolean hasDuration = request.operation() == UpdateBotNotificationPreferencesRequest.Operation.MUTE_FOR;
        if (hasDuration && (request.durationSeconds() == null
                || (request.durationSeconds() != 86400 && request.durationSeconds() != 604800))) badCommand();
        if (hasDuration != (request.durationSeconds() != null)) badCommand();
        if ((!hasCategory && request.category() != null) || hasEnabled != (request.enabled() != null)
                || hasMute != (request.mutedUntil() != null)) badCommand();
    }

    private static void badCommand() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference command"); }
    private static String fingerprint(UpdateBotNotificationPreferencesRequest request) {
        String normalized = request.operation().name() + "|" + request.category() + "|" + request.enabled() + "|" + request.mutedUntil() + "|" + request.durationSeconds();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
