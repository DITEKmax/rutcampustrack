"""Handler for headman alert events — sends notifications to group headmen only."""

import logging

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_headman_alert(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Send alert to headman(s) of the group only.

    Handles excuse.requested and late_checkin.requested events.
    Threat T-24-06: only headmen (is_headman=True) receive these notifications.
    Threat T-24-07: validates required fields before processing.
    """
    event_type = event.get("event_type")
    payload = event.get("payload", {})

    try:
        group_id = payload["group_id"]
        user_id = payload["user_id"]
    except KeyError as exc:
        logger.warning("headman alert event missing required field: %s", exc)
        return

    members = await academic_client.get_group_members(group_id)

    # T-24-06: filter headmen with telegram_id only
    headmen = [m for m in members if m.is_headman and m.telegram_id]

    if not headmen:
        logger.warning("No headman with telegram_id found for group_id=%s, skipping headman alert", group_id)
        return

    # Resolve student name: payload > member lookup > fallback
    student_name = payload.get("student_name")
    if not student_name:
        matching = next((m for m in members if m.user_id == user_id), None)
        if matching:
            student_name = matching.display_name
        else:
            student_name = f"Студент #{user_id}"

    if event_type == "excuse.requested":
        excuse_type_label = payload.get("excuse_type", "не указан")
        text = f"Запрос у.п.\n\nСтудент: {student_name}\nТип: {excuse_type_label}"
    elif event_type == "late_checkin.requested":
        text = f"Запрос подтверждения присутствия\n\nСтудент: {student_name}"
        lesson_date = payload.get("lesson_date")
        if lesson_date:
            text += f"\nДата: {lesson_date}"
    else:
        logger.debug("handle_headman_alert called with unexpected event_type: %s", event_type)
        return

    for headman in headmen:
        await send_queue.put(
            SendTask(
                coroutine_factory=lambda h=headman: bot.send_message(chat_id=h.telegram_id, text=text),
                user_id=headman.user_id,
                chat_id=headman.telegram_id,
            )
        )
