"""Handler for attendance.marked events — deletes reminders when student checks in."""
import logging

from aiogram import Bot
from aiogram.exceptions import TelegramBadRequest

from bot.services.redis_client import ReminderRedisClient

logger = logging.getLogger(__name__)


async def handle_attendance_marked(
    event: dict,
    bot: Bot,
    academic_client,
    redis_client: ReminderRedisClient,
) -> None:
    """Delete reminder messages for a student who has checked in (status=present).

    CRITICAL: Only act on status=present. The auto_scheduler writes status=absent
    for no-shows — deleting reminders on absent would break NOTIF-04 cleanup.

    Protocol:
    1. Guard: if status != "present", return immediately.
    2. Validate required payload fields.
    3. Look up student's telegram_id via group members (cached).
    4. Delete all stored message_ids for (lesson_id, user_id) and clear Redis key.
    """
    payload = event.get("payload", {})
    status = payload.get("status")
    if status != "present":
        return  # Only clean up on self check-in (not absent/excused/free_attendance)

    lesson_id = payload.get("lesson_id")
    user_id = payload.get("user_id")
    group_id = payload.get("group_id")
    if lesson_id is None or user_id is None or group_id is None:
        logger.warning("attendance.marked missing required fields")
        return

    # Look up student's telegram_id via group members (cached)
    members = await academic_client.get_group_members(group_id)
    student = next((m for m in members if m.user_id == user_id), None)
    if student is None or not student.telegram_id:
        return

    message_ids = await redis_client.get_message_ids(lesson_id, user_id)
    if not message_ids:
        return  # No reminders to delete

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
    await redis_client.delete_key(lesson_id, user_id)
