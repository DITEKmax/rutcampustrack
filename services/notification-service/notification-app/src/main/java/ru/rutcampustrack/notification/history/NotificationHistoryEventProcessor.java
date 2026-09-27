package ru.rutcampustrack.notification.history;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One Mongo transaction owns the event claim and all recipient history rows. */
@Component
public class NotificationHistoryEventProcessor extends AbstractEventConsumer {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Moscow");
    private static final Set<String> GROUP_EVENT_TYPES = Set.of(
            "lesson.started", "lesson.cancelled", "homework.published", "homework.updated");
    private static final Set<String> GROUP_ID_FIELDS = Set.of(
            "lesson_id", "group_id", "subject_id", "homework_id");

    private final NotificationHistoryRepository repository;
    private final NotificationHistoryService historyService;
    private final IdempotencyGuard idempotencyGuard;
    private final AcademicGroupMemberClient academicGroupMemberClient;

    public NotificationHistoryEventProcessor(NotificationHistoryRepository repository,
                                             NotificationHistoryService historyService,
                                             IdempotencyGuard idempotencyGuard,
                                             AcademicGroupMemberClient academicGroupMemberClient) {
        this.repository = repository;
        this.historyService = historyService;
        this.idempotencyGuard = idempotencyGuard;
        this.academicGroupMemberClient = academicGroupMemberClient;
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

            String eventId = UUID.fromString(envelope.get("event_id").toString()).toString();
            String traceId = envelope.get("trace_id") instanceof String value ? value : null;
            if (GROUP_EVENT_TYPES.contains(eventType)) {
                long groupId = requirePositiveLong(payload.get("group_id"), "group_id");
                LocalDate eventDate = eventDate(envelope.get("occurred_at"));
                List<Long> recipientIds = validateRecipientIds(
                        academicGroupMemberClient.getMemberUserIds(groupId, eventDate));
                persistForRecipients(eventId, recipientIds, maybeType.get(),
                        groupDisplayPayload(eventType, payload), traceId);
                return;
            }

            long userId = requirePositiveLong(payload.get("user_id"), "user_id");
            persistForRecipients(eventId, List.of(userId), maybeType.get(), payload, traceId);
        });
    }

    private void persistForRecipients(String eventId,
                                     List<Long> recipientIds,
                                     NotificationType type,
                                     Map<String, Object> payload,
                                     String traceId) {
        if (recipientIds.isEmpty()) {
            return;
        }

        Set<Long> existingRecipientIds = repository.findByEventIdAndUserIdIn(eventId, recipientIds).stream()
                .map(NotificationHistoryDocument::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        Instant sentAt = Instant.now();
        List<NotificationHistoryDocument> documents = new ArrayList<>();
        for (Long recipientId : recipientIds) {
            if (!existingRecipientIds.contains(recipientId)) {
                documents.add(NotificationHistoryDocument.builder()
                        .eventId(eventId)
                        .userId(recipientId)
                        .type(type)
                        .payload(payload)
                        .sentAt(sentAt)
                        .traceId(traceId)
                        .build());
            }
        }
        if (documents.isEmpty()) {
            return;
        }
        repository.saveAll(documents);
        invalidateUnreadAfterCommit(documents.stream()
                .map(NotificationHistoryDocument::getUserId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private void invalidateUnreadAfterCommit(Set<Long> userIds) {
        Runnable invalidate = () -> userIds.forEach(historyService::invalidateUnreadCount);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidate.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidate.run();
            }
        });
    }

    private static List<Long> validateRecipientIds(List<Long> recipientIds) {
        if (recipientIds == null) {
            throw new IllegalStateException("Academic returned no membership result");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long userId : recipientIds) {
            if (userId == null || userId <= 0 || !uniqueIds.add(userId)) {
                throw new IllegalStateException("Academic returned invalid member IDs");
            }
        }
        return List.copyOf(uniqueIds);
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

    /**
     * New group-fanout history stores only the sanctioned display snapshot.
     * Full cancellation and homework details are left to their protected
     * detail APIs; unknown fields are never copied into history.
     */
    private static Map<String, Object> groupDisplayPayload(String eventType, Map<String, Object> source) {
        List<String> fields = switch (eventType) {
            case "lesson.started" -> List.of(
                    "lesson_id", "group_id", "subject_id", "lesson_number", "start_time", "end_time", "room");
            case "lesson.cancelled" -> List.of(
                    "lesson_id", "group_id", "subject_id", "date", "start_time", "end_time",
                    "lesson_number", "cancelled_at");
            case "homework.published", "homework.updated" -> List.of(
                    "homework_id", "group_id", "subject_id", "lesson_date", "lesson_number", "title");
            default -> throw new IllegalArgumentException("Unsupported group notification type: " + eventType);
        };

        Map<String, Object> displayPayload = new LinkedHashMap<>();
        for (String field : fields) {
            Object value = source.get(field);
            if (value == null) {
                continue;
            }
            if (GROUP_ID_FIELDS.contains(field)) {
                displayPayload.put(field, requirePositiveLong(value, field));
            } else if ("lesson_number".equals(field)) {
                displayPayload.put(field, requirePositiveInteger(value, field));
            } else if (value instanceof String text) {
                displayPayload.put(field, text);
            } else {
                throw new IllegalArgumentException(field + " must be a string");
            }
        }
        return displayPayload;
    }

    private static LocalDate eventDate(Object occurredAt) {
        if (!(occurredAt instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("occurred_at is required for group notifications");
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(EVENT_ZONE).toLocalDate();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("occurred_at must be an ISO offset date-time", error);
        }
    }

    private static String requireString(Object value, String field) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return result;
    }

    private static long requirePositiveLong(Object value, String field) {
        if (value instanceof Number number) {
            try {
                long id = new BigDecimal(number.toString()).longValueExact();
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Fractional, non-finite, or out-of-range IDs are malformed.
            }
        }
        throw new IllegalArgumentException(field + " must be a positive integer");
    }

    private static int requirePositiveInteger(Object value, String field) {
        if (value instanceof Number number) {
            try {
                int numberValue = new BigDecimal(number.toString()).intValueExact();
                if (numberValue > 0) {
                    return numberValue;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Fractional, non-finite, or out-of-range values are malformed.
            }
        }
        throw new IllegalArgumentException(field + " must be a positive integer");
    }

    /** Mapped broadcast types persist to each historically resolved recipient. */
    static Optional<NotificationType> mapType(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "excuse.requested" -> Optional.of(NotificationType.EXCUSE_REQUESTED);
            case "excuse.decided" -> Optional.of(decisionType(payload,
                    NotificationType.EXCUSE_APPROVED, NotificationType.EXCUSE_REJECTED));
            case "late_checkin.requested" -> Optional.of(NotificationType.LATE_CHECKIN_REQUESTED);
            case "late_checkin.decided" -> "cancelled".equals(payload.get("status"))
                    ? Optional.empty()
                    : Optional.of(decisionType(payload,
                            NotificationType.LATE_CHECKIN_APPROVED, NotificationType.LATE_CHECKIN_REJECTED));
            case "late_checkin.decision" -> Optional.empty();
            case "attendance.marked" -> "headman".equals(payload.get("marked_by"))
                    ? Optional.of(NotificationType.ATTENDANCE_MARKED_BY_HEADMAN)
                    : Optional.empty();
            case "homework.weekly_digest" -> Optional.of(NotificationType.HOMEWORK_WEEKLY_DIGEST);
            case "homework.due_reminder" -> Optional.of(NotificationType.HOMEWORK_DUE_REMINDER);
            case "lesson.started" -> Optional.of(NotificationType.LESSON_STARTED);
            case "lesson.cancelled" -> Optional.of(NotificationType.LESSON_CANCELLED);
            case "homework.published" -> Optional.of(NotificationType.HOMEWORK_PUBLISHED);
            case "homework.updated" -> Optional.of(NotificationType.HOMEWORK_UPDATED);
            default -> Optional.empty();
        };
    }

    private static NotificationType decisionType(Map<String, Object> payload,
                                                 NotificationType approved,
                                                 NotificationType rejected) {
        return "rejected".equals(payload.get("status")) ? rejected : approved;
    }
}
