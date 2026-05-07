import html
import logging

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import CREDENTIALS_LABEL, LINKS_LABEL, keyboard_signature, main_keyboard
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

_LINKS_BLOCK = (
    "🌐 <b>Сайт и PWA</b>\n\n"
    "<b>Сайт</b>\n"
    "• Без VPN: https://ru.ruttrack.site\n"
    "• С VPN: https://ruttrack.site\n\n"
    "<b>PWA / приложение на телефон</b>\n"
    "• https://ru.ruttrack.site/app\n"
    "• https://ruttrack.site/app\n\n"
    "<b>Telegram Mini App</b>\n"
    "• Откройте Mini App из профиля бота или из меню Telegram."
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


@start_router.message(Command("credentials"))
@start_router.message(F.text == CREDENTIALS_LABEL)
async def cmd_credentials(message: Message, academic_client) -> None:
    """Show structured login data for the linked Telegram account."""
    telegram_id = message.from_user.id
    try:
        response = await academic_client.get_user_by_telegram_id(telegram_id)
    except Exception:
        logger.warning("Academic gRPC unavailable for credentials", exc_info=True)
        await message.answer("⚠️ <b>Сервис временно недоступен</b>\n\nПопробуйте ещё раз чуть позже.", parse_mode="HTML")
        return

    if not response.found:
        await message.answer(
            "⚠️ <b>Аккаунт не найден</b>\n\nВаш Telegram пока не привязан к системе.",
            parse_mode="HTML",
        )
        return

    display_name = html.escape(str(response.display_name or ""))
    login = html.escape(str(response.login or ""))
    group_name = html.escape(str(response.group_name or ""))
    initial_password = html.escape(str(response.initial_password or ""))

    lines = [
        "🔐 <b>Данные для входа</b>",
        "",
        f"Имя: {display_name}",
        f"Группа: {group_name or 'не указана'}",
        f"Логин: <code>{login}</code>",
    ]
    if initial_password:
        lines.extend(
            [
                f"Пароль: <code>{initial_password}</code>",
                "",
                "После первого входа смените пароль.",
            ]
        )
    else:
        lines.extend(
            [
                "Пароль: уже изменён.",
                "",
                "Бот не хранит текущий пароль после смены. "
                "Если пароль забыт, обратитесь к старосте или администратору.",
            ]
        )

    await message.answer("\n".join(lines), parse_mode="HTML")


@start_router.message(Command("links"))
@start_router.message(F.text == LINKS_LABEL)
async def cmd_links(message: Message) -> None:
    """Show site and PWA links."""
    await message.answer(f"{_LINKS_BLOCK}\n\n{_INSTALL_BLOCK}", parse_mode="HTML")
