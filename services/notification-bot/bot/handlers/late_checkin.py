"""Aiogram router — handles inline-button callbacks for late-checkin requests.

Callback data format: "lcr:approve:<request_id>" | "lcr:reject:<request_id>".

On press the bot publishes a late_checkin.decision event via RabbitMQ; the
attendance-service consumes it, applies the decision, and publishes
late_checkin.decided which notifies the student. The bot also edits the original
message so the headman sees immediate feedback ("⏳ Обрабатываем…" → final verdict
once late_checkin.decided arrives — see student_alerts.py). For simplicity we edit
straight to the final label here, since the decision event is fire-and-forget and
the attendance-service is authoritative.
"""

import logging

from aiogram import F, Router
from aiogram.types import CallbackQuery

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
                "decision_by": callback.from_user.id,
            },
        )
    except Exception:
        logger.exception("Failed to publish late_checkin.decision")
        await callback.answer("Не удалось отправить решение, попробуйте ещё раз", show_alert=True)
        return

    verdict_line = "✅ Подтверждено" if approved else "❌ Отклонено"
    original = callback.message.text or ""
    new_text = f"{original}\n\n{verdict_line}"
    try:
        await callback.message.edit_text(new_text, reply_markup=None)
    except Exception:
        logger.warning("Failed to edit message after decision — continuing", exc_info=True)

    await callback.answer(verdict_line)
