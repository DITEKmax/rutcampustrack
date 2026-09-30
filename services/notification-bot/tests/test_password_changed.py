"""Tests for metadata-only password-change notifications."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.password_changed import handle_password_changed


@pytest.mark.asyncio
async def test_password_changed_notifies_only_the_bound_telegram_account():
    bot = MagicMock()
    bot.send_message = AsyncMock()

    await handle_password_changed(
        {"event_type": "password.changed", "payload": {"telegram_id": 42}},
        bot=bot,
    )

    bot.send_message.assert_awaited_once()
    kwargs = bot.send_message.await_args.kwargs
    assert kwargs["chat_id"] == 42
    assert "изменён" in kwargs["text"]


@pytest.mark.asyncio
async def test_password_changed_malformed_event_does_not_send():
    bot = MagicMock()
    bot.send_message = AsyncMock()

    await handle_password_changed(
        {"event_type": "password.changed", "payload": {"telegram_id": True}},
        bot=bot,
    )

    bot.send_message.assert_not_awaited()
