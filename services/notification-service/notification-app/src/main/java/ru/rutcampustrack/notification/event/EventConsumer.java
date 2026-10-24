package ru.rutcampustrack.notification.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.notification.history.*;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.push.NotificationPushIntentStore;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.util.*;

/**
 * The event claim, immutable audience, history and external intent commit together; no provider
 * runs here.
 */
@Component
public class EventConsumer extends AbstractEventConsumer {
    public static final String CONSUMER_ID = "notification-web";
    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushDeliveryService webPushDeliveryService;
    private final IdempotencyGuard idempotencyGuard;
    private final ReminderAttendanceStateService reminderState;
    private final NotificationAudienceService audience;
    private final NotificationHistoryEventProcessor history;
    private final NotificationPushIntentStore intents;
    private final NotificationPreferencesService preferences;

    @Autowired
    public EventConsumer(
            SimpMessagingTemplate messagingTemplate,
            WebPushDeliveryService webPushDeliveryService,
            IdempotencyGuard idempotencyGuard,
            ReminderAttendanceStateService reminderState,
            NotificationAudienceService audience,
            NotificationHistoryEventProcessor history,
            NotificationPushIntentStore intents,
            NotificationPreferencesService preferences) {
        this.messagingTemplate = messagingTemplate;
        this.webPushDeliveryService = webPushDeliveryService;
        this.idempotencyGuard = idempotencyGuard;
        this.reminderState = reminderState;
        this.audience = audience;
        this.history = history;
        this.intents = intents;
        this.preferences = preferences;
    }

    public EventConsumer(
            SimpMessagingTemplate messagingTemplate,
            WebPushDeliveryService webPushDeliveryService,
            IdempotencyGuard idempotencyGuard,
            ReminderAttendanceStateService reminderState) {
        this(
                messagingTemplate,
                webPushDeliveryService,
                idempotencyGuard,
                reminderState,
                null,
                null,
                null,
                null);
    }

    public EventConsumer(
            SimpMessagingTemplate messagingTemplate,
            WebPushDeliveryService webPushDeliveryService,
            IdempotencyGuard idempotencyGuard) {
        this(messagingTemplate, webPushDeliveryService, idempotencyGuard, null);
    }

    @EventIdempotent(consumer = CONSUMER_ID)
    @org.springframework.transaction.annotation.Transactional
    public void onEvent(Map<String, Object> envelope) {
        if ("group.updated".equals(envelope.get("event_type"))) {
            groupUpdated(envelope);
            return;
        }
        if ("dashboard.invalidated".equals(envelope.get("event_type"))) {
            if (!("academic-service".equals(envelope.get("source"))
                            || "attendance-service".equals(envelope.get("source")))
                    || !Integer.valueOf(1).equals(envelope.get("event_version"))
                    || !(envelope.get("payload") instanceof Map<?, ?> payload)
                    || !payload.isEmpty()) return;
            if (idempotencyGuard.tryClaim(CONSUMER_ID, envelope))
                messagingTemplate.convertAndSend(
                        "/topic/admin/dashboard",
                        Map.of("type", "dashboard.invalidated", "payload", Map.of()));
            return;
        }
        if (!(envelope.get("event_type") instanceof String eventType))
            throw new IllegalArgumentException("Event type is required");
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) return;
        withTraceContext(
                envelope,
                () -> {
                    Map<String, Object> payload = NotificationAudienceClient.payload(envelope);
                    updateReminderState(eventType, payload);
                    if (NotificationCatalog.eventType(eventType, payload).isEmpty()) return;
                    if (audience == null
                            || history == null
                            || intents == null
                            || preferences == null)
                        throw new IllegalStateException(
                                "Notification delivery authority is unavailable");
                    Map<Long, Set<String>> recipients = audience.snapshot(envelope);
                    history.persistResolved(envelope, recipients);
                    if (!webPushDeliveryService.shouldPush(eventType)) return;
                    Map<Long, Set<String>> admitted = new LinkedHashMap<>();
                    Long actor =
                            payload.get("actor_user_id") == null
                                    ? null
                                    : NotificationAudienceClient.positive(
                                            payload.get("actor_user_id"));
                    Long owner =
                            payload.get("user_id") == null
                                    ? null
                                    : NotificationAudienceClient.positive(payload.get("user_id"));
                    recipients.forEach(
                            (id, roles) -> {
                                // Submission history is private; only privileged recipients get an
                                // external request task.
                                if ((eventType.equals("excuse.requested")
                                                || eventType.equals("late_checkin.requested"))
                                        && id.equals(owner)) return;
                                if (id.equals(actor)
                                        && !Set.of(
                                                        "semester.operation.completed",
                                                        "lesson.transferred",
                                                        "lesson.updated")
                                                .contains(eventType)) return;
                                if (preferences.isEnabledForUser(id, eventType, payload))
                                    admitted.put(id, roles);
                            });
                    intents.enqueue(envelope, admitted);
                });
    }

    private void groupUpdated(Map<String, Object> envelope) {
        if (!"academic-service".equals(envelope.get("source"))
                || !Integer.valueOf(1).equals(envelope.get("event_version"))
                || !(envelope.get("payload") instanceof Map<?, ?> payload)
                || !(payload.get("affected_user_ids") instanceof List<?> rawRecipients)) return;
        long groupId = NotificationAudienceClient.positive(payload.get("group_id"));
        Set<Long> recipients = new LinkedHashSet<>();
        for (Object raw : rawRecipients) recipients.add(NotificationAudienceClient.positive(raw));
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) return;
        // Hidden hints share the existing private topic; no history, push, or live audience lookup.
        Runnable publish =
                () ->
                        recipients.forEach(
                                userId ->
                                        messagingTemplate.convertAndSend(
                                                "/topic/user/" + userId,
                                                Map.of(
                                                        "type",
                                                        "group.updated",
                                                        "payload",
                                                        Map.of(
                                                                "group_id",
                                                                Long.toString(groupId)))));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish.run();
                    }
                });
    }

    private void updateReminderState(String eventType, Map<String, Object> payload) {
        if (reminderState == null) return;
        if ("attendance.marked".equals(eventType)) reminderState.recordMarked(payload);
        else if (Set.of("lesson.closed", "lesson.cancelled", "lesson.deleted", "lesson.transferred")
                .contains(eventType)) {
            if (payload.get("source") instanceof Map<?, ?> source
                    && source.get("lesson_id") != null)
                reminderState.deleteLessonState(Map.of("lesson_id", source.get("lesson_id")));
            else reminderState.deleteLessonState(payload);
        }
    }
}
