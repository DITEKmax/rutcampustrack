"""Notification on/off toggle handler — controls bot-side notifications only."""

import logging

from aiogram import F, Router
from aiogram.types import KeyboardButton, Message, ReplyKeyboardMarkup

logger = logging.getLogger(__name__)

prefs_router = Router()

NOTIF_ON_LABEL = "🔔 Уведомления: вкл"
NOTIF_OFF_LABEL = "🔕 Уведомления: выкл"
LOGIN_LABEL = "🔑 Получить код для входа"


def main_keyboard(notifications_enabled: bool) -> ReplyKeyboardMarkup:
    """Persistent reply keyboard shown to user after /start."""
    notif_btn = KeyboardButton(text=NOTIF_OFF_LABEL if notifications_enabled else NOTIF_ON_LABEL)
    login_btn = KeyboardButton(text=LOGIN_LABEL)
    return ReplyKeyboardMarkup(
        keyboard=[[notif_btn], [login_btn]],
        resize_keyboard=True,
        is_persistent=True,
    )


@prefs_router.message(F.text == NOTIF_OFF_LABEL)
async def cmd_notifications_off(message: Message, prefs_client) -> None:
    telegram_id = message.from_user.id
    await prefs_client.disable(telegram_id)
    await message.answer(
        "Уведомления в этом боте выключены. Уведомления в браузере и PWA продолжат приходить.",
        reply_markup=main_keyboard(notifications_enabled=False),
    )


@prefs_router.message(F.text == NOTIF_ON_LABEL)
async def cmd_notifications_on(message: Message, prefs_client) -> None:
    telegram_id = message.from_user.id
    await prefs_client.enable(telegram_id)
    await message.answer(
        "Уведомления в этом боте включены.",
        reply_markup=main_keyboard(notifications_enabled=True),
    )
