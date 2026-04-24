package ru.rutcampustrack.notification.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer, сохраняющий user-facing события в {@code notification_history}
 * (M10 G3 / NEW-166).
 *
 * <p>Subscribed на {@code notification-web.history} queue (D5), которая
 * bound к fanout {@code rut-uit.events} — получает ВСЕ события.
 * Маппер {@link #mapType(String)} фильтрует user-facing types; broadcast
 * (lesson.started/closed/cancelled) skip'ается (D6).
 *
 * <p>Error isolation: {@code try/catch} оборачивает логику persist, exception
 * логируется warn'ом с trace_id и НЕ rethrow'ится — failed save не должен
 * рушить delivery pipeline (ACK идёт всё равно; дальнейшие re-deliveries
 * не помогут в любом случае — persistence bug). См. PLAN.md G3.
 */
@Component
@Slf4j
public class NotificationHistoryConsumer extends AbstractEventConsumer {

    public static final String CONSUMER_ID = "notification-history";

    private final NotificationHistoryRepository repository;
    private final NotificationHistoryService historyService;
    private final IdempotencyGuard idempotencyGuard;

    public NotificationHistoryConsumer(NotificationHistoryRepository repository,
                                       NotificationHistoryService historyService,
                                       IdempotencyGuard idempotencyGuard) {
        this.repository = repository;
        this.historyService = historyService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @RabbitListener(queues = "notification-web.history")
    @EventIdempotent(consumer = CONSUMER_ID)
    @Transactional
    @SuppressWarnings("unchecked")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("history-consumer: envelope without event_type, skipping");
            return;
        }
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
            return;
        }

        withTraceContext(envelope, () -> {
            try {
                Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
                if (payload == null) {
                    log.warn("history-consumer: {} has no payload, skip", eventType);
                    return;
                }

                Optional<NotificationType> maybeType = mapType(eventType, payload);
                if (maybeType.isEmpty()) {
                    log.trace("history-consumer: {} — not user-facing, skip", eventType);
                    return;
                }

                Long userId = extractUserId(payload);
                if (userId == null) {
                    log.warn("history-consumer: {} has no payload.user_id, skip", eventType);
                    return;
                }

                String traceId = (String) envelope.get("trace_id");

                NotificationHistoryDocument doc = NotificationHistoryDocument.builder()
                        .userId(userId)
                        .type(maybeType.get())
                        .payload(payload)
                        .sentAt(Instant.now())
                        .traceId(traceId)
                        .build();
                repository.save(doc);
                historyService.invalidateUnreadCount(userId);
                log.debug("history-consumer: persisted {} for user {}", maybeType.get(), userId);
            } catch (Exception ex) {
                log.warn("history-consumer: persist failed for event {}: {}",
                        eventType, ex.getMessage(), ex);
            }
        });
    }

    /**
     * Маппинг event_type → NotificationType. Возвращает empty для broadcast
     * events и system events — они не попадают в per-user history (D6).
     *
     * <p>Для {@code *.decided} событий читает {@code payload.status}
     * ({@code "approved"|"rejected"}, см. event-schemas) и выбирает
     * соответствующий APPROVED/REJECTED тип. Без payload-aware маппинга
     * REJECTED-события силенциально writer'ились бы как APPROVED
     * (G9 hot-patch H1, 2026-04-24).
     */
    static Optional<NotificationType> mapType(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "excuse.requested" -> Optional.of(NotificationType.EXCUSE_REQUESTED);
            case "excuse.decided" -> Optional.of(decisionType(payload,
                    NotificationType.EXCUSE_APPROVED, NotificationType.EXCUSE_REJECTED));
            case "late_checkin.requested" -> Optional.of(NotificationType.LATE_CHECKIN_REQUESTED);
            case "late_checkin.decided", "late_checkin.decision" -> Optional.of(decisionType(payload,
                    NotificationType.LATE_CHECKIN_APPROVED, NotificationType.LATE_CHECKIN_REJECTED));
            case "attendance.marked" -> Optional.of(NotificationType.ATTENDANCE_RED_ZONE);
            default -> Optional.empty();
        };
    }

    private static NotificationType decisionType(Map<String, Object> payload,
                                                 NotificationType approved,
                                                 NotificationType rejected) {
        Object status = payload.get("status");
        return "rejected".equals(status) ? rejected : approved;
    }

    private static Long extractUserId(Map<String, Object> payload) {
        Object raw = payload.get("user_id");
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
