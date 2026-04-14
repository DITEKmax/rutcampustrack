"""Handler for group.archived events — notifies all group students via Telegram.

58-07 / BUG-006-6: запускается из EventDispatcher когда приходит RabbitMQ-событие
{event_type:"group.archived", payload:{group_id:N}}. Архивация обычно связана с
выпуском (4 курс → суффикс «(выпуск YYYY)»); сообщение поздравляет студентов.
"""

import logging

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


async def handle_group_archived(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    payload = event.get("payload") or {}
    group_id = payload.get("group_id")
    if group_id is None:
        logger.warning("group.archived event missing group_id: %s", event)
        return

    group_name = ""
    try:
        group = await academic_client.get_group(group_id)
        group_name = getattr(group, "name", "") or ""
    except Exception:
        logger.warning("get_group failed for group_id=%s (group.archived), using fallback", group_id)

    if group_name:
        text = f"🎓 Группа <b>{group_name}</b> архивирована. Поздравляем с выпуском!"
    else:
        text = "🎓 Ваша группа архивирована. Поздравляем с выпуском!"

    try:
        members = await academic_client.get_group_members(group_id)
    except Exception:
        logger.exception("get_group_members failed for group_id=%s (group.archived)", group_id)
        return

    for student in members:
        if not getattr(student, "telegram_id", 0):
            continue
        await send_queue.put(
            SendTask(
                coroutine_factory=lambda s=student: bot.send_message(
                    chat_id=s.telegram_id, text=text, parse_mode="HTML"
                ),
                user_id=student.user_id,
                chat_id=student.telegram_id,
            )
        )
