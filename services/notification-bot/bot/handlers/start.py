import logging

from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

logger = logging.getLogger(__name__)

start_router = Router()


@start_router.message(Command("start"))
async def cmd_start(message: Message, academic_client) -> None:
    """Handle /start command — account linking (D-02, D-03)."""
    telegram_id = message.from_user.id
    try:
        response = await academic_client.get_user_by_telegram_id(telegram_id)

        if not response.found:
            # D-03: unknown telegram_id
            await message.answer(
                "Ваш Telegram не привязан к системе. "
                "Обратитесь к старосте вашей группы для привязки аккаунта."
            )
            return

        # D-02: known user
        if response.initial_password:
            # First login — show credentials
            await message.answer(
                f"Добро пожаловать, {response.display_name}!\n\n"
                f"Ваш логин: {response.login}\n"
                f"Ваш пароль: {response.initial_password}\n\n"
                "Используйте эти данные для входа в веб-панель.\n"
                "После входа смените пароль."
            )
        else:
            # Password already changed
            await message.answer(
                f"Добро пожаловать, {response.display_name}!\n\n"
                f"Логин: {response.login}\n"
                f"Группа: {response.group_name}"
            )

    except Exception:
        # D-14: service unavailability
        logger.warning("Academic gRPC unavailable for /start", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.")
