import logging

import aiohttp
from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import LOGIN_LABEL

logger = logging.getLogger(__name__)

login_router = Router()

WEB_LOGIN_URL = "https://ruttrack.site/login"


@login_router.message(Command("login"))
@login_router.message(F.text == LOGIN_LABEL)
async def cmd_login(message: Message, auth_client) -> None:
    """Handle /login command — request OTP and direct user to enter it on the web (D-05, D-06)."""
    telegram_id = message.from_user.id

    try:
        code = await auth_client.request_otp(telegram_id)
        await message.answer(
            f"Ваш код для входа: <code>{code}</code>\n\n"
            f"Откройте веб-панель и введите этот код:\n{WEB_LOGIN_URL}\n\n"
            "Код действует 5 минут.",
            parse_mode="HTML",
        )

    except aiohttp.ClientResponseError as e:
        if e.status == 429:
            # D-13: rate limit
            await message.answer("Слишком много попыток. Подождите.")
        elif e.status == 401:
            await message.answer("Ваш аккаунт не найден. Обратитесь к старосте.")
        else:
            # D-14: service unavailable
            logger.warning("OTP request failed: %s", e)
            await message.answer("Сервис временно недоступен. Попробуйте позже.")
    except Exception:
        logger.warning("OTP request failed unexpectedly", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.")
