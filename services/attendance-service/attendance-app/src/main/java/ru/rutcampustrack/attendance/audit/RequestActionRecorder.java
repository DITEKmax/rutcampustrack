package ru.rutcampustrack.attendance.audit;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Captures true ticket before state at the fenced transition, not a projected journal status. */
@Service
public class RequestActionRecorder {
    private final LessonActionService actions;
    public RequestActionRecorder(LessonActionService actions) { this.actions = actions; }

    public void excuse(ExcuseTicket ticket, Map<String, Object> before, Long actor, String role, Instant at) {
        java.util.Set<Long> lessons = new java.util.TreeSet<>(ticket.getLessonIds());
        if (ticket.getDecisionSelection() != null) ticket.getDecisionSelection().getExcludedLessons()
                .forEach(snapshot -> lessons.add(snapshot.getLessonId()));
        for (Long lesson : lessons) {
            actions.local(ticket.getGroupId(), ticket.getSemesterId(), lesson, action(before, ticket.getStatus().name()),
                    LessonActionService.person(actor, role, null),
                    LessonActionService.person(ticket.getStudentId(), "STUDENT", ticket.getStudentName()),
                    before, snapshot(ticket), null, reference("EXCUSE", ticket.getId(), ticket.getStatus().name()), at);
        }
    }

    public void late(LateCheckinRequest request, Map<String, Object> before, Long actor, String role, Instant at) {
        actions.local(request.getGroupId(), request.getSemesterId(), request.getLessonId(),
                action(before, request.getStatus().name()), LessonActionService.person(actor, role, null),
                LessonActionService.person(request.getStudentId(), "STUDENT", request.getStudentName()),
                before, snapshot(request), null, reference("LATE_CHECKIN", request.getId(), request.getStatus().name()), at);
    }

    private static String action(Map<String, Object> before, String status) {
        return before == null ? "REQUEST_SUBMITTED" : "CANCELLED".equals(status) ? "REQUEST_CANCELLED" : "REQUEST_DECIDED";
    }

    public static Map<String, Object> reference(String kind, String id, String status) {
        return Map.of("kind", kind, "id", id, "status", status);
    }

    public static Map<String, Object> snapshot(ExcuseTicket t) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", t.getStatus().name()); p.put("excuse_type", t.getExcuseType() == null ? null : t.getExcuseType().name());
        p.put("excuse_comment", t.getComment()); p.put("decision_by", t.getDecisionBy());
        p.put("decision_comment", t.getDecisionComment()); p.put("decision_at", t.getDecisionAt() == null ? null : t.getDecisionAt().toString());
        p.put("excuse_lesson_ids", t.getLessonIds());
        if (t.getDecisionSelection() != null) {
            var selected = t.getDecisionSelection();
            p.put("excuse_decision_selection", Map.of("request_key", selected.getRequestKey(), "expected_revision", selected.getExpectedRevision(),
                    "included_lessons", selected.getIncludedLessons(), "excluded_lessons", selected.getExcludedLessons()));
        }
        return p;
    }

    public static Map<String, Object> snapshot(LateCheckinRequest t) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", t.getStatus().name()); p.put("origin", t.getOrigin() == null ? null : t.getOrigin().name());
        p.put("resolution_reason", t.getResolutionReason() == null ? null : t.getResolutionReason().name());
        p.put("decision_by", t.getDecisionBy()); p.put("decision_comment", t.getDecisionComment());
        p.put("decision_at", t.getDecisionAt() == null ? null : t.getDecisionAt().toString());
        return p;
    }
}
