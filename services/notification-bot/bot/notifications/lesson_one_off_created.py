"""Handler for lesson.one_off.created — notify group students on headman add (Phase 60-04, D-18)."""

import logging

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_lesson_one_off_created(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Send a plain-text notice about a newly-added one-off lesson to every group student.

    Per D-18 the headman is NOT filtered out — the entire group (headman included)
    receives the push on a single channel.

    Threat T-60-04 (idempotency): duplicate events cause duplicate pushes — acceptable
    for informational notifications; any send failure is absorbed by the send queue.
    """
    payload = event.get("payload", {})
    try:
        group_id = payload["group_id"]
        subject_id = payload["subject_id"]
        date = payload["date"]
        lesson_number = payload["lesson_number"]
    except KeyError as exc:
        logger.warning("lesson.one_off.created event missing required field: %s", exc)
        return

    classroom = payload.get("classroom") or "каб. не указан"

    # Resolve subject name via gRPC — fallback gracefully on failure
    subject_name = "Пара"
    try:
        subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
        if subjects_resp.subjects:
            subject_name = subjects_resp.subjects[0].subject_name
    except Exception:
        logger.warning(
            "Could not resolve subject_id=%s for lesson.one_off.created, using fallback",
            subject_id,
        )

    text = (
        "📅 Добавлена разовая пара\n\n"
        f"Предмет: {subject_name}\n"
        f"Дата: {date}\n"
        f"Пара: №{lesson_number}\n"
        f"Аудитория: {classroom}"
    )

    members = await academic_client.get_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue
        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, text=text),
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="schedule",
            )
        )
