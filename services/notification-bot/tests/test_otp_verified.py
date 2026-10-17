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

    tracker.pop.assert_awaited_once_with(777, purpose="login")
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

    tracker.pop.assert_awaited_once_with(42, purpose="login")
    assert bot.delete_message.await_count == 2


@pytest.mark.asyncio
async def test_handle_otp_verified_missing_telegram_id_is_noop():
    bot = _bot()
    tracker = _tracker(None)

    await handle_otp_verified({"event_type": "otp.verified", "payload": {}}, bot=bot, tracker=tracker)

    tracker.pop.assert_not_called()
    bot.delete_message.assert_not_called()


@pytest.mark.asyncio
async def test_subject_delete_cleanup_only_removes_matching_challenge_messages():
    bot = _bot()
    tracker = _tracker({"chat_id": 42, "bot_message_id": 12})
    challenge = "S" * 32
    await handle_otp_verified(
        {"payload": {"telegram_id": 42, "purpose": "subject_delete", "challenge_id": challenge}},
        bot=bot,
        tracker=tracker,
    )
    tracker.pop.assert_awaited_once_with(42, purpose=f"subject_delete:{challenge}")
    bot.delete_message.assert_awaited_once_with(chat_id=42, message_id=12)


@pytest.mark.asyncio
async def test_subject_delete_cleanup_without_exact_challenge_cannot_pop_login():
    bot = _bot()
    tracker = _tracker(None)
    await handle_otp_verified({"payload": {"telegram_id": 42, "purpose": "subject_delete"}}, bot=bot, tracker=tracker)
    tracker.pop.assert_not_called()
    bot.delete_message.assert_not_called()


@pytest.mark.asyncio
async def test_malformed_cleanup_never_logs_private_payload(caplog):
    from uuid import uuid4

    marker = uuid4().hex
    bot = _bot()
    tracker = _tracker(None)
    await handle_otp_verified(
        {"payload": {"purpose": "subject_delete", "challenge_id": marker}}, bot=bot, tracker=tracker
    )
    assert marker not in caplog.text
    tracker.pop.assert_not_called()


@pytest.mark.asyncio
async def test_actual_tracker_keeps_new_challenge_login_and_reset_after_old_cleanup():
    from bot.services.otp_message_tracker import OtpMessageTracker

    stored = {}
    cache = MagicMock()

    async def save(key, value, ex):
        stored[key] = value

    cache.set = AsyncMock(side_effect=save)
    pipe = MagicMock()
    pipe.__aenter__ = AsyncMock(return_value=pipe)
    pipe.__aexit__ = AsyncMock(return_value=False)
    selected = []
    pipe.get.side_effect = lambda key: selected.append(key)

    async def consume():
        return stored.pop(selected[-1], None), 1

    pipe.execute = AsyncMock(side_effect=consume)
    cache.pipeline.return_value = pipe
    tracker = OtpMessageTracker(120, redis_client=cache)
    old, new = "S" * 32, "T" * 32
    for message, purpose in [
        (1, f"subject_delete:{old}"),
        (2, f"subject_delete:{new}"),
        (3, "login"),
        (4, "password_reset"),
    ]:
        await tracker.store(42, 42, None, message, purpose=purpose)
    bot = _bot()
    event = {"payload": {"telegram_id": 42, "purpose": "subject_delete", "challenge_id": old}}
    await handle_otp_verified(event, bot=bot, tracker=tracker)
    await handle_otp_verified(event, bot=bot, tracker=tracker)
    bot.delete_message.assert_awaited_once_with(chat_id=42, message_id=1)
    assert (await tracker.pop(42, purpose=f"subject_delete:{new}"))["bot_message_id"] == 2
    assert (await tracker.pop(42, purpose="login"))["bot_message_id"] == 3
    assert (await tracker.pop(42, purpose="password_reset"))["bot_message_id"] == 4
