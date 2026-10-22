package ru.rutcampustrack.notification.history;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.contract.dto.history.NotificationCatalogDto;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

/** Server-owned business meanings, shared by history and external delivery admission. */
@Component
public class NotificationCatalog {
    public static final String REVISION = "notifications-v2-20261010-all-roles-32";
    public static final List<String> COLORS =
            List.of(
                    "violet",
                    "lavender",
                    "purple",
                    "indigo",
                    "blue",
                    "cobalt",
                    "sky",
                    "cyan",
                    "turquoise",
                    "teal",
                    "green",
                    "mint",
                    "lime",
                    "olive",
                    "yellow",
                    "amber",
                    "orange",
                    "coral",
                    "red",
                    "raspberry",
                    "pink",
                    "rose",
                    "brown",
                    "grey");

    public record Meaning(
            NotificationType type,
            String category,
            String group,
            String heading,
            List<String> roles) {}

    private static final List<String> STUDENT = List.of("STUDENT");
    private static final List<String> HEADMAN = List.of("HEADMAN");
    private static final List<String> GROUP = List.of("STUDENT", "HEADMAN");
    private static final List<String> EDUCATIONAL = List.of("STUDENT", "HEADMAN", "TEACHER");
    private static final List<String> GROUP_ADMIN = List.of("STUDENT", "HEADMAN", "ADMIN");
    private static final List<String> ADMIN = List.of("ADMIN");
    private static final List<Meaning> MEANINGS =
            List.of(
                    meaning(
                            NotificationType.LESSON_STARTED,
                            "reminders",
                            "lessons",
                            "Занятие началось",
                            GROUP),
                    meaning(
                            NotificationType.LESSON_REMINDER,
                            "reminders",
                            "lessons",
                            "Напоминание об отметке",
                            GROUP),
                    meaning(
                            NotificationType.LESSON_CLOSED,
                            "lessons",
                            "lessons",
                            "Занятие завершено",
                            GROUP),
                    meaning(
                            NotificationType.LESSON_CANCELLED,
                            "lessons",
                            "lessons",
                            "Занятие отменено",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.LESSON_BLOCKED,
                            "lessons",
                            "lessons",
                            "Самостоятельная отметка закрыта",
                            GROUP),
                    meaning(
                            NotificationType.LESSON_ONE_OFF_CREATED,
                            "schedule",
                            "lessons",
                            "Добавлено разовое занятие",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.EXCUSE_REQUESTED,
                            "tickets",
                            "requests",
                            "Новая заявка на уважительную причину",
                            GROUP),
                    meaning(
                            NotificationType.EXCUSE_APPROVED,
                            "tickets",
                            "requests",
                            "Уважительная причина одобрена",
                            STUDENT),
                    meaning(
                            NotificationType.EXCUSE_REJECTED,
                            "tickets",
                            "requests",
                            "Уважительная причина отклонена",
                            STUDENT),
                    meaning(
                            NotificationType.EXCUSE_CANCELLED,
                            "tickets",
                            "requests",
                            "Заявка на уважительную причину отменена",
                            GROUP),
                    meaning(
                            NotificationType.LATE_CHECKIN_REQUESTED,
                            "tickets",
                            "requests",
                            "Новая заявка на отметку",
                            GROUP),
                    meaning(
                            NotificationType.LATE_CHECKIN_APPROVED,
                            "tickets",
                            "requests",
                            "Заявка на отметку одобрена",
                            STUDENT),
                    meaning(
                            NotificationType.LATE_CHECKIN_REJECTED,
                            "tickets",
                            "requests",
                            "Заявка на отметку отклонена",
                            STUDENT),
                    meaning(
                            NotificationType.LATE_CHECKIN_CANCELLED,
                            "tickets",
                            "requests",
                            "Заявка на отметку отменена",
                            GROUP),
                    meaning(
                            NotificationType.ATTENDANCE_MARKED_BY_HEADMAN,
                            "tickets",
                            "attendance",
                            "Отметка посещаемости изменена",
                            STUDENT),
                    meaning(
                            NotificationType.HOMEWORK_PUBLISHED,
                            "homework",
                            "homework",
                            "Новое домашнее задание",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.HOMEWORK_UPDATED,
                            "homework",
                            "homework",
                            "Домашнее задание изменено",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.HOMEWORK_WEEKLY_DIGEST,
                            "homework",
                            "homework",
                            "Домашние задания на неделю",
                            STUDENT),
                    meaning(
                            NotificationType.HOMEWORK_DUE_REMINDER,
                            "homework",
                            "homework",
                            "Скоро срок домашнего задания",
                            STUDENT),
                    meaning(
                            NotificationType.GROUP_RENAMED,
                            "group",
                            "group",
                            "Название группы изменено",
                            GROUP_ADMIN),
                    meaning(
                            NotificationType.GROUP_ARCHIVED,
                            "group",
                            "group",
                            "Группа архивирована",
                            GROUP_ADMIN),
                    meaning(
                            NotificationType.LESSON_TRANSFERRED,
                            "lessons",
                            "lessons",
                            "Занятие перенесено",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.LESSON_RESTORED,
                            "lessons",
                            "lessons",
                            "Занятие восстановлено",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.LESSON_UPDATED,
                            "lessons",
                            "lessons",
                            "Занятие изменено",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.HOMEWORK_WITHDRAWN,
                            "homework",
                            "homework",
                            "Домашнее задание снято",
                            EDUCATIONAL),
                    meaning(
                            NotificationType.GROUP_MEMBERSHIP_CHANGED,
                            "group",
                            "group",
                            "Твоя группа изменена",
                            STUDENT),
                    meaning(
                            NotificationType.HEADMAN_ACCESS_CHANGED,
                            "group",
                            "group",
                            "Полномочия старосты изменены",
                            STUDENT),
                    meaning(
                            NotificationType.ASSISTANT_ACCESS_CHANGED,
                            "group",
                            "group",
                            "Полномочия помощника изменены",
                            STUDENT),
                    meaning(
                            NotificationType.ADMIN_ACCESS_CHANGED,
                            "group",
                            "group",
                            "Административные полномочия изменены",
                            ADMIN),
                    meaning(
                            NotificationType.SEMESTER_ACTIVATED,
                            "group",
                            "group",
                            "Семестр активирован",
                            ADMIN),
                    meaning(
                            NotificationType.SEMESTER_OPERATION_COMPLETED,
                            "group",
                            "group",
                            "Операция с семестром завершена",
                            ADMIN),
                    meaning(
                            NotificationType.GROUP_PROMOTION_COMPLETED,
                            "group",
                            "group",
                            "Обработана партия автоматического перевода групп",
                            ADMIN));

    /** Exact old profile shape is the only supported additive migration source. */
    public static Set<String> legacyTypeIds() {
        return typeIds().stream()
                .filter(
                        id ->
                                !Set.of(
                                                "LESSON_TRANSFERRED",
                                                "LESSON_RESTORED",
                                                "LESSON_UPDATED",
                                                "HOMEWORK_WITHDRAWN",
                                                "GROUP_MEMBERSHIP_CHANGED",
                                                "HEADMAN_ACCESS_CHANGED",
                                                "ASSISTANT_ACCESS_CHANGED",
                                                "ADMIN_ACCESS_CHANGED",
                                                "SEMESTER_ACTIVATED",
                                                "SEMESTER_OPERATION_COMPLETED",
                                                "GROUP_PROMOTION_COMPLETED")
                                        .contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Meaning meaning(
            NotificationType type,
            String category,
            String group,
            String heading,
            List<String> roles) {
        return new Meaning(type, category, category, heading, roles);
    }

    public static List<Meaning> meanings() {
        return MEANINGS;
    }

    public static Set<String> typeIds() {
        return MEANINGS.stream()
                .map(value -> value.type().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static Meaning meaning(NotificationType type) {
        return MEANINGS.stream()
                .filter(value -> value.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported notification type"));
    }

    public NotificationCatalogDto catalog(Set<String> accountRoles) {
        List<NotificationCatalogDto.Type> types =
                MEANINGS.stream()
                        .map(
                                value ->
                                        new NotificationCatalogDto.Type(
                                                value.type().name(),
                                                value.group(),
                                                value.heading(),
                                                value.roles()))
                        .toList();
        return new NotificationCatalogDto(
                2,
                REVISION,
                List.of(
                        new NotificationCatalogDto.Group("lessons", "Занятия"),
                        new NotificationCatalogDto.Group("reminders", "Напоминания"),
                        new NotificationCatalogDto.Group("tickets", "Заявки и посещаемость"),
                        new NotificationCatalogDto.Group("schedule", "Расписание"),
                        new NotificationCatalogDto.Group("homework", "Домашние задания"),
                        new NotificationCatalogDto.Group("group", "Группа")),
                types,
                java.util.stream.IntStream.range(0, COLORS.size())
                        .mapToObj(
                                index ->
                                        new NotificationCatalogDto.Color(
                                                COLORS.get(index),
                                                List.of(
                                                                "Фиолетовый",
                                                                "Лавандовый",
                                                                "Пурпурный",
                                                                "Индиго",
                                                                "Синий",
                                                                "Кобальтовый",
                                                                "Небесный",
                                                                "Голубой",
                                                                "Бирюзовый",
                                                                "Морской",
                                                                "Зелёный",
                                                                "Мятный",
                                                                "Лаймовый",
                                                                "Оливковый",
                                                                "Жёлтый",
                                                                "Янтарный",
                                                                "Оранжевый",
                                                                "Коралловый",
                                                                "Красный",
                                                                "Малиновый",
                                                                "Розовый",
                                                                "Розовый красный",
                                                                "Коричневый",
                                                                "Серый")
                                                        .get(index)))
                        .toList());
    }

    public static Optional<NotificationType> eventType(String event, Map<String, Object> payload) {
        return switch (event) {
            case "lesson.started" -> Optional.of(NotificationType.LESSON_STARTED);
            case "lesson.reminder" -> Optional.of(NotificationType.LESSON_REMINDER);
            case "lesson.closed" -> Optional.of(NotificationType.LESSON_CLOSED);
            case "lesson.cancelled", "lesson.one_off.cancelled" ->
                    Optional.of(NotificationType.LESSON_CANCELLED);
            case "lesson.blocked" -> Optional.of(NotificationType.LESSON_BLOCKED);
            case "lesson.one_off.created" -> Optional.of(NotificationType.LESSON_ONE_OFF_CREATED);
            case "group.renamed" -> Optional.of(NotificationType.GROUP_RENAMED);
            case "group.archived" -> Optional.of(NotificationType.GROUP_ARCHIVED);
            case "excuse.requested" -> Optional.of(NotificationType.EXCUSE_REQUESTED);
            case "late_checkin.requested" -> Optional.of(NotificationType.LATE_CHECKIN_REQUESTED);
            case "excuse.decided" ->
                    Optional.of(
                            decision(
                                    payload,
                                    NotificationType.EXCUSE_APPROVED,
                                    NotificationType.EXCUSE_REJECTED,
                                    NotificationType.EXCUSE_CANCELLED));
            case "late_checkin.decided" ->
                    Optional.of(
                            decision(
                                    payload,
                                    NotificationType.LATE_CHECKIN_APPROVED,
                                    NotificationType.LATE_CHECKIN_REJECTED,
                                    NotificationType.LATE_CHECKIN_CANCELLED));
            case "attendance.marked" ->
                    "headman".equals(payload.get("marked_by"))
                            ? Optional.of(NotificationType.ATTENDANCE_MARKED_BY_HEADMAN)
                            : Optional.empty();
            case "homework.published" -> Optional.of(NotificationType.HOMEWORK_PUBLISHED);
            case "homework.updated" -> Optional.of(NotificationType.HOMEWORK_UPDATED);
            case "lesson.transferred" -> Optional.of(NotificationType.LESSON_TRANSFERRED);
            case "lesson.restored" -> Optional.of(NotificationType.LESSON_RESTORED);
            case "lesson.updated" -> Optional.of(NotificationType.LESSON_UPDATED);
            case "homework.withdrawn" -> Optional.of(NotificationType.HOMEWORK_WITHDRAWN);
            case "group.membership.changed" ->
                    Optional.of(NotificationType.GROUP_MEMBERSHIP_CHANGED);
            case "headman.access.changed" -> Optional.of(NotificationType.HEADMAN_ACCESS_CHANGED);
            case "assistant.access.changed" ->
                    Optional.of(NotificationType.ASSISTANT_ACCESS_CHANGED);
            case "admin.access.changed" -> Optional.of(NotificationType.ADMIN_ACCESS_CHANGED);
            case "semester.activated" -> Optional.of(NotificationType.SEMESTER_ACTIVATED);
            case "semester.operation.completed" ->
                    Optional.of(NotificationType.SEMESTER_OPERATION_COMPLETED);
            case "group.promotion.completed" ->
                    Optional.of(NotificationType.GROUP_PROMOTION_COMPLETED);
            case "homework.weekly_digest" -> Optional.of(NotificationType.HOMEWORK_WEEKLY_DIGEST);
            case "homework.due_reminder" -> Optional.of(NotificationType.HOMEWORK_DUE_REMINDER);
            default -> Optional.empty();
        };
    }

    private static NotificationType decision(
            Map<String, Object> payload,
            NotificationType approved,
            NotificationType rejected,
            NotificationType cancelled) {
        return switch (Objects.toString(payload.get("status"), "")) {
            case "approved" -> approved;
            case "rejected" -> rejected;
            case "cancelled" -> cancelled;
            default -> throw new IllegalArgumentException("Unsupported notification decision");
        };
    }

    public NotificationInboxDto.Item present(NotificationHistoryDocument doc, String role) {
        var view = doc.getRoleViews().get(role);
        Map<String, Object> payload = doc.getPayload() == null ? Map.of() : doc.getPayload();
        String heading = meaning(doc.getType()).heading();
        if (doc.getType() == NotificationType.EXCUSE_APPROVED && excluded(payload).size() > 0) {
            heading = "Уважительная причина одобрена частично";
        }
        List<NotificationInboxDto.Detail> details = new ArrayList<>();
        detail(details, payload, "subject_name", "Предмет");
        String number = positiveId(payload.get("lesson_number"));
        if (number != null) details.add(new NotificationInboxDto.Detail("Номер занятия", number));
        String lessonType =
                switch (Objects.toString(payload.get("lesson_type"), "")) {
                    case "lecture" -> "Лекция";
                    case "practice" -> "Практика";
                    case "lab" -> "Лабораторная работа";
                    default -> null;
                };
        if (lessonType != null)
            details.add(new NotificationInboxDto.Detail("Тип занятия", lessonType));
        detail(details, payload, "date", "Дата");
        detail(details, payload, "lesson_date", "Дата занятия");
        detail(details, payload, "start_time", "Начало");
        detail(details, payload, "end_time", "Окончание");
        detail(details, payload, "room", "Аудитория");
        detail(details, payload, "title", "Задание");
        detail(details, payload, "deadline", "Срок");
        detail(details, payload, "due_date", "Срок");
        detail(details, payload, "new_name", "Новое название");
        detail(details, payload, "week_start", "Начало недели");
        detail(details, payload, "week_end", "Конец недели");
        detail(details, payload, "decided_at", "Время решения");
        detail(details, payload, "effective_date", "Дата перевода");
        detail(details, payload, "semester_name", "Семестр");
        detail(details, payload, "date_from", "Начало семестра");
        detail(details, payload, "date_to", "Конец семестра");
        scopeDetail(details, payload.get("source"), "До изменения");
        scopeDetail(details, payload.get("target"), "После изменения");
        if (payload.get("after_active") instanceof Boolean active) {
            details.add(
                    new NotificationInboxDto.Detail(
                            "Полномочия", active ? "Действуют" : "Отозваны"));
        }
        if (payload.get("after_permissions") instanceof List<?> permissions) {
            List<String> labels =
                    permissions.stream()
                            .map(value -> permissionLabel(Objects.toString(value, "")))
                            .filter(Objects::nonNull)
                            .toList();
            details.add(
                    new NotificationInboxDto.Detail(
                            "Доступные действия",
                            labels.isEmpty() ? "Нет" : String.join(", ", labels)));
        }
        if (doc.getType() == NotificationType.ADMIN_ACCESS_CHANGED) {
            if ("FULL".equals(payload.get("mode")))
                details.add(new NotificationInboxDto.Detail("Доступ", "Полный"));
            else if ("LIMITED".equals(payload.get("mode")))
                details.add(new NotificationInboxDto.Detail("Доступ", "Ограниченный"));
        }
        if (doc.getType() == NotificationType.SEMESTER_OPERATION_COMPLETED) {
            String operation =
                    switch (Objects.toString(payload.get("action"), "")) {
                        case "ARCHIVE" -> "Архивация";
                        case "RESTORE" -> "Восстановление";
                        case "DELETE" -> "Удаление";
                        default -> null;
                    };
            if (operation != null)
                details.add(new NotificationInboxDto.Detail("Операция", operation));
            if ("CANCELLED".equals(payload.get("outcome")))
                heading = "Операция с семестром отменена";
        }
        if (doc.getType() == NotificationType.GROUP_PROMOTION_COMPLETED) {
            heading = "Обработана партия автоматического перевода групп";
            for (var entry :
                    Map.of(
                                    "promoted",
                                    "Переведено",
                                    "graduated",
                                    "Выпущено",
                                    "skipped",
                                    "Пропущено")
                            .entrySet()) {
                Object count = payload.get(entry.getKey());
                if (count instanceof Number)
                    details.add(
                            new NotificationInboxDto.Detail(entry.getValue(), count.toString()));
            }
        }
        if ("STUDENT".equals(role)) {
            detail(details, payload, "decision_comment", "Комментарий");
            if (nonempty(payload.get("included_lesson_ids")))
                details.add(
                        new NotificationInboxDto.Detail(
                                "Одобрено занятий",
                                Integer.toString(
                                        ((Collection<?>) payload.get("included_lesson_ids"))
                                                .size())));
            if (nonempty(payload.get("excluded_lesson_ids")))
                details.add(
                        new NotificationInboxDto.Detail(
                                "Исключено занятий",
                                Integer.toString(
                                        ((Collection<?>) payload.get("excluded_lesson_ids"))
                                                .size())));
            List<?> excluded = excluded(payload);
            if (!excluded.isEmpty()) {
                details.add(
                        new NotificationInboxDto.Detail(
                                "Исключено занятий", Integer.toString(excluded.size())));
                for (Object raw : excluded.stream().limit(3).toList()) {
                    if (!(raw instanceof Map<?, ?> lesson)) continue;
                    List<String> parts = new ArrayList<>();
                    String lessonDate = date(lesson.get("date"));
                    String lessonNumber = positiveId(lesson.get("lesson_number"));
                    String subject = text(lesson.get("subject_name"));
                    if (lessonDate != null) parts.add(lessonDate);
                    if (lessonNumber != null) parts.add("№" + lessonNumber);
                    if (subject != null) parts.add(subject);
                    if (!parts.isEmpty())
                        details.add(
                                new NotificationInboxDto.Detail(
                                        "Исключённое занятие", String.join(" · ", parts)));
                }
            }
        }
        if (doc.getType() == NotificationType.HOMEWORK_DUE_REMINDER
                && payload.get("homework") instanceof Map<?, ?> homework) {
            List<String> parts = new ArrayList<>();
            for (String key : List.of("subject_name", "title", "lesson_date")) {
                String value = text(homework.get(key));
                if (value != null) parts.add(value);
            }
            if (!parts.isEmpty())
                details.add(new NotificationInboxDto.Detail("Задание", String.join(" · ", parts)));
        }
        if (doc.getType() == NotificationType.HOMEWORK_WEEKLY_DIGEST
                && payload.get("items") instanceof List<?> items) {
            for (Object raw : items.stream().limit(20).toList()) {
                if (!(raw instanceof Map<?, ?> task)) continue;
                List<String> parts = new ArrayList<>();
                for (String key : List.of("subject_name", "title", "lesson_date")) {
                    String value = text(task.get(key));
                    if (value != null) parts.add(value);
                }
                if (!parts.isEmpty())
                    details.add(
                            new NotificationInboxDto.Detail("Задание", String.join(" · ", parts)));
            }
        }
        if (doc.getType() == NotificationType.ATTENDANCE_MARKED_BY_HEADMAN) {
            String mark =
                    switch (Objects.toString(payload.get("status"), "")) {
                        case "present" -> "Присутствует (+)";
                        case "absent" -> "Отсутствует (н)";
                        case "excused" -> "Уважительная причина (б)";
                        default -> null;
                    };
            if (mark != null) details.add(new NotificationInboxDto.Detail("Отметка", mark));
        }
        String summary = text(payload.get("subject_name"));
        return new NotificationInboxDto.Item(
                doc.getId(),
                doc.getType().name(),
                heading,
                summary,
                doc.getSentAt(),
                view.getOrderAt(),
                view.getReadAt(),
                Long.toString(view.getRevision()),
                List.copyOf(details),
                action(doc.getType(), payload, role));
    }

    public NotificationInboxDto.Action action(
            NotificationType type, Map<String, Object> payload, String role) {
        if (payload.get("target") instanceof Map<?, ?> target
                && Set.of(NotificationType.LESSON_TRANSFERRED, NotificationType.LESSON_UPDATED)
                        .contains(type)) {
            String id = positiveId(target.get("lesson_id"));
            return id == null
                    ? null
                    : new NotificationInboxDto.Action(
                            "lesson",
                            role,
                            id,
                            date(target.get("lesson_date")),
                            null,
                            null,
                            null,
                            null,
                            null);
        }
        if (Set.of(
                        NotificationType.GROUP_MEMBERSHIP_CHANGED,
                        NotificationType.HEADMAN_ACCESS_CHANGED,
                        NotificationType.ASSISTANT_ACCESS_CHANGED,
                        NotificationType.ADMIN_ACCESS_CHANGED,
                        NotificationType.SEMESTER_ACTIVATED,
                        NotificationType.SEMESTER_OPERATION_COMPLETED,
                        NotificationType.GROUP_PROMOTION_COMPLETED)
                .contains(type)) return null;
        String date = date(payload.getOrDefault("lesson_date", payload.get("date")));
        String lesson = positiveId(payload.get("lesson_id"));
        if (type.name().startsWith("EXCUSE") || type.name().startsWith("LATE_CHECKIN")) {
            Object rawId =
                    payload.get(type.name().startsWith("EXCUSE") ? "ticket_id" : "request_id");
            String id = rawId instanceof String value ? value : null;
            if (id == null || !id.matches("[A-Za-z0-9_:-]{1,128}")) return null;
            return new NotificationInboxDto.Action(
                    "request",
                    role,
                    null,
                    null,
                    id,
                    type.name().startsWith("EXCUSE") ? "EXCUSE" : "LATE_CHECKIN",
                    null,
                    null,
                    null);
        }
        if (type == NotificationType.HOMEWORK_WEEKLY_DIGEST) {
            String start = date(payload.get("week_start"));
            return start == null
                    ? null
                    : new NotificationInboxDto.Action(
                            "homework-week", role, null, null, null, null, null, null, start);
        }
        if (type.name().startsWith("HOMEWORK")) {
            String id = positiveId(payload.get("homework_id"));
            return id == null
                    ? null
                    : new NotificationInboxDto.Action(
                            "homework", role, null, date, null, null, id, null, null);
        }
        if (type.name().startsWith("GROUP")) {
            String id = positiveId(payload.get("group_id"));
            return id == null
                    ? null
                    : new NotificationInboxDto.Action(
                            "group", role, null, null, null, null, null, id, null);
        }
        return lesson == null
                ? null
                : new NotificationInboxDto.Action(
                        "lesson", role, lesson, date, null, null, null, null, null);
    }

    public static String positiveId(Object value) {
        try {
            if (value instanceof String text && !text.matches("[1-9][0-9]{0,18}")) return null;
            long id = new java.math.BigDecimal(Objects.toString(value, "")).longValueExact();
            return id > 0 ? Long.toString(id) : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String date(Object value) {
        try {
            return LocalDate.parse(Objects.toString(value, "")).toString();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean nonempty(Object value) {
        return value instanceof Collection<?> list && !list.isEmpty();
    }

    private static List<?> excluded(Map<String, Object> payload) {
        for (String key :
                List.of("excluded_lessons", "excluded_lesson_ids", "excluded_occurrence_ids")) {
            if (payload.get(key) instanceof List<?> values && !values.isEmpty()) return values;
        }
        return List.of();
    }

    private static String text(Object value) {
        if (!(value instanceof String text)) return null;
        text = text.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "").strip();
        return text.isBlank() ? null : text.substring(0, Math.min(text.length(), 2000));
    }

    private static void detail(
            List<NotificationInboxDto.Detail> target,
            Map<String, Object> source,
            String field,
            String label) {
        String value = text(source.get(field));
        if (value != null) target.add(new NotificationInboxDto.Detail(label, value));
    }

    private static void scopeDetail(
            List<NotificationInboxDto.Detail> details, Object value, String label) {
        if (!(value instanceof Map<?, ?> scope)) return;
        List<String> parts = new ArrayList<>();
        for (String key : List.of("lesson_date", "start_time", "end_time", "room")) {
            String text = text(scope.get(key));
            if (text != null) parts.add(text);
        }
        String number = positiveId(scope.get("lesson_number"));
        if (number != null) parts.add("№" + number);
        if (!parts.isEmpty())
            details.add(new NotificationInboxDto.Detail(label, String.join(" · ", parts)));
    }

    private static String permissionLabel(String permission) {
        return switch (permission.toUpperCase(java.util.Locale.ROOT)) {
            case "MARK_ATTENDANCE" -> "Отметка посещаемости";
            case "MANAGE_EXCUSES" -> "Заявки";
            case "MANAGE_HOMEWORK" -> "Домашние задания";
            case "CANCEL_LESSONS" -> "Отмена занятий";
            case "VIEW_STATS" -> "Статистика";
            default -> null;
        };
    }
}
