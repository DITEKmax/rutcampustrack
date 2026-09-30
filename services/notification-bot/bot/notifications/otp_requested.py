"""Deliver purpose-specific OTPs through Telegram without logging their value."""

from __future__ import annotations

import asyncio
import html
import logging
import re
from urllib.parse import quote

from aiogram import Bot

from bot.notifications.otp_verified import cleanup_otp_messages
from bot.services.otp_message_tracker import OtpMessageTracker

logger = logging.getLogger(__name__)

WEB_LOGIN_URL = "https://ruttrack.site/login"
WEB_PASSWORD_RESET_URL = "https://ruttrack.site/password-reset"
_RESET_CHALLENGE = re.compile(r"[A-Za-z0-9_-]{32,64}")
_OTP_CODE = re.compile(r"[0-9]{6}")


async def handle_otp_requested(
    event: dict,
    *,
    bot: Bot,
    tracker: OtpMessageTracker,
) -> None:
    """Send a login or password-reset OTP from an ``otp.requested`` event."""
    payload = event.get("payload") or {}
    if not isinstance(payload, dict):
        logger.warning("otp.requested event malformed: payload_type=%s", type(payload).__name__)
        return

    telegram_id = payload.get("telegram_id")
    code = payload.get("code")
    purpose = payload.get("purpose", "login")
    try:
        telegram_id = int(telegram_id)
        ttl_seconds = max(1, int(payload.get("ttl_seconds") or 300))
    except (TypeError, ValueError):
        logger.warning("otp.requested event malformed: event_keys=%s payload_keys=%s",
                       list(event.keys()), list(payload.keys()))
        return

    if not isinstance(code, str) or _OTP_CODE.fullmatch(code) is None:
        logger.warning("otp.requested event malformed: event_keys=%s payload_keys=%s",
                       list(event.keys()), list(payload.keys()))
        return

    if purpose == "login":
        text = (
            "🔑 <b>Код для входа</b>\n\n"
            f"Код: <code>{code}</code>\n"
            f"Действует: {_ttl_text(ttl_seconds)}\n\n"
            f"Веб-панель:\n{WEB_LOGIN_URL}"
        )
    elif purpose == "password_reset":
        challenge_id = payload.get("challenge_id")
        attempts_remaining = payload.get("attempts_remaining")
        if (not isinstance(challenge_id, str)
                or _RESET_CHALLENGE.fullmatch(challenge_id) is None
                or isinstance(attempts_remaining, bool)
                or not isinstance(attempts_remaining, int)
                or not 1 <= attempts_remaining <= 3):
            logger.warning("otp.requested reset event malformed: payload_keys=%s", list(payload.keys()))
            return

        reset_url = (f"{WEB_PASSWORD_RESET_URL}#challengeId={quote(challenge_id, safe='')}&"
                     f"code={quote(code, safe='')}")
        text = (
            "🔐 <b>Код для сброса пароля</b>\n\n"
            f"Код: <code>{html.escape(code)}</code>\n"
            f"Действует: {_ttl_text(ttl_seconds)}\n"
            f"Осталось попыток: {attempts_remaining}\n\n"
            f'<a href="{html.escape(reset_url, quote=True)}">Открыть форму сброса пароля</a>'
        )
    else:
        logger.warning("otp.requested event has unsupported purpose=%s", purpose)
        return

    try:
        bot_message = await bot.send_message(
            chat_id=telegram_id,
            text=text,
            parse_mode="HTML",
        )
    except Exception as error:
        # The event contains the OTP. Keep it out of exception strings and traces.
        logger.warning("OTP Telegram delivery failed: purpose=%s error_type=%s",
                       purpose, type(error).__name__)
        return

    await tracker.finalize_with_bot_msg(
        telegram_id, bot_message.message_id, purpose=purpose)
    asyncio.create_task(_cleanup_after_expiry(
        telegram_id, ttl_seconds=ttl_seconds, purpose=purpose, bot=bot, tracker=tracker))


def _ttl_text(ttl_seconds: int) -> str:
    minutes = max(1, (ttl_seconds + 59) // 60)
    return f"{minutes} мин."


async def _cleanup_after_expiry(
    telegram_id: int,
    *,
    ttl_seconds: int,
    purpose: str,
    bot: Bot,
    tracker: OtpMessageTracker,
) -> None:
    """Delete both OTP messages once their server-supplied lifetime ends."""
    try:
        await asyncio.sleep(ttl_seconds)
        await cleanup_otp_messages(
            telegram_id, bot=bot, tracker=tracker, reason="expired", purpose=purpose)
    except asyncio.CancelledError:
        raise
    except Exception as error:
        logger.warning("OTP expiry cleanup failed: purpose=%s error_type=%s",
                       purpose, type(error).__name__)
