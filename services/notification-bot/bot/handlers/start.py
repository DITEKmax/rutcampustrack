import logging

from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import main_keyboard

logger = logging.getLogger(__name__)

start_router = Router()

_WHERE_TO_MARK_BLOCK = (
    "🌐 <b>Где отмечаться на парах</b>\n"
    "• Сайт без VPN: https://ru.ruttrack.site\n"
    "• Сайт с VPN: https://ruttrack.site\n"
    "• Mini App в Telegram\n"
    "• Приложение (рекомендуется): <code>ru.ruttrack.site/app</code> — только на телефон"
)

_INSTALL_BLOCK = (
    "📱 <b>Установка приложения</b>\n\n"
    "🤖 <b>Android</b> — откройте ссылку <b>в Chrome</b>:\n"
    "1. Нажмите ⋮ (три точки) в правом верхнем углу.\n"
    "2. Выберите «Установить приложение» или «Добавить на главный экран».\n"
    "3. Нажмите «Установить».\n"
    "Иконка появится на рабочем столе.\n\n"
    "🍎 <b>iPhone / iPad</b> — откройте ссылку <b>в Safari</b> "
    "(не в Chrome или Яндекс):\n"
    "1. Нажмите кнопку «Поделиться» внизу экрана (квадрат со стрелкой вверх).\n"
    "2. Прокрутите и выберите «На экран „Домой“».\n"
    "3. Нажмите «Добавить» в правом верхнем углу.\n"
    "Иконка появится на главном экране."
)


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
                "Используйте эти данные для входа. После входа смените пароль.\n\n"
                f"{_WHERE_TO_MARK_BLOCK}\n\n"
                f"{_INSTALL_BLOCK}",
                reply_markup=keyboard,
                parse_mode="HTML",
            )
        else:
            # Password already changed
            await message.answer(
                f"Добро пожаловать, {response.display_name}!\n\n"
                f"Логин: <code>{response.login}</code>\n"
                f"Группа: {response.group_name}\n\n"
                f"{_WHERE_TO_MARK_BLOCK}\n\n"
                f"{_INSTALL_BLOCK}",
                reply_markup=keyboard,
                parse_mode="HTML",
            )

    except Exception:
        # D-14: service unavailability
        logger.warning("Academic gRPC unavailable for /start", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.", reply_markup=keyboard)
