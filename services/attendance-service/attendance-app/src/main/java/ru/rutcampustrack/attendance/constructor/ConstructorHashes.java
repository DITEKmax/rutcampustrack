package ru.rutcampustrack.attendance.constructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.util.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

/** Constructor v1 canonical JSON, identical to Schedule's immutable manifest encoding. */
public final class ConstructorHashes {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ConstructorHashes() {}

    public static ByteString hash(Object value) {
        return bytes(canonical(value).getBytes(StandardCharsets.UTF_8));
    }

    public static ByteString bytes(byte[] value) {
        try {
            return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("JSON key");
                sorted.put(text, item);
            });
            return sorted.entrySet().stream().map(e -> canonical(e.getKey()) + ":" + canonical(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> list) {
            List<String> items = new ArrayList<>();
            list.forEach(item -> items.add(canonical(item)));
            return String.join(",", items).transform(text -> "[" + text + "]");
        }
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Boolean bool) return bool.toString();
        if (value instanceof String text) {
            try { return JSON.writeValueAsString(text); }
            catch (com.fasterxml.jackson.core.JsonProcessingException impossible) { throw new IllegalArgumentException(impossible); }
        }
        throw new IllegalArgumentException("Unsupported canonical JSON value");
    }

    public static ByteString lesson(LessonResponse lesson) {
        Map<String, Object> p = new TreeMap<>();
        p.put("lesson_id", lesson.getId());
        p.put("occurrence_id", lesson.getOccurrenceId());
        p.put("group_id", lesson.getGroupId());
        p.put("subject_id", lesson.getSubjectId());
        p.put("semester_id", lesson.getSemesterId());
        p.put("lesson_type", lesson.getLessonType().toLowerCase(Locale.ROOT));
        p.put("date", java.time.LocalDate.parse(lesson.getDate()).toString());
        p.put("lesson_number", lesson.getLessonNumber());
        p.put("start_time", LocalTime.parse(lesson.getStartTime()).toString());
        p.put("end_time", LocalTime.parse(lesson.getEndTime()).toString());
        p.put("generation", lesson.getGeneration());
        return hash(p);
    }
}
