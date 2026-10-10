package ru.rutcampustrack.notification.history;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** The sole role/capability authority is Academic; malformed replies never become an empty success. */
@Component
public class NotificationAudienceClient {
    private static final Metadata.Key<String> GRPC_SECRET = Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);
    private static final Set<String> ROLES = Set.of("STUDENT", "HEADMAN", "TEACHER", "ADMIN");
    @GrpcClient("academic-service")
    private NotificationAudienceGrpcServiceGrpc.NotificationAudienceGrpcServiceBlockingStub stub;
    private final String sharedSecret;
    public NotificationAudienceClient(@Value("${grpc.auth.secret:}") String sharedSecret) { this.sharedSecret = sharedSecret; }

    public Map<Long, Set<String>> resolve(Map<String, Object> envelope) {
        if (sharedSecret == null || sharedSecret.isBlank()) throw new IllegalStateException("Audience admission is unavailable");
        Metadata headers = new Metadata();
        headers.put(GRPC_SECRET, sharedSecret);
        Map<Long, Set<String>> union = new LinkedHashMap<>();
        for (var request : requests(envelope)) {
            var response = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(3, TimeUnit.SECONDS).resolveNotificationAudience(request);
            validate(request, response).forEach((id, roles) -> union.merge(id, roles, (left, right) -> {
                Set<String> merged = new LinkedHashSet<>(left); merged.addAll(right); return Set.copyOf(merged);
            }));
        }
        return Collections.unmodifiableMap(union);
    }

    static NotificationAudienceRequest request(Map<String, Object> envelope) {
        return requests(envelope).getFirst();
    }
    public static List<NotificationAudienceRequest> requests(Map<String, Object> envelope) {
        String requestId = UUID.fromString(requiredString(envelope.get("event_id"))).toString();
        String eventType = requiredString(envelope.get("event_type"));
        String occurredAt = requiredString(envelope.get("occurred_at"));
        OffsetDateTime.parse(occurredAt);
        Map<String, Object> payload = payload(envelope);
        if (NotificationCatalog.eventType(eventType, payload).isEmpty()) throw new IllegalArgumentException("Unsupported audience event");
        var request = NotificationAudienceRequest.newBuilder().setRequestId(requestId).setEventType(eventType).setOccurredAt(occurredAt);
        Set<Long> groups = new TreeSet<>();
        if (payload.get("group_id") != null) groups.add(positive(payload.get("group_id")));
        Object affected = payload.getOrDefault("user_id", payload.get("target_user_id"));
        if (affected != null) request.setAffectedUserId(positive(affected));
        if (eventType.equals("excuse.decided") || eventType.equals("late_checkin.decided")) {
            String outcome = requiredString(payload.get("status"));
            if (!Set.of("approved", "rejected", "cancelled").contains(outcome)) throw new IllegalArgumentException("Invalid request outcome");
            request.setRequestOutcome(outcome);
        }
        if (payload.get("source") instanceof Map<?, ?> source) {
            if (source.get("group_id") != null) groups.add(positive(source.get("group_id")));
            addScope(request, source);
        }
        if (payload.get("target") instanceof Map<?, ?> target) {
            if (target.get("group_id") != null) groups.add(positive(target.get("group_id")));
            addScope(request, target);
        }
        if (!payload.containsKey("source") && !payload.containsKey("target")) addScope(request, payload);
        var base = request.build();
        if (groups.isEmpty()) return List.of(base);
        // Unknown tuple stays empty: authoritative dated group facts survive, Teacher is never inferred.
        return groups.stream().map(group -> base.toBuilder().setGroupId(group).clearLessonScopes()
                .addAllLessonScopes(base.getLessonScopesList().stream().filter(scope -> scope.getGroupId() == group).toList()).build()).toList();
    }

    public static byte[] contextBytes(Map<String, Object> envelope) {
        String stable = requests(envelope).stream().map(request -> java.util.Base64.getEncoder().encodeToString(request.toByteArray()))
                .collect(java.util.stream.Collectors.joining("|"));
        return stable.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void addScope(NotificationAudienceRequest.Builder request, Map<?, ?> values) {
        // Legacy context is never fabricated into a teacher assignment tuple.
        if (values.get("group_id") == null || values.get("subject_id") == null || values.get("semester_id") == null
                || values.get("lesson_type") == null || values.get("lesson_date") == null) return;
        String type = requiredString(values.get("lesson_type"));
        String date = LocalDate.parse(requiredString(values.get("lesson_date"))).toString();
        var scope = LessonNotificationScope.newBuilder().setGroupId(positive(values.get("group_id")))
                .setSubjectId(positive(values.get("subject_id"))).setSemesterId(positive(values.get("semester_id")))
                .setLessonType(type).setLessonDate(date);
        if (values.get("assignment_id") != null) scope.setAssignmentId(positive(values.get("assignment_id")));
        request.addLessonScopes(scope);
    }

    static Map<Long, Set<String>> validate(NotificationAudienceRequest request, NotificationAudienceResponse response) {
        if (response == null || !request.getRequestId().equals(response.getRequestId()) || !request.getEventType().equals(response.getEventType()))
            throw new IllegalStateException("Audience response scope is invalid");
        var allowed = Set.copyOf(NotificationCatalog.meaning(NotificationCatalog.eventType(request.getEventType(),
                Map.of("status", request.getRequestOutcome(), "marked_by", "headman")).orElseThrow()).roles());
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        for (var recipient : response.getRecipientsList()) {
            long id = recipient.getUserId();
            Set<String> roles = new LinkedHashSet<>(recipient.getRolesList());
            if (id <= 0 || result.containsKey(id) || roles.isEmpty() || roles.size() != recipient.getRolesCount()
                    || !ROLES.containsAll(roles) || !allowed.containsAll(roles)) throw new IllegalStateException("Audience recipients are invalid");
            boolean requestEvent = request.getEventType().startsWith("excuse.") || request.getEventType().startsWith("late_checkin.");
            if (requestEvent && request.hasAffectedUserId() && id == request.getAffectedUserId() && !roles.equals(Set.of("STUDENT")))
                throw new IllegalStateException("Private request owner projection is invalid");
            boolean personal = Set.of("attendance.marked", "homework.weekly_digest", "homework.due_reminder",
                    "group.membership.changed", "headman.access.changed", "assistant.access.changed", "admin.access.changed")
                    .contains(request.getEventType()) || (requestEvent && request.getEventType().endsWith(".decided")
                    && !"cancelled".equals(request.getRequestOutcome()));
            if (personal && (!request.hasAffectedUserId() || id != request.getAffectedUserId()))
                throw new IllegalStateException("Personal audience recipient is invalid");
            result.put(id, Set.copyOf(roles));
        }
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> payload(Map<String, Object> envelope) {
        if (!(envelope.get("payload") instanceof Map<?, ?> values) || values.keySet().stream().anyMatch(key -> !(key instanceof String)))
            throw new IllegalArgumentException("Notification payload must be an object");
        return (Map<String, Object>) values;
    }
    public static long positive(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Notification ID must be a positive integer");
        try { long id = new java.math.BigDecimal(number.toString()).longValueExact(); if (id > 0) return id; }
        catch (ArithmeticException | NumberFormatException ignored) { }
        throw new IllegalArgumentException("Notification ID must be a positive integer");
    }
    private static String requiredString(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Notification context is required");
        return text;
    }
}
