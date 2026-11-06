package ru.rutcampustrack.notification.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.notification.security.RequestContext;

class NotificationInboxPresentationTest {
    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final NotificationSubjectNames names = mock(NotificationSubjectNames.class);
    private final NotificationInboxService inbox =
            new NotificationInboxService(
                    mongo, new NotificationCatalog(), Clock.systemUTC(), null, names);

    @Test
    void resolvesOnlyNamesMissingFromTheVisiblePageAndPreservesRoomSnapshot() {
        var old = Map.of("lesson_id", 51L, "subject_id", 23L, "lesson_type", "practice", "room", "А-214");
        var current = Map.of("lesson_id", 51L, "subject_id", 23L, "lesson_type", "practice", "room", "А-508");
        var room = document("room", NotificationType.LESSON_UPDATED, Map.of("source", old, "target", current));
        var named = document("named", NotificationType.LESSON_STARTED,
                Map.of("subject_id", 24L, "subject_name", "Имя на момент события"));
        var overflow = document("overflow", NotificationType.LESSON_CLOSED, Map.of("subject_id", 25L));
        when(mongo.find(any(Query.class), eq(NotificationHistoryDocument.class)))
                .thenReturn(List.of(), List.of(room, named, overflow));
        when(names.lookup(Set.of(23L))).thenReturn(Map.of(23L, "Компьютерные сети"));

        var page = inbox.list(context(), 2, null, false);
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.items()).extracting(NotificationInboxDto.Item::id).containsExactly("room", "named");
        var first = page.items().getFirst();
        assertThat(first.heading()).isEqualTo("Изменилась аудитория");
        assertThat(first.details()).containsExactly(
                new NotificationInboxDto.Detail("Предмет", "Компьютерные сети"),
                new NotificationInboxDto.Detail("Тип занятия", "Практика"),
                new NotificationInboxDto.Detail("Предыдущая аудитория", "А-214"),
                new NotificationInboxDto.Detail("Новая аудитория", "А-508"));
        assertThat(first.action().lessonId()).isEqualTo("51");
        assertThat(page.items().getLast().summary()).isEqualTo("Имя на момент события");
        verify(names).lookup(Set.of(23L));
        verifyNoMoreInteractions(names);
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).find(query.capture(), eq(NotificationHistoryDocument.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("user_id", 11L)
                .containsKey("role_views.STUDENT");
    }

    @Test
    void keepsAuthorizedReadAcknowledgementUsableWithoutAcademicAndNeverEnrichesMissingRows() {
        when(mongo.find(any(Query.class), eq(NotificationHistoryDocument.class))).thenReturn(List.of());
        assertThatThrownBy(
                        () -> inbox.readState(context(), "other-users-row",
                                new NotificationInboxDto.ReadState(false, "0")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verifyNoInteractions(names);

        var homework = document("homework", NotificationType.HOMEWORK_DUE_REMINDER,
                Map.of("homework", Map.of("subject_id", 27L, "title", "Упражнение", "lesson_date", "2026-10-15")));
        when(mongo.findOne(any(Query.class), eq(NotificationHistoryDocument.class))).thenReturn(homework);
        when(names.lookup(Set.of(27L))).thenReturn(Map.of());
        var ack = inbox.readState(context(), "homework", new NotificationInboxDto.ReadState(false, "0"));
        assertThat(ack.item().summary()).isNull();
        assertThat(ack.item().details()).containsExactly(
                new NotificationInboxDto.Detail("Задание", "Упражнение · 2026-10-15"));
        verify(names).lookup(Set.of(27L));
    }

    @Test
    void usesCompatibleSnapshotFieldsIndividuallyAndKeepsRoomRemovalFactual() {
        var catalog = new NotificationCatalog();
        var compatible = document("compatible", NotificationType.LESSON_UPDATED,
                Map.of("subject_name", "Имя при отправке", "lesson_type", "lab",
                        "source", Map.of("room", "А-214"), "target", Map.of("subject_id", 23L)));
        assertThat(NotificationCatalog.inboxSubjectLookupId(compatible)).isNull();
        assertThat(catalog.presentInbox(compatible, "STUDENT", Map.of()).details()).containsExactly(
                new NotificationInboxDto.Detail("Предмет", "Имя при отправке"),
                new NotificationInboxDto.Detail("Тип занятия", "Лабораторная работа"),
                new NotificationInboxDto.Detail("Предыдущая аудитория", "А-214"),
                new NotificationInboxDto.Detail("Новая аудитория", "Не указана"));

        var different = document("different", NotificationType.LESSON_UPDATED,
                Map.of("subject_id", 24L, "subject_name", "Чужое имя", "lesson_type", "lecture",
                        "source", Map.of("room", "А-214"), "target", Map.of("subject_id", 23L)));
        assertThat(NotificationCatalog.inboxSubjectLookupId(different)).isEqualTo(23L);
        assertThat(catalog.presentInbox(different, "STUDENT", Map.of(23L, "Точное имя")).details())
                .containsExactly(
                        new NotificationInboxDto.Detail("Предмет", "Точное имя"),
                        new NotificationInboxDto.Detail("Предыдущая аудитория", "А-214"),
                        new NotificationInboxDto.Detail("Новая аудитория", "Не указана"));
    }

    @Test
    void pushUsesTheSavedRoomAndSubjectSnapshotWithoutAnAcademicLookup() {
        var catalog = new NotificationCatalog();
        var room = document("room", NotificationType.LESSON_UPDATED,
                Map.of("subject_id", 42L, "subject_name", "Имя другого предмета",
                        "source", Map.of("room", "А-214"),
                        "target", Map.of("subject_id", 23L, "subject_name", "Имя из события",
                                "lesson_type", "practice", "room", "А-508")));
        var push = catalog.present(room, "STUDENT");
        assertThat(push.heading()).isEqualTo("Изменилась аудитория");
        assertThat(push.details()).containsExactly(
                new NotificationInboxDto.Detail("Предмет", "Имя из события"),
                new NotificationInboxDto.Detail("Тип занятия", "Практика"),
                new NotificationInboxDto.Detail("Предыдущая аудитория", "А-214"),
                new NotificationInboxDto.Detail("Новая аудитория", "А-508"));
        assertThat(push).isEqualTo(catalog.presentInbox(room, "STUDENT", Map.of()));
    }

    @Test
    void dueReminderUsesTheExactNestedTargetAndKeepsRoutableLegacyDates() {
        var catalog = new NotificationCatalog();
        var type = NotificationType.HOMEWORK_DUE_REMINDER;
        var current = document("reminder", type,
                Map.of("homework_id", 7L, "lesson_date", "2026-10-10", "due_date", "2026-10-17",
                        "homework", Map.of("homework_id", 73L, "lesson_date", "2026-10-15")));
        var action = catalog.present(current, "STUDENT").action();
        assertThat(action.homeworkId()).isEqualTo("73");
        assertThat(action.lessonDate()).isEqualTo("2026-10-15");
        assertThat(catalog.presentInbox(current, "STUDENT", Map.of()).action()).isEqualTo(action);
        assertThat(catalog.action(type, Map.of("homework_id", 73L, "date", "2026-10-15"), "STUDENT"))
                .isEqualTo(action);
        assertThat(catalog.action(type, Map.of("lesson_date", "2026-10-15",
                "homework", Map.of("homework_id", 73L)), "STUDENT")).isEqualTo(action);
        assertThat(catalog.action(type, Map.of("homework_id", 7L, "lesson_date", "2026-10-10",
                "homework", Map.of("homework_id", 0L, "lesson_date", "2026-10-15")), "STUDENT"))
                .isNull();
        assertThat(catalog.action(type, Map.of("homework_id", 7L, "lesson_date", "2026-10-10",
                "homework", Map.of("lesson_date", "2026-10-15")), "STUDENT")).isNull();
        assertThat(catalog.action(type, Map.of("homework_id", 7L, "lesson_date", "2026-10-10",
                "homework", Map.of("homework_id", 73L)), "STUDENT")).isNull();
        assertThat(catalog.action(type, Map.of("homework", Map.of("homework_id", 73L,
                "lesson_date", "2026-02-30")), "STUDENT")).isNull();
        assertThat(catalog.action(type, Map.of("homework", Map.of("homework_id", 73L),
                "due_date", "2026-10-17"), "STUDENT")).isNull();
    }

    @Test
    void legacyReminderTargetSurvivesInboxAdoptionAndBothPushProjections() {
        var catalog = new NotificationCatalog();
        var type = NotificationType.HOMEWORK_DUE_REMINDER;
        Map<String, Object> payload =
                Map.of(
                        "homework_id", 7L,
                        "lesson_date", "2026-10-10",
                        "homework",
                                Map.of(
                                        "id", "73",
                                        "lesson_date", "2026-10-15",
                                        "description", "Не сохраняется в уведомлении"));
        var legacy = catalog.presentInbox(document("legacy", type, payload), "STUDENT", Map.of());
        assertThat(legacy.action().homeworkId()).isEqualTo("73");
        assertThat(legacy.action().lessonDate()).isEqualTo("2026-10-15");

        var projected = retainedDueReminder(payload);
        assertThat(projected.get("homework"))
                .isEqualTo(Map.of("homework_id", 73L, "lesson_date", "2026-10-15"));
        assertThat(catalog.present(document("push", type, projected), "STUDENT").action())
                .isEqualTo(legacy.action());
        assertThat(NotificationPayloadSnapshot.forRecipient("homework.updated", payload, 11)
                        .get("homework"))
                .isEqualTo(Map.of("lesson_date", "2026-10-15"));
    }

    @Test
    void reminderProjectionKeepsExplicitNullAndNeverGuessesAConflictingDateOrId() {
        var catalog = new NotificationCatalog();
        var type = NotificationType.HOMEWORK_DUE_REMINDER;
        Map<String, Object> nullDate = new LinkedHashMap<>(Map.of("id", 73L));
        nullDate.put("lesson_date", null);
        Map<String, Object> nullCanonicalId = new LinkedHashMap<>(Map.of("id", 73L));
        nullCanonicalId.put("homework_id", null);
        for (var homework :
                List.of(nullDate, nullCanonicalId, Map.of("id", 74L), Map.of("id", "73.0"))) {
            Map<String, Object> payload =
                    Map.of("homework_id", 73L, "lesson_date", "2026-10-15", "homework", homework);
            assertThat(catalog.action(type, payload, "STUDENT")).isNull();
            assertThat(catalog.action(type, retainedDueReminder(payload), "STUDENT")).isNull();
        }

        Map<String, Object> current =
                Map.of("homework", Map.of("homework_id", 73L, "id", 74L,
                        "lesson_date", "2026-10-15"));
        assertThat(catalog.action(type, retainedDueReminder(current), "STUDENT").homeworkId())
                .isEqualTo("73");
        Map<String, Object> invalidCanonical =
                Map.of("homework", Map.of("homework_id", 0L, "id", 73L,
                        "lesson_date", "2026-10-15"));
        assertThat(catalog.action(type, invalidCanonical, "STUDENT")).isNull();
        assertThatThrownBy(() -> retainedDueReminder(invalidCanonical))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> retainedDueReminder(Map<String, Object> payload) {
        var stored =
                NotificationPayloadSnapshot.forRecipient("homework.due_reminder", payload, 11);
        Document retained = Document.parse(new Document("payload", stored).toJson());
        return NotificationPayloadSnapshot.forRecipient(
                "homework.due_reminder", retained.get("payload", Document.class), 11);
    }

    @Test
    void homeworkActionRequiresAnExistingTargetAndASupportedRole() {
        var catalog = new NotificationCatalog();
        Map<String, Object> data = Map.of("homework_id", 73L, "lesson_date", "2026-10-15",
                "title", "Упражнение");
        assertThat(catalog.action(NotificationType.HOMEWORK_PUBLISHED, data, "TEACHER")).isNull();
        assertThat(catalog.action(NotificationType.HOMEWORK_UPDATED, data, "TEACHER")).isNull();
        assertThat(catalog.action(NotificationType.HOMEWORK_PUBLISHED, data, "STUDENT").homeworkId())
                .isEqualTo("73");
        assertThat(catalog.action(NotificationType.HOMEWORK_UPDATED,
                Map.of("homework_id", 73L), "HEADMAN").homeworkId()).isEqualTo("73");

        var withdrawn = document("withdrawn", NotificationType.HOMEWORK_WITHDRAWN, data);
        assertThat(catalog.present(withdrawn, "STUDENT").action()).isNull();
        assertThat(catalog.presentInbox(withdrawn, "STUDENT", Map.of()).details())
                .contains(new NotificationInboxDto.Detail("Задание", "Упражнение"));
    }

    @Test
    void legacyReadDoesNotLookUpAnUnusedSubjectName() {
        var lesson = document("read", NotificationType.LESSON_STARTED, Map.of("subject_id", 23L));
        lesson.getRoleViews().get("STUDENT").setReadAt(NOW);
        when(mongo.find(any(Query.class), eq(NotificationHistoryDocument.class))).thenReturn(List.of());
        when(mongo.findOne(any(Query.class), eq(NotificationHistoryDocument.class))).thenReturn(lesson);
        assertThat(inbox.legacyRead(context(), "read")).isTrue();
        verifyNoInteractions(names);
    }

    private static NotificationHistoryDocument document(
            String id, NotificationType type, Map<String, Object> payload) {
        return NotificationHistoryDocument.builder()
                .id(id)
                .type(type)
                .sentAt(NOW)
                .payload(payload)
                .roleViews(Map.of("STUDENT",
                        NotificationHistoryDocument.RoleView.builder().orderAt(NOW).build()))
                .build();
    }

    private static RequestContext context() {
        var context = new RequestContext();
        context.setUserId(11L);
        context.setSelectedRole("STUDENT");
        context.setSessionId(UUID.randomUUID());
        context.setInternalToken("synthetic-context");
        context.setSessionVersion(1);
        context.setRolesVersion(1);
        return context;
    }
}
