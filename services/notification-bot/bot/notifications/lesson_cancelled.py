"""Handler for lesson.cancelled events — sends plain-text cancellation to group students."""

import logging

from aiogram import Bot

from bot.notifications.presentation import message_options
from bot.services.notification_prefs import type_for_event
from bot.services.send_queue import SendTask, TelegramSendQueue, group_audience

logger = logging.getLogger(__name__)


async def handle_lesson_cancelled(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Send a plain-text cancellation notice to all group students with telegram_id.

    cancel_reason is optional — appended to the message when present.
    Threat T-24-02: missing payload fields are caught and logged.
    """
    payload = event.get("payload", {})
    try:
        group_id = payload["group_id"]
        subject_id = payload["subject_id"]
    except KeyError as exc:
        logger.warning("lesson.cancelled event missing required field: %s", exc)
        return

    # Resolve subject name via gRPC — fallback gracefully on any failure
    subject_name = "Пара"
    try:
        subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
        if subjects_resp.subjects:
            subject_name = subjects_resp.subjects[0].subject_name
    except Exception:
        logger.warning("Could not resolve subject_id=%s for lesson.cancelled, using fallback", subject_id)

    options = message_options(event, config=kwargs.get("config"), facts={"subject_name": subject_name})

    members = await academic_client.get_current_group_members(group_id)
    for student in members:
        # Skip students without a linked Telegram account
        if not student.telegram_id:
            continue

        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, **options),
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="lessons",
                notification_type=type_for_event(event),
                audience_check=group_audience(academic_client, group_id, student.user_id, student.telegram_id),
            )
        )
