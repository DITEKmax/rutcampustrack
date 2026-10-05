package ru.rutcampustrack.attendance.checkin;

import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Revision and status always come from the same persisted document snapshot. */
public final class AttendanceRevisions {
    private AttendanceRevisions() { }

    public static String effective(AttendanceDocument document) {
        if (document == null) return null;
        if (document.getMarkRevision() != null && !document.getMarkRevision().isBlank()) {
            return document.getMarkRevision();
        }
        return "legacy-" + digest(Arrays.asList(document.getId(), semantic(document),
                document.getCreatedAt(), document.getUpdatedAt()));
    }

    public static boolean sameMeaning(AttendanceDocument before, AttendanceDocument after) {
        return before != null && after != null && semantic(before).equals(semantic(after));
    }

    public static AttendanceDocument copy(AttendanceDocument d) {
        if (d == null) return null;
        return new AttendanceDocument(d.getId(), d.getLessonId(), d.getUserId(), d.getGroupId(),
                d.getSubjectId(), d.getSemesterId(), d.getLessonNumber(), d.getLessonDate(), d.getStatus(),
                d.getSource(), d.getMarkedBy(), d.getExcuseReason(), d.getExcuseType(), d.getExcuseComment(),
                d.getAttachmentId(), d.getAttachmentName(), d.getAttachmentContentType(), d.getAttachmentSize(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getMarkRevision());
    }

    /** Evaluate the actual Mongo update before writing it, preserving no-op tokens. */
    @SuppressWarnings("unchecked")
    public static boolean prepare(AttendanceDocument before, Update update) {
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        String[] keys = {"lesson_id", "user_id", "group_id", "subject_id", "semester_id", "lesson_number", "lesson_date",
                "status", "source", "marked_by", "excuse_reason", "excuse_type", "excuse_comment",
                "attachment_id", "attachment_name", "attachment_content_type", "attachment_size"};
        if (before != null) {
            Object[] values = {before.getLessonId(), before.getUserId(), before.getGroupId(), before.getSubjectId(),
                    before.getSemesterId(), before.getLessonNumber(), before.getLessonDate(), before.getStatus(),
                    before.getSource(), before.getMarkedBy(), before.getExcuseReason(), before.getExcuseType(),
                    before.getExcuseComment(), before.getAttachmentId(), before.getAttachmentName(),
                    before.getAttachmentContentType(), before.getAttachmentSize()};
            for (int i = 0; i < keys.length; i++) state.put(keys[i], values[i]);
        }
        Document operations = update.getUpdateObject();
        if (before == null && operations.get("$setOnInsert") instanceof Map<?, ?> insert) state.putAll((Map<String, Object>) insert);
        if (operations.get("$set") instanceof Map<?, ?> set) state.putAll((Map<String, Object>) set);
        if (operations.get("$unset") instanceof Map<?, ?> unset) unset.keySet().forEach(key -> state.remove(key.toString()));
        if (before != null && semantic(before).equals(encode(Arrays.stream(keys).map(state::get).toList()))) return false;
        update.set("mark_revision", UUID.randomUUID().toString());
        return true;
    }

    private static String semantic(AttendanceDocument d) {
        return encode(Arrays.asList(d.getLessonId(), d.getUserId(), d.getGroupId(), d.getSubjectId(),
                d.getSemesterId(), d.getLessonNumber(), d.getLessonDate(), d.getStatus(), d.getSource(),
                d.getMarkedBy(), d.getExcuseReason(), d.getExcuseType(), d.getExcuseComment(),
                d.getAttachmentId(), d.getAttachmentName(), d.getAttachmentContentType(), d.getAttachmentSize()));
    }

    private static String encode(Iterable<?> values) {
        StringBuilder s = new StringBuilder();
        for (Object value : values) {
            if (value == null) s.append("-1:");
            else { String text = value.toString(); s.append(text.length()).append(':').append(text); }
        }
        return s.toString();
    }

    private static String digest(Iterable<?> values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(encode(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
