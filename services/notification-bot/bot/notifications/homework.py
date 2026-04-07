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

    if event_type == "homework.published":
        subject_id = payload.get("subject_id")
        subject_name = "Предмет"
        if subject_id is not None:
            try:
                subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
                if subjects_resp.subjects:
                    subject_name = subjects_resp.subjects[0].subject_name
            except Exception:
                logger.warning(
                    "Could not resolve subject_id=%s for homework.published, using fallback",
                    subject_id,
                )
        text = f"Новое домашнее задание\n\n{subject_name}\n{title}"
    elif event_type == "homework.updated":
        text = f"Домашнее задание обновлено\n\n{title}"
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
            )
        )
