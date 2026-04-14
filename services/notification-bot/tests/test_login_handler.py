"""Tests for /login command handler — OTP via web flow."""

import asyncio
from unittest.mock import AsyncMock, MagicMock

import aiohttp
import pytest

from bot.handlers.login import WEB_LOGIN_URL, cmd_login


def _make_message(user_id: int = 12345, chat_id: int = 99, message_id: int = 777, text: str = "") -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.chat = MagicMock()
    message.chat.id = chat_id
    message.message_id = message_id
    message.text = text
    reply = MagicMock()
    reply.message_id = 1001
    message.answer = AsyncMock(return_value=reply)
    return message


def _make_tracker() -> MagicMock:
    tracker = MagicMock()
    tracker.store = AsyncMock()
    tracker.pop = AsyncMock(return_value=None)
    return tracker


def _make_bot() -> MagicMock:
    bot = MagicMock()
    bot.delete_message = AsyncMock()
    return bot


def _make_http_error(status: int) -> aiohttp.ClientResponseError:
    request_info = MagicMock()
    request_info.url = "http://auth-service/auth/otp/request"
    request_info.method = "POST"
    request_info.headers = {}
    return aiohttp.ClientResponseError(
        request_info=request_info,
        history=(),
        status=status,
    )


async def _drain_tasks() -> None:
    """Let the fire-and-forget expiry task start so we can cancel it."""
    await asyncio.sleep(0)
    for task in asyncio.all_tasks() - {asyncio.current_task()}:
        task.cancel()
    await asyncio.gather(*(asyncio.all_tasks() - {asyncio.current_task()}), return_exceptions=True)


@pytest.mark.asyncio
async def test_login_requests_otp_and_links_web():
    """Successful /login sends OTP code, persists messages, schedules cleanup."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(return_value="123456")
    tracker = _make_tracker()
    bot = _make_bot()

    await cmd_login(message, auth_client=auth_client, bot=bot, otp_tracker=tracker)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "123456" in text
    assert WEB_LOGIN_URL in text
    assert message.answer.call_args.kwargs.get("parse_mode") == "HTML"

    tracker.store.assert_awaited_once_with(
        telegram_id=12345,
        chat_id=99,
        user_message_id=777,
        bot_message_id=1001,
    )

    await _drain_tasks()


@pytest.mark.asyncio
async def test_login_rate_limited():
    """OTP request with rate limit (429) shows friendly message — no tracker write."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(429))
    tracker = _make_tracker()
    bot = _make_bot()

    await cmd_login(message, auth_client=auth_client, bot=bot, otp_tracker=tracker)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Слишком много попыток" in text
    tracker.store.assert_not_called()


@pytest.mark.asyncio
async def test_login_account_not_found():
    """Unknown telegram_id (401) prompts user to contact headman — no tracker write."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(401))
    tracker = _make_tracker()
    bot = _make_bot()

    await cmd_login(message, auth_client=auth_client, bot=bot, otp_tracker=tracker)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "не найден" in text
    tracker.store.assert_not_called()


@pytest.mark.asyncio
async def test_login_service_unavailable():
    """5xx from auth-service shows service-unavailable message — no tracker write."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(503))
    tracker = _make_tracker()
    bot = _make_bot()

    await cmd_login(message, auth_client=auth_client, bot=bot, otp_tracker=tracker)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "временно недоступен" in text
    tracker.store.assert_not_called()
