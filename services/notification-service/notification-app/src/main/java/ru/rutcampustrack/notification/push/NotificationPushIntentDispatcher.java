package ru.rutcampustrack.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.notification.history.*;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;

import java.time.Instant;
import java.util.*;

/** A pending job survives authority outages. Current grants/preferences are checked before any provider attempt. */
@Service
@Slf4j
public class NotificationPushIntentDispatcher {
    private final NotificationPushIntentStore store;
    private final NotificationAudienceService audience;
    private final NotificationPreferencesService preferences;
    private final PushSubscriptionRepository subscriptions;
    private final WebPushDeliveryService delivery;
    private final NotificationCatalog catalog;
    public NotificationPushIntentDispatcher(NotificationPushIntentStore store, NotificationAudienceService audience,
            NotificationPreferencesService preferences, PushSubscriptionRepository subscriptions,
            WebPushDeliveryService delivery, NotificationCatalog catalog) {
        this.store = store; this.audience = audience; this.preferences = preferences;
        this.subscriptions = subscriptions; this.delivery = delivery; this.catalog = catalog;
    }
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        try { dispatchOne(); }
        catch (RuntimeException error) { log.warn("WebPush durable dispatch deferred error={}", error.getClass().getSimpleName()); }
    }
    public void dispatchOne() {
        Document job = store.claim();
        if (job == null) return;
        boolean providerAttempt = false;
        try {
            Map<String, Object> envelope = job.get("context", Document.class);
            Map<String, Object> payload = NotificationAudienceClient.payload(envelope);
            String eventType = (String) envelope.get("event_type");
            var type = NotificationCatalog.eventType(eventType, payload).orElseThrow();
            Map<Long, Set<String>> original = NotificationAudienceService.decode(job);
            Set<String> delivered = new HashSet<>(job.getList("delivered", String.class));
            for (PushSubscriptionDocument sub : subscriptions.findAllByUserIdIn(original.keySet())) {
                if (sub.getUserId() == null || sub.getId() == null || !original.containsKey(sub.getUserId()))
                    throw new IllegalStateException("Delivery subscription recipient is invalid");
                String key = sub.getUserId() + ":" + sub.getId();
                if (delivered.contains(key)) continue;
                if (!preferences.isEnabledForUser(sub.getUserId(), eventType, payload)) continue;
                Map<Long, Set<String>> current = audience.current(envelope, original);
                if (!current.containsKey(sub.getUserId())) continue;
                // The stored is_headman/group flag is never an authority for role selection.
                Set<String> roles = current.get(sub.getUserId());
                String role = List.of("STUDENT", "HEADMAN", "TEACHER", "ADMIN").stream().filter(roles::contains).findFirst().orElseThrow();
                Map<String, Object> projection = NotificationPayloadSnapshot.forRecipient(eventType, payload, sub.getUserId());
                Instant sentAt = job.getDate("created_at").toInstant();
                var document = NotificationHistoryDocument.builder().id("external").type(type).payload(projection).sentAt(sentAt)
                        .roleViews(Map.of(role, NotificationHistoryDocument.RoleView.builder().orderAt(sentAt).revision(0).build())).build();
                if (!store.startProvider(job, key)) return;
                providerAttempt = true;
                int status = delivery.sendToSubscription(sub, eventType, catalog.present(document, role));
                if ((status >= 200 && status < 300) || status == 404 || status == 410) {
                    store.acknowledge(job, key);
                    delivered.add(key);
                    providerAttempt = false;
                } else {
                    // An explicit rejected response is safe to retry; transport ambiguity is retained below.
                    store.retry(job, "ProviderRejected" + status);
                    return;
                }
            }
            store.complete(job);
        } catch (Exception error) {
            if (providerAttempt) store.uncertain(job, error.getClass().getSimpleName());
            else store.retry(job, error.getClass().getSimpleName());
        }
    }
}
