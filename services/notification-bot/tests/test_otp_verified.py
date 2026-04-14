"""Tests for otp.verified handler and OTP message cleanup."""

from unittest.mock import AsyncMock, MagicMock

import pytest
from aiogram.exceptions import TelegramBadRequest

from bot.notifications.otp_verified import cleanup_otp_messages, handle_otp_verified


def _bot() -> MagicMock:
    bot = MagicMock()
    bot.delete_message = AsyncMock()
    return bot


def _tracker(stored: dict | None) -> MagicMock:
    tracker = MagicMock()
    tracker.pop = AsyncMock(return_value=stored)
    return tracker


@pytest.mark.asyncio
async def test_cleanup_deletes_both_messages_when_tracked():
    bot = _bot()
    tracker = _tracker({"chat_id": 10, "user_message_id": 1, "bot_message_id": 2})

    await cleanup_otp_messages(777, bot=bot, tracker=tracker)

    tracker.pop.assert_awaited_once_with(777)
    assert bot.delete_message.await_count == 2
    called_pairs = {(call.kwargs["chat_id"], call.kwargs["message_id"]) for call in bot.delete_message.await_args_list}
    assert called_pairs == {(10, 1), (10, 2)}


@pytest.mark.asyncio
async def test_cleanup_noop_when_nothing_tracked():
    bot = _bot()
    tracker = _tracker(None)

    await cleanup_otp_messages(777, bot=bot, tracker=tracker)

    bot.delete_message.assert_not_called()


@pytest.mark.asyncio
async def test_cleanup_swallows_telegram_bad_request():
    bot = _bot()
    bot.delete_message = AsyncMock(
        side_effect=TelegramBadRequest(method=MagicMock(), message="message to delete not found")
    )
    tracker = _tracker({"chat_id": 10, "user_message_id": 1, "bot_message_id": 2})

    # Must not raise — already-deleted messages are fine
    await cleanup_otp_messages(777, bot=bot, tracker=tracker)

    assert bot.delete_message.await_count == 2


@pytest.mark.asyncio
async def test_handle_otp_verified_routes_payload_telegram_id():
    bot = _bot()
    tracker = _tracker({"chat_id": 5, "user_message_id": 6, "bot_message_id": 7})
    event = {"event_type": "otp.verified", "payload": {"telegram_id": 42}}

    await handle_otp_verified(event, bot=bot, tracker=tracker)

    tracker.pop.assert_awaited_once_with(42)
    assert bot.delete_message.await_count == 2


@pytest.mark.asyncio
async def test_handle_otp_verified_missing_telegram_id_is_noop():
    bot = _bot()
    tracker = _tracker(None)

    await handle_otp_verified({"event_type": "otp.verified", "payload": {}}, bot=bot, tracker=tracker)

    tracker.pop.assert_not_called()
    bot.delete_message.assert_not_called()
