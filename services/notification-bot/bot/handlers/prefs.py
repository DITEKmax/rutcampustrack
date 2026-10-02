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

from bot.services.notification_prefs import CATEGORIES, NotificationPrefsClient, NotificationPreferencesUnavailable, NotificationBindingMismatch

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


async def _build_menu(prefs_client: NotificationPrefsClient, telegram_id: int, snapshot=None) -> tuple[str, InlineKeyboardMarkup]:
    snapshot = snapshot or await prefs_client.get_snapshot(telegram_id)
    global_on = snapshot.global_enabled
    categories = snapshot.categories
    muted_until = snapshot.muted_until

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
    try:
        text, markup = await _build_menu(prefs_client, message.from_user.id)
    except (NotificationPreferencesUnavailable, NotificationBindingMismatch):
        await message.answer("⚠️ Настройки временно недоступны. Попробуй ещё раз позже.")
        return
    await message.answer(text, reply_markup=markup)


async def _apply(callback: CallbackQuery, prefs_client: NotificationPrefsClient, operation: str, **fields) -> None:
    try:
        snapshot = await prefs_client.command(callback.from_user.id, callback.id, operation, **fields)
    except (NotificationPreferencesUnavailable, NotificationBindingMismatch):
        await callback.answer("Настройки временно недоступны. Попробуй ещё раз позже.", show_alert=True)
        return
    text, markup = await _build_menu(prefs_client, callback.from_user.id, snapshot)
    try:
        await callback.message.edit_text(text, reply_markup=markup)
    except Exception:
        logger.debug("edit_text failed for prefs menu", exc_info=True)
    if operation == "TOGGLE_GLOBAL":
        verdict = "Уведомления включены" if snapshot.global_enabled else "Уведомления выключены"
    elif operation == "TOGGLE_CATEGORY":
        category = fields["category"]
        verdict = f"{_CATEGORY_LABELS[category]}: {'вкл' if snapshot.categories[category] else 'выкл'}"
    elif operation == "CLEAR_MUTE":
        verdict = "Пауза снята"
    else:
        verdict = "Пауза включена на день" if fields["durationSeconds"] == 86400 else "Пауза включена на неделю"
    await callback.answer(verdict)


@prefs_router.callback_query(F.data == _GLOBAL_CB)
async def cb_toggle_global(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    await _apply(callback, prefs_client, "TOGGLE_GLOBAL")


@prefs_router.callback_query(F.data == _MUTE_DAY_CB)
async def cb_mute_day(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    await _apply(callback, prefs_client, "MUTE_FOR", durationSeconds=86400)


@prefs_router.callback_query(F.data == _MUTE_WEEK_CB)
async def cb_mute_week(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    await _apply(callback, prefs_client, "MUTE_FOR", durationSeconds=604800)


@prefs_router.callback_query(F.data == _MUTE_CLEAR_CB)
async def cb_mute_clear(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    await _apply(callback, prefs_client, "CLEAR_MUTE")


@prefs_router.callback_query(F.data.startswith(_CAT_CB_PREFIX))
async def cb_toggle_category(callback: CallbackQuery, prefs_client: NotificationPrefsClient) -> None:
    category = (callback.data or "").removeprefix(_CAT_CB_PREFIX)
    if category not in CATEGORIES:
        await callback.answer("Неизвестная категория")
        return
    await _apply(callback, prefs_client, "TOGGLE_CATEGORY", category=category)
