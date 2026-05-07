from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.prefs import HOMEWORK_WEEK_LABEL, keyboard_signature, main_keyboard
from bot.services.keyboard_sync import KeyboardSyncClient, mark_keyboard_synced, sync_registered_keyboards


@pytest.mark.asyncio
async def test_keyboard_registry_roundtrip(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)

    await client.register_user(222)
    await client.register_user(111)
    await client.mark_user_synced(111, "v1")

    assert await client.get_registered_user_ids() == [111, 222]
    assert await client.get_user_keyboard_version(111) == "v1"
    assert await client.get_user_keyboard_version(222) is None


@pytest.mark.asyncio
async def test_mark_keyboard_synced_noops_without_client():
    await mark_keyboard_synced(None, 111, "v1")


@pytest.mark.asyncio
async def test_sync_registered_keyboards_sends_only_stale_users(fake_redis):
    client = KeyboardSyncClient(redis_client=fake_redis)
    await client.register_user(111)
    await client.register_user(222)
    await client.mark_user_synced(111, "current")

    bot = MagicMock()
    bot.send_message = AsyncMock()

    await sync_registered_keyboards(
        bot=bot,
        keyboard_sync=client,
        keyboard=main_keyboard(),
        keyboard_version="current",
        delay_seconds=0,
    )

    bot.send_message.assert_awaited_once()
    assert bot.send_message.call_args.kwargs["chat_id"] == 222
    assert bot.send_message.call_args.kwargs["disable_notification"] is True
    assert await client.get_user_keyboard_version(222) == "current"


def test_main_keyboard_has_homework_button_and_signature_changes_with_layout():
    keyboard = main_keyboard()

    assert keyboard.keyboard[0][0].text == HOMEWORK_WEEK_LABEL
    assert isinstance(keyboard_signature(), str)
    assert len(keyboard_signature()) == 16
