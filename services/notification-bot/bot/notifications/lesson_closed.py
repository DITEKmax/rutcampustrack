"""Handler for lesson.closed events — deletes all reminder messages and clears Redis keys."""

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
    reminder_scheduler,
) -> None:
    """Delete all reminder messages for the closed lesson and clear Redis keys.

    Protocol:
    1. Cancel pending timer tasks (NOTIF-02/-03) before they fire stale reminders.
    2. Resolve group members (cached in academic_client, 5-min TTL).
    3. For each student: delete all stored message_ids, then clear the Redis key.

    TelegramBadRequest is silently caught — message may have already been deleted by
    NOTIF-05 (student checked in) or by the student themselves.
    """
    payload = event.get("payload", {})
    lesson_id = payload.get("lesson_id")
    group_id = payload.get("group_id")
    if lesson_id is None or group_id is None:
        logger.warning("lesson.closed missing required fields: lesson_id or group_id")
        return

    # Cancel any pending timer tasks before they fire
    if reminder_scheduler is not None:
        reminder_scheduler.cancel_lesson(lesson_id)
    else:
        logger.warning("reminder_scheduler is None — skipping cancel_lesson(%s)", lesson_id)

    # Resolve group members (cached in academic_client, 5-min TTL)
    members = await academic_client.get_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue
        message_ids = await redis_client.get_message_ids(lesson_id, student.user_id)
        if not message_ids:
            continue  # Already cleaned up (NOTIF-05) or never received a reminder
        for msg_id in message_ids:
            try:
                await bot.delete_message(chat_id=student.telegram_id, message_id=msg_id)
            except TelegramBadRequest:
                pass  # Already deleted — idempotent
            except Exception:
                logger.warning(
                    "Failed to delete message_id=%d for chat_id=%d",
                    msg_id,
                    student.telegram_id,
                )
        await redis_client.delete_key(lesson_id, student.user_id)
