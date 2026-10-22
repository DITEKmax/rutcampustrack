"""Tests for otp.requested handler (M09 G2, 08 P0-2)."""

import traceback
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4

import pytest

from bot.notifications.otp_requested import (
    WEB_LOGIN_URL,
    WEB_PASSWORD_RESET_URL,
    handle_otp_requested,
)


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


@pytest.mark.asyncio
async def test_handle_otp_requested_sends_code_and_finalizes_tracker(otp_tasks):
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

    tracker.finalize_with_bot_msg.assert_awaited_once_with(42, 1001, purpose="login")


@pytest.mark.asyncio
async def test_handle_password_reset_otp_includes_fragment_link_and_server_metadata(otp_tasks):
    bot = _bot(sent_message_id=1002)
    tracker = _tracker()
    challenge_id = "A" * 32
    event = {
        "event_type": "otp.requested",
        "payload": {
            "telegram_id": 42,
            "code": "123456",
            "ttl_seconds": 120,
            "purpose": "password_reset",
            "challenge_id": challenge_id,
            "attempts_remaining": 2,
        },
    }

    await handle_otp_requested(event, bot=bot, tracker=tracker)

    text = bot.send_message.await_args.kwargs["text"]
    assert "Код для сброса пароля" in text
    assert "123456" in text
    assert "Осталось попыток: 2" in text
    assert f"{WEB_PASSWORD_RESET_URL}#challengeId={challenge_id}" in text
    assert "&amp;code=123456" in text
    assert "?" not in text
    tracker.finalize_with_bot_msg.assert_awaited_once_with(42, 1002, purpose="password_reset")


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
async def test_handle_otp_requested_custom_ttl_rounds_to_minutes(otp_tasks):
    bot = _bot()
    tracker = _tracker()
    event = {
        "event_type": "otp.requested",
        "payload": {"telegram_id": 1, "code": "999888", "ttl_seconds": 120},
    }

    await handle_otp_requested(event, bot=bot, tracker=tracker)

    text = bot.send_message.await_args.kwargs["text"]
    assert "2 мин" in text


@pytest.mark.asyncio
async def test_handle_otp_requested_send_failure_is_safe_and_does_not_finalize_tracker(caplog):
    bot = MagicMock()
    marker = uuid4().hex
    bot.send_message = AsyncMock(side_effect=RuntimeError(marker))
    tracker = _tracker()
    event = {
        "event_type": "otp.requested",
        "payload": {"telegram_id": 42, "code": "123456", "ttl_seconds": 300},
    }

    with pytest.raises(RuntimeError) as failure:
        await handle_otp_requested(event, bot=bot, tracker=tracker)

    assert str(failure.value) == "OTP Telegram delivery failed"
    assert failure.value.__cause__ is None
    rendered = "".join(traceback.format_exception(failure.value))
    safe_trace = marker not in rendered
    safe_log = marker not in caplog.text
    assert safe_trace
    assert safe_log
    tracker.finalize_with_bot_msg.assert_not_called()


@pytest.mark.asyncio
async def test_subject_delete_is_challenge_scoped_without_login_or_reset_link(otp_tasks):
    bot = _bot(sent_message_id=1003)
    tracker = _tracker()
    challenge = "S" * 32
    await handle_otp_requested(
        {
            "payload": {
                "telegram_id": 42,
                "code": "123456",
                "ttl_seconds": 120,
                "purpose": "subject_delete",
                "challenge_id": challenge,
                "attempts_remaining": 3,
            }
        },
        bot=bot,
        tracker=tracker,
    )
    text = bot.send_message.await_args.kwargs["text"]
    assert "Подтверждение удаления предмета" in text
    assert "необратимо" in text
    assert "123456" in text
    assert WEB_LOGIN_URL not in text and WEB_PASSWORD_RESET_URL not in text
    tracker.finalize_with_bot_msg.assert_awaited_once_with(42, 1003, purpose=f"subject_delete:{challenge}")


@pytest.mark.asyncio
@pytest.mark.parametrize("invalid", [{"challenge_id": "wrong"}, {"telegram_id": "42"}, {"attempts_remaining": 4}])
async def test_subject_delete_refuses_unbound_challenge_before_any_delivery(invalid):
    bot = _bot()
    tracker = _tracker()
    payload = {
        "telegram_id": 42,
        "code": "123456",
        "ttl_seconds": 120,
        "purpose": "subject_delete",
        "challenge_id": "S" * 32,
        "attempts_remaining": 3,
    }
    payload.update(invalid)
    await handle_otp_requested({"payload": payload}, bot=bot, tracker=tracker)
    bot.send_message.assert_not_called()
    tracker.finalize_with_bot_msg.assert_not_called()


@pytest.mark.asyncio
@pytest.mark.parametrize("purpose", ["login", "password_reset", "subject_delete"])
async def test_expiry_cleans_only_its_tracked_messages(fake_redis, otp_tasks, purpose):
    import asyncio

    from bot.services.otp_message_tracker import OtpMessageTracker

    tracker = OtpMessageTracker(300, redis_client=fake_redis)
    challenge = "S" * 32
    own_purpose = f"subject_delete:{challenge}" if purpose == "subject_delete" else purpose
    await tracker.store_pending_user_msg(42, 42, 7, purpose=own_purpose)
    await tracker.store(99, 99, None, 88, purpose="password_reset")
    bot = _bot(1003)
    payload = {"telegram_id": 42, "code": "123456", "ttl_seconds": 120, "purpose": purpose}
    if purpose != "login":
        payload.update(challenge_id=challenge, attempts_remaining=3)
    await handle_otp_requested({"payload": payload}, bot=bot, tracker=tracker)
    await asyncio.wait_for(otp_tasks.entered.wait(), 1)
    assert otp_tasks.waits == [120]
    bot.delete_message.assert_not_awaited()
    otp_tasks.release.set()
    await asyncio.wait_for(asyncio.gather(*otp_tasks.tasks), 1)
    assert {(c.kwargs["chat_id"], c.kwargs["message_id"]) for c in bot.delete_message.await_args_list} == {
        (42, 7),
        (42, 1003),
    }
    assert await tracker.pop(42, purpose=own_purpose) is None
    assert (await tracker.pop(99, purpose="password_reset"))["bot_message_id"] == 88
