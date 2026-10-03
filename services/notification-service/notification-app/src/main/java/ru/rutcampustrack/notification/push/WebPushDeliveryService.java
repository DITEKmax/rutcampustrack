package ru.rutcampustrack.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.notification.history.AcademicGroupMemberClient;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;

import java.time.Clock;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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

    /** Events that should create Web Push notifications. */
    private static final Set<String> PUSH_EVENT_TYPES = Set.of(
            "lesson.started",
            "lesson.reminder",
            "lesson.blocked",
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
            "attendance.marked",
            "homework.weekly_digest",
            "homework.due_reminder"
    );

    /** Events that fan out only to headmen of the group (старостам). */
    private static final Set<String> HEADMAN_ONLY_EVENT_TYPES = Set.of(
            "excuse.requested",
            "late_checkin.requested",
            "lesson.closed"
    );

    /** Events that are sent to a single subscriber identified by payload.user_id. */
    private static final Set<String> USER_SCOPED_EVENT_TYPES = Set.of(
            "excuse.decided",
            "late_checkin.decided",
            "attendance.marked",
            "homework.weekly_digest",
            "homework.due_reminder"
    );

    private final PushSubscriptionRepository repository;
    private final PushService webPushService;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final NotificationPreferencesService preferencesService;
    private final ReminderAttendanceStateService reminderAttendanceStateService;
    private final AcademicGroupMemberClient academicGroupMemberClient;

    @Autowired
    public WebPushDeliveryService(PushSubscriptionRepository repository,
                                   PushService webPushService,
                                   ObjectMapper objectMapper,
                                   MongoTemplate mongoTemplate,
                                   Clock clock,
                                   NotificationPreferencesService preferencesService,
                                   ReminderAttendanceStateService reminderAttendanceStateService,
                                   AcademicGroupMemberClient academicGroupMemberClient) {
        this.repository = repository;
        this.webPushService = webPushService;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.preferencesService = preferencesService;
        this.reminderAttendanceStateService = reminderAttendanceStateService;
        this.academicGroupMemberClient = academicGroupMemberClient;
    }

    public WebPushDeliveryService(PushSubscriptionRepository repository,
                                   PushService webPushService,
                                   ObjectMapper objectMapper,
                                   MongoTemplate mongoTemplate,
                                   Clock clock,
                                   NotificationPreferencesService preferencesService,
                                   ReminderAttendanceStateService reminderAttendanceStateService) {
        this(repository, webPushService, objectMapper, mongoTemplate, clock,
                preferencesService, reminderAttendanceStateService, null);
    }

    public WebPushDeliveryService(PushSubscriptionRepository repository,
                                   PushService webPushService,
                                   ObjectMapper objectMapper,
                                   MongoTemplate mongoTemplate,
                                   Clock clock) {
        this(repository, webPushService, objectMapper, mongoTemplate, clock, null, null, null);
    }

    /**
     * Returns true if this event type should trigger a Web Push notification.
     */
    public boolean shouldPush(String eventType) {
        return PUSH_EVENT_TYPES.contains(eventType)
                || HEADMAN_ONLY_EVENT_TYPES.contains(eventType);
    }

    /**
     * Convenience entry point resolves preferences synchronously. Production event
     * consumers use the async overload with an already admitted recipient snapshot.
     */
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload) {
        try {
            return deliverToGroup(groupId, eventType, payload,
                    resolveEligibleAudience(groupId, eventType, payload));
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    /**
     * Resolves only the authority boundary. Academic's bounded RPC must succeed
     * before the event claim commits; provider I/O belongs to the async worker.
     */
    public Set<Long> resolveCurrentAudience(long groupId, String eventType) {
        if (academicGroupMemberClient == null) {
            throw new IllegalStateException("Current audience resolver is unavailable");
        }
        return Set.copyOf(HEADMAN_ONLY_EVENT_TYPES.contains(eventType)
                ? academicGroupMemberClient.getCurrentHeadmanUserIds(groupId)
                : academicGroupMemberClient.getCurrentMemberUserIds(groupId));
    }

    /** Resolve canonical prefs while the event transaction can still retry, never in the provider worker. */
    public Set<Long> resolveEligibleAudience(long groupId, String eventType, Map<String, Object> payload) {
        if (preferencesService == null) throw new IllegalStateException("Preferences resolver is unavailable");
        if ("lesson.reminder".equals(eventType)) return resolveReminderAudience(groupId, payload);
        Set<Long> candidates;
        if (USER_SCOPED_EVENT_TYPES.contains(eventType)) {
            Object raw = payload.get("user_id");
            long userId;
            try {
                if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
                    userId = ((Number) raw).longValue();
                } else if (raw instanceof BigInteger integer) {
                    userId = integer.longValueExact();
                } else if (raw instanceof BigDecimal decimal) {
                    userId = decimal.longValueExact();
                } else {
                    throw new IllegalArgumentException("Notification user_id must be a positive integer");
                }
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Notification user_id must be a positive integer", error);
            }
            if (userId <= 0) throw new IllegalArgumentException("Notification user_id must be positive");
            candidates = Set.of(userId);
        } else {
            candidates = resolveCurrentAudience(groupId, eventType);
        }
        return candidates.stream().filter(userId -> preferencesService.isEnabledForUser(userId, eventType))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Resolve marks and preferences before WS/enqueue, while a failure can still roll back the claim. */
    public Set<Long> resolveReminderAudience(long groupId, Map<String, Object> payload) {
        Object rawLessonId = payload == null ? null : payload.get("lesson_id");
        long lessonId;
        try {
            if (rawLessonId instanceof Byte || rawLessonId instanceof Short
                    || rawLessonId instanceof Integer || rawLessonId instanceof Long) {
                lessonId = ((Number) rawLessonId).longValue();
            } else if (rawLessonId instanceof BigInteger integer) {
                lessonId = integer.longValueExact();
            } else if (rawLessonId instanceof BigDecimal decimal) {
                lessonId = decimal.longValueExact();
            } else {
                throw new IllegalArgumentException("lesson_id must be a positive integer");
            }
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("lesson_id must be a positive integer", error);
        }
        if (lessonId <= 0 || groupId <= 0) {
            throw new IllegalArgumentException("Reminder lesson and group IDs must be positive integers");
        }
        if (reminderAttendanceStateService == null) {
            throw new IllegalStateException("Reminder attendance resolver is unavailable");
        }
        if (preferencesService == null) {
            throw new IllegalStateException("Reminder preferences resolver is unavailable");
        }
        Set<Long> currentUserIds = resolveCurrentAudience(groupId, "lesson.reminder");
        return Set.copyOf(reminderAttendanceStateService.getUnmarkedUserIds(lessonId, currentUserIds).stream()
                .filter(preferencesService::isReminderEnabledForUser)
                .collect(Collectors.toSet()));
    }

    /** Uses the audience authorized by the consumer, without blocking its Mongo transaction on the provider. */
    @Async("pushTaskExecutor")
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload,
                                             Set<Long> currentAudienceIds) {
        return deliverToGroup(groupId, eventType, payload, Set.copyOf(currentAudienceIds));
    }

    private CompletableFuture<Void> deliverToGroup(long groupId, String eventType, Map<String, Object> payload,
                                                  Set<Long> currentAudienceIds) {
        List<PushSubscriptionDocument> subs = repository.findAllByGroupId(groupId);
        if (subs.isEmpty()) {
            log.info("Push delivery skipped event={} group={} subscriptions=0 reason=no_subscriptions",
                    eventType, groupId);
            return CompletableFuture.completedFuture(null);
        }

        List<PushSubscriptionDocument> targets = filterRecipients(subs, eventType, payload, currentAudienceIds);
        if (targets.isEmpty()) {
            log.info(
                    "Push delivery skipped event={} group={} subscriptions={} targets=0 reason=no_eligible_recipients",
                    eventType, groupId, subs.size());
            return CompletableFuture.completedFuture(null);
        }

        String title = buildTitle(eventType, payload);
        String body = buildBody(eventType, payload);

        List<String> deliveredEndpoints = new ArrayList<>(targets.size());
        int failed = 0;
        int expired = 0;
        for (PushSubscriptionDocument sub : targets) {
            try {
                Notification notification = createNotification(sub, buildPayloadJson(title, body, eventType, payload, sub));
                // 5.1.2's single-argument send still chooses the legacy aesgcm format.
                HttpResponse response = webPushService.send(notification, Encoding.AES128GCM);
                if (response == null) {
                    throw new IllegalStateException("Push provider returned no status");
                }
                int status;
                try {
                    if (response.getStatusLine() == null) throw new IllegalStateException("Push provider returned no status");
                    status = response.getStatusLine().getStatusCode();
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
                // The library returns 4xx/5xx responses; it does not throw for them.
                if (status < 200 || status >= 300) {
                    throw new HttpResponseException(status, "Push provider rejected notification");
                }
                deliveredEndpoints.add(sub.getEndpoint());
                log.debug("Push provider accepted event={} user={} status={}", eventType, sub.getUserId(), status);
            } catch (Exception e) {
                if (isExpired(e)) {
                    // D-10: Retire endpoints no longer known to the provider (404/410).
                    repository.deleteByEndpoint(sub.getEndpoint());
                    expired++;
                    log.info("Deleted expired push subscription user={}", sub.getUserId());
                } else {
                    // D-08: Log and continue — do not block other subscriptions
                    failed++;
                    // Endpoint and provider exception text can contain subscription credentials.
                    log.warn("Push failed event={} user={} status={} error={}", eventType, sub.getUserId(),
                            e instanceof HttpResponseException http ? http.getStatusCode() : -1, e.getClass().getSimpleName());
                }
            }
        }
        touchLastSeen(deliveredEndpoints);
        log.info(
                "Push delivery finished event={} group={} subscriptions={} targets={} sent={} failed={} expired={}",
                eventType, groupId, subs.size(), targets.size(), deliveredEndpoints.size(), failed, expired);
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
     *   <li>Headman events → only subscriptions whose user is the current Academic headman</li>
     *   <li>USER_SCOPED events  → only subscriber matching payload.user_id</li>
     *   <li>Group events → only subscriptions belonging to current Academic members</li>
     * </ul>
     */
    private List<PushSubscriptionDocument> filterRecipients(List<PushSubscriptionDocument> subs,
                                                            String eventType,
                                                            Map<String, Object> payload,
                                                            Set<Long> currentAudienceIds) {
        if ("late_checkin.decided".equals(eventType)
                && "cancelled".equals(payload.get("status"))) {
            return List.of();
        }
        if (HEADMAN_ONLY_EVENT_TYPES.contains(eventType)) {
            if (currentAudienceIds == null || currentAudienceIds.isEmpty()) {
                return List.of();
            }
            return subs.stream()
                    .filter(s -> s.getUserId() != null && currentAudienceIds.contains(s.getUserId()))
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
                    .filter(s -> s.getUserId() != null && s.getUserId() == userId
                            && currentAudienceIds != null && currentAudienceIds.contains(s.getUserId()))
                    .collect(Collectors.toList());
        }
        return subs.stream()
                .filter(s -> s.getUserId() != null && currentAudienceIds != null
                        && currentAudienceIds.contains(s.getUserId()))
                .collect(Collectors.toList());
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
     * Checks for endpoints expired or unregistered at the provider.
     */
    private boolean isExpired(Exception e) {
        if (e instanceof HttpResponseException hre) {
            return hre.getStatusCode() == 404 || hre.getStatusCode() == 410;
        }
        if (e.getCause() instanceof HttpResponseException hre) {
            return hre.getStatusCode() == 404 || hre.getStatusCode() == 410;
        }
        return false;
    }

    private String buildTitle(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "lesson.started" -> "Пара началась";
            case "lesson.reminder" -> "Не забудьте отметиться";
            case "lesson.closed" -> "Пара завершена";
            case "lesson.blocked" -> "Пара заблокирована";
            case "attendance.marked" -> "Староста изменил статус";
            case "lesson.cancelled" -> "Пара отменена";
            case "lesson.one_off.created" -> "Добавлена пара";
            case "lesson.one_off.cancelled" -> "Пара отменена";
            case "homework.published" -> "Новое ДЗ";
            case "homework.updated" -> "ДЗ обновлено";
            case "homework.weekly_digest" -> "ДЗ на следующую неделю";
            case "homework.due_reminder" -> "ДЗ через 2 дня";
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
            case "lesson.closed" -> "Откройте расписание для подробностей";
            case "lesson.blocked" -> {
                String base = lessonBody(payload, "Самостоятельная отметка невозможна");
                yield base + " · отмечает староста";
            }
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
            case "homework.weekly_digest" -> homeworkWeeklyDigestBody(payload);
            case "homework.due_reminder" -> homeworkDueReminderBody(payload);
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

    private String homeworkDueReminderBody(Map<String, Object> payload) {
        Map<String, Object> homework = map(payload.get("homework"));
        if (homework.isEmpty()) {
            return "Откройте ДЗ, чтобы проверить дедлайн";
        }
        return homeworkItemLine(homework);
    }

    private String homeworkWeeklyDigestBody(Map<String, Object> payload) {
        int total = intValue(payload.get("total_count"));
        if (total <= 0) {
            return "На следующую неделю невыполненных заданий нет";
        }
        List<Map<String, Object>> items = maps(payload.get("items"));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), 3); i++) {
            addIfNotBlank(lines, homeworkItemLine(items.get(i)));
        }
        if (total > lines.size()) {
            lines.add("Еще " + (total - lines.size()));
        }
        return lines.isEmpty() ? total + " заданий" : String.join("\n", lines);
    }

    private String homeworkItemLine(Map<String, Object> homework) {
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, str(homework, "subject_name"));
        addIfNotBlank(parts, str(homework, "title"));
        addIfNotBlank(parts, formatLessonRef(homework));
        return parts.isEmpty() ? "Домашнее задание" : String.join(" · ", parts);
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

    private static int intValue(Object raw) {
        return raw instanceof Number n ? n.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> ? (Map<String, Object>) raw : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static String firstNonEmpty(String a, String b) {
        return a == null || a.isEmpty() ? (b == null ? "" : b) : a;
    }

    private byte[] buildPayloadJson(String title, String body, String eventType, Map<String, Object> payload,
                                   PushSubscriptionDocument subscription) {
        try {
            // A retired account/endpoint must not display an already queued message
            // after the browser enrolls a new account. Never expose endpoint/key material.
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(subscription.getEndpoint().getBytes(StandardCharsets.UTF_8)));
            Map<String, Object> json = Map.of(
                    "recipientUserId", subscription.getUserId().toString(),
                    "subscriptionFingerprint", fingerprint,
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
