"""Handler for lesson.started events — sends a Telegram reminder to group students."""

import logging

from aiogram import Bot

from bot.config import Settings
from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_lesson_started(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    redis_client,
    config: Settings,
) -> None:
    """Send a plain Telegram message to all group students that have a
    telegram_id set. Stores each sent message_id in Redis so reminder cleanup
    can delete them later.

    Threat T-24-02: missing payload fields are caught and logged.
    """
    payload = event.get("payload", {})
    try:
        lesson_id = payload["lesson_id"]
        group_id = payload["group_id"]
        subject_id = payload["subject_id"]
    except KeyError as exc:
        logger.warning("lesson.started event missing required field: %s", exc)
        return

    start_time = payload.get("start_time", "")
    end_time = payload.get("end_time", "")
    room = payload.get("room", "")

    # Resolve subject name via gRPC — fallback gracefully on any failure
    subject_name = "Пара"
    try:
        subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
        if subjects_resp.subjects:
            subject_name = subjects_resp.subjects[0].subject_name
    except Exception:
        logger.warning("Could not resolve subject_id=%s for lesson.started, using fallback", subject_id)

    text = (
        f"📚 Пара началась\n\n"
        f"Предмет: {subject_name}\n"
        f"Аудитория: {room}\n"
        f"Время: {start_time} - {end_time}\n\n"
        "Откройте приложение и отметьтесь."
    )

    # Fetch group members — T-24-03: each student only receives their own message
    members = await academic_client.get_group_members(group_id)
    for student in members:
        # T-24-03: skip students without a linked Telegram account
        if not student.telegram_id:
            continue

        # Use default arg binding to avoid late-binding closure bug
        async def send_and_store(s=student):
            result = await bot.send_message(chat_id=s.telegram_id, text=text)
            await redis_client.add_message_id(lesson_id, s.user_id, result.message_id)

        await send_queue.put(
            SendTask(
                coroutine_factory=send_and_store,
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="reminders",
            )
        )
