"""Aiogram router — handles inline-button callbacks for late-checkin requests.

Callback data format: "lcr:approve:<request_id>" | "lcr:reject:<request_id>".

On press the bot publishes a late_checkin.decision event via RabbitMQ; the
attendance-service consumes it, applies the decision, and publishes
late_checkin.decided, which notifies the student and updates the original
card. The callback acknowledges that the command was sent.
This matters when a geo retry has already cancelled the request.

M09 G6 (06 P1-1): перед publish проверяем is_headman через academic_client
(симметрично excuse.py). Student или unlinked Telegram получает alert
«Недостаточно прав» без publish.
"""

import logging

from aiogram import F, Router
from aiogram.types import CallbackQuery

from bot.handlers.excuse import _resolve_headman

logger = logging.getLogger(__name__)

late_checkin_router = Router(name="late_checkin_callbacks")


@late_checkin_router.callback_query(F.data.startswith("lcr:"))
async def handle_late_checkin_decision(callback: CallbackQuery, **data) -> None:
    parts = callback.data.split(":")
    if len(parts) != 3 or parts[1] not in ("approve", "reject"):
        await callback.answer("Некорректный запрос", show_alert=False)
        return

    action, request_id = parts[1], parts[2]
    approved = action == "approve"

    # Один lookup ДО publish даёт и role check, и canonical internal audit actor.
    academic_client = data.get("academic_client")
    headman = await _resolve_headman(callback, academic_client)
    if headman is None:
        return

    event_publisher = data.get("event_publisher")
    if event_publisher is None:
        logger.error("event_publisher not injected into dispatcher workflow data")
        await callback.answer("Сервис временно недоступен", show_alert=True)
        return

    try:
        await event_publisher.publish(
            "late_checkin.decision",
            {
                "request_id": request_id,
                "approved": approved,
                "decision_by": headman.user_id,
            },
        )
    except Exception:
        logger.exception("Failed to publish late_checkin.decision")
        await callback.answer("Не удалось отправить решение, попробуйте ещё раз", show_alert=True)
        return

    # Only the terminal event edits the card, even if it arrives before this acknowledgement.
    await callback.answer("⏳ Решение отправлено")
