package ru.rutcampustrack.schedule.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal factual scope; callers supply their locked or durable mutation snapshot. */
public final class LessonNotificationSnapshot {
    private LessonNotificationSnapshot() { }

    public static Map<String, Object> from(Map<String, Object> snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of("lesson_id", "group_id", "subject_id", "semester_id", "lesson_type",
                "assignment_id", "assigned_teacher_id", "start_time", "end_time", "lesson_number")) {
            Object value = snapshot.get(field);
            // The legacy audit snapshot used String.valueOf for absent times.
            if (("start_time".equals(field) || "end_time".equals(field)) && "null".equals(value)) continue;
            if (value != null) result.put(field, value);
        }
        if (snapshot.get("date") != null) result.put("lesson_date", snapshot.get("date").toString());
        Object room = snapshot.containsKey("room") ? snapshot.get("room") : snapshot.get("room_snapshot");
        if (room != null) result.put("room", room);
        return Collections.unmodifiableMap(result);
    }
}
