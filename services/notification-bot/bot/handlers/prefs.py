"""Handler for per-user notification preferences.

Главная точка входа — reply-кнопка «⚙️ Настройки уведомлений». Вместо
одной клавиши on/off показываем inline-меню с toggle'ами по категориям
(пары, ДЗ, тикеты, расписание, группа, напоминания) и отдельным
глобальным выключателем. Категории синхронизированы с PWA (см.
:mod:`bot.services.notification_prefs`).
"""

from __future__ import annotations

import logging

from aiogram import F, Router
from aiogram.types import (
    CallbackQuery,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    KeyboardButton,
    Message,
    ReplyKeyboardMarkup,
)

from bot.services.notification_prefs import CATEGORIES, NotificationPrefsClient

logger = logging.getLogger(__name__)

prefs_router = Router()

SETTINGS_LABEL = "⚙️ Настройки уведомлений"
LOGIN_LABEL = "🔑 Получить код для входа"

_CATEGORY_LABELS: dict[str, str] = {
    "lessons": "Пары",
    "homework": "Домашки",
    "tickets": "Тикеты (у.п., опоздания)",
    "schedule": "Изменения расписания",
    "group": "Группа",
    "reminders": "Напоминания на паре",
}

_GLOBAL_CB = "prefs:global:toggle"
_CAT_CB_PREFIX = "prefs:cat:"


def main_keyboard(notifications_enabled: bool | None = None) -> ReplyKeyboardMarkup:
    """Persistent reply keyboard shown to user after /start.

    Сохранён параметр для обратной совместимости со старыми вызовами —
    значение больше не используется, клавиатура статична.
    """
    del notifications_enabled  # unused — kept for backward-compat keyword args
    return ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text=SETTINGS_LABEL)],
            [KeyboardButton(text=LOGIN_LABEL)],
        ],
        resize_keyboard=True,
        is_persistent=True,
    )


def _checkbox(enabled: bool) -> str:
    return "✅" if enabled else "⬜"


async def _build_menu(
    prefs_client: NotificationPrefsClient, telegram_id: int
) -> tuple[str, InlineKeyboardMarkup]:
    global_on = await prefs_client.is_global_enabled(telegram_id)
    categories = await prefs_client.get_categories(telegram_id)

    header = (
        "🔔 Уведомления включены" if global_on else "🔕 Уведомления глобально выключены"
    )
    body = (
        "Выберите, какие уведомления хотите получать в этом боте. "
        "Настройки для браузера и PWA — отдельные, в приложении."
    )
    text = f"{header}\n\n{body}"

    rows: list[list[InlineKeyboardButton]] = [
        [
            InlineKeyboardButton(
                text=("🔕 Отключить все" if global_on else "🔔 Включить все"),
                callback_data=_GLOBAL_CB,
            )
        ]
    ]
    if global_on:
        for category in CATEGORIES:
            enabled = categories.get(category, True)
            label = _CATEGORY_LABELS.get(category, category)
            rows.append(
                [
                    InlineKeyboardButton(
                        text=f"{_checkbox(enabled)} {label}",
                        callback_data=f"{_CAT_CB_PREFIX}{category}",
                    )
                ]
            )
    return text, InlineKeyboardMarkup(inline_keyboard=rows)


@prefs_router.message(F.text == SETTINGS_LABEL)
async def cmd_open_settings(message: Message, prefs_client: NotificationPrefsClient) -> None:
    text, markup = await _build_menu(prefs_client, message.from_user.id)
    await message.answer(text, reply_markup=markup)


@prefs_router.callback_query(F.data == _GLOBAL_CB)
async def cb_toggle_global(
    callback: CallbackQuery, prefs_client: NotificationPrefsClient
) -> None:
    telegram_id = callback.from_user.id
    currently_on = await prefs_client.is_global_enabled(telegram_id)
    if currently_on:
        await prefs_client.disable(telegram_id)
        verdict = "Уведомления выключены"
    else:
        await prefs_client.enable(telegram_id)
        verdict = "Уведомления включены"

    text, markup = await _build_menu(prefs_client, telegram_id)
    try:
        await callback.message.edit_text(text, reply_markup=markup)
    except Exception:
        logger.debug("edit_text failed for prefs menu", exc_info=True)
    await callback.answer(verdict)


@prefs_router.callback_query(F.data.startswith(_CAT_CB_PREFIX))
async def cb_toggle_category(
    callback: CallbackQuery, prefs_client: NotificationPrefsClient
) -> None:
    category = (callback.data or "").removeprefix(_CAT_CB_PREFIX)
    if category not in CATEGORIES:
        await callback.answer("Неизвестная категория")
        return

    telegram_id = callback.from_user.id
    snapshot = await prefs_client.get_categories(telegram_id)
    currently_on = snapshot.get(category, True)
    await prefs_client.set_category(telegram_id, category, enabled=not currently_on)

    text, markup = await _build_menu(prefs_client, telegram_id)
    try:
        await callback.message.edit_text(text, reply_markup=markup)
    except Exception:
        logger.debug("edit_text failed for prefs menu (category)", exc_info=True)
    label = _CATEGORY_LABELS.get(category, category)
    await callback.answer(f"{label}: {'вкл' if not currently_on else 'выкл'}")
