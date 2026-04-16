"""Handler for headman alert events — sends notifications to group headmen only."""

import base64
import binascii
import logging

from aiogram import Bot
from aiogram.types import BufferedInputFile, InlineKeyboardButton, InlineKeyboardMarkup

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)

# Telegram's hard limit for bot uploads is 50 MB; the Java side caps at 10 MB.
# Anything larger is dropped with a warning — we never silently truncate.
MAX_FORWARDED_FILE_BYTES = 10 * 1024 * 1024


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

    reply_markup = None

    file_bytes: bytes | None = None
    file_name: str | None = None

    if event_type == "excuse.requested":
        excuse_type_label = payload.get("excuse_type", "не указан")
        text = f"Запрос у.п.\n\nСтудент: {student_name}\nТип: {excuse_type_label}"
        comment = payload.get("comment")
        if comment:
            text += f"\nКомментарий: {comment}"

        file_b64 = payload.get("file_payload_b64")
        if file_b64:
            try:
                file_bytes = base64.b64decode(file_b64, validate=True)
            except (binascii.Error, ValueError) as exc:
                logger.warning("excuse.requested file_payload_b64 decode failed: %s", exc)
                file_bytes = None
            if file_bytes is not None and len(file_bytes) > MAX_FORWARDED_FILE_BYTES:
                logger.warning(
                    "excuse.requested attachment too large (%d bytes), skipping",
                    len(file_bytes),
                )
                file_bytes = None
            file_name = payload.get("file_name") or "attachment"
    elif event_type == "late_checkin.requested":
        text = f"Запрос подтверждения присутствия\n\nСтудент: {student_name}"
        subject_name = payload.get("subject_name")
        if subject_name:
            text += f"\nПредмет: {subject_name}"
        lesson_number = payload.get("lesson_number")
        if lesson_number:
            text += f"\nПара: №{lesson_number}"
        lesson_date = payload.get("lesson_date")
        if lesson_date:
            text += f"\nДата: {lesson_date}"
        request_id = payload.get("request_id")
        if request_id:
            reply_markup = InlineKeyboardMarkup(
                inline_keyboard=[
                    [
                        InlineKeyboardButton(text="✅ Подтвердить", callback_data=f"lcr:approve:{request_id}"),
                        InlineKeyboardButton(text="❌ Отклонить", callback_data=f"lcr:reject:{request_id}"),
                    ]
                ]
            )
        else:
            logger.warning("late_checkin.requested missing request_id, sending without buttons")
    else:
        logger.debug("handle_headman_alert called with unexpected event_type: %s", event_type)
        return

    for headman in headmen:
        if file_bytes:
            # Telegram caption limit is 1024 chars — truncate defensively so the
            # send never fails because a long comment pushed us over the edge.
            caption = text if len(text) <= 1024 else text[:1020] + "…"

            async def _send_document(h=headman, markup=reply_markup,
                                     payload_bytes=file_bytes,
                                     payload_name=file_name,
                                     payload_caption=caption):
                return await bot.send_document(
                    chat_id=h.telegram_id,
                    document=BufferedInputFile(payload_bytes, filename=payload_name),
                    caption=payload_caption,
                    reply_markup=markup,
                )

            await send_queue.put(
                SendTask(
                    coroutine_factory=_send_document,
                    user_id=headman.user_id,
                    chat_id=headman.telegram_id,
                )
            )
        else:
            await send_queue.put(
                SendTask(
                    coroutine_factory=lambda h=headman, markup=reply_markup: bot.send_message(
                        chat_id=h.telegram_id, text=text, reply_markup=markup
                    ),
                    user_id=headman.user_id,
                    chat_id=headman.telegram_id,
                )
            )
