"""Handler for per-user notification preferences.

Главная точка входа — reply-кнопка «⚙️ Настройки уведомлений». Вместо
одной клавиши on/off показываем inline-меню с toggle'ами по категориям
(пары, ДЗ, тикеты, расписание, группа, напоминания) и отдельным
глобальным выключателем. Категории синхронизированы с PWA (см.
:mod:`bot.services.notification_prefs`).
"""

from __future__ import annotations

import logging
from datetime import timedelta
from hashlib import sha256

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
HOMEWORK_WEEK_LABEL = "📚 ДЗ на неделю"
CREDENTIALS_LABEL = "🔐 Данные для входа"
LINKS_LABEL = "🌐 Сайт и PWA"

_MAIN_KEYBOARD_LAYOUT: tuple[tuple[str, ...], ...] = (
    (HOMEWORK_WEEK_LABEL,),
    (CREDENTIALS_LABEL, LINKS_LABEL),
    (SETTINGS_LABEL,),
    (LOGIN_LABEL,),
)

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
_MUTE_DAY_CB = "prefs:mute:day"
_MUTE_WEEK_CB = "prefs:mute:week"
_MUTE_CLEAR_CB = "prefs:mute:clear"


def main_keyboard(notifications_enabled: bool | None = None) -> ReplyKeyboardMarkup:
    """Persistent reply keyboard shown to user after /start.

    Сохранён параметр для обратной совместимости со старыми вызовами —
    значение больше не используется, клавиатура статична.
    """
    del notifications_enabled  # unused — kept for backward-compat keyword args
    return ReplyKeyboardMarkup(
        keyboard=[[KeyboardButton(text=label) for label in row] for row in _MAIN_KEYBOARD_LAYOUT],
        resize_keyboard=True,
        is_persistent=True,
    )


def keyboard_signature() -> str:
    """Stable signature for backend-driven reply keyboard updates."""
    raw = "\n".join("|".join(row) for row in _MAIN_KEYBOARD_LAYOUT)
    return sha256(raw.encode("utf-8")).hexdigest()[:16]


def _checkbox(enabled: bool) -> str:
    return "✅" if enabled else "⬜"


async def _build_menu(prefs_client: NotificationPrefsClient, telegram_id: int) -> tuple[str, InlineKeyboardMarkup]:
    global_on = await prefs_client.is_global_enabled(telegram_id)
    categories = await prefs_client.get_categories(telegram_id)
    muted_until = await prefs_client.get_muted_until(telegram_id)

    status = "🔔 включены" if global_on else "🔕 глобально выключены"
    pause = f"до {_format_mute_until(muted_until)}" if muted_until is not None else "нет"
    text = (
        "🔔 Настройки уведомлений\n\n"
        f"Статус: {status}\n"
        f"Пауза: {pause}\n\n"
        "Выберите категории, которые хотите получать в Telegram.\n"
        "Настройки браузера и PWA меняются отдельно в приложении."
    )

    rows: list[list[InlineKeyboardButton]] = [
        [
            InlineKeyboardButton(
                text=("🔕 Отключить все" if global_on else "🔔 Включить все"),
                callback_data=_GLOBAL_CB,
            )
        ]
    ]
    rows.append(
        [
            InlineKeyboardButton(text="Пауза на день", callback_data=_MUTE_DAY_CB),
            InlineKeyboardButton(text="Пауза на неделю", callback_data=_MUTE_WEEK_CB),
        ]
    )
    if muted_until is not None:
        rows.append([InlineKeyboardButton(text="Снять паузу", callback_data=_MUTE_CLEAR_CB)])
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


def _format_mute_until(value) -> str:
    return value.strftime("%d.%m %H:%M UTC")


@prefs_router.message(F.text == SETTINGS_LABEL)
async def cmd_open_settings(message: Message, prefs_client: NotificationPrefsClient) -> None:
    text, markup = await _build_menu(prefs_client, message.from_user.id)
    await message.answer(text, reply_markup=markup)


@prefs_router.callback_query(F.data == _GLOBAL_CB)
async def cb_toggle_global(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
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


@prefs_router.callback_query(F.data == _MUTE_DAY_CB)
async def cb_mute_day(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    await _set_mute(callback, prefs_client, timedelta(days=1), "Пауза включена на день")


@prefs_router.callback_query(F.data == _MUTE_WEEK_CB)
async def cb_mute_week(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    await _set_mute(callback, prefs_client, timedelta(days=7), "Пауза включена на неделю")


@prefs_router.callback_query(F.data == _MUTE_CLEAR_CB)
async def cb_mute_clear(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    telegram_id = callback.from_user.id
    await prefs_client.clear_mute(telegram_id)
    text, markup = await _build_menu(prefs_client, telegram_id)
    try:
        await callback.message.edit_text(text, reply_markup=markup)
    except Exception:
        logger.debug("edit_text failed for prefs mute clear", exc_info=True)
    await callback.answer("Пауза снята")


async def _set_mute(
    callback: CallbackQuery,
    prefs_client: NotificationPrefsClient,
    duration: timedelta,
    verdict: str,
) -> None:
    telegram_id = callback.from_user.id
    await prefs_client.mute_for(telegram_id, duration)
    text, markup = await _build_menu(prefs_client, telegram_id)
    try:
        await callback.message.edit_text(text, reply_markup=markup)
    except Exception:
        logger.debug("edit_text failed for prefs mute", exc_info=True)
    await callback.answer(verdict)


@prefs_router.callback_query(F.data.startswith(_CAT_CB_PREFIX))
async def cb_toggle_category(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
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
