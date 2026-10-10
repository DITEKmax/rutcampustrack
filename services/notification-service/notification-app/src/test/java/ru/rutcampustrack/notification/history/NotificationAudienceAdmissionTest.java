package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class NotificationAudienceAdmissionTest {
    @Test void transferUsesEachFactualGroupAndCompleteTeacherTupleWithoutInventingLegacyAssignment() {
        var source = Map.<String, Object>of("lesson_id", 51L, "group_id", 7L, "subject_id", 9L,
                "semester_id", 3L, "lesson_type", "practice", "lesson_date", "2026-10-10", "assignment_id", 21L);
        var target = Map.<String, Object>of("lesson_id", 52L, "group_id", 8L, "subject_id", 9L,
                "semester_id", 3L, "lesson_type", "practice", "lesson_date", "2026-10-11");
        var requests = NotificationAudienceClient.requests(envelope("lesson.transferred", Map.of("source", source, "target", target)));
        assertThat(requests).extracting(NotificationAudienceRequest::getGroupId).containsExactly(7L, 8L);
        assertThat(requests.getFirst().getLessonScopesList()).hasSize(1);
        assertThat(requests.getFirst().getLessonScopes(0).getAssignmentId()).isEqualTo(21);
        assertThat(requests.getLast().getLessonScopes(0).getLessonDate()).isEqualTo("2026-10-11");
        var legacy = NotificationAudienceClient.request(envelope("lesson.cancelled", Map.of("group_id", 7L, "lesson_id", 51L)));
        assertThat(legacy.getGroupId()).isEqualTo(7);
        assertThat(legacy.getLessonScopesList()).isEmpty();
        var closed = NotificationAudienceClient.request(envelope("lesson.closed", source));
        assertThat(closed.getLessonScopesList()).isEmpty();
        var terminal = NotificationAudienceClient.request(envelope("semester.operation.completed",
                Map.of("semester_id", 3L, "actor_user_id", 12L, "action", "ARCHIVE", "outcome", "COMPLETED")));
        assertThat(terminal.getAffectedUserId()).isEqualTo(12L);
        assertThat(NotificationAudienceClient.validate(terminal, response(terminal, recipient(12, "ADMIN"))))
                .containsOnlyKeys(12L);
        assertThatThrownBy(() -> NotificationAudienceClient.validate(terminal, response(terminal, recipient(13, "ADMIN"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NotificationAudienceClient.requests(envelope("semester.operation.completed", Map.of("semester_id", 3L))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationAudienceClient.requests(envelope("lesson.cancelled",
                Map.of("group_id", 7L, "subject_id", 9L, "semester_id", 3L, "lesson_type", "practice", "lesson_date", "invalid"))))
                .isInstanceOf(java.time.DateTimeException.class);
    }

    @Test void responseEchoRolesAndPrivateOwnerAreValidatedBeforeUnion() {
        var request = NotificationAudienceClient.request(envelope("excuse.decided",
                Map.of("user_id", 11L, "group_id", 7L, "status", "cancelled", "ticket_id", "ticket-1")));
        assertThat(request.getRequestOutcome()).isEqualTo("cancelled");
        var valid = response(request, recipient(11, "STUDENT"), recipient(12, "STUDENT", "HEADMAN"));
        assertThat(NotificationAudienceClient.validate(request, valid)).containsEntry(12L, Set.of("STUDENT", "HEADMAN"));
        assertThatThrownBy(() -> NotificationAudienceClient.validate(request,
                response(request, recipient(11, "STUDENT", "HEADMAN")))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NotificationAudienceClient.validate(request,
                response(request, recipient(12, "ASSISTANT")))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NotificationAudienceClient.validate(request,
                response(request, recipient(12, "HEADMAN"), recipient(12, "STUDENT")))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NotificationAudienceClient.validate(request,
                valid.toBuilder().setRequestId(UUID.randomUUID().toString()).build())).isInstanceOf(IllegalStateException.class);
        var approved = request.toBuilder().setRequestOutcome("approved").build();
        assertThatThrownBy(() -> NotificationAudienceClient.validate(approved,
                response(approved, recipient(12, "STUDENT")))).isInstanceOf(IllegalStateException.class);
    }

    @Test void privateDecisionDetailsNeverEnterOtherRecipientsProjectionAndActionsUsePhysicalTarget() {
        var privatePayload = Map.<String, Object>of("user_id", 11L, "group_id", 7L, "ticket_id", "ticket-1",
                "status", "cancelled", "reason", "private reason", "files", List.of("private file"),
                "decision_comment", "private decision", "excluded_lesson_ids", List.of(51L));
        assertThat(NotificationPayloadSnapshot.forRecipient("excuse.decided", privatePayload, 12))
                .containsOnlyKeys("group_id", "ticket_id");
        assertThat(NotificationPayloadSnapshot.forRecipient("excuse.decided", privatePayload, 11))
                .containsEntry("decision_comment", "private decision").doesNotContainKeys("reason", "files");
        var payload = NotificationPayloadSnapshot.forRecipient("lesson.transferred",
                Map.of("source", Map.of("lesson_id", 51L), "target", Map.of("lesson_id", 52L, "lesson_date", "2026-10-11")), 12);
        var item = new NotificationCatalog().present(document(NotificationType.LESSON_TRANSFERRED, payload, "TEACHER"), "TEACHER");
        assertThat(item.action().lessonId()).isEqualTo("52");
        assertThat(item.action().role()).isEqualTo("TEACHER");
        assertThat(new NotificationCatalog().present(document(NotificationType.SEMESTER_ACTIVATED,
                Map.of("semester_id", 3L), "ADMIN"), "ADMIN").action()).isNull();
        assertThat(NotificationCatalog.typeIds()).hasSize(32);
        assertThat(NotificationCatalog.legacyTypeIds()).hasSize(21);
    }

    static Map<String, Object> envelope(String type, Map<String, Object> payload) {
        return Map.of("event_id", UUID.randomUUID().toString(), "event_type", type,
                "occurred_at", "2026-10-10T10:00:00Z", "payload", payload);
    }
    private static NotificationAudienceRecipient recipient(long id, String... roles) {
        return NotificationAudienceRecipient.newBuilder().setUserId(id).addAllRoles(List.of(roles)).build();
    }
    private static NotificationAudienceResponse response(NotificationAudienceRequest request, NotificationAudienceRecipient... values) {
        return NotificationAudienceResponse.newBuilder().setRequestId(request.getRequestId()).setEventType(request.getEventType())
                .addAllRecipients(List.of(values)).build();
    }
    private static NotificationHistoryDocument document(NotificationType type, Map<String, Object> payload, String role) {
        Instant now = Instant.parse("2026-10-10T10:00:00Z");
        return NotificationHistoryDocument.builder().id("test").type(type).payload(payload).sentAt(now)
                .roleViews(Map.of(role, NotificationHistoryDocument.RoleView.builder().orderAt(now).revision(0).build())).build();
    }
}
