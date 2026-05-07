import html
import logging

from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import keyboard_signature, main_keyboard
from bot.services.keyboard_sync import mark_keyboard_synced

logger = logging.getLogger(__name__)

start_router = Router()

_WHERE_TO_MARK_BLOCK = (
    "🌐 <b>Где отмечаться</b>\n"
    "• Без VPN: https://ru.ruttrack.site\n"
    "• С VPN: https://ruttrack.site\n"
    "• Telegram Mini App\n"
    "• Приложение на телефон: <code>ru.ruttrack.site/app</code>"
)

_INSTALL_BLOCK = (
    "📱 <b>Установка приложения</b>\n\n"
    "<b>Android</b>\n"
    "1. Откройте ссылку в Chrome.\n"
    "2. Нажмите ⋮ в правом верхнем углу.\n"
    "3. Выберите «Установить приложение» или «Добавить на главный экран».\n\n"
    "<b>iPhone / iPad</b>\n"
    "1. Откройте ссылку в Safari.\n"
    "2. Нажмите «Поделиться».\n"
    "3. Выберите «На экран Домой».\n"
    "4. Нажмите «Добавить»."
)


@start_router.message(Command("start"))
async def cmd_start(message: Message, academic_client, prefs_client, keyboard_sync=None) -> None:
    """Handle /start command — account linking (D-02, D-03)."""
    telegram_id = message.from_user.id
    notifications_enabled = await prefs_client.is_enabled(telegram_id)
    keyboard = main_keyboard(notifications_enabled=notifications_enabled)
    await mark_keyboard_synced(keyboard_sync, telegram_id, keyboard_signature())
    try:
        response = await academic_client.get_user_by_telegram_id(telegram_id)

        if not response.found:
            # D-03: unknown telegram_id
            await message.answer(
                "⚠️ <b>Аккаунт не найден</b>\n\n"
                "Ваш Telegram пока не привязан к системе.\n\n"
                "Обратитесь к старосте вашей группы, чтобы он привязал ваш аккаунт.",
                reply_markup=keyboard,
                parse_mode="HTML",
            )
            return

        # D-02: known user
        display_name = html.escape(str(response.display_name or ""))
        login = html.escape(str(response.login or ""))
        if response.initial_password:
            # First login — show credentials
            initial_password = html.escape(str(response.initial_password or ""))
            await message.answer(
                f"👋 <b>Добро пожаловать, {display_name}</b>\n\n"
                "🔐 <b>Данные для входа</b>\n"
                f"Логин: <code>{login}</code>\n"
                f"Пароль: <code>{initial_password}</code>\n\n"
                "После первого входа смените пароль.\n\n"
                f"{_WHERE_TO_MARK_BLOCK}\n\n"
                f"{_INSTALL_BLOCK}",
                reply_markup=keyboard,
                parse_mode="HTML",
            )
        else:
            # Password already changed
            group_name = html.escape(str(response.group_name or ""))
            await message.answer(
                f"👋 <b>Добро пожаловать, {display_name}</b>\n\n"
                "👤 <b>Профиль</b>\n"
                f"Логин: <code>{login}</code>\n"
                f"Группа: {group_name}\n\n"
                f"{_WHERE_TO_MARK_BLOCK}\n\n"
                f"{_INSTALL_BLOCK}",
                reply_markup=keyboard,
                parse_mode="HTML",
            )

    except Exception:
        # D-14: service unavailability
        logger.warning("Academic gRPC unavailable for /start", exc_info=True)
        await message.answer(
            "⚠️ <b>Сервис временно недоступен</b>\n\nПопробуйте ещё раз чуть позже.",
            reply_markup=keyboard,
            parse_mode="HTML",
        )
