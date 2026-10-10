"""Handler for group.renamed events — notifies all group students via Telegram.

58-07 / BUG-006-6: запускается из EventDispatcher когда приходит RabbitMQ-событие
{event_type:"group.renamed", payload:{group_id:N}}. Резолвит актуальное имя через
gRPC AcademicGrpcService.GetGroup и шлёт сообщение всем студентам группы,
у которых привязан telegram_id.
"""

import logging

from aiogram import Bot

from bot.notifications.presentation import message_options
from bot.services.notification_prefs import NotificationPreferencesUnavailable, type_for_event
from bot.services.send_queue import SendTask, TelegramSendQueue, group_audience

logger = logging.getLogger(__name__)


async def handle_group_renamed(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    payload = event.get("payload") or {}
    group_id = payload.get("group_id")
    if group_id is None:
        logger.warning("group.renamed event missing group_id: %s", event)
        return

    # Resolve fresh group info; fall back to generic text if gRPC fails.
    group_name = ""
    try:
        group = await academic_client.get_group(group_id)
        group_name = getattr(group, "name", "") or ""
    except Exception:
        logger.warning("get_group failed for group_id=%s (group.renamed), using fallback", group_id)

    options = message_options(event, config=kwargs.get("config"), facts={"group_name": group_name})

    try:
        members = await academic_client.get_current_group_members(group_id)
    except Exception:
        logger.exception("get_current_group_members failed for group_id=%s (group.renamed)", group_id)
        raise NotificationPreferencesUnavailable("Current group audience is unavailable")

    for student in members:
        if not getattr(student, "telegram_id", 0):
            continue
        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, **options),
                user_id=student.user_id,
                chat_id=student.telegram_id,
                category="group",
                notification_type=type_for_event(event),
                audience_check=group_audience(academic_client, group_id, student.user_id, student.telegram_id),
            )
        )
