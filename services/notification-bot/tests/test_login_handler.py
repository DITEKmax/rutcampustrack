"""Tests for /login command handler — OTP via web flow."""

from unittest.mock import AsyncMock, MagicMock

import aiohttp
import pytest

from bot.handlers.login import WEB_LOGIN_URL, cmd_login


def _make_message(user_id: int = 12345, text: str = "") -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.text = text
    message.answer = AsyncMock()
    return message


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
async def test_login_requests_otp_and_links_web():
    """Successful /login sends OTP code and link to the web-panel."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(return_value="123456")

    await cmd_login(message, auth_client=auth_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "123456" in text
    assert WEB_LOGIN_URL in text
    assert message.answer.call_args.kwargs.get("parse_mode") == "HTML"


@pytest.mark.asyncio
async def test_login_rate_limited():
    """OTP request with rate limit (429) shows friendly message."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(429))

    await cmd_login(message, auth_client=auth_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Слишком много попыток" in text


@pytest.mark.asyncio
async def test_login_account_not_found():
    """Unknown telegram_id (401) prompts user to contact headman."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(401))

    await cmd_login(message, auth_client=auth_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "не найден" in text


@pytest.mark.asyncio
async def test_login_service_unavailable():
    """5xx from auth-service shows service-unavailable message."""
    message = _make_message()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(503))

    await cmd_login(message, auth_client=auth_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "временно недоступен" in text
