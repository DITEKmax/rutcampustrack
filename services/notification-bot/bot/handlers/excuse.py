"""Aiogram router — handles inline-button callbacks for excuse tickets.

Callback data format: "ex:approve:<ticket_id>" | "ex:reject:<ticket_id>".

Симметрия с late_checkin.py: бот публикует excuse.decision в RabbitMQ, а
attendance-service консумирует и применяет решение (включая каскад на
attendance-документы, если APPROVED). Сообщение редактируем сразу —
attendance-service авторитетен, дубли идемпотентны на его стороне.

Файл-аттачмент (если был) приходит отдельным сообщением выше/ниже текста.
В будущем можно отредактировать и документ, но пока трогаем только текст.
"""

import logging

from aiogram import F, Router
from aiogram.types import CallbackQuery

logger = logging.getLogger(__name__)

excuse_router = Router(name="excuse_callbacks")


@excuse_router.callback_query(F.data.startswith("ex:"))
async def handle_excuse_decision(callback: CallbackQuery, **data) -> None:
    parts = callback.data.split(":")
    if len(parts) != 3 or parts[1] not in ("approve", "reject"):
        await callback.answer("Некорректный запрос", show_alert=False)
        return

    action, ticket_id = parts[1], parts[2]
    approved = action == "approve"

    event_publisher = data.get("event_publisher")
    if event_publisher is None:
        logger.error("event_publisher not injected into dispatcher workflow data")
        await callback.answer("Сервис временно недоступен", show_alert=True)
        return

    try:
        await event_publisher.publish(
            "excuse.decision",
            {
                "ticket_id": ticket_id,
                "approved": approved,
                "decision_by": callback.from_user.id,
            },
        )
    except Exception:
        logger.exception("Failed to publish excuse.decision")
        await callback.answer("Не удалось отправить решение, попробуйте ещё раз", show_alert=True)
        return

    verdict_line = "✅ Одобрено" if approved else "❌ Отклонено"
    # Берём caption, если это document/photo, иначе — text.
    original = callback.message.caption if callback.message.caption else (callback.message.text or "")
    new_text = f"{original}\n\n{verdict_line}"
    try:
        if callback.message.caption is not None:
            await callback.message.edit_caption(caption=new_text, reply_markup=None)
        else:
            await callback.message.edit_text(new_text, reply_markup=None)
    except Exception:
        logger.warning("Failed to edit message after excuse decision — continuing", exc_info=True)

    await callback.answer(verdict_line)
