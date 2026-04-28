"""Handler for attendance.marked events.

Two responsibilities:
  1. (NOTIF unification) При `marked_by=headman` сообщить студенту, что
     староста проставил ему статус — независимо от того, был ли это
     present/absent/excused/free_attendance. self/auto не уведомляем.
  2. (NOTIF-05) Удалить активные ремайндеры в Telegram, когда статус
     перестал быть «студента ждут» (present/excused/free_attendance —
     неважно кем выставлен).
"""

import logging

from aiogram import Bot
from aiogram.exceptions import TelegramBadRequest

from bot.services.redis_client import ReminderRedisClient

logger = logging.getLogger(__name__)

# Статусы, при которых reminder-сообщения больше не нужны.
_CLEANUP_STATUSES = frozenset({"present", "excused", "free_attendance"})

# Человекочитаемые подписи статусов для текста уведомления студенту.
_STATUS_LABEL_RU: dict[str, str] = {
    "present": "присутствует (б)",
    "absent": "отсутствует (н)",
    "excused": "уважительная (у)",
    "free_attendance": "свободное посещение (сп)",
}


async def handle_attendance_marked(
    event: dict,
    bot: Bot,
    academic_client,
    redis_client: ReminderRedisClient,
) -> None:
    payload = event.get("payload", {})
    status = payload.get("status")
    marked_by = payload.get("marked_by")
    lesson_id = payload.get("lesson_id")
    user_id = payload.get("user_id")
    group_id = payload.get("group_id")
    if lesson_id is None or user_id is None or group_id is None:
        logger.warning("attendance.marked missing required fields")
        return

    members = await academic_client.get_group_members(group_id)
    student = next((m for m in members if m.user_id == user_id), None)
    if student is None or not student.telegram_id:
        return

    # 1. Студенту — уведомление о ручной правке статуса старостой.
    if marked_by == "headman":
        status_label = _STATUS_LABEL_RU.get(status, status or "—")
        lesson_number = payload.get("lesson_number")
        lesson_date = payload.get("lesson_date")
        subject_name = payload.get("subject_name")
        # Сообщение строится «без жёстких полей»: если subject_name не пришёл,
        # просто опускаем — сервер обогащает best-effort через gRPC.
        line_lesson = ""
        if lesson_number is not None and subject_name:
            line_lesson = f"Пара №{lesson_number} · {subject_name}"
        elif lesson_number is not None:
            line_lesson = f"Пара №{lesson_number}"
        elif subject_name:
            line_lesson = subject_name
        line_date = lesson_date or ""
        text_parts = ["Староста проставил вам статус: " + status_label]
        if line_lesson:
            text_parts.append(line_lesson)
        if line_date:
            text_parts.append(line_date)
        try:
            await bot.send_message(chat_id=student.telegram_id, text="\n".join(text_parts))
        except Exception:
            logger.warning(
                "Failed to notify student chat_id=%d about manual mark",
                student.telegram_id,
                exc_info=True,
            )

    # 2. Cleanup активных ремайндеров (только для «отметка получена» статусов).
    if status not in _CLEANUP_STATUSES:
        return

    message_ids = await redis_client.get_message_ids(lesson_id, user_id)
    if not message_ids:
        return

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
    await redis_client.delete_key(lesson_id, user_id)
