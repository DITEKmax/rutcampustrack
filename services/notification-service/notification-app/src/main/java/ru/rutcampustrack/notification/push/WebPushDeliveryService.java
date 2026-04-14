package ru.rutcampustrack.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.HttpResponseException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Async Web Push delivery service.
 *
 * Named WebPushDeliveryService (not PushService) to avoid name collision with
 * nl.martijndwars.webpush.PushService bean from WebPushConfig (bean name: webPushService).
 *
 * Per D-07/D-08: Push delivery is @Async and does NOT block the RabbitMQ consumer thread.
 * Per D-10/PUSH-07: HTTP 410 (Gone) from push service auto-deletes expired subscription.
 * Per T-27-08: Bounded thread pool (pushTaskExecutor, max=10) limits concurrent push I/O.
 */
@Service
@Slf4j
public class WebPushDeliveryService {

    private static final Set<String> PUSH_EVENT_TYPES = Set.of(
            "lesson.started", "lesson.cancelled", "homework.published",
            // 58-07 / BUG-006-6: уведомляем студентов о переименовании/архивации группы.
            "group.renamed", "group.archived"
    );

    private final PushSubscriptionRepository repository;
    private final PushService webPushService;
    private final ObjectMapper objectMapper;

    public WebPushDeliveryService(PushSubscriptionRepository repository,
                                   PushService webPushService,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.webPushService = webPushService;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns true if this event type should trigger a Web Push notification.
     */
    public boolean shouldPush(String eventType) {
        return PUSH_EVENT_TYPES.contains(eventType);
    }

    /**
     * Sends Web Push notifications asynchronously to all subscribers in the group.
     * Runs on the pushTaskExecutor thread pool — does not block the caller thread.
     */
    @Async("pushTaskExecutor")
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload) {
        List<PushSubscriptionDocument> subs = repository.findAllByGroupId(groupId);
        if (subs.isEmpty()) {
            log.debug("No push subscriptions for group {}", groupId);
            return CompletableFuture.completedFuture(null);
        }

        String title = buildTitle(eventType, payload);
        String body = buildBody(eventType, payload);
        byte[] payloadBytes = buildPayloadJson(title, body, eventType, payload);

        for (PushSubscriptionDocument sub : subs) {
            try {
                Notification notification = createNotification(sub, payloadBytes);
                webPushService.send(notification);
                log.debug("Push sent to {} for event {}", sub.getEndpoint(), eventType);
            } catch (Exception e) {
                if (isGone(e)) {
                    // D-10: Auto-delete expired subscription on HTTP 410 (PUSH-07)
                    repository.deleteByEndpoint(sub.getEndpoint());
                    log.info("Deleted expired push subscription: {}", sub.getEndpoint());
                } else {
                    // D-08: Log and continue — do not block other subscriptions
                    log.warn("Push failed for {}: {}", sub.getEndpoint(), e.getMessage());
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Creates a Web Push Notification for the given subscription and payload.
     * Protected to allow stubbing in unit tests (avoids real EC key parsing).
     */
    protected Notification createNotification(PushSubscriptionDocument sub, byte[] payloadBytes)
            throws Exception {
        return new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payloadBytes);
    }

    /**
     * Checks if the exception indicates HTTP 410 Gone (subscription expired/unregistered).
     */
    private boolean isGone(Exception e) {
        if (e instanceof HttpResponseException hre) {
            return hre.getStatusCode() == 410;
        }
        if (e.getCause() instanceof HttpResponseException hre) {
            return hre.getStatusCode() == 410;
        }
        return false;
    }

    private String buildTitle(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "lesson.started" -> "Пара началась";
            case "lesson.cancelled" -> "Пара отменена";
            case "homework.published" -> "Новое ДЗ";
            case "group.renamed" -> "Группа переименована";
            case "group.archived" -> "Группа архивирована";
            default -> "Уведомление";
        };
    }

    private String buildBody(String eventType, Map<String, Object> payload) {
        String subjectName = (String) payload.getOrDefault("subject_name", "");
        return switch (eventType) {
            case "lesson.started" -> subjectName + " — отметьтесь!";
            case "lesson.cancelled" -> subjectName + " — пара отменена";
            case "homework.published" -> {
                String title = (String) payload.getOrDefault("title", "");
                yield subjectName + ": " + title;
            }
            case "group.renamed" -> "Ваша группа получила новое название. Откройте приложение для подробностей.";
            case "group.archived" -> "Группа архивирована (выпуск). Поздравляем!";
            default -> "";
        };
    }

    private byte[] buildPayloadJson(String title, String body, String eventType, Map<String, Object> payload) {
        try {
            Map<String, Object> json = Map.of(
                    "title", title,
                    "body", body,
                    "event_type", eventType,
                    "data", payload
            );
            return objectMapper.writeValueAsBytes(json);
        } catch (Exception e) {
            log.error("Failed to serialize push payload", e);
            return "{}".getBytes();
        }
    }
}
