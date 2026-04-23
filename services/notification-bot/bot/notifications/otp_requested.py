"""Handler for otp.requested events (M09 G2, 08 P0-2).

Auth Service publishes otp.requested вместо возврата кода в HTTP body.
Этот handler получает event, отправляет код пользователю в Telegram и
сохраняет (chat_id, bot_message_id) в OtpMessageTracker — чтобы
последующий otp.verified мог удалить сообщения с кодом.

chat_id == telegram_id для приватных Telegram-чатов; user_message_id
сохраняется в pending-ключ при /login и финализируется здесь через
tracker.finalize_with_bot_msg.
"""

from __future__ import annotations

import asyncio
import logging

from aiogram import Bot

from bot.notifications.otp_verified import cleanup_otp_messages
from bot.services.otp_message_tracker import OtpMessageTracker

logger = logging.getLogger(__name__)

WEB_LOGIN_URL = "https://ruttrack.site/login"


async def handle_otp_requested(
    event: dict,
    *,
    bot: Bot,
    tracker: OtpMessageTracker,
) -> None:
    """Send OTP code to the user via Telegram.

    Schema — event-schemas/otp.requested.json:
      payload.telegram_id, payload.code, payload.ttl_seconds.
    """
    payload = event.get("payload") or {}
    telegram_id = payload.get("telegram_id")
    code = payload.get("code")
    ttl_seconds = payload.get("ttl_seconds") or 300

    if telegram_id is None or code is None:
        logger.warning("otp.requested event malformed: %s", event)
        return

    telegram_id = int(telegram_id)
    ttl_minutes = max(1, int(ttl_seconds) // 60)

    try:
        bot_message = await bot.send_message(
            chat_id=telegram_id,
            text=(
                f"Ваш код для входа: <code>{code}</code>\n\n"
                f"Откройте веб-панель и введите этот код:\n{WEB_LOGIN_URL}\n\n"
                f"Код действует {ttl_minutes} мин."
            ),
            parse_mode="HTML",
        )
    except Exception:
        logger.exception("Failed to send OTP message to telegram_id=%d", telegram_id)
        return

    await tracker.finalize_with_bot_msg(telegram_id, bot_message.message_id)
    asyncio.create_task(
        _cleanup_after_expiry(telegram_id, ttl_seconds=int(ttl_seconds), bot=bot, tracker=tracker)
    )


async def _cleanup_after_expiry(
    telegram_id: int,
    *,
    ttl_seconds: int,
    bot: Bot,
    tracker: OtpMessageTracker,
) -> None:
    """Sleep for the OTP TTL then clean up messages if still present.

    If otp.verified arrives first, tracker.pop() ∈ cleanup_otp_messages
    wins и эта задача станет no-op.
    """
    try:
        await asyncio.sleep(ttl_seconds)
        await cleanup_otp_messages(telegram_id, bot=bot, tracker=tracker, reason="expired")
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("OTP expiry cleanup failed for telegram_id=%d", telegram_id)
