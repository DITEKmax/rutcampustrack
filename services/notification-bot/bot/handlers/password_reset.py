"""Telegram entry point for requesting an account password reset."""

from __future__ import annotations

import logging

import aiohttp
from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.services.otp_message_tracker import OtpMessageTracker

logger = logging.getLogger(__name__)

password_reset_router = Router()


@password_reset_router.message(Command("reset"))
async def cmd_password_reset(
    message: Message,
    auth_client,
    otp_tracker: OtpMessageTracker,
) -> None:
    """Request a reset code while keeping unknown accounts indistinguishable."""
    telegram_id = message.from_user.id
    await otp_tracker.store_pending_user_msg(
        telegram_id=telegram_id,
        chat_id=message.chat.id,
        user_message_id=message.message_id,
        purpose="password_reset",
    )

    try:
        await auth_client.request_password_reset(telegram_id)
    except aiohttp.ClientResponseError as error:
        if error.status == 429:
            await message.answer("⏳ Слишком много запросов. Подожди немного и попробуй снова.")
            return
        logger.warning(
            "Password-reset request failed: status=%s",
            error.status,
        )
    except Exception as error:
        logger.warning(
            "Password-reset request failed: error_type=%s",
            type(error).__name__,
        )
    else:
        await message.answer(
            "Если этот Telegram привязан к аккаунту, код придёт сюда. "
            "Открой ссылку из сообщения бота, чтобы задать новый пароль."
        )
        return

    await message.answer("⚠️ Сервис временно недоступен. Попробуй ещё раз чуть позже.")
