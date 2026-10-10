"""Handler for lesson.reminder events.

Fans out midpoint/near-end reminders to Telegram users who have not been
marked for the lesson yet. The checked-in state is tracked separately from
message ids so a failed start-message send does not suppress later reminders.
"""

import logging

from aiogram import Bot

from bot.notifications.presentation import message_options
from bot.services.notification_prefs import type_for_event
from bot.services.redis_client import ReminderRedisClient
from bot.services.send_queue import SendTask, TelegramSendQueue, group_audience

logger = logging.getLogger(__name__)


async def handle_lesson_reminder(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    redis_client: ReminderRedisClient,
    config=None,
) -> None:
    payload = event.get("payload", {})
    lesson_id = payload.get("lesson_id")
    group_id = payload.get("group_id")
    if lesson_id is None or group_id is None:
        logger.warning("lesson.reminder missing required fields: lesson_id or group_id")
        return

    subject_name = payload.get("subject_name")
    if not subject_name and payload.get("subject_id"):
        try:
            result = await academic_client.get_subjects_by_ids([payload["subject_id"]])
            if result.subjects:
                subject_name = result.subjects[0].subject_name
        except Exception:
            logger.warning("Could not resolve reminder subject context")
    options = message_options(event, config=config, facts={"subject_name": subject_name})

    members = await academic_client.get_current_group_members(group_id)
    queued = 0
    for student in members:
        if not student.telegram_id:
            continue
        if await redis_client.is_student_marked(lesson_id, student.user_id):
            continue

        async def send_and_store(s=student):
            result = await bot.send_message(
                chat_id=s.telegram_id,
                **options,
            )
            await redis_client.add_message_id(lesson_id, s.user_id, result.message_id)

        await send_queue.put(
            SendTask(
                coroutine_factory=send_and_store,
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="reminders",
                notification_type=type_for_event(event),
                audience_check=group_audience(academic_client, group_id, student.user_id, student.telegram_id),
            )
        )
        queued += 1
    logger.info("lesson.reminder queued for lesson_id=%s, students=%d", lesson_id, queued)
