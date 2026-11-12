"""Handlers for homework notification events."""

import logging
from html import escape

import grpc
from aiogram import Bot

from bot.notifications.presentation import digest_pages, message_options
from bot.services.notification_prefs import NotificationPreferencesUnavailable, type_for_event
from bot.services.send_queue import SendTask, TelegramSendQueue, group_audience

logger = logging.getLogger(__name__)


async def handle_homework(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Send homework notifications.

    Handles group-wide publish/update events and per-student digest/reminder events.
    Threat T-24-07: validates required fields group_id and title before processing.
    """
    event_type = event.get("event_type")
    payload = event.get("payload", {})

    if event_type == "homework.weekly_digest":
        await _handle_weekly_digest(
            payload, bot=bot, academic_client=academic_client, send_queue=send_queue, config=kwargs.get("config")
        )
        return
    if event_type == "homework.due_reminder":
        await _handle_due_reminder(
            payload, bot=bot, academic_client=academic_client, send_queue=send_queue, config=kwargs.get("config")
        )
        return

    try:
        group_id = payload["group_id"]
        payload["title"]
    except KeyError as exc:
        logger.warning("homework event missing required field: %s", exc)
        return

    async def _resolve_subject_name(subject_id):
        if subject_id is None:
            return "Предмет"
        try:
            subjects_resp = await academic_client.get_subjects_by_ids([subject_id])
            if subjects_resp.subjects:
                return subjects_resp.subjects[0].subject_name
        except Exception:
            logger.warning(
                "Could not resolve subject_id=%s for %s, using fallback",
                subject_id,
                event_type,
            )
        return "Предмет"

    if event_type == "homework.published":
        subject_name = await _resolve_subject_name(payload.get("subject_id"))
    elif event_type == "homework.updated":
        # Phase 61 / D-07: payload теперь содержит subject_id + lesson_date + lesson_number.
        subject_name = await _resolve_subject_name(payload.get("subject_id"))
    else:
        logger.debug("handle_homework called with unexpected event_type: %s", event_type)
        return

    options = message_options(event, config=kwargs.get("config"), facts={"subject_name": subject_name})
    members = await academic_client.get_current_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue

        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, **options),
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="homework",
                notification_type=type_for_event(event),
                audience_check=group_audience(academic_client, group_id, student.user_id, student.telegram_id),
            )
        )


async def _handle_weekly_digest(
    payload: dict, bot: Bot, academic_client, send_queue: TelegramSendQueue, config=None
) -> None:
    user_id = payload.get("user_id")
    if user_id is None:
        logger.warning("homework.weekly_digest missing user_id")
        return

    user = await _resolve_user(user_id, academic_client)
    if user is None or not getattr(user, "telegram_id", 0):
        return

    event = {"event_type": "homework.weekly_digest", "payload": payload}
    pages = digest_pages(event)
    for index, page in enumerate(pages, start=1):
        suffix = f"\n\nСтраница {index} из {len(pages)}" if len(pages) > 1 else ""
        options = message_options(event, config=config, text=escape(page + suffix))
        await send_queue.put(
            SendTask(
                coroutine_factory=lambda chat=user.telegram_id, page_options=options: bot.send_message(
                    chat_id=chat, **page_options
                ),
                user_id=user_id,
                chat_id=user.telegram_id,
                category="homework",
                notification_type=type_for_event(event),
            )
        )


async def _handle_due_reminder(
    payload: dict, bot: Bot, academic_client, send_queue: TelegramSendQueue, config=None
) -> None:
    user_id = payload.get("user_id")
    homework = payload.get("homework")
    if user_id is None or not isinstance(homework, dict):
        logger.warning("homework.due_reminder missing user_id or homework")
        return

    user = await _resolve_user(user_id, academic_client)
    if user is None or not getattr(user, "telegram_id", 0):
        return

    event = {"event_type": "homework.due_reminder", "payload": payload}
    options = message_options(event, config=config)
    await send_queue.put(
        SendTask(
            coroutine_factory=lambda chat=user.telegram_id: bot.send_message(chat_id=chat, **options),
            user_id=user_id,
            chat_id=user.telegram_id,
            category="homework",
            notification_type=type_for_event(event),
        )
    )


async def _resolve_user(user_id, academic_client):
    try:
        return await academic_client.get_user_by_id(int(user_id))
    except Exception as error:
        # A known removed user is a terminal denial; transport failure is unknown.
        if isinstance(error, grpc.aio.AioRpcError) and error.code() == grpc.StatusCode.NOT_FOUND:
            return None
        logger.warning("Could not resolve user_id=%s for homework notification", user_id, exc_info=True)
        raise NotificationPreferencesUnavailable("Current user identity is unavailable")
