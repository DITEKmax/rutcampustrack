import asyncio
import logging

import aiohttp
from aiogram import Bot, F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import LOGIN_LABEL
from bot.notifications.otp_verified import cleanup_otp_messages
from bot.services.otp_message_tracker import OtpMessageTracker

logger = logging.getLogger(__name__)

login_router = Router()

WEB_LOGIN_URL = "https://ruttrack.site/login"

# OTP lifetime in seconds — mirrors OtpProperties.ttlSeconds in auth-service.
# Messages are scheduled for deletion when this window elapses without a successful verify.
OTP_TTL_SECONDS = 300


@login_router.message(Command("login"))
@login_router.message(F.text == LOGIN_LABEL)
async def cmd_login(
    message: Message,
    auth_client,
    bot: Bot,
    otp_tracker: OtpMessageTracker,
) -> None:
    """Handle /login — request OTP, send code message, and arrange cleanup.

    On success: persist (chat_id, user_message_id, bot_message_id) in Redis and
    schedule a deferred cleanup task that fires after OTP_TTL_SECONDS. The same
    messages are also removed earlier if auth-service publishes otp.verified.
    """
    telegram_id = message.from_user.id

    try:
        code = await auth_client.request_otp(telegram_id)
        bot_message = await message.answer(
            f"Ваш код для входа: <code>{code}</code>\n\n"
            f"Откройте веб-панель и введите этот код:\n{WEB_LOGIN_URL}\n\n"
            "Код действует 5 минут.",
            parse_mode="HTML",
        )

        await otp_tracker.store(
            telegram_id=telegram_id,
            chat_id=message.chat.id,
            user_message_id=message.message_id,
            bot_message_id=bot_message.message_id,
        )
        asyncio.create_task(_cleanup_after_expiry(telegram_id, bot=bot, tracker=otp_tracker))

    except aiohttp.ClientResponseError as e:
        if e.status == 429:
            await message.answer("Слишком много попыток. Подождите.")
        elif e.status == 401:
            await message.answer("Ваш аккаунт не найден. Обратитесь к старосте.")
        else:
            logger.warning("OTP request failed: %s", e)
            await message.answer("Сервис временно недоступен. Попробуйте позже.")
    except Exception:
        logger.warning("OTP request failed unexpectedly", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.")


async def _cleanup_after_expiry(
    telegram_id: int,
    *,
    bot: Bot,
    tracker: OtpMessageTracker,
) -> None:
    """Sleep for the OTP TTL then clean up messages if still present.

    If the user verifies successfully, otp.verified consumer will pop() the
    tracker first and this call becomes a no-op — pop() is atomic.
    """
    try:
        await asyncio.sleep(OTP_TTL_SECONDS)
        await cleanup_otp_messages(telegram_id, bot=bot, tracker=tracker, reason="expired")
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("OTP expiry cleanup failed for telegram_id=%d", telegram_id)
