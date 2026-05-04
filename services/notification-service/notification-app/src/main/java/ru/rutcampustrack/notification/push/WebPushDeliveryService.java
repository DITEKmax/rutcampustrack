package ru.rutcampustrack.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.HttpResponseException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    /** Events that fan out to every subscriber of the group. */
    private static final Set<String> PUSH_EVENT_TYPES = Set.of(
            "lesson.started",
            "lesson.reminder",
            "lesson.cancelled",
            "lesson.one_off.created",
            "lesson.one_off.cancelled",
            "homework.published",
            "homework.updated",
            "group.renamed",
            "group.archived",
            // Студент-таргетированные: доходят только подписчику user_id из payload.
            "excuse.decided",
            "late_checkin.decided",
            "attendance.marked"
    );

    /** Events that fan out only to headmen of the group (старостам). */
    private static final Set<String> HEADMAN_ONLY_EVENT_TYPES = Set.of(
            "excuse.requested",
            "late_checkin.requested"
    );

    /** Events that are sent to a single subscriber identified by payload.user_id. */
    private static final Set<String> USER_SCOPED_EVENT_TYPES = Set.of(
            "excuse.decided",
            "late_checkin.decided",
            "attendance.marked"
    );

    private final PushSubscriptionRepository repository;
    private final PushService webPushService;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public WebPushDeliveryService(PushSubscriptionRepository repository,
                                   PushService webPushService,
                                   ObjectMapper objectMapper,
                                   MongoTemplate mongoTemplate,
                                   Clock clock) {
        this.repository = repository;
        this.webPushService = webPushService;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /**
     * Returns true if this event type should trigger a Web Push notification.
     */
    public boolean shouldPush(String eventType) {
        return PUSH_EVENT_TYPES.contains(eventType)
                || HEADMAN_ONLY_EVENT_TYPES.contains(eventType);
    }

    /**
     * Sends Web Push notifications asynchronously to subscribers in the group,
     * respecting per-event routing rules (headman-only, user-scoped).
     */
    @Async("pushTaskExecutor")
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload) {
        List<PushSubscriptionDocument> subs = repository.findAllByGroupId(groupId);
        if (subs.isEmpty()) {
            log.debug("No push subscriptions for group {}", groupId);
            return CompletableFuture.completedFuture(null);
        }

        List<PushSubscriptionDocument> targets = filterRecipients(subs, eventType, payload);
        if (targets.isEmpty()) {
            log.debug("No eligible push recipients for event {} in group {}", eventType, groupId);
            return CompletableFuture.completedFuture(null);
        }

        String title = buildTitle(eventType, payload);
        String body = buildBody(eventType, payload);
        byte[] payloadBytes = buildPayloadJson(title, body, eventType, payload);

        List<String> deliveredEndpoints = new ArrayList<>(targets.size());
        for (PushSubscriptionDocument sub : targets) {
            try {
                Notification notification = createNotification(sub, payloadBytes);
                webPushService.send(notification);
                deliveredEndpoints.add(sub.getEndpoint());
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
        touchLastSeen(deliveredEndpoints);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * M05 G7 (NEW-148): single bulk {@code $set} обновляет {@code last_seen}
     * для всех endpoint'ов с успешной доставкой. Одна Mongo-operation на
     * fanout вместо N save'ов (write amplification × N).
     */
    private void touchLastSeen(List<String> endpoints) {
        if (endpoints.isEmpty()) {
            return;
        }
        Query query = new Query(Criteria.where("endpoint").in(endpoints));
        Update update = new Update().set("last_seen", Instant.now(clock));
        mongoTemplate.updateMulti(query, update, PushSubscriptionDocument.class);
    }

    /**
     * Narrows subscribers to those eligible for this event:
     * <ul>
     *   <li>HEADMAN_ONLY events → only subscribers with headman=true</li>
     *   <li>USER_SCOPED events  → only subscriber matching payload.user_id</li>
     *   <li>everyone else       → all group subscribers</li>
     * </ul>
     */
    private List<PushSubscriptionDocument> filterRecipients(List<PushSubscriptionDocument> subs,
                                                            String eventType,
                                                            Map<String, Object> payload) {
        if (HEADMAN_ONLY_EVENT_TYPES.contains(eventType)) {
            return subs.stream()
                    .filter(PushSubscriptionDocument::isHeadman)
                    .collect(Collectors.toList());
        }
        if ("attendance.marked".equals(eventType) && !"headman".equals(payload.get("marked_by"))) {
            return List.of();
        }
        if (USER_SCOPED_EVENT_TYPES.contains(eventType)) {
            Number userIdNum = (Number) payload.get("user_id");
            if (userIdNum == null) {
                log.warn("Event {} is user-scoped but payload has no user_id", eventType);
                return List.of();
            }
            long userId = userIdNum.longValue();
            return subs.stream()
                    .filter(s -> s.getUserId() != null && s.getUserId() == userId)
                    .collect(Collectors.toList());
        }
        return subs;
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
            case "lesson.reminder" -> "Не забудьте отметиться";
            case "attendance.marked" -> "Староста изменил статус";
            case "lesson.cancelled" -> "Пара отменена";
            case "lesson.one_off.created" -> "Добавлена пара";
            case "lesson.one_off.cancelled" -> "Пара отменена";
            case "homework.published" -> "Новое ДЗ";
            case "homework.updated" -> "ДЗ обновлено";
            case "group.renamed" -> "Группа переименована";
            case "group.archived" -> "Группа архивирована";
            case "excuse.requested" -> "Новый тикет о пропуске";
            case "excuse.decided" -> isApproved(payload)
                    ? "Уважительная одобрена"
                    : "Уважительная отклонена";
            case "late_checkin.requested" -> "Запрос опоздалой отметки";
            case "late_checkin.decided" -> isApproved(payload)
                    ? "Присутствие подтверждено"
                    : "Запрос отклонён";
            default -> "Уведомление";
        };
    }

    private String buildBody(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "lesson.started" -> lessonBody(payload, "Время отметиться");
            case "lesson.reminder" -> lessonBody(payload, "Сейчас идёт пара");
            case "attendance.marked" -> attendanceBody(payload);
            case "lesson.cancelled" -> {
                String base = lessonBody(payload, "Пара отменена");
                String reason = str(payload, "cancel_reason");
                yield reason.isEmpty() ? base : base + " · " + reason;
            }
            case "lesson.one_off.created" -> {
                String parts = lessonBody(payload, "Дополнительная пара");
                String classroom = str(payload, "classroom");
                yield classroom.isEmpty() ? parts : parts + " · ауд. " + classroom;
            }
            case "lesson.one_off.cancelled" -> lessonBody(payload, "Староста отменил пару");
            case "homework.published", "homework.updated" -> homeworkBody(payload);
            case "group.renamed" -> {
                String newName = str(payload, "new_name");
                if (!newName.isEmpty()) {
                    yield "Новое название: " + newName;
                }
                yield "Откройте приложение для подробностей";
            }
            case "group.archived" -> "Группа выпустилась. Поздравляем!";
            case "excuse.requested", "late_checkin.requested" -> {
                String student = str(payload, "student_name");
                String lessonPart = formatLessonRef(payload);
                if (student.isEmpty() && lessonPart.isEmpty()) {
                    yield "Нужна ваша реакция в приложении";
                }
                if (student.isEmpty()) {
                    yield lessonPart;
                }
                yield lessonPart.isEmpty() ? student : student + " · " + lessonPart;
            }
            case "excuse.decided", "late_checkin.decided" -> {
                String comment = str(payload, "decision_comment");
                if (!comment.isEmpty()) {
                    yield comment;
                }
                yield isApproved(payload)
                        ? "Староста одобрил ваш запрос"
                        : "Староста отклонил ваш запрос";
            }
            default -> "";
        };
    }

    /**
     * Builds a short lesson reference like «№3, 14:30-16:00» or «№2, 17.04»
     * using only fields that are guaranteed by the event schema (no subject_name).
     * Returns {@code fallback} if nothing useful can be built.
     */
    private String lessonBody(Map<String, Object> payload, String fallback) {
        String lessonRef = formatLessonRef(payload);
        return lessonRef.isEmpty() ? fallback : lessonRef;
    }

    private String homeworkBody(Map<String, Object> payload) {
        List<String> lines = new ArrayList<>();
        addIfNotBlank(lines, str(payload, "title"));
        addIfNotBlank(lines, formatLessonRef(payload));
        addIfNotBlank(lines, str(payload, "description"));
        addIfNotBlank(lines, str(payload, "link"));
        if (lines.isEmpty()) {
            return "Откройте приложение для подробностей";
        }
        return String.join("\n", lines);
    }

    private String attendanceBody(Map<String, Object> payload) {
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, attendanceStatusRu(str(payload, "status")));

        String subject = str(payload, "subject_name");
        Number lessonNumber = (Number) payload.get("lesson_number");
        if (lessonNumber != null && !subject.isBlank()) {
            parts.add("№" + lessonNumber.intValue() + " · " + subject);
        } else if (lessonNumber != null) {
            parts.add("№" + lessonNumber.intValue());
        } else {
            addIfNotBlank(parts, subject);
        }

        addIfNotBlank(parts, formatShortDate(str(payload, "lesson_date")));
        if (parts.isEmpty()) {
            return "Откройте приложение для подробностей";
        }
        return String.join(" · ", parts);
    }

    private String attendanceStatusRu(String status) {
        return switch (status) {
            case "present" -> "Присутствует (+)";
            case "absent" -> "Отсутствует (н)";
            case "excused" -> "Уважительная (у)";
            case "free_attendance" -> "Свобод. посещение (сп)";
            default -> status;
        };
    }

    private static void addIfNotBlank(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private String formatLessonRef(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        Number lessonNumber = (Number) payload.get("lesson_number");
        if (lessonNumber != null) {
            sb.append("№").append(lessonNumber.intValue());
        }
        String startTime = str(payload, "start_time");
        String endTime = str(payload, "end_time");
        if (!startTime.isEmpty() && !endTime.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(startTime).append("–").append(endTime);
        } else {
            String date = firstNonEmpty(str(payload, "lesson_date"), str(payload, "date"));
            if (!date.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(formatShortDate(date));
            }
        }
        return sb.toString();
    }

    /** Converts ISO "2026-04-17" → "17.04". Returns input unchanged on parse errors. */
    private String formatShortDate(String iso) {
        if (iso.length() >= 10 && iso.charAt(4) == '-' && iso.charAt(7) == '-') {
            return iso.substring(8, 10) + "." + iso.substring(5, 7);
        }
        return iso;
    }

    private static boolean isApproved(Map<String, Object> payload) {
        Object status = payload.get("status");
        return status instanceof String s && "approved".equals(s);
    }

    private static String str(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v instanceof String s ? s : "";
    }

    private static String firstNonEmpty(String a, String b) {
        return a == null || a.isEmpty() ? (b == null ? "" : b) : a;
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
