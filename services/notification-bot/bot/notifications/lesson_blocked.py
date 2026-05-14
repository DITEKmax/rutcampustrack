"""Handler for lesson.blocked events — notifies group students that self check-in is unavailable."""

import logging

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_lesson_blocked(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Send a plain-text block notice to all group students with telegram_id."""
    payload = event.get("payload", {})
    try:
        group_id = payload["group_id"]
        subject_id = payload["subject_id"]
    except KeyError as exc:
        logger.warning("lesson.blocked event missing required field: %s", exc)
        return

    subject_name = "Пара"
    try:
        subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
        if subjects_resp.subjects:
            subject_name = subjects_resp.subjects[0].subject_name
    except Exception:
        logger.warning("Could not resolve subject_id=%s for lesson.blocked, using fallback", subject_id)

    text = _build_message(payload, subject_name)

    members = await academic_client.get_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue

        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, text=text),
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="lessons",
            )
        )


def _build_message(payload: dict, subject_name: str) -> str:
    lines = [
        "🔒 Пара заблокирована старостой",
        "",
        f"Предмет: {subject_name}",
    ]
    date = payload.get("date")
    if date:
        lines.append(f"Дата: {date}")

    lesson_number = payload.get("lesson_number")
    start_time = payload.get("start_time")
    end_time = payload.get("end_time")
    room = payload.get("room")
    details: list[str] = []
    if lesson_number:
        details.append(f"№{lesson_number}")
    if start_time and end_time:
        details.append(f"{start_time}-{end_time}")
    if room:
        details.append(f"ауд. {room}")
    if details:
        lines.append("Пара: " + ", ".join(details))

    lines.extend(["", "Самостоятельная отметка невозможна. Посещаемость проставляет староста."])
    return "\n".join(lines)
