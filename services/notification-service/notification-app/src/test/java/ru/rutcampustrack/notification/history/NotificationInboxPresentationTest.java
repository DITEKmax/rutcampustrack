package ru.rutcampustrack.notification.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
