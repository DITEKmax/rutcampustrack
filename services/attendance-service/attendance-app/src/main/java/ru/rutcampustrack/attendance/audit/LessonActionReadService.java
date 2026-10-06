package ru.rutcampustrack.attendance.audit;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.attendance.contract.dto.report.LessonActionsResponse;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LessonActionReadService {
    private final MongoTemplate mongo;
    private final ScheduleGrpcClient schedule;
    private final AcademicGrpcClient academic;
    private final RequestContext actor;
    public LessonActionReadService(MongoTemplate mongo, ScheduleGrpcClient schedule,
                                   AcademicGrpcClient academic, RequestContext actor) {
        this.mongo = mongo; this.schedule = schedule; this.academic = academic; this.actor = actor;
    }

    public LessonActionsResponse read(Long lessonId, String cursor, int limit) {
        if (lessonId == null || lessonId <= 0 || limit < 1 || limit > 100) throw new BadRequestException("Некорректная страница истории");
        // Re-authorize before every query, including pages in an archived semester.
        var lesson = schedule.getLessonById(lessonId);
        if (actor.getUserId() == null || lesson == null || !Objects.equals(actor.getGroupId(), lesson.getGroupId())) {
            throw new AccessDeniedException("История доступна только для своей группы");
        }
        boolean headman = actor.isHeadman() && academic.isHeadman(actor.getUserId(), actor.getGroupId()).getIsHeadman();
        boolean excuses = headman || academic.hasAssistantPermission(actor.getGroupId(), "MANAGE_EXCUSES");
        boolean homework = headman || academic.hasAssistantPermission(actor.getGroupId(), "MANAGE_HOMEWORK");
        boolean mark = headman || academic.hasAssistantPermission(actor.getGroupId(), "MARK_ATTENDANCE");
        if (!mark && !excuses && !homework) throw new AccessDeniedException("Отсутствует право читать действия по паре");
        Criteria scope = Criteria.where("lesson_id").is(lessonId).and("group_id").is(actor.getGroupId())
                .and("semester_id").is(lesson.getSemesterId());
        Query query = Query.query(scope);
        if (cursor != null) {
            Cursor page = decode(cursor, lessonId);
            query.addCriteria(new Criteria().orOperator(Criteria.where("occurred_at").lt(page.at()),
                    Criteria.where("occurred_at").is(page.at()).and("_id").lt(page.id())));
        }
        query.with(Sort.by(Sort.Order.desc("occurred_at"), Sort.Order.desc("_id"))).limit(limit + 1);
        List<LessonActionDocument> rows = mongo.find(query, LessonActionDocument.class);
        List<LessonActionDocument> page = rows.stream().limit(limit).toList();
        String next = rows.size() <= limit ? null : encode(lessonId, page.get(page.size() - 1));
        return new LessonActionsResponse(page.stream().map(row -> entry(row, excuses, homework)).toList(), next);
    }

    @SuppressWarnings("unchecked")
    private static LessonActionsResponse.Entry entry(LessonActionDocument row, boolean excuses, boolean homework) {
        Map<String, Object> p = row.getPayload();
        String action = (String) p.get("action");
        boolean hideHomework = action.startsWith("HOMEWORK_") && !homework;
        Map<String, Object> before = hideHomework ? null : redact((Map<String, Object>) p.get("before"), excuses);
        Map<String, Object> after = hideHomework ? null : redact((Map<String, Object>) p.get("after"), excuses);
        Map<String, Object> geo = (Map<String, Object>) p.get("geo");
        return new LessonActionsResponse.Entry(row.getId(), row.getOccurredAt(), action,
                person((Map<String, Object>) p.get("actor")), person((Map<String, Object>) p.get("student")),
                before, after, geo == null ? null : new LessonActionsResponse.Geo(number(geo.get("distance_meters")),
                        number(geo.get("radius_meters")), number(geo.get("accuracy_meters")), (String) geo.get("verdict")),
                excuses ? (Map<String, Object>) p.get("ticket") : null,
                homework ? (Map<String, Object>) p.get("homework") : null,
                row.getSource(), "REJECTED".equals(p.get("outcome")) ? "REJECTED" : "APPLIED",
                "REJECTED".equals(p.get("outcome")) ? (String) p.get("error_code") : null);
    }

    static Map<String, Object> redact(Map<String, Object> source, boolean excuses) {
        if (source == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalized = key.toLowerCase(java.util.Locale.ROOT);
            if (excuses || !(normalized.contains("excuse") || normalized.contains("attachment")
                    || normalized.contains("file") || normalized.contains("comment") || normalized.contains("reason"))) {
                result.put(key, redactValue(value, excuses));
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object redactValue(Object value, boolean excuses) {
        if (value instanceof Map<?, ?> m) return redact((Map<String, Object>) m, excuses);
        if (value instanceof List<?> l) return l.stream().map(item -> redactValue(item, excuses)).toList();
        return value;
    }
    private static Double number(Object n) { return n == null ? null : ((Number) n).doubleValue(); }
    private static LessonActionsResponse.Person person(Map<String, Object> p) {
        return p == null ? null : new LessonActionsResponse.Person(((Number) p.get("user_id")).longValue(),
                (String) p.get("role"), (String) p.get("display_name"));
    }
    private record Cursor(Instant at, String id) { }
    private static String encode(Long lesson, LessonActionDocument row) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (lesson + "\n" + row.getOccurredAt() + "\n" + row.getId()).getBytes(StandardCharsets.UTF_8));
    }
    private static Cursor decode(String value, Long lesson) {
        try {
            if (value.length() > 512) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\n", -1);
            if (parts.length != 3 || !parts[0].equals(lesson.toString()) || !parts[2].matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[1]), parts[2]);
        } catch (RuntimeException invalid) { throw new BadRequestException("Некорректный cursor истории"); }
    }
}
