"""Factual Telegram presentation of the canonical business event catalog."""

from __future__ import annotations

from html import escape

from aiogram.types import InlineKeyboardMarkup

from bot.services.mini_app_links import build_mini_app_button, encode_notification_action
from bot.services.notification_prefs import type_for_event

HEADINGS = {
    "LESSON_TRANSFERRED": "Занятие перенесено",
    "LESSON_RESTORED": "Занятие восстановлено",
    "LESSON_UPDATED": "Занятие изменено",
    "HOMEWORK_WITHDRAWN": "Домашнее задание снято с публикации",
    "GROUP_MEMBERSHIP_CHANGED": "Участие в группе изменено",
    "HEADMAN_ACCESS_CHANGED": "Права старосты изменены",
    "ASSISTANT_ACCESS_CHANGED": "Права помощника изменены",
    "ADMIN_ACCESS_CHANGED": "Права администратора изменены",
    "SEMESTER_ACTIVATED": "Семестр активирован",
    "SEMESTER_OPERATION_COMPLETED": "Операция с семестром завершена",
    "GROUP_PROMOTION_COMPLETED": "Выполнена партия перевода групп",

    "LESSON_STARTED": "Занятие началось",
    "LESSON_CLOSED": "Занятие завершено",
    "LESSON_CANCELLED": "Занятие отменено",
    "LESSON_REMINDER": "Напоминание об отметке",
    "LESSON_BLOCKED": "Самостоятельная отметка закрыта",
    "LESSON_ONE_OFF_CREATED": "Добавлено разовое занятие",
    "HOMEWORK_PUBLISHED": "Новое домашнее задание",
    "HOMEWORK_UPDATED": "Домашнее задание изменено",
    "HOMEWORK_WEEKLY_DIGEST": "Домашние задания на неделю",
    "HOMEWORK_DUE_REMINDER": "Скоро срок домашнего задания",
    "EXCUSE_REQUESTED": "Новая заявка на уважительную причину",
    "EXCUSE_APPROVED": "Уважительная причина одобрена",
    "EXCUSE_REJECTED": "Уважительная причина отклонена",
    "EXCUSE_CANCELLED": "Заявка на уважительную причину отменена",
    "LATE_CHECKIN_REQUESTED": "Новая заявка на отметку",
    "LATE_CHECKIN_APPROVED": "Заявка на отметку одобрена",
    "LATE_CHECKIN_REJECTED": "Заявка на отметку отклонена",
    "LATE_CHECKIN_CANCELLED": "Заявка на отметку отменена",
    "ATTENDANCE_MARKED_BY_HEADMAN": "Отметка посещаемости изменена",
    "GROUP_RENAMED": "Название группы изменено",
    "GROUP_ARCHIVED": "Группа архивирована",
}
MARKS = {
    "present": "присутствует",
    "absent": "отсутствует",
    "excused": "уважительная причина",
    "free_attendance": "свободное посещение",
}


def _text(value, limit=500):
    if not isinstance(value, (str, int)) or isinstance(value, bool):
        return ""
    text = " ".join(str(value).split())
    return text[:limit].rstrip() + "…" if len(text) > limit else text


def _date(value):
    value = _text(value, 40)
    if len(value) >= 10 and value[4:5] == "-" and value[7:8] == "-":
        return f"{value[8:10]}.{value[5:7]}.{value[:4]}" + value[10:]
    return value


def lesson_context(payload):
    """Only display facts actually present; identifiers are action keys, never copy."""
    lines = []
    subject = _text(payload.get("subject_name"), 100)
    kind = _text(payload.get("lesson_type"), 60)
    if subject or kind:
        lines.append(" · ".join(part for part in (subject, kind) if part))
    day = _date(payload.get("date") or payload.get("lesson_date"))
    start, end = _text(payload.get("start_time"), 40), _text(payload.get("end_time"), 40)
    time = "–".join(part for part in (start, end) if part)
    number = _text(payload.get("lesson_number"), 10)
    when = " · ".join(part for part in (day, time, f"Пара №{number}" if number else "") if part)
    if when:
        lines.append(when)
    room = _text(payload.get("room") or payload.get("classroom"), 80)
    if room:
        lines.append(f"Аудитория: {room}")
    group = _text(payload.get("group_name"), 80)
    if group:
        lines.append(f"Группа: {group}")
    return lines


def render(event, *, facts=None):
    notification_type = type_for_event(event)
    if notification_type not in HEADINGS:
        raise ValueError("Unsupported business notification meaning")
    payload = dict(event.get("payload") or {})
    if facts:
        payload.update(facts)
    heading = HEADINGS[notification_type]
    lines = lesson_context(payload)
    if notification_type in {"LESSON_TRANSFERRED", "LESSON_UPDATED"}:
        lines = []
        for key, label in (("source", "Было"), ("target", "Стало")):
            snapshot = payload.get(key)
            if isinstance(snapshot, dict):
                context = lesson_context(snapshot)
                if context:
                    lines.append(label + ": " + " · ".join(context))
    if notification_type == "GROUP_MEMBERSHIP_CHANGED":
        if payload.get("change") == "TRANSFERRED":
            lines.append("Перевод в другую группу")
        effective = _date(payload.get("effective_date"))
        if effective:
            lines.append(f"Дата: {effective}")
    if notification_type in {"HEADMAN_ACCESS_CHANGED", "ASSISTANT_ACCESS_CHANGED"}:
        after = payload.get("after_active")
        if type(after) is bool:
            lines.append("Доступ предоставлен" if after else "Доступ отозван")
        if notification_type == "ASSISTANT_ACCESS_CHANGED":
            labels = {"mark_attendance": "Отметка посещаемости", "manage_excuses": "Обработка заявок"}
            for key, label in (("before_permissions", "Было"), ("after_permissions", "Стало")):
                values = payload.get(key)
                if isinstance(values, list) and all(isinstance(value, str) for value in values):
                    known = [labels[value.lower()] for value in values if value.lower() in labels]
                    lines.append(label + ": " + (", ".join(known) or "Нет дополнительных прав"))
    if notification_type == "ADMIN_ACCESS_CHANGED":
        mode = {"FULL": "Полный доступ", "LIMITED": "Ограниченный доступ"}.get(payload.get("mode"))
        if mode:
            lines.append(mode)
    if notification_type.startswith("SEMESTER_"):
        name = _text(payload.get("semester_name"), 100)
        if name:
            lines.append(name)
        dates = " — ".join(_date(payload.get(key)) for key in ("date_from", "date_to") if payload.get(key))
        if dates:
            lines.append(dates)
        action = {
            "ARCHIVE": "Архивирование", "RESTORE": "Восстановление", "DELETE": "Удаление",
        }.get(payload.get("action"))
        outcome = {"COMPLETED": "выполнено", "CANCELLED": "отменено"}.get(payload.get("outcome"))
        if action and outcome:
            lines.append(action + ": " + outcome)
    if notification_type == "GROUP_PROMOTION_COMPLETED":
        for key, label in (("promoted", "Переведено"), ("graduated", "Выпущено"), ("skipped", "Пропущено")):
            value = payload.get(key)
            if type(value) is int and value >= 0:
                lines.append(f"{label}: {value}")
    if notification_type.startswith("HOMEWORK_"):
        homework = payload.get("homework")
        if isinstance(homework, dict):
            lines = lesson_context(homework)
            payload = {**payload, **homework}
        if notification_type == "HOMEWORK_WEEKLY_DIGEST":
            lines = [
                " — ".join(part for part in (_date(payload.get("week_start")), _date(payload.get("week_end"))) if part)
            ]
            items = [item for item in payload.get("items", []) if isinstance(item, dict)]
            for item in items[:8]:
                context = " · ".join(lesson_context(item)[:2])
                title = _text(item.get("title"), 160)
                lines.append(" · ".join(part for part in (context, title) if part))
            if len(items) > 8:
                lines.append(f"Ещё заданий: {len(items) - 8}")
            if not items:
                lines.append("Невыполненных заданий за этот период нет")
        else:
            title = _text(payload.get("title"), 250)
            if title:
                lines.append(title)
            due = _date(payload.get("due_date") or payload.get("deadline"))
            if due:
                lines.append(f"Срок: {due}")
            description = _text(payload.get("description"), 500)
            if description and notification_type == "HOMEWORK_PUBLISHED":
                lines.append(description)
    if notification_type.startswith(("EXCUSE_", "LATE_CHECKIN_")):
        # Requested headman copy is supplied only from the protected canonical RPC.
        student = _text(payload.get("student_name"), 100)
        if student:
            lines.insert(0, f"Студент: {student}")
        lessons = payload.get("lessons")
        if isinstance(lessons, list):
            for lesson in lessons[:4]:
                if isinstance(lesson, dict):
                    context = " · ".join(lesson_context(lesson)[:2])
                    if context:
                        lines.append(context)
        excluded = payload.get("excluded_lessons")
        excluded_ids = payload.get("excluded_lesson_ids") or payload.get("excluded_occurrence_ids")
        if notification_type == "EXCUSE_APPROVED" and (
            (isinstance(excluded, list) and excluded) or (isinstance(excluded_ids, list) and excluded_ids)
        ):
            heading = "Уважительная причина одобрена частично"
            lines.append(
                f"Исключено занятий: {len(excluded) if isinstance(excluded, list) and excluded else len(excluded_ids)}"
            )
            for lesson in (excluded or [])[:3]:
                if isinstance(lesson, dict):
                    context = " · ".join(lesson_context(lesson)[:2])
                    if context:
                        lines.append(f"Исключено: {context}")
        created_at = _date(payload.get("created_at"))
        decided_at = _date(payload.get("decided_at") or payload.get("decision_at"))
        if created_at and notification_type.endswith("REQUESTED"):
            lines.append(f"Подано: {created_at}")
        if decided_at:
            lines.append(f"Решение: {decided_at}")
        reason = _text(payload.get("decision_comment") or payload.get("reason_label") or payload.get("comment"), 500)
        if reason:
            lines.append(f"Причина: {reason}")
        resolution = payload.get("resolution_reason")
        if resolution == "geo_confirmed":
            lines.append("Присутствие подтверждено по геолокации")
        elif resolution == "present_priority":
            lines.append("На занятии уже стоит отметка присутствия")
    if notification_type == "ATTENDANCE_MARKED_BY_HEADMAN":
        current = MARKS.get(payload.get("status"))
        previous = MARKS.get(payload.get("old_status") or payload.get("previous_status"))
        if current:
            lines.append(f"Отметка: {previous + ' → ' if previous else ''}{current}")
        lines.append("Автор: староста")
        comment = _text(payload.get("comment"), 500)
        if comment:
            lines.append(f"Комментарий: {comment}")
    if notification_type == "LESSON_CANCELLED":
        reason = _text(payload.get("cancel_reason"), 500)
        if reason:
            lines.append(f"Причина: {reason}")
    # Bound plain values before escaping; never cut HTML/entities after rendering.
    return f"<b>{escape(heading)}</b>" + (
        "\n\n" + "\n".join(escape(line) for line in lines if line) if any(lines) else ""
    )


def _positive_id(value):
    if isinstance(value, int) and not isinstance(value, bool) and 0 < value <= 2**63 - 1:
        return str(value)
    if isinstance(value, str) and value.isascii() and value.isdecimal() and value[0:1] != "0" and len(value) <= 19:
        return value if 0 < int(value) <= 2**63 - 1 else None
    return None


def notification_action(event, *, facts=None, role="STUDENT"):
    """Actual event target plus caller's verified recipient context, no guessed role."""
    payload = {**(event.get("payload") or {}), **(facts or {})}
    if event.get("event_type") in {"lesson.transferred", "lesson.updated"}:
        target = payload.get("target")
        if isinstance(target, dict):
            payload = target
    notification_type = type_for_event(event)
    if notification_type not in HEADINGS:
        return None
    if notification_type.startswith(("EXCUSE_", "LATE_CHECKIN_")):
        action = {
            "kind": "request",
            "role": role,
            "requestKind": "EXCUSE" if notification_type.startswith("EXCUSE_") else "LATE_CHECKIN",
            "requestId": payload.get("ticket_id") or payload.get("request_id"),
        }
    elif notification_type == "HOMEWORK_WEEKLY_DIGEST":
        action = {"kind": "homework-week", "role": role, "weekStart": payload.get("week_start")}
    elif notification_type == "HOMEWORK_WITHDRAWN":
        return None
    elif notification_type.startswith("HOMEWORK_"):
        homework = payload.get("homework")
        homework = homework if isinstance(homework, dict) else {}
        action = {
            "kind": "homework",
            "role": role,
            "homeworkId": _positive_id(payload.get("homework_id") or homework.get("homework_id") or homework.get("id")),
        }
    elif notification_type in {
        "HEADMAN_ACCESS_CHANGED", "ASSISTANT_ACCESS_CHANGED", "ADMIN_ACCESS_CHANGED",
        "SEMESTER_ACTIVATED", "SEMESTER_OPERATION_COMPLETED", "GROUP_PROMOTION_COMPLETED",
        "GROUP_MEMBERSHIP_CHANGED", "HOMEWORK_WITHDRAWN",
    }:
        return None
    elif notification_type.startswith("GROUP_"):
        action = {"kind": "group", "role": role, "groupId": _positive_id(payload.get("group_id"))}
    else:
        action = {"kind": "lesson", "role": role, "lessonId": _positive_id(payload.get("lesson_id"))}
    day = payload.get("date") or payload.get("lesson_date")
    if day and action["kind"] in {"lesson", "homework"}:
        action["lessonDate"] = day
    return action if encode_notification_action(action) is not None else None


def message_options(event, *, config=None, facts=None, fallback_markup=None, role="STUDENT"):
    options = {"text": render(event, facts=facts), "parse_mode": "HTML"}
    base_url = None
    if config is not None:
        base_url = getattr(config, "mini_app_web_url", None) or getattr(config, "mini_app_url", None)
    action = notification_action(event, facts=facts, role=role)
    encoded = encode_notification_action(action) if action is not None else None
    labels = {
        "lesson": "Открыть занятие",
        "request": "Открыть заявку",
        "homework": "Открыть задание",
        "group": "Открыть группу",
        "homework-week": "Задания на неделю",
    }
    button = build_mini_app_button(
        text=labels[action["kind"]] if action else "Открыть приложение",
        base_url=base_url,
        start_param=f"n2_{encoded}" if encoded else None,
    )
    if fallback_markup is not None:
        # Existing authorized decisions remain until addressed app replacement is verified.
        rows = ([[button]] if button is not None else []) + fallback_markup.inline_keyboard
        options["reply_markup"] = InlineKeyboardMarkup(inline_keyboard=rows)
    elif button is not None:
        options["reply_markup"] = InlineKeyboardMarkup(inline_keyboard=[[button]])
    return options
