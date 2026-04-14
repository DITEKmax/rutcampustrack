"""Handler for lesson.one_off.cancelled — notify group students on headman delete (Phase 60-04, D-22)."""

import logging

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_lesson_one_off_cancelled(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Notify every group student that a one-off lesson has been removed.

    Per D-22 the one-off may be deleted on any date (past/today/future) — the
    message simply states the cancellation, no preconditions are enforced on date.
    Per D-18 the headman is NOT filtered out.
    """
    payload = event.get("payload", {})
    try:
        group_id = payload["group_id"]
        subject_id = payload["subject_id"]
        date = payload["date"]
        lesson_number = payload["lesson_number"]
    except KeyError as exc:
        logger.warning("lesson.one_off.cancelled event missing required field: %s", exc)
        return

    # Resolve subject name via gRPC — fallback gracefully on failure
    subject_name = "Пара"
    try:
        subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
        if subjects_resp.subjects:
            subject_name = subjects_resp.subjects[0].subject_name
    except Exception:
        logger.warning(
            "Could not resolve subject_id=%s for lesson.one_off.cancelled, using fallback",
            subject_id,
        )

    text = f"❌ Разовая пара отменена\n\n{subject_name}\nДата: {date}\nПара: {lesson_number}-я"

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
