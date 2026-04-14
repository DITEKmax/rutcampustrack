import logging

from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import main_keyboard

logger = logging.getLogger(__name__)

start_router = Router()


@start_router.message(Command("start"))
async def cmd_start(message: Message, academic_client, prefs_client) -> None:
    """Handle /start command — account linking (D-02, D-03)."""
    telegram_id = message.from_user.id
    notifications_enabled = await prefs_client.is_enabled(telegram_id)
    keyboard = main_keyboard(notifications_enabled=notifications_enabled)
    try:
        response = await academic_client.get_user_by_telegram_id(telegram_id)

        if not response.found:
            # D-03: unknown telegram_id
            await message.answer(
                "Ваш Telegram не привязан к системе. Обратитесь к старосте вашей группы для привязки аккаунта.",
                reply_markup=keyboard,
            )
            return

        # D-02: known user
        if response.initial_password:
            # First login — show credentials
            await message.answer(
                f"Добро пожаловать, {response.display_name}!\n\n"
                f"Ваш логин: <code>{response.login}</code>\n"
                f"Ваш пароль: <code>{response.initial_password}</code>\n\n"
                "Используйте эти данные для входа в веб-панель.\n"
                "После входа смените пароль.",
                reply_markup=keyboard,
                parse_mode="HTML",
            )
        else:
            # Password already changed
            await message.answer(
                f"Добро пожаловать, {response.display_name}!\n\n"
                f"Логин: <code>{response.login}</code>\n"
                f"Группа: {response.group_name}",
                reply_markup=keyboard,
                parse_mode="HTML",
            )

    except Exception:
        # D-14: service unavailability
        logger.warning("Academic gRPC unavailable for /start", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.", reply_markup=keyboard)
