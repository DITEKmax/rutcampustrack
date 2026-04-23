"""Tests for otp.requested handler (M09 G2, 08 P0-2)."""

import asyncio
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.otp_requested import WEB_LOGIN_URL, handle_otp_requested


def _bot(sent_message_id: int = 555) -> MagicMock:
    bot = MagicMock()
    sent = MagicMock()
    sent.message_id = sent_message_id
    bot.send_message = AsyncMock(return_value=sent)
    bot.delete_message = AsyncMock()
    return bot


def _tracker() -> MagicMock:
    tracker = MagicMock()
    tracker.finalize_with_bot_msg = AsyncMock()
    tracker.pop = AsyncMock(return_value=None)
    return tracker


async def _drain_tasks() -> None:
    """Cancel any fire-and-forget _cleanup_after_expiry tasks."""
    await asyncio.sleep(0)
    for task in asyncio.all_tasks() - {asyncio.current_task()}:
        task.cancel()
    await asyncio.gather(*(asyncio.all_tasks() - {asyncio.current_task()}), return_exceptions=True)


@pytest.mark.asyncio
async def test_handle_otp_requested_sends_code_and_finalizes_tracker():
    bot = _bot(sent_message_id=1001)
    tracker = _tracker()
    event = {
        "event_type": "otp.requested",
        "payload": {"telegram_id": 42, "code": "123456", "ttl_seconds": 300},
    }

    await handle_otp_requested(event, bot=bot, tracker=tracker)

    bot.send_message.assert_awaited_once()
    call = bot.send_message.await_args
    assert call.kwargs["chat_id"] == 42
    assert call.kwargs["parse_mode"] == "HTML"
    text = call.kwargs["text"]
    assert "123456" in text
    assert WEB_LOGIN_URL in text
    assert "5 мин" in text

    tracker.finalize_with_bot_msg.assert_awaited_once_with(42, 1001)
    await _drain_tasks()


@pytest.mark.asyncio
async def test_handle_otp_requested_malformed_event_is_noop():
    bot = _bot()
    tracker = _tracker()

    # missing code
    await handle_otp_requested(
        {"event_type": "otp.requested", "payload": {"telegram_id": 42}},
        bot=bot,
        tracker=tracker,
    )
    # missing telegram_id
    await handle_otp_requested(
        {"event_type": "otp.requested", "payload": {"code": "000000"}},
        bot=bot,
        tracker=tracker,
    )

    bot.send_message.assert_not_called()
    tracker.finalize_with_bot_msg.assert_not_called()


@pytest.mark.asyncio
async def test_handle_otp_requested_custom_ttl_rounds_to_minutes():
    bot = _bot()
    tracker = _tracker()
    event = {
        "event_type": "otp.requested",
        "payload": {"telegram_id": 1, "code": "999888", "ttl_seconds": 120},
    }

    await handle_otp_requested(event, bot=bot, tracker=tracker)

    text = bot.send_message.await_args.kwargs["text"]
    assert "2 мин" in text
    await _drain_tasks()


@pytest.mark.asyncio
async def test_handle_otp_requested_send_failure_does_not_finalize_tracker():
    bot = MagicMock()
    bot.send_message = AsyncMock(side_effect=RuntimeError("telegram down"))
    tracker = _tracker()
    event = {
        "event_type": "otp.requested",
        "payload": {"telegram_id": 42, "code": "123456", "ttl_seconds": 300},
    }

    await handle_otp_requested(event, bot=bot, tracker=tracker)

    tracker.finalize_with_bot_msg.assert_not_called()
