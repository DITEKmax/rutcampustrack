"""Handler for otp.verified events — removes Telegram messages with OTP code.

Triggered when Auth Service publishes otp.verified after a successful login.
Deletes both the user's "получить код для входа" request and the bot's reply
with the code, so that no stale code remains visible in the chat.

Also invoked locally (via cleanup_otp_messages) by the expiry timer in login.py
when the OTP lifetime elapses without verification. pop() is atomic — whichever
path runs first wins, the other gets None and exits silently.
"""

from __future__ import annotations

import logging

from aiogram import Bot
from aiogram.exceptions import TelegramBadRequest

from bot.services.otp_message_tracker import OtpMessageTracker

logger = logging.getLogger(__name__)


async def cleanup_otp_messages(
    telegram_id: int,
    *,
    bot: Bot,
    tracker: OtpMessageTracker,
    reason: str = "verified",
) -> None:
    """Delete the OTP-related Telegram messages for the given user.

    Idempotent: if the tracker entry is already gone (another cleanup path
    ran first), this is a no-op. TelegramBadRequest (message already deleted,
    too old to delete) is swallowed — nothing to do at that point.
    """
    stored = await tracker.pop(telegram_id)
    if stored is None:
        logger.debug("No OTP messages to clean for telegram_id=%d (%s)", telegram_id, reason)
        return

    chat_id = stored["chat_id"]
    for key in ("bot_message_id", "user_message_id"):
        message_id = stored.get(key)
        if message_id is None:
            continue
        try:
            await bot.delete_message(chat_id=chat_id, message_id=message_id)
        except TelegramBadRequest as e:
            logger.debug(
                "Could not delete %s=%d for telegram_id=%d: %s",
                key,
                message_id,
                telegram_id,
                e.message,
            )
        except Exception:
            logger.exception(
                "Unexpected error deleting %s=%d for telegram_id=%d",
                key,
                message_id,
                telegram_id,
            )

    logger.info("Cleaned OTP messages for telegram_id=%d (%s)", telegram_id, reason)


async def handle_otp_verified(
    event: dict,
    *,
    bot: Bot,
    tracker: OtpMessageTracker,
) -> None:
    """Dispatch wrapper for RabbitMQ otp.verified events."""
    payload = event.get("payload", {}) or {}
    telegram_id = payload.get("telegram_id")
    if telegram_id is None:
        logger.warning("otp.verified event missing telegram_id: %s", event)
        return
    await cleanup_otp_messages(int(telegram_id), bot=bot, tracker=tracker, reason="verified")
