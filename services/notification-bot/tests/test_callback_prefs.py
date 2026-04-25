"""Unit-тесты preferences callback handlers (M09 G4.4, 14 P1-7).

Три handler'а: глобальный toggle, category toggle, main-keyboard open.

NotificationPrefsClient подаётся с fake_redis — реальное поведение Redis
API (get/set/delete/hget/hset/hdel/hgetall).
"""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.prefs import (
    cb_toggle_category,
    cb_toggle_global,
    cmd_open_settings,
    main_keyboard,
)
from bot.services.notification_prefs import NotificationPrefsClient


@pytest.fixture
def prefs_client(fake_redis):
    return NotificationPrefsClient(redis_client=fake_redis)


@pytest.fixture
def prefs_callback_factory():
    """CallbackQuery mock для prefs — отличается от generic callback_query_factory
    тем, что edit_text awaited-проверяется по аргументам с InlineKeyboardMarkup."""

    def _make(data: str, user_id: int = 100) -> MagicMock:
        cb = MagicMock()
        cb.data = data
        cb.from_user = MagicMock()
        cb.from_user.id = user_id
        cb.answer = AsyncMock()

        message = MagicMock()
        message.text = "старое меню"
        message.edit_text = AsyncMock()
        cb.message = message
        return cb

    return _make


@pytest.fixture
def prefs_message_factory():
    def _make(text: str, user_id: int = 100) -> MagicMock:
        msg = MagicMock()
        msg.text = text
        msg.from_user = MagicMock()
        msg.from_user.id = user_id
        msg.answer = AsyncMock()
        return msg

    return _make


# ====================================================== main_keyboard


def test_main_keyboard_has_login_and_settings_buttons():
    kb = main_keyboard()
    labels = [btn.text for row in kb.keyboard for btn in row]
    assert "⚙️ Настройки уведомлений" in labels
    assert "🔑 Получить код для входа" in labels


def test_main_keyboard_ignores_legacy_arg():
    # Параметр notifications_enabled больше не используется; вызов со старым
    # kwarg должен пройти без падения (backward-compat).
    kb = main_keyboard(notifications_enabled=True)
    assert kb is not None


# ====================================================== cmd_open_settings


@pytest.mark.asyncio
async def test_cmd_open_settings_shows_menu_with_all_categories_default_on(prefs_message_factory, prefs_client):
    msg = prefs_message_factory("⚙️ Настройки уведомлений", user_id=100)

    await cmd_open_settings(msg, prefs_client=prefs_client)

    msg.answer.assert_awaited_once()
    text, kwargs = msg.answer.await_args.args[0], msg.answer.await_args.kwargs
    assert "🔔" in text  # по умолчанию globally-enabled
    markup = kwargs["reply_markup"]
    # 1 global + 6 категорий = 7 кнопок (каждая в своей row)
    assert len(markup.inline_keyboard) == 7
    # Каждая категория по умолчанию включена → checkbox ✅
    for row in markup.inline_keyboard[1:]:
        assert row[0].text.startswith("✅")


@pytest.mark.asyncio
async def test_cmd_open_settings_when_globally_disabled_hides_categories(
    prefs_message_factory, prefs_client, fake_redis
):
    telegram_id = 100
    await fake_redis.set(f"bot:notif:{telegram_id}", "off")

    msg = prefs_message_factory("⚙️ Настройки уведомлений", user_id=telegram_id)

    await cmd_open_settings(msg, prefs_client=prefs_client)

    text = msg.answer.await_args.args[0]
    markup = msg.answer.await_args.kwargs["reply_markup"]
    assert "🔕" in text
    # Только global-toggle строка
    assert len(markup.inline_keyboard) == 1


# ====================================================== cb_toggle_global


@pytest.mark.asyncio
async def test_toggle_global_from_on_to_off_sets_redis_key(prefs_callback_factory, prefs_client, fake_redis):
    cb = prefs_callback_factory("prefs:global:toggle", user_id=100)

    await cb_toggle_global(cb, prefs_client=prefs_client)

    value = await fake_redis.get("bot:notif:100")
    assert value == "off"
    cb.answer.assert_awaited_once_with("Уведомления выключены")
    cb.message.edit_text.assert_awaited_once()


@pytest.mark.asyncio
async def test_toggle_global_from_off_to_on_deletes_redis_key(prefs_callback_factory, prefs_client, fake_redis):
    await fake_redis.set("bot:notif:100", "off")
    cb = prefs_callback_factory("prefs:global:toggle", user_id=100)

    await cb_toggle_global(cb, prefs_client=prefs_client)

    value = await fake_redis.get("bot:notif:100")
    assert value is None  # enable → delete key
    cb.answer.assert_awaited_once_with("Уведомления включены")


# ====================================================== cb_toggle_category


@pytest.mark.asyncio
async def test_toggle_category_lessons_from_on_to_off(prefs_callback_factory, prefs_client, fake_redis):
    cb = prefs_callback_factory("prefs:cat:lessons", user_id=100)

    await cb_toggle_category(cb, prefs_client=prefs_client)

    hval = await fake_redis.hget("bot:notif:cat:100", "lessons")
    assert hval == "off"
    cb.answer.assert_awaited_once_with("Пары: выкл")


@pytest.mark.asyncio
async def test_toggle_category_homework_from_off_to_on_removes_hash_field(
    prefs_callback_factory, prefs_client, fake_redis
):
    await fake_redis.hset("bot:notif:cat:100", "homework", "off")
    cb = prefs_callback_factory("prefs:cat:homework", user_id=100)

    await cb_toggle_category(cb, prefs_client=prefs_client)

    hval = await fake_redis.hget("bot:notif:cat:100", "homework")
    assert hval is None
    cb.answer.assert_awaited_once_with("Домашки: вкл")


@pytest.mark.asyncio
async def test_toggle_category_unknown_answers_without_redis_write(prefs_callback_factory, prefs_client, fake_redis):
    cb = prefs_callback_factory("prefs:cat:not_a_real_category", user_id=100)

    await cb_toggle_category(cb, prefs_client=prefs_client)

    cb.answer.assert_awaited_once_with("Неизвестная категория")
    cb.message.edit_text.assert_not_called()
    # Redis не должен получить hset для несуществующей категории
    all_keys = await fake_redis.keys("bot:notif:cat:100")
    assert all_keys == []


@pytest.mark.asyncio
async def test_toggle_category_covers_all_known_categories(prefs_callback_factory, prefs_client, fake_redis):
    """Smoke: каждая категория из CATEGORIES может быть переключена без ошибок."""
    from bot.services.notification_prefs import CATEGORIES

    for category in CATEGORIES:
        cb = prefs_callback_factory(f"prefs:cat:{category}", user_id=200)
        await cb_toggle_category(cb, prefs_client=prefs_client)
        cb.answer.assert_awaited_once()
