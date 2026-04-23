"""Tests for /login command handler — OTP via web flow (M09 G2).

После M09 G2 /login дёргает auth-service (204 No Content), сохраняет
pending user_message_id в tracker и НЕ отвечает пользователю —
сообщение с кодом отправит handle_otp_requested, когда придёт
событие otp.requested из RabbitMQ.
"""

from unittest.mock import AsyncMock, MagicMock

import aiohttp
import pytest

from bot.handlers.login import cmd_login


def _make_message(user_id: int = 12345, chat_id: int = 99, message_id: int = 777, text: str = "") -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.chat = MagicMock()
    message.chat.id = chat_id
    message.message_id = message_id
    message.text = text
    message.answer = AsyncMock()
    return message


def _make_tracker() -> MagicMock:
    tracker = MagicMock()
    tracker.store = AsyncMock()
    tracker.store_pending_user_msg = AsyncMock()
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


@pytest.mark.asyncio
async def test_login_saves_pending_msg_and_requests_otp():
    """/login сохраняет pending user_message_id и дёргает auth (204).

    Сам по себе НЕ отвечает пользователю — это задача handle_otp_requested.
    """
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(return_value=None)
    tracker = _make_tracker()
    bot = _make_bot()

    await cmd_login(message, auth_client=auth_client, bot=bot, otp_tracker=tracker)

    tracker.store_pending_user_msg.assert_awaited_once_with(
        telegram_id=12345,
        chat_id=99,
        user_message_id=777,
    )
    auth_client.request_otp.assert_awaited_once_with(12345)
    # No direct answer — message sent by otp.requested consumer.
    message.answer.assert_not_called()


@pytest.mark.asyncio
async def test_login_rate_limited():
    """OTP request with rate limit (429) shows friendly message."""
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
    """Unknown telegram_id (401) prompts user to contact headman."""
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
    """5xx from auth-service shows service-unavailable message."""
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
