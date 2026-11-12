"""Factual Telegram presentation of the canonical business event catalog."""

from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
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
    "present": ("✅", "присутствует"),
    "absent": ("❌", "отсутствует"),
    "excused": ("📝", "уважительная причина"),
    "free_attendance": ("ℹ️", "свободное посещение"),
}
LESSON_TYPES = {"lecture": "лекция", "practice": "практика", "lab": "лабораторная"}
ICONS = {
    "LESSON_CANCELLED": "🚫",
    "LESSON_TRANSFERRED": "🗓",
    "LESSON_UPDATED": "✏️",
    "LESSON_RESTORED": "↩️",
    "HOMEWORK_WEEKLY_DIGEST": "📚",
    "EXCUSE_REQUESTED": "📩",
    "LATE_CHECKIN_REQUESTED": "📩",
    "EXCUSE_APPROVED": "✅",
    "LATE_CHECKIN_APPROVED": "✅",
    "EXCUSE_REJECTED": "❌",
    "LATE_CHECKIN_REJECTED": "❌",
    "EXCUSE_CANCELLED": "🚫",
    "LATE_CHECKIN_CANCELLED": "🚫",
}
_REQUEST_FIELDS = {
    "ticket_id",
    "request_id",
    "status",
    "group_id",
    "user_id",
    "created_at",
    "decided_at",
    "excluded_lesson_ids",
    "excluded_occurrence_ids",
    "resolution_reason",
}
# All supported notification events postdate the last Moscow offset change.
_MOSCOW = timezone(timedelta(hours=3), "МСК")


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


def _moscow_datetime(value):
    try:
        stamp = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if stamp.tzinfo is not None:
            return stamp.astimezone(_MOSCOW)
    except (AttributeError, TypeError, ValueError, OverflowError):
        pass
    return None


def _timestamp(value):
    stamp = _moscow_datetime(value)
    return stamp.strftime("%d.%m.%Y в %H:%M МСК") if stamp else ""


def _lesson_time(value):
    value = _text(value, 40)
    stamp = _moscow_datetime(value)
    if stamp is not None:
        return stamp.strftime("%H:%M")
    try:
        if "T" in value:
            return datetime.fromisoformat(value).strftime("%H:%M")
        return time.fromisoformat(value).strftime("%H:%M")
    except ValueError:
        return value


def _lesson_fields(payload):
    number = _text(payload.get("lesson_number"), 10)
    start, end = (_lesson_time(payload.get(key)) for key in ("start_time", "end_time"))
    return {
        "Предмет": _text(payload.get("subject_name"), 100),
        "Вид занятия": LESSON_TYPES.get(_text(payload.get("lesson_type"), 60).lower(), ""),
        "Дата": _date(payload.get("date") or payload.get("lesson_date")),
        "Время": "–".join(part for part in (start, end) if part),
        "Номер пары": f"Пара №{number}" if number and number != "0" else "",
        "Аудитория": _text(payload.get("room") or payload.get("classroom"), 80),
        "Группа": _text(payload.get("group_name"), 80),
    }


def lesson_context(payload):
    """Only display facts actually present; identifiers are action keys, never copy."""
    fields = _lesson_fields(payload)
    return [
        ", ".join(part for part in (fields["Предмет"], fields["Вид занятия"]) if part),
        ", ".join(part for part in (fields["Дата"], fields["Время"], fields["Номер пары"]) if part),
        *[f"{label}: {fields[label]}" for label in ("Аудитория", "Группа") if fields[label]],
    ]


def _lesson_change(payload):
    source, target = (payload.get(key) for key in ("source", "target"))
    source = source if isinstance(source, dict) else {}
    target = target if isinstance(target, dict) else {}
    before, after = _lesson_fields(source), _lesson_fields(target)
    changed = [label for label in before if before[label] != after[label]]
    for field, label in (
        ("subject_id", "Предмет"),
        ("group_id", "Группа"),
        ("assigned_teacher_id", "Преподаватель"),
    ):
        if _positive_id(source.get(field)) and _positive_id(target.get(field)) and source[field] != target[field]:
            if label not in changed:
                changed.append(label)
    result = ["<b>Изменения:</b> " + ", ".join(label.lower() for label in changed), ""] if changed else []
    for label, fields, style in (("Было", before, "s"), ("Стало", after, "b")):
        if not any(fields.values()):
            continue
        if result and result[-1]:
            result.append("")
        result.append(f"<b>{label}</b>")
        for field, value in fields.items():
            if not value:
                continue
            text = escape(value)
            if field in changed:
                text = f"<{style}>{text}</{style}>"
            result.append(f"{field}: {text}" if field != "Номер пары" else text)
    return result


def private_request_event(event):
    return {
        **event,
        "payload": {key: value for key, value in (event.get("payload") or {}).items() if key in _REQUEST_FIELDS},
    }


def digest_pages(event):
    from bot.services.homework_pager import homework_pages

    payload = event["payload"]
    period = " — ".join(part for part in (_date(payload.get("week_start")), _date(payload.get("week_end"))) if part)
    items = payload.get("items") or []
    return homework_pages(
        f"{ICONS['HOMEWORK_WEEKLY_DIGEST']} {HEADINGS['HOMEWORK_WEEKLY_DIGEST']}",
        period,
        "Невыполненных заданий за этот период нет",
        [
            {key: item[key] for key in ("subject_name", "lesson_date", "lesson_number", "title") if key in item}
            for item in items
            if isinstance(item, dict)
        ],
        show_completion=False,
    )


def _excluded_lesson_context(lesson):
    """Render available exclusion facts without inventing context from bad data."""
    facts = dict(lesson)
    for key in ("date", "lesson_date"):
        value = facts.get(key)
        try:
            if not isinstance(value, str) or len(value) != 10:
                raise ValueError
            date.fromisoformat(value)
        except ValueError:
            facts.pop(key, None)
    if _positive_id(facts.get("lesson_number")) is None:
        facts.pop("lesson_number", None)
    return lesson_context(facts)


def render(event, *, facts=None):
    notification_type = type_for_event(event)
    if notification_type not in HEADINGS:
        raise ValueError("Unsupported business notification meaning")
    payload = dict(event.get("payload") or {})
    if facts:
        payload.update(facts)
    heading = HEADINGS[notification_type]
    lines = lesson_context(payload)
    if notification_type == "GROUP_RENAMED":
        name = _text(payload.get("new_name"), 80)
        if name:
            lines = [f"Группа: {name}"]
    if notification_type in {"LESSON_TRANSFERRED", "LESSON_UPDATED"}:
        return _card(heading, notification_type, _lesson_change(payload))
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
            "ARCHIVE": "Архивирование",
            "RESTORE": "Восстановление",
            "DELETE": "Удаление",
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
            return escape("\n\n".join(digest_pages(event)))
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
                    context = "\n  ".join(part for part in lesson_context(lesson)[:2] if part)
                    if context:
                        lines.append(f"• {context}")
            if len(lessons) > 4:
                lines.append(f"Ещё занятий: {len(lessons) - 4}")
        excluded = payload.get("excluded_lessons")
        excluded = excluded if isinstance(excluded, list) else []
        excluded_ids = payload.get("excluded_lesson_ids") or payload.get("excluded_occurrence_ids")
        if notification_type == "EXCUSE_APPROVED" and (
            (isinstance(excluded, list) and excluded) or (isinstance(excluded_ids, list) and excluded_ids)
        ):
            heading = "Уважительная причина одобрена частично"
            lines.append(
                f"Исключено занятий: {len(excluded) if isinstance(excluded, list) and excluded else len(excluded_ids)}"
            )
            for lesson in excluded[:3]:
                if isinstance(lesson, dict):
                    context = ", ".join(part for part in _excluded_lesson_context(lesson)[:2] if part)
                    if context:
                        lines.append(f"Исключено: {context}")
            if len(excluded) > 3:
                lines.append(f"Ещё исключено: {len(excluded) - 3}")
        created_at = _timestamp(payload.get("created_at"))
        decided_at = _timestamp(payload.get("decided_at")) or _timestamp(event.get("occurred_at"))
        if notification_type.endswith("REQUESTED"):
            submitted = created_at or _timestamp(event.get("occurred_at"))
            if submitted:
                lines.append(f"Подано: {submitted}")
        else:
            if created_at:
                lines.append(f"Подано: {created_at}")
            if decided_at:
                label = "Отменено" if notification_type.endswith("CANCELLED") else "Решение"
                lines.append(f"{label}: {decided_at}")
        reason = _text(payload.get("reason_label"), 200)
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
        details = [escape(line) for line in lines if line]
        if current:
            icon, status = current
            before = f"{previous[1]} → " if previous and previous != current else ""
            details.append(f"{icon} <b>Отметка: {before}{status}</b>")
        details.append("Автор: староста")
        stamp = _timestamp(event.get("occurred_at"))
        if stamp:
            details.append(f"Изменено: {stamp}")
        return _card(heading, notification_type, details)
    if notification_type == "LESSON_CANCELLED":
        reason = _text(payload.get("cancel_reason"), 500)
        if reason:
            lines.append(f"Причина: {reason}")
    return _card(heading, notification_type, [escape(line) for line in lines if line])


def _card(heading, notification_type, lines):
    # Bound plain values before escaping; never cut HTML/entities after rendering.
    icon = ICONS.get(notification_type)
    prefix = f"{icon} " if icon else ""
    return f"<b>{prefix}{escape(heading)}</b>" + ("\n\n" + "\n".join(lines) if lines else "")


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
    if notification_type == "HOMEWORK_WITHDRAWN" or (role == "TEACHER" and notification_type.startswith("HOMEWORK_")):
        return None
    day = payload.get("date") or payload.get("lesson_date")
    if notification_type.startswith(("EXCUSE_", "LATE_CHECKIN_")):
        action = {
            "kind": "request",
            "role": role,
            "requestKind": "EXCUSE" if notification_type.startswith("EXCUSE_") else "LATE_CHECKIN",
            "requestId": payload.get("ticket_id" if notification_type.startswith("EXCUSE_") else "request_id"),
        }
    elif notification_type == "HOMEWORK_WEEKLY_DIGEST":
        action = {"kind": "homework-week", "role": role, "weekStart": payload.get("week_start")}
    elif notification_type.startswith("HOMEWORK_"):
        homework = payload.get("homework")
        if notification_type == "HOMEWORK_DUE_REMINDER":
            root_id = _positive_id(payload.get("homework_id"))
            homework_id = root_id
            day = payload.get("lesson_date", payload.get("date"))
            if isinstance(homework, dict):
                # Never substitute a legacy root ID for a missing or invalid nested ID.
                homework_id = _positive_id(homework.get("homework_id", homework.get("id")))
                if "lesson_date" in homework:
                    day = homework["lesson_date"]
                elif "homework_id" in payload and homework_id != root_id:
                    day = None
        else:
            homework = homework if isinstance(homework, dict) else {}
            homework_id = _positive_id(payload.get("homework_id") or homework.get("homework_id") or homework.get("id"))
        # The student homework view requires a lesson date. The encoder checks ISO date validity.
        if role == "STUDENT" and (not isinstance(day, str) or not day or day.startswith("00")):
            return None
        action = {
            "kind": "homework",
            "role": role,
            "homeworkId": homework_id,
        }
    elif notification_type in {
        "HEADMAN_ACCESS_CHANGED",
        "ASSISTANT_ACCESS_CHANGED",
        "ADMIN_ACCESS_CHANGED",
        "SEMESTER_ACTIVATED",
        "SEMESTER_OPERATION_COMPLETED",
        "GROUP_PROMOTION_COMPLETED",
        "GROUP_MEMBERSHIP_CHANGED",
    }:
        return None
    elif notification_type.startswith("GROUP_"):
        action = {"kind": "group", "role": role, "groupId": _positive_id(payload.get("group_id"))}
    elif notification_type == "LESSON_STARTED" and role == "STUDENT":
        action = {"kind": "checkin", "role": role, "lessonId": _positive_id(payload.get("lesson_id"))}
    else:
        action = {"kind": "lesson", "role": role, "lessonId": _positive_id(payload.get("lesson_id"))}
    if day and action["kind"] in {"lesson", "homework"}:
        action["lessonDate"] = day
    return action if encode_notification_action(action) is not None else None


def message_options(event, *, config=None, facts=None, fallback_markup=None, role="STUDENT", text=None):
    options = {"text": render(event, facts=facts) if text is None else text, "parse_mode": "HTML"}
    base_url = None
    if config is not None:
        base_url = getattr(config, "mini_app_web_url", None) or getattr(config, "mini_app_url", None)
    action = notification_action(event, facts=facts, role=role)
    encoded = encode_notification_action(action) if action is not None else None
    labels = {
        "checkin": "Открыть отметку",
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
