import html
import logging

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import CREDENTIALS_LABEL, LINKS_LABEL, keyboard_signature, main_keyboard
from bot.services.bot_menu import resolve_menu_scope
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


def private_sender(message: Message) -> int | None:
    """Check the Telegram transport scope before any account lookup."""
    sender = getattr(getattr(message, "from_user", None), "id", None)
    chat = getattr(message, "chat", None)
    chat_id = getattr(chat, "id", None)
    if (
        getattr(chat, "type", None) != "private"
        or type(sender) is not int
        or type(chat_id) is not int
        or sender <= 0
        or sender != chat_id
    ):
        return None
    return sender


async def _private_only(message: Message) -> bool:
    if private_sender(message) is not None:
        return True
    await message.answer("Открой личный чат с ботом, чтобы посмотреть свой аккаунт.")
    return False


def _profile_tuple(profile) -> tuple[int, int, int]:
    values = (profile.user_id, profile.telegram_binding_version, profile.roles_version)
    if profile.found is not True or any(type(value) is not int or value <= 0 for value in values):
        raise RuntimeError("Own profile identity unavailable")
    return values


async def _require_profile_scope(client, sender: int, profile):
    expected = _profile_tuple(profile)
    scope = await resolve_menu_scope(client, sender)
    if (
        scope is None
        or (scope.user_id, scope.telegram_binding_version, scope.roles_version) != expected
        or not any(grant.role in {"TEACHER", "ADMIN"} or grant.group_id > 0 for grant in scope.grants)
    ):
        raise RuntimeError("Own profile authority unavailable or changed")
    return scope


async def _refresh_own_profile(client, sender: int, previous):
    """A denied card cannot authorize disclosure of the earlier redacted profile."""
    fresh = await client.own_profile(sender)
    if _profile_tuple(fresh) != _profile_tuple(previous):
        raise RuntimeError("Own profile binding or grants changed")
    scope = await _require_profile_scope(client, sender, fresh)
    return fresh, scope


@start_router.message(Command("start"))
async def cmd_start(
    message: Message, academic_client, prefs_client, keyboard_sync=None, credential_cards=None, bot_menu_client=None
) -> None:
    """Handle /start command — account linking (D-02, D-03)."""
    if not await _private_only(message):
        return
    telegram_id = private_sender(message)
    keyboard_pending = keyboard_sync is None
    if keyboard_sync is not None:
        # Keep failures discoverable by the bounded startup recovery, including
        # first-time users and users whose old role signature is still current.
        await keyboard_sync.register_user(telegram_id)
        keyboard_pending = await keyboard_sync.invalidate_user_keyboard(telegram_id)
    keyboard = main_keyboard()
    try:
        if bot_menu_client is None:
            raise RuntimeError("Own Auth profile capability unavailable")
        response = await bot_menu_client.own_profile(telegram_id)
        if not response.found:
            # D-03: unknown telegram_id
            await message.answer(
                "⚠️ <b>Аккаунт не найден</b>\n\n"
                "Ваш Telegram пока не привязан к системе.\n\n"
                "Обратитесь к старосте вашей группы, чтобы он привязал ваш аккаунт.",
                reply_markup=keyboard if keyboard_pending else None,
                parse_mode="HTML",
            )
            return

        # D-02: a found profile needs positive current authority before any own data/card.
        scope = await _require_profile_scope(bot_menu_client, telegram_id, response)
        options = scope.keyboard_options
        keyboard = main_keyboard(**options)
        display_name = html.escape(str(response.display_name or ""))
        if credential_cards is not None and await credential_cards.deliver(
            message,
            response,
            intro=f"👋 <b>Добро пожаловать, {display_name}</b>\n\n",
            footer=f"\n\n{_WHERE_TO_MARK_BLOCK}\n\n{_INSTALL_BLOCK}",
            reply_markup=keyboard,
        ):
            await mark_keyboard_synced(keyboard_sync, telegram_id, keyboard_signature(**options))
            return
        response, scope = await _refresh_own_profile(bot_menu_client, telegram_id, response)
        keyboard = main_keyboard(**scope.keyboard_options)
        display_name = html.escape(str(response.display_name or ""))
        login = html.escape(str(response.login or ""))
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
        await mark_keyboard_synced(keyboard_sync, telegram_id, keyboard_signature(**scope.keyboard_options))

    except Exception:
        # D-14: service unavailability
        logger.warning("Own Auth profile unavailable for /start")
        await message.answer(
            "⚠️ <b>Сервис временно недоступен</b>\n\nПопробуйте ещё раз чуть позже.",
            # Redis may still hold the signature of the previous role keyboard.
            # Preserve that keyboard until its pending state is durably recorded.
            reply_markup=main_keyboard() if keyboard_pending else None,
            parse_mode="HTML",
        )


@start_router.message(Command("credentials"))
@start_router.message(F.text == CREDENTIALS_LABEL)
async def cmd_credentials(message: Message, academic_client, credential_cards=None, bot_menu_client=None) -> None:
    """Show structured login data for the linked Telegram account."""
    if not await _private_only(message):
        return
    telegram_id = private_sender(message)
    try:
        if bot_menu_client is None:
            raise RuntimeError("Own Auth profile capability unavailable")
        response = await bot_menu_client.own_profile(telegram_id)
    except Exception:
        logger.warning("Own Auth profile unavailable for credentials")
        await message.answer("⚠️ <b>Сервис временно недоступен</b>\n\nПопробуйте ещё раз чуть позже.", parse_mode="HTML")
        return

    if not response.found:
        await message.answer(
            "⚠️ <b>Аккаунт не найден</b>\n\nВаш Telegram пока не привязан к системе.",
            parse_mode="HTML",
        )
        return

    try:
        await _require_profile_scope(bot_menu_client, telegram_id, response)
        if credential_cards is not None and await credential_cards.deliver(message, response):
            return
        response, _ = await _refresh_own_profile(bot_menu_client, telegram_id, response)
    except Exception:
        logger.warning("Own profile fallback authority unavailable")
        await message.answer("Сервис временно недоступен. Попробуй ещё раз чуть позже.")
        return

    display_name = html.escape(str(response.display_name or ""))
    login = html.escape(str(response.login or ""))
    group_name = html.escape(str(response.group_name or ""))
    lines = [
        "🔐 <b>Данные для входа</b>",
        "",
        f"Имя: {display_name}",
        f"Группа: {group_name or 'не указана'}",
        f"Логин: <code>{login}</code>",
    ]
    if response.password_changed:
        lines.extend(
            [
                "Пароль: уже изменён.",
                "",
                "Бот не хранит текущий пароль после смены. "
                "Если пароль забыт, обратитесь к старосте или администратору.",
            ]
        )
    else:
        lines.append("Начальные данные входа сейчас недоступны. Попробуй ещё раз позже.")

    await message.answer("\n".join(lines), parse_mode="HTML")


@start_router.message(Command("links"))
@start_router.message(F.text == LINKS_LABEL)
async def cmd_links(message: Message) -> None:
    """Show site and PWA links."""
    await message.answer(f"{_LINKS_BLOCK}\n\n{_INSTALL_BLOCK}", parse_mode="HTML")
