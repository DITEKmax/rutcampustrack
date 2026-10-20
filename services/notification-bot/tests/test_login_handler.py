"""Private login preserves pending namespace and correlates the eventual OTP cleanup."""

import asyncio
import json
from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import aiohttp
import pytest
from aiogram import Bot
from aiogram.methods import SendMessage
from aiogram.types import Chat, Message, User

from bot.handlers.login import cmd_login
from bot.notifications.otp_requested import handle_otp_requested
from bot.services.otp_message_tracker import OtpMessageTracker


def _message(bot):
    return Message(
        message_id=777,
        date=datetime.now(timezone.utc),
        chat=Chat(id=12345, type="private"),
        from_user=User(id=12345, is_bot=False, first_name="Synthetic"),
        text="/login",
    ).as_(bot)


async def _case(fake_redis, status, expiry=None):
    session = AsyncMock(return_value=True)
    bot = Bot("123456:" + "A" * 35, session=session)
    tracker = OtpMessageTracker(300, redis_client=fake_redis)
    await tracker.store(999, 999, 90, 91)
    await tracker.store(12345, 12345, 80, 81, purpose="password_reset")
    if status == 204:
        auth = SimpleNamespace(request_otp=AsyncMock(return_value=None))
    else:
        error = aiohttp.ClientResponseError(MagicMock(), (), status=status)
        auth = SimpleNamespace(request_otp=AsyncMock(side_effect=error))
    await cmd_login(_message(bot), auth_client=auth, bot=bot, otp_tracker=tracker)
    pending = json.loads(await fake_redis.get("otp_pending_user_msg:12345"))
    assert pending == {"chat_id": 12345, "user_message_id": 777}
    assert await fake_redis.get("otp_msgs:12345") is None
    if status != 204:
        # The only provider operation is a generic failure explanation; no OTP event/code delivery.
        assert session.await_count == 1
        method = session.await_args.args[1]
        assert isinstance(method, SendMessage) and method.chat_id == 12345
        expected = {429: "много попыток", 401: "не найден", 503: "недоступен"}
        assert expected[status] in method.text
    else:
        session.assert_not_awaited()
        session.return_value = SimpleNamespace(message_id=1001)
        await handle_otp_requested(
            {"payload": {"telegram_id": 12345, "code": "123456", "ttl_seconds": 120}}, bot=bot, tracker=tracker
        )
        assert await fake_redis.get("otp_pending_user_msg:12345") is None
        assert json.loads(await fake_redis.get("otp_msgs:12345")) == {
            "chat_id": 12345,
            "user_message_id": 777,
            "bot_message_id": 1001,
        }
        await asyncio.wait_for(expiry.entered.wait(), 1)
        expiry.release.set()
        await asyncio.wait_for(asyncio.gather(*expiry.tasks), 1)
        assert await fake_redis.get("otp_msgs:12345") is None
        deletes = [c.args[1].message_id for c in session.await_args_list if c.args[1].__api_method__ == "deleteMessage"]
        assert sorted(deletes) == [777, 1001]
    assert (await tracker.pop(999))["bot_message_id"] == 91
    assert (await tracker.pop(12345, purpose="password_reset"))["bot_message_id"] == 81


@pytest.mark.asyncio
async def test_login_saves_pending_msg_and_requests_otp(fake_redis, otp_tasks):
    await _case(fake_redis, 204, otp_tasks)


@pytest.mark.asyncio
async def test_login_rate_limited(fake_redis):
    await _case(fake_redis, 429)


@pytest.mark.asyncio
async def test_login_account_not_found(fake_redis):
    await _case(fake_redis, 401)


@pytest.mark.asyncio
async def test_login_service_unavailable(fake_redis):
    await _case(fake_redis, 503)
