"""Handler for homework events — sends notifications to all group students."""

import logging

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
    """Send homework notification to all group students with telegram_id.

    Handles both homework.published (with subject resolution) and homework.updated.
    Threat T-24-07: validates required fields group_id and title before processing.
    """
    event_type = event.get("event_type")
    payload = event.get("payload", {})

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

    def _add_optional(lines, value):
        if isinstance(value, str) and value.strip():
            lines.append(value.strip())

    def _build_card(prefix, subject_name):
        lines = [prefix, "", subject_name, title]
        _add_optional(lines, payload.get("description"))
        _add_optional(lines, payload.get("link"))
        if lesson_date and lesson_number:
            lines.append(f"Пара {lesson_number}, {lesson_date}")
        return "\n".join(lines)

    if event_type == "homework.published":
        subject_name = await _resolve_subject_name(payload.get("subject_id"))
        text = _build_card("Новое домашнее задание", subject_name)
    elif event_type == "homework.updated":
        # Phase 61 / D-07: payload теперь содержит subject_id + lesson_date + lesson_number.
        subject_name = await _resolve_subject_name(payload.get("subject_id"))
        text = _build_card("ДЗ изменено", subject_name)
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
