"""Handler for attendance.marked events — deletes reminders when student checks in."""

import logging

from aiogram import Bot
from aiogram.exceptions import TelegramBadRequest

from bot.services.redis_client import ReminderRedisClient

logger = logging.getLogger(__name__)

# Статусы, при которых напоминания больше не нужны: студент либо отметился сам,
# либо ему проставили уважительную/свободное посещение. absent (авто-статус для
# не отметившихся в конце пары) обрабатывается через lesson.closed → NOTIF-04.
_CLEANUP_STATUSES = frozenset({"present", "excused", "free_attendance"})


async def handle_attendance_marked(
    event: dict,
    bot: Bot,
    academic_client,
    redis_client: ReminderRedisClient,
) -> None:
    """Delete reminder messages for a student whose attendance became final.

    Снимаем ремайндер-сообщения ТГ, когда статус перестал быть «студента ждут
    на паре»:
      · present          — студент отметился сам;
      · excused          — староста/админ поставил у.п.;
      · free_attendance  — свободное посещение на эту пару.

    Для status=absent, который auto_scheduler пишет в конце пары для не
    отметившихся, мы НЕ чистим тут — lesson.closed приедет чуть позже и
    приведёт к общему cleanup через lesson_closed handler (NOTIF-04).

    Protocol:
    1. Guard: принимаем только perminated-статусы, прочее (absent, cancelled) игнор.
    2. Validate required payload fields.
    3. Look up student's telegram_id via group members (cached).
    4. Delete all stored message_ids for (lesson_id, user_id) and clear Redis key.
    """
    payload = event.get("payload", {})
    status = payload.get("status")
    if status not in _CLEANUP_STATUSES:
        return

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
