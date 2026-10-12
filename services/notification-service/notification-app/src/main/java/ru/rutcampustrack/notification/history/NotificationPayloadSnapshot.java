package ru.rutcampustrack.notification.history;

import java.util.*;

/**
 * Sanctioned factual display fields only; other people's requests never retain the original ticket
 * body.
 */
public final class NotificationPayloadSnapshot {
    private NotificationPayloadSnapshot() {}

    private static final Set<String> IDS =
            Set.of(
                    "lesson_id",
                    "source_lesson_id",
                    "group_id",
                    "subject_id",
                    "semester_id",
                    "assignment_id",
                    "homework_id",
                    "old_group_id",
                    "new_group_id",
                    "cycle_semester_id");
    private static final Set<String> STRINGS =
            Set.of(
                    "subject_name",
                    "lesson_type",
                    "lesson_date",
                    "date",
                    "start_time",
                    "end_time",
                    "room",
                    "classroom",
                    "title",
                    "deadline",
                    "due_date",
                    "new_name",
                    "group_name",
                    "week_start",
                    "week_end",
                    "decided_at",
                    "ticket_id",
                    "request_id",
                    "status",
                    "change",
                    "effective_date",
                    "semester_name",
                    "date_from",
                    "date_to",
                    "action",
                    "outcome",
                    "mode",
                    "cycle_date_to",
                    "notification_cause",
                    "operation_id");
    private static final Set<String> COUNTS =
            Set.of("lesson_number", "total_count", "promoted", "graduated", "skipped", "version");
    private static final Set<String> FLAGS =
            Set.of("before_active", "after_active", "permissions_changed");
    private static final Set<String> PERMISSIONS =
            Set.of("before_permissions", "after_permissions", "permissions");

    public static Map<String, Object> forRecipient(
            String eventType, Map<String, Object> source, long recipient) {
        if ((eventType.startsWith("excuse.") || eventType.startsWith("late_checkin."))
                && recipient != NotificationAudienceClient.positive(source.get("user_id"))) {
            Map<String, Object> minimal = new LinkedHashMap<>();
            for (String key : List.of("group_id", "ticket_id", "request_id"))
                copyScalar(minimal, source, key);
            return Collections.unmodifiableMap(minimal);
        }
        Map<String, Object> result = factual(source);
        if (eventType.equals("excuse.decided") || eventType.equals("late_checkin.decided")) {
            Object comment = source.get("decision_comment");
            if (comment instanceof String text) result.put("decision_comment", bounded(text));
            for (String key :
                    List.of(
                            "included_lesson_ids",
                            "excluded_lesson_ids",
                            "included_occurrence_ids",
                            "excluded_occurrence_ids")) {
                if (source.get(key) instanceof List<?> ids) {
                    result.put(
                            key,
                            ids.stream()
                                    .limit(500)
                                    .map(
                                            id -> {
                                                String safe = NotificationCatalog.positiveId(id);
                                                if (safe == null)
                                                    throw new IllegalArgumentException(
                                                            "Decision lesson ID is invalid");
                                                return safe;
                                            })
                                    .toList());
                }
            }
            if (source.get("excluded_lessons") instanceof List<?> lessons) {
                result.put(
                        "excluded_lessons",
                        lessons.stream()
                                .limit(500)
                                .map(
                                        item -> {
                                            if (!(item instanceof Map<?, ?> values))
                                                throw new IllegalArgumentException(
                                                        "Decision lesson snapshot is invalid");
                                            return factual(values);
                                        })
                                .toList());
            }
        }
        if (source.get("homework") instanceof Map<?, ?> homework)
            result.put("homework", factual(homework));
        if (source.get("items") instanceof List<?> items)
            result.put(
                    "items",
                    items.stream()
                            .limit(100)
                            .map(
                                    item -> {
                                        if (!(item instanceof Map<?, ?> values))
                                            throw new IllegalArgumentException(
                                                    "Homework snapshot is invalid");
                                        return factual(values);
                                    })
                            .toList());
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> factual(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : IDS) copyScalar(result, source, key);
        for (String key : STRINGS) copyScalar(result, source, key);
        for (String key : COUNTS) copyScalar(result, source, key);
        for (String key : FLAGS) copyScalar(result, source, key);
        for (String key : PERMISSIONS) {
            if (source.get(key) instanceof List<?> values) {
                result.put(
                        key,
                        values.stream()
                                .map(
                                        value -> {
                                            if (!(value instanceof String text)
                                                    || !text.matches("[A-Za-z_]{1,64}"))
                                                throw new IllegalArgumentException(
                                                        "Permission snapshot is invalid");
                                            return text;
                                        })
                                .toList());
            }
        }
        for (String key : List.of("source", "target")) {
            if (source.get(key) instanceof Map<?, ?> value)
                result.put(key, Collections.unmodifiableMap(factual(value)));
        }
        // Old one-off classroom is a factual alias, never a replacement for the physical lesson ID.
        if (!result.containsKey("room") && result.get("classroom") instanceof String room)
            result.put("room", room);
        return result;
    }

    private static void copyScalar(Map<String, Object> result, Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value == null) return;
        if (IDS.contains(key)) result.put(key, NotificationAudienceClient.positive(value));
        else if (STRINGS.contains(key)) {
            if (!(value instanceof String text))
                throw new IllegalArgumentException("Display field must be a string");
            result.put(key, bounded(text));
        } else if (FLAGS.contains(key)) {
            if (!(value instanceof Boolean))
                throw new IllegalArgumentException("Display flag must be boolean");
            result.put(key, value);
        } else if (COUNTS.contains(key)) {
            if (!(value instanceof Number number))
                throw new IllegalArgumentException("Display count must be integer");
            long count;
            try {
                count = new java.math.BigDecimal(number.toString()).longValueExact();
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Display count must be integer");
            }
            if (count < 0 || (key.equals("lesson_number") && count == 0))
                throw new IllegalArgumentException("Display count is invalid");
            result.put(key, count);
        }
    }

    private static String bounded(String value) {
        String text = value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "").strip();
        return text.substring(0, Math.min(text.length(), 2000));
    }
}
