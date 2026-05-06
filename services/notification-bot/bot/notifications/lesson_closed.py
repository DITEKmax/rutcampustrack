"""Handler for lesson.closed events."""

import logging

from aiogram import Bot
from aiogram.exceptions import TelegramBadRequest

from bot.services.redis_client import ReminderRedisClient

logger = logging.getLogger(__name__)


async def handle_lesson_closed(
    event: dict,
    bot: Bot,
    academic_client,
    redis_client: ReminderRedisClient,
) -> None:
    """Delete reminder messages and per-lesson reminder state."""
    payload = event.get("payload", {})
    lesson_id = payload.get("lesson_id")
    group_id = payload.get("group_id")
    if lesson_id is None or group_id is None:
        logger.warning("lesson.closed missing required fields: lesson_id or group_id")
        return

    members = await academic_client.get_group_members(group_id)
    for student in members:
        if student.telegram_id:
            message_ids = await redis_client.get_message_ids(lesson_id, student.user_id)
            for msg_id in message_ids:
                try:
                    await bot.delete_message(chat_id=student.telegram_id, message_id=msg_id)
                except TelegramBadRequest:
                    pass
                except Exception:
                    logger.warning(
                        "Failed to delete message_id=%d for chat_id=%d",
                        msg_id,
                        student.telegram_id,
                    )
            await redis_client.delete_key(lesson_id, student.user_id)
        await redis_client.delete_marked_key(lesson_id, student.user_id)
