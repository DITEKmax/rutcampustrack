"""Tests for the public Telegram password-reset request command."""

from unittest.mock import AsyncMock, MagicMock

import aiohttp
import pytest

from bot.handlers.password_reset import cmd_password_reset


def _message() -> MagicMock:
    message = MagicMock()
    message.from_user.id = 12345
    message.chat.id = 12345
    message.chat.type = "private"
    message.message_id = 777
    message.answer = AsyncMock()
    return message


def _tracker() -> MagicMock:
    tracker = MagicMock()
    tracker.store_pending_user_msg = AsyncMock()
    return tracker


def _http_error(status: int) -> aiohttp.ClientResponseError:
    request_info = MagicMock()
    request_info.url = "http://auth-service/auth/password-reset/request"
    request_info.method = "POST"
    request_info.headers = {}
    return aiohttp.ClientResponseError(request_info, (), status=status)


@pytest.mark.asyncio
async def test_reset_requests_code_and_keeps_acknowledgement_generic():
    message = _message()
    auth_client = MagicMock()
    auth_client.request_password_reset = AsyncMock()
    tracker = _tracker()

    await cmd_password_reset(message, auth_client=auth_client, otp_tracker=tracker)

    tracker.store_pending_user_msg.assert_awaited_once_with(
        telegram_id=12345,
        chat_id=12345,
        user_message_id=777,
        purpose="password_reset",
    )
    auth_client.request_password_reset.assert_awaited_once_with(12345)
    response = message.answer.await_args.args[0]
    assert "Если этот Telegram привязан" in response
    assert "код придёт сюда" in response
    assert "не найден" not in response


@pytest.mark.asyncio
async def test_reset_rate_limit_does_not_disclose_account_status():
    message = _message()
    auth_client = MagicMock()
    auth_client.request_password_reset = AsyncMock(side_effect=_http_error(429))
    tracker = _tracker()

    await cmd_password_reset(message, auth_client=auth_client, otp_tracker=tracker)

    assert "Слишком много запросов" in message.answer.await_args.args[0]
    assert "аккаунт" not in message.answer.await_args.args[0].lower()


@pytest.mark.asyncio
@pytest.mark.parametrize("chat_type", ["group", "supergroup"])
async def test_reset_in_group_directs_user_to_private_chat_without_starting_recovery(chat_type: str):
    message = _message()
    message.chat.type = chat_type
    auth_client = MagicMock()
    auth_client.request_password_reset = AsyncMock()
    tracker = _tracker()

    await cmd_password_reset(message, auth_client=auth_client, otp_tracker=tracker)

    response = message.answer.await_args.args[0]
    assert "личный чат с ботом" in response
    assert "/reset" in response
    auth_client.request_password_reset.assert_not_awaited()
    tracker.store_pending_user_msg.assert_not_awaited()
