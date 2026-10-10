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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** One provider attempt; central admission and durable retry belong to NotificationPushIntentDispatcher. */
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
                || HEADMAN_ONLY_EVENT_TYPES.contains(eventType)
                || Set.of("lesson.transferred", "lesson.restored", "lesson.updated", "homework.withdrawn",
                        "group.membership.changed", "headman.access.changed", "assistant.access.changed", "admin.access.changed",
                        "semester.activated", "semester.operation.completed", "group.promotion.completed").contains(eventType);
    }

    /** Durable dispatcher owns admission, lease and receipts; this method performs one explicit provider attempt. */
    public int sendToSubscription(PushSubscriptionDocument subscription, String eventType,
                                  ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto.Item item) throws Exception {
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(subscription.getEndpoint().getBytes(StandardCharsets.UTF_8)));
        String body = item.details().stream().limit(3).map(detail ->
                (detail.label() == null ? "" : detail.label() + ": ") + detail.value()).collect(Collectors.joining("\n"));
        if (body.isBlank()) body = "Открой приложение для подробностей";
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("recipientUserId", subscription.getUserId().toString());
        json.put("subscriptionFingerprint", fingerprint);
        json.put("title", item.heading()); json.put("body", boundedText(body, 600));
        json.put("event_type", eventType); json.put("data", Map.of());
        if (item.action() != null) {
            String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(Map.of("v", 2, "action", item.action())));
            if (encoded.length() <= 384) json.put("notificationAction", encoded);
        }
        HttpResponse response = webPushService.send(createNotification(subscription, objectMapper.writeValueAsBytes(json)), Encoding.AES128GCM);
        if (response == null || response.getStatusLine() == null) throw new IllegalStateException("Push provider returned no status");
        int status;
        try { status = response.getStatusLine().getStatusCode(); }
        finally { EntityUtils.consumeQuietly(response.getEntity()); }
        if (status == 404 || status == 410) repository.deleteByEndpoint(subscription.getEndpoint());
        if (status >= 200 && status < 300) touchLastSeen(List.of(subscription.getEndpoint()));
        return status;
    }

    /** Legacy group fanout cannot bypass central admission and the durable worker. */
    @Deprecated
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload) {
        return CompletableFuture.failedFuture(legacyUnavailable());
    }
    @Deprecated
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload,
                                              Set<Long> currentAudienceIds) {
        return CompletableFuture.failedFuture(legacyUnavailable());
    }
    @Deprecated
    public Set<Long> resolveCurrentAudience(long groupId, String eventType) { throw legacyUnavailable(); }
    @Deprecated
    public Set<Long> resolveEligibleAudience(long groupId, String eventType, Map<String, Object> payload) { throw legacyUnavailable(); }
    @Deprecated
    public Set<Long> resolveReminderAudience(long groupId, Map<String, Object> payload) { throw legacyUnavailable(); }
    private static IllegalStateException legacyUnavailable() {
        return new IllegalStateException("Central durable notification admission is required");
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
        return presentation(eventType, payload).heading();
    }

    private String buildBody(String eventType, Map<String, Object> payload) {
        return boundedText(presentation(eventType, payload).details().stream()
                .sorted(java.util.Comparator.comparingInt(detail -> "Исключено занятий".equals(detail.label()) ? 0
                        : "Исключённое занятие".equals(detail.label()) ? 1 : "Комментарий".equals(detail.label()) ? 2 : 3)).limit(8)
                .map(detail -> detail.label() + ": " + detail.value()).collect(Collectors.joining(" · ")), 600);
    }

    private ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto.Item presentation(
            String eventType, Map<String, Object> payload) {
        var type = ru.rutcampustrack.notification.history.NotificationCatalog.eventType(eventType, payload)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported notification presentation"));
        String role = HEADMAN_ONLY_EVENT_TYPES.contains(eventType) ? "HEADMAN" : "STUDENT";
        Instant now = Instant.now(clock);
        var doc = ru.rutcampustrack.notification.history.NotificationHistoryDocument.builder().type(type)
                .payload(payload).sentAt(now).roleViews(Map.of(role,
                        ru.rutcampustrack.notification.history.NotificationHistoryDocument.RoleView.builder().orderAt(now).build())).build();
        return new ru.rutcampustrack.notification.history.NotificationCatalog().present(doc, role);
    }

    private static boolean hasExcludedLessons(Map<String, Object> payload) {
        return !excludedLessons(payload).isEmpty();
    }

    private static List<?> excludedLessons(Map<String, Object> payload) {
        Object raw = payload.get("excluded_lessons");
        if (raw instanceof List<?> lessons && !lessons.isEmpty()) return lessons;
        // Older producers supplied IDs only. Keep partial approval visible without inventing metadata.
        Object ids = payload.get("excluded_lesson_ids");
        if (ids instanceof List<?> lessons && !lessons.isEmpty()) return lessons;
        Object occurrences = payload.get("excluded_occurrence_ids");
        // Occurrence IDs are not lesson IDs: retain the partial verdict with an honest unavailable-detail fallback.
        return occurrences instanceof List<?> lessons ? java.util.Collections.nCopies(lessons.size(), Map.of()) : List.of();
    }

    private static String excludedLessonsBody(Map<String, Object> payload) {
        List<?> lessons = excludedLessons(payload);
        List<String> lines = new ArrayList<>();
        lines.add("Одобрено частично. Исключённые пары:");
        for (int i = 0; i < Math.min(lessons.size(), 3); i++) {
            lines.add("• " + excludedLessonRef(lessons.get(i)));
        }
        if (lessons.size() > 3) lines.add("Ещё исключено: " + (lessons.size() - 3));
        lines.add("Полный состав решения — в заявке в приложении.");
        return String.join("\n", lines);
    }

    private static String excludedLessonRef(Object raw) {
        Map<String, Object> lesson = map(raw);
        String id = positiveInteger(lesson.isEmpty() ? raw : lesson.get("lesson_id"));
        List<String> parts = new ArrayList<>();
        String date = str(lesson, "date");
        try {
            if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) parts.add(LocalDate.parse(date).toString());
        } catch (DateTimeParseException ignored) {
            // Optional malformed metadata must not prevent delivery of the decision.
        }
        String number = positiveInteger(lesson.get("lesson_number"));
        if (!number.isEmpty()) parts.add("№" + number);
        if (parts.isEmpty()) parts.add(id.isEmpty() ? "Данные пары недоступны" : "Пара ID " + id);
        String subject = str(lesson, "subject_name");
        if (!subject.isBlank()) parts.add(boundedText(subject, 80));
        return String.join(" · ", parts);
    }

    private static String positiveInteger(Object raw) {
        if (raw instanceof Number number) {
            try {
                long value = new BigDecimal(number.toString()).longValueExact();
                if (value > 0) return Long.toString(value);
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Fractional and non-finite optional values are not lesson references.
            }
        }
        return "";
    }

    private static String boundedText(String value, int limit) {
        // JSON escapes C0 controls as six ASCII bytes; normalize them before applying the display bound.
        String line = value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").strip();
        return line.length() > limit ? line.substring(0, limit) + "…" : line;
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
            Map<String, Object> data = new LinkedHashMap<>();
            for (String key : List.of("lesson_id", "group_id", "subject_id", "homework_id", "ticket_id", "request_id",
                    "user_id", "student_id", "status")) {
                Object value = payload.get(key);
                if (value == null) continue;
                if (key.endsWith("_id") && !key.equals("ticket_id") && !key.equals("request_id")) {
                    if (ru.rutcampustrack.notification.history.NotificationCatalog.positiveId(value) != null) data.put(key, value);
                } else if (value instanceof String text && text.matches("[A-Za-z0-9_:-]{1,128}")) data.put(key, text);
            }
            if ("excuse.decided".equals(eventType) && isApproved(payload) && hasExcludedLessons(payload)) {
                // Full immutable snapshots remain in the outbox/history; push carries only the detail navigation keys.
                data = new LinkedHashMap<>();
                for (String key : List.of("ticket_id", "user_id", "student_id", "status")) {
                    if (payload.containsKey(key)) data.put(key, payload.get(key));
                }
                data.put("excluded_lesson_count", excludedLessons(payload).size());
            }
            Map<String, Object> json = new LinkedHashMap<>(Map.of(
                    "recipientUserId", subscription.getUserId().toString(),
                    "subscriptionFingerprint", fingerprint,
                    "title", title,
                    "body", body,
                    "event_type", eventType,
                    "data", data
            ));
            var action = presentation(eventType, payload).action();
            if (action != null) {
                String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                        objectMapper.writeValueAsBytes(Map.of("v", 2, "action", action)));
                if (encoded.length() <= 384) json.put("notificationAction", encoded);
            }
            return objectMapper.writeValueAsBytes(json);
        } catch (Exception e) {
            log.error("Failed to serialize push payload", e);
            return "{}".getBytes();
        }
    }
}
