"""Handler for lesson.reminder events — fan-out reminder to students who haven't checked in yet.

Источник события — schedule-service LessonReminderJob (раз на пару, при пересечении
середины). Per-user фильтрация: студент уже отметился, если ReminderRedisClient
очистил ключ (в attendance_marked.py при PRESENT/EXCUSED/FREE_ATTENDANCE).

Категория для пользовательских настроек — "reminders" (см. NotificationPrefsClient).
"""

import logging

from aiogram import Bot

from bot.services.redis_client import ReminderRedisClient
from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_lesson_reminder(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    redis_client: ReminderRedisClient,
) -> None:
    payload = event.get("payload", {})
    lesson_id = payload.get("lesson_id")
    group_id = payload.get("group_id")
    if lesson_id is None or group_id is None:
        logger.warning("lesson.reminder missing required fields: lesson_id or group_id")
        return

    phase = payload.get("phase")
    reminder_text = (
        "Пара скоро закончится. Если вы ещё не отметились, сделайте это сейчас."
        if phase == "near_end"
        else "Напоминание: отметьтесь на паре!"
    )

    members = await academic_client.get_group_members(group_id)
    queued = 0
    for student in members:
        if not student.telegram_id:
            continue
        existing_ids = await redis_client.get_message_ids(lesson_id, student.user_id)
        if not existing_ids:
            # Студент уже отметился (NOTIF-05 очистил ключ) либо не получал стартового
            # сообщения — напоминать нечего.
            continue

        async def send_and_store(s=student):
            result = await bot.send_message(
                chat_id=s.telegram_id,
                text=reminder_text,
            )
            await redis_client.add_message_id(lesson_id, s.user_id, result.message_id)

        await send_queue.put(
            SendTask(
                coroutine_factory=send_and_store,
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="reminders",
            )
        )
        queued += 1
    logger.info("lesson.reminder queued for lesson_id=%s, students=%d", lesson_id, queued)
