import logging

import aiohttp
from aiogram import Bot, F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import LOGIN_LABEL
from bot.services.otp_message_tracker import OtpMessageTracker

logger = logging.getLogger(__name__)

login_router = Router()


@login_router.message(Command("login"))
@login_router.message(F.text == LOGIN_LABEL)
async def cmd_login(
    message: Message,
    auth_client,
    bot: Bot,
    otp_tracker: OtpMessageTracker,
) -> None:
    """Handle /login — попросить Auth Service сгенерировать OTP.

    M09 G2 (08 P0-2): auth возвращает 204 No Content, код уходит
    через RabbitMQ event otp.requested → handler (bot.notifications.
    otp_requested). Здесь — только триггер и сохранение user_message_id
    для последующего cleanup.
    """
    telegram_id = message.from_user.id

    try:
        # M09 G2: сохраняем user_message_id в pending-ключ ДО вызова auth,
        # чтобы event handler мог его подхватить и собрать полный tracker.
        await otp_tracker.store_pending_user_msg(
            telegram_id=telegram_id,
            chat_id=message.chat.id,
            user_message_id=message.message_id,
        )
        await auth_client.request_otp(telegram_id)
        # Ответ пользователю отправит handle_otp_requested, когда придёт
        # событие. Здесь ничего отправлять НЕ надо — избегаем spoiler'а "сейчас
        # пришлю код", если Rabbit ляжет в этот момент (bot увидит ошибку
        # таймаута в handle_otp_requested).
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
