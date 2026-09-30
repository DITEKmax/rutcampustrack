"""Notify an account's Telegram after a committed password change."""

from __future__ import annotations

import logging

from aiogram import Bot

logger = logging.getLogger(__name__)


async def handle_password_changed(event: dict, *, bot: Bot) -> None:
    payload = event.get("payload")
    if not isinstance(payload, dict):
        logger.warning("password.changed event malformed: payload_type=%s", type(payload).__name__)
        return

    telegram_id = payload.get("telegram_id")
    if isinstance(telegram_id, bool):
        logger.warning("password.changed event malformed: payload_keys=%s", list(payload.keys()))
        return
    try:
        telegram_id = int(telegram_id)
    except (TypeError, ValueError):
        logger.warning("password.changed event malformed: payload_keys=%s", list(payload.keys()))
        return
    if telegram_id <= 0:
        logger.warning("password.changed event malformed: payload_keys=%s", list(payload.keys()))
        return

    await bot.send_message(
        chat_id=telegram_id,
        text=(
            "🔐 Пароль RutCampusTrack изменён. "
            "Если это сделал не ты, немедленно обратись в поддержку."
        ),
    )
