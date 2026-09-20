"""Aiogram router — handles inline-button callbacks for excuse tickets.

Callback data format: "ex:approve:<ticket_id>" | "ex:reject:<ticket_id>".

Симметрия с late_checkin.py: бот публикует excuse.decision в RabbitMQ, а
attendance-service консумирует и применяет решение (включая каскад на
attendance-документы, если APPROVED). Сообщение редактируем сразу —
attendance-service авторитетен, дубли идемпотентны на его стороне.

Файл-аттачмент (если был) приходит отдельным сообщением выше/ниже текста.
Окончательный статус приходит только из authoritative ``excuse.decided``.

M09 G6 (06 P1-1): перед publish проверяем is_headman через
academic_client.get_user_by_telegram_id. Student или unlinked Telegram
получает alert «Недостаточно прав» без publish — защита от forwarded
inline-кнопок и compromised telegram_id из группового чата.
"""

import logging

from aiogram import F, Router
from aiogram.types import CallbackQuery

logger = logging.getLogger(__name__)

excuse_router = Router(name="excuse_callbacks")


async def _resolve_headman(callback: CallbackQuery, academic_client):
    """Возвращает проверенного старосту с положительным internal ``user_id``.

    Иначе отвечает на callback и возвращает ``None``. Один lookup сохраняет
    связку Telegram caller → academic user для decision events.

    Ошибка gRPC → fail-closed: считаем не-старостой и возвращаем False.
    Это безопаснее fail-open (лучше отказать при сетевой проблеме, чем
    пропустить decision от студента).
    """
    if academic_client is None:
        logger.error("academic_client not injected into dispatcher workflow data")
        await callback.answer("Сервис временно недоступен", show_alert=True)
        return None
    try:
        user = await academic_client.get_user_by_telegram_id(callback.from_user.id)
    except Exception:
        logger.exception("academic_client.get_user_by_telegram_id failed — denying")
        await callback.answer("Не удалось проверить права, попробуйте ещё раз", show_alert=True)
        return None
    user_id = getattr(user, "user_id", None)
    if (
        not getattr(user, "found", False)
        or not getattr(user, "is_headman", False)
        or not isinstance(user_id, int)
        or user_id <= 0
    ):
        await callback.answer("Недостаточно прав", show_alert=True)
        return None
    return user


async def _verify_headman(callback: CallbackQuery, academic_client) -> bool:
    """Совместимая bool-обёртка для существующего excuse callback flow."""
    return await _resolve_headman(callback, academic_client) is not None


@excuse_router.callback_query(F.data.startswith("ex:"))
async def handle_excuse_decision(callback: CallbackQuery, **data) -> None:
    parts = callback.data.split(":")
    if len(parts) != 3 or parts[1] not in ("approve", "reject"):
        await callback.answer("Некорректный запрос", show_alert=False)
        return

    action, ticket_id = parts[1], parts[2]
    approved = action == "approve"

    # M09 G6 (06 P1-1) — role check ДО publish: student или не-headman
    # не должен иметь возможность решать тикет через forwarded кнопку.  The
    # resolved internal id is the only actor identity accepted by Attendance;
    # Telegram ids never enter the decision event.
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
            "excuse.decision",
            {
                "ticket_id": ticket_id,
                "approved": approved,
                "decision_by": headman.user_id,
            },
        )
    except Exception:
        logger.exception("Failed to publish excuse.decision")
        await callback.answer("Не удалось отправить решение, попробуйте ещё раз", show_alert=True)
        return

    # Callback acknowledgement is deliberately pending.  The authoritative
    # ``excuse.decided`` event supplies the final approved/rejected text after
    # Attendance has committed the decision.
    verdict_line = "⏳ Решение отправлено"
    # Берём caption, если это document/photo, иначе — text.
    original = callback.message.caption if callback.message.caption else (callback.message.text or "")
    new_text = f"{original}\n\nРешение: {verdict_line}"
    try:
        if callback.message.caption is not None:
            await callback.message.edit_caption(caption=new_text, reply_markup=None)
        else:
            await callback.message.edit_text(new_text, reply_markup=None)
    except Exception:
        logger.warning("Failed to edit message after excuse decision — continuing", exc_info=True)

    await callback.answer(verdict_line)
