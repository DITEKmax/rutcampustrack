package ru.rutcampustrack.notification.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One Mongo transaction owns the event claim and all recipient history rows. */
@Component
@Slf4j
public class NotificationHistoryEventProcessor extends AbstractEventConsumer {

    private static final Map<String, String> HISTORY_CHANGED_SIGNAL =
            Map.of("type", "notification.history.changed");
    private final NotificationHistoryRepository repository;
    private final NotificationHistoryService historyService;
    private final IdempotencyGuard idempotencyGuard;
    private final AcademicGroupMemberClient academicGroupMemberClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService reminderState;
    private NotificationAudienceService audience;
    private NotificationRoleHistoryWriter roleWriter;

    public NotificationHistoryEventProcessor(NotificationHistoryRepository repository,
                                             NotificationHistoryService historyService,
                                             IdempotencyGuard idempotencyGuard,
                                             AcademicGroupMemberClient academicGroupMemberClient,
                                             SimpMessagingTemplate messagingTemplate) {
        this(repository, historyService, idempotencyGuard, academicGroupMemberClient, messagingTemplate, null);
    }

    public NotificationHistoryEventProcessor(NotificationHistoryRepository repository,
                                             NotificationHistoryService historyService,
                                             IdempotencyGuard idempotencyGuard,
                                             AcademicGroupMemberClient academicGroupMemberClient,
                                             SimpMessagingTemplate messagingTemplate,
                                             ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService reminderState) {
        this.repository = repository;
        this.historyService = historyService;
        this.idempotencyGuard = idempotencyGuard;
        this.academicGroupMemberClient = academicGroupMemberClient;
        this.messagingTemplate = messagingTemplate;
        this.reminderState = reminderState;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationHistoryEventProcessor(NotificationHistoryRepository repository, NotificationHistoryService historyService,
            IdempotencyGuard idempotencyGuard, AcademicGroupMemberClient academicGroupMemberClient,
            SimpMessagingTemplate messagingTemplate, ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService reminderState,
            NotificationAudienceService audience, NotificationRoleHistoryWriter roleWriter) {
        this(repository, historyService, idempotencyGuard, academicGroupMemberClient, messagingTemplate, reminderState);
        this.audience = audience; this.roleWriter = roleWriter;
    }

    @Transactional
    public void persist(Map<String, Object> envelope) {
        String eventType = requireString(envelope == null ? null : envelope.get("event_type"), "event_type");
        if (!idempotencyGuard.tryClaim(NotificationHistoryConsumer.CONSUMER_ID, envelope)) {
            return;
        }

        withTraceContext(envelope, () -> {
            Object rawPayload = envelope.get("payload");
            if (!(rawPayload instanceof Map<?, ?> sourcePayload)) {
                throw new IllegalArgumentException("Notification event payload must be an object");
            }
            Map<String, Object> payload = copyPayload(sourcePayload);
            Optional<NotificationType> maybeType = mapType(eventType, payload);
            if (maybeType.isEmpty()) {
                return;
            }

            if (audience == null || roleWriter == null) throw new IllegalStateException("Central notification authority is unavailable");
            var recipients = audience.snapshot(envelope);
            persistResolved(envelope, recipients);
        });
    }

    /** Delivery consumer calls this in its own event-claim/intent transaction; history queue later merges safely. */
    public void persistResolved(Map<String, Object> envelope, Map<Long, Set<String>> recipients) {
        if (roleWriter == null) throw new IllegalStateException("Role history writer is unavailable");
        String eventType = requireString(envelope.get("event_type"), "event_type");
        Map<String, Object> payload = NotificationAudienceClient.payload(envelope);
        NotificationType type = mapType(eventType, payload).orElseThrow();
        String eventId = UUID.fromString(envelope.get("event_id").toString()).toString();
        String traceId = envelope.get("trace_id") instanceof String value ? value : null;
        Instant sentAt = Instant.now();
        recipients.forEach((userId, roles) -> roleWriter.persist(eventId, userId, type,
                NotificationPayloadSnapshot.forRecipient(eventType, payload, userId), traceId, roles, sentAt));
        invalidateUnreadAfterCommit(recipients.keySet());
    }

    private void invalidateUnreadAfterCommit(Set<Long> userIds) {
        Runnable invalidateAndNotify = () -> {
            userIds.forEach(historyService::invalidateUnreadCount);
            userIds.forEach(this::publishHistoryChanged);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateAndNotify.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateAndNotify.run();
            }
        });
    }

    private void publishHistoryChanged(Long userId) {
        try {
            messagingTemplate.convertAndSend("/topic/user/" + userId, HISTORY_CHANGED_SIGNAL);
        } catch (RuntimeException error) {
            log.debug("Unable to publish notification history change error={}", error.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> copyPayload(Map<?, ?> sourcePayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        sourcePayload.forEach((key, value) -> {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("Notification payload keys must be strings");
            }
            payload.put(name, value);
        });
        return payload;
    }

    private static String requireString(Object value, String field) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return result;
    }

    /** Mapped event types persist to their resolved recipients. */
    static Optional<NotificationType> mapType(String eventType, Map<String, Object> payload) {
        return NotificationCatalog.eventType(eventType, payload);
    }
}
