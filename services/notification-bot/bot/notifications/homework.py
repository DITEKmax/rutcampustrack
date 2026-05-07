"""Handlers for homework notification events."""

import logging
from collections import defaultdict

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_homework(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Send homework notifications.

    Handles group-wide publish/update events and per-student digest/reminder events.
    Threat T-24-07: validates required fields group_id and title before processing.
    """
    event_type = event.get("event_type")
    payload = event.get("payload", {})

    if event_type == "homework.weekly_digest":
        await _handle_weekly_digest(payload, bot=bot, academic_client=academic_client, send_queue=send_queue)
        return
    if event_type == "homework.due_reminder":
        await _handle_due_reminder(payload, bot=bot, academic_client=academic_client, send_queue=send_queue)
        return

    try:
        group_id = payload["group_id"]
        title = payload["title"]
    except KeyError as exc:
        logger.warning("homework event missing required field: %s", exc)
        return

    lesson_date = payload.get("lesson_date")
    lesson_number = payload.get("lesson_number")

    async def _resolve_subject_name(subject_id):
        if subject_id is None:
            return "Предмет"
        try:
            subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
            if subjects_resp.subjects:
                return subjects_resp.subjects[0].subject_name
        except Exception:
            logger.warning(
                "Could not resolve subject_id=%s for %s, using fallback",
                subject_id,
                event_type,
            )
        return "Предмет"

    def _add_optional_block(lines, label, value):
        if isinstance(value, str) and value.strip():
            lines.extend(["", f"{label}:", value.strip()])

    def _build_card(prefix, subject_name):
        lines = [prefix, "", f"Предмет: {subject_name}", f"Задание: {title}"]
        if lesson_date and lesson_number:
            lines.append(f"Пара: №{lesson_number}, {lesson_date}")
        _add_optional_block(lines, "Описание", payload.get("description"))
        _add_optional_block(lines, "Ссылка", payload.get("link"))
        return "\n".join(lines)

    if event_type == "homework.published":
        subject_name = await _resolve_subject_name(payload.get("subject_id"))
        text = _build_card("📝 Новое домашнее задание", subject_name)
    elif event_type == "homework.updated":
        # Phase 61 / D-07: payload теперь содержит subject_id + lesson_date + lesson_number.
        subject_name = await _resolve_subject_name(payload.get("subject_id"))
        text = _build_card("✏️ Домашнее задание изменено", subject_name)
    else:
        logger.debug("handle_homework called with unexpected event_type: %s", event_type)
        return

    members = await academic_client.get_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue

        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, text=text),
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="homework",
            )
        )


async def _handle_weekly_digest(payload: dict, bot: Bot, academic_client, send_queue: TelegramSendQueue) -> None:
    user_id = payload.get("user_id")
    if user_id is None:
        logger.warning("homework.weekly_digest missing user_id")
        return

    user = await _resolve_user(user_id, academic_client)
    if user is None or not getattr(user, "telegram_id", 0):
        return

    text = _build_weekly_digest_text(payload)
    await send_queue.put(
        SendTask(
            coroutine_factory=lambda chat=user.telegram_id, t=text: bot.send_message(chat_id=chat, text=t),
            user_id=user_id,
            chat_id=user.telegram_id,
            category="homework",
        )
    )


async def _handle_due_reminder(payload: dict, bot: Bot, academic_client, send_queue: TelegramSendQueue) -> None:
    user_id = payload.get("user_id")
    homework = payload.get("homework")
    if user_id is None or not isinstance(homework, dict):
        logger.warning("homework.due_reminder missing user_id or homework")
        return

    user = await _resolve_user(user_id, academic_client)
    if user is None or not getattr(user, "telegram_id", 0):
        return

    text = _build_due_reminder_text(payload, homework)
    await send_queue.put(
        SendTask(
            coroutine_factory=lambda chat=user.telegram_id, t=text: bot.send_message(chat_id=chat, text=t),
            user_id=user_id,
            chat_id=user.telegram_id,
            category="homework",
        )
    )


async def _resolve_user(user_id, academic_client):
    try:
        return await academic_client.get_user_by_id(int(user_id))
    except Exception:
        logger.warning("Could not resolve user_id=%s for homework notification", user_id, exc_info=True)
        return None


def _build_due_reminder_text(payload: dict, homework: dict) -> str:
    due_date = payload.get("due_date") or homework.get("lesson_date")
    lines = [
        "⏰ Напоминание о домашнем задании",
        "",
        f"Дедлайн: {_format_date(due_date)}",
        _format_homework_item(homework, numbered=False),
    ]
    _add_optional(lines, "Описание", homework.get("description"))
    _add_optional(lines, "Ссылка", homework.get("link"))
    return _truncate("\n".join(line for line in lines if line is not None))


def _build_weekly_digest_text(payload: dict) -> str:
    week_start = _format_date(payload.get("week_start"))
    week_end = _format_date(payload.get("week_end"))
    items = [item for item in payload.get("items", []) if isinstance(item, dict)]
    lines = [
        "📚 Домашнее задание на следующую неделю",
        f"{week_start} — {week_end}",
        "",
    ]
    if not items:
        lines.append("Невыполненных заданий на следующую неделю нет.")
        return "\n".join(lines)

    by_date: dict[str, list[dict]] = defaultdict(list)
    for item in items:
        by_date[str(item.get("lesson_date") or "")].append(item)

    index = 1
    for lesson_date in sorted(by_date):
        lines.append(_format_date(lesson_date))
        for item in sorted(by_date[lesson_date], key=lambda i: (i.get("lesson_number") or 0, i.get("title") or "")):
            lines.append(_format_homework_item(item, number=index))
            index += 1
        lines.append("")

    return _truncate("\n".join(lines).strip())


def _format_homework_item(item: dict, numbered: bool = True, number: int | None = None) -> str:
    prefix = f"{number}. " if numbered and number is not None else ""
    subject = item.get("subject_name") or "Предмет"
    title = item.get("title") or "Домашнее задание"
    lesson_number = item.get("lesson_number")
    lesson_part = f" · пара №{lesson_number}" if lesson_number else ""
    return f"{prefix}{subject}{lesson_part}\n   {title}"


def _add_optional(lines: list[str], label: str, value) -> None:
    if isinstance(value, str) and value.strip():
        lines.extend(["", f"{label}:", value.strip()])


def _format_date(value) -> str:
    if not isinstance(value, str) or len(value) < 10:
        return str(value or "")
    if value[4] == "-" and value[7] == "-":
        return f"{value[8:10]}.{value[5:7]}.{value[0:4]}"
    return value


def _truncate(text: str, limit: int = 3900) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 16].rstrip() + "\n\n..."
