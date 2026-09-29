package ru.rutcampustrack.notification.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class EventConsumer extends AbstractEventConsumer {

    public static final String CONSUMER_ID = "notification-web";

    private static final Set<String> HEADMAN_ONLY_EVENTS = Set.of(
            "excuse.requested",
            "late_checkin.requested",
            "lesson.closed"
    );

    /** Events in this set are delivered only to the user named by payload.user_id. */
    private static final Set<String> USER_SCOPED_EVENTS = Set.of(
            "excuse.decided",
            "late_checkin.decided",
            "attendance.marked",
            "homework.weekly_digest",
            "homework.due_reminder"
    );

    /** Shared notification events currently produced and eligible for group delivery. */
    private static final Set<String> GROUP_NOTIFICATION_EVENTS = Set.of(
            "lesson.started",
            "lesson.reminder",
            "lesson.blocked",
            "lesson.cancelled",
            "lesson.one_off.created",
            "lesson.one_off.cancelled",
            "homework.published",
            "homework.updated",
            "group.renamed",
            "group.archived"
    );

    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushDeliveryService webPushDeliveryService;
    private final IdempotencyGuard idempotencyGuard;
    private final ReminderAttendanceStateService reminderAttendanceStateService;

    @Autowired
    public EventConsumer(SimpMessagingTemplate messagingTemplate,
                         WebPushDeliveryService webPushDeliveryService,
                         IdempotencyGuard idempotencyGuard,
                         ReminderAttendanceStateService reminderAttendanceStateService) {
        this.messagingTemplate = messagingTemplate;
        this.webPushDeliveryService = webPushDeliveryService;
        this.idempotencyGuard = idempotencyGuard;
        this.reminderAttendanceStateService = reminderAttendanceStateService;
    }

    public EventConsumer(SimpMessagingTemplate messagingTemplate,
                         WebPushDeliveryService webPushDeliveryService,
                         IdempotencyGuard idempotencyGuard) {
        this(messagingTemplate, webPushDeliveryService, idempotencyGuard, null);
    }

    @RabbitListener(queues = "notification-web.events")
    @EventIdempotent(consumer = CONSUMER_ID)
    @org.springframework.transaction.annotation.Transactional
    @SuppressWarnings("unchecked")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
            return;
        }

        // M04 QA3 — extract trace_id из envelope в MDC до handler'а.
        // Логи WebSocket-роутинга и Web Push delivery получат correlation id producer'а.
        withTraceContext(envelope, () -> {
            Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
            if (payload == null) {
                log.warn("Event {} has no payload, ignoring", eventType);
                return;
            }

            updateReminderAttendanceState(eventType, payload);

            Long groupId = positiveIntegralId(payload.get("group_id"));
            String destination = null;
            if (USER_SCOPED_EVENTS.contains(eventType)) {
                Long userId = positiveIntegralId(payload.get("user_id"));
                if (userId != null) {
                    destination = "/topic/user/" + userId;
                } else {
                    log.debug("Event {} has no valid user_id; skipping WebSocket routing", eventType);
                }
            } else if (HEADMAN_ONLY_EVENTS.contains(eventType)) {
                if (groupId != null) {
                    destination = "/topic/group/" + groupId + "/headman";
                } else {
                    log.debug("Headman event {} has no valid group_id; skipping WebSocket routing", eventType);
                }
            } else if (GROUP_NOTIFICATION_EVENTS.contains(eventType)) {
                if (groupId != null) {
                    destination = "/topic/group/" + groupId;
                } else {
                    log.debug("Group event {} has no valid group_id; skipping WebSocket routing", eventType);
                }
            } else {
                log.debug("Event {} is not a supported WebSocket notification", eventType);
            }

            if (destination != null) {
                // D-06: Wrap in {type, payload} envelope — no enrichment
                Map<String, Object> wsMessage = Map.of("type", eventType, "payload", payload);
                messagingTemplate.convertAndSend(destination, wsMessage);
                log.debug("Routed {} to {}", eventType, destination);
            }

            // D-07, D-08: After STOMP delivery — trigger async Web Push for push-eligible events.
            if (groupId != null && webPushDeliveryService.shouldPush(eventType)) {
                webPushDeliveryService.sendToGroup(groupId, eventType, payload);
                log.debug("Triggered async push for {} to group {}", eventType, groupId);
            }
        });
    }

    private static Long positiveIntegralId(Object rawValue) {
        try {
            long value;
            if (rawValue instanceof Byte || rawValue instanceof Short
                    || rawValue instanceof Integer || rawValue instanceof Long) {
                value = ((Number) rawValue).longValue();
            } else if (rawValue instanceof BigInteger bigInteger) {
                value = bigInteger.longValueExact();
            } else if (rawValue instanceof BigDecimal bigDecimal) {
                value = bigDecimal.longValueExact();
            } else {
                return null;
            }
            return value > 0 ? value : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private void updateReminderAttendanceState(String eventType, Map<String, Object> payload) {
        if (reminderAttendanceStateService == null) {
            return;
        }
        if ("attendance.marked".equals(eventType)) {
            reminderAttendanceStateService.recordMarked(payload);
        } else if ("lesson.closed".equals(eventType)
                || "lesson.cancelled".equals(eventType)
                || "lesson.deleted".equals(eventType)) {
            reminderAttendanceStateService.deleteLessonState(payload);
        }
    }
}
