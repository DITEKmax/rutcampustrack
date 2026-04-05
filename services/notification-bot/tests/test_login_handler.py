"""Tests for /login command handler (OTP FSM flow)."""
from unittest.mock import AsyncMock, MagicMock

import aiohttp
import pytest

from bot.handlers.login import cmd_login, process_otp_code, LoginStates


def _make_message(user_id: int = 12345, text: str = "") -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.text = text
    message.answer = AsyncMock()
    return message


def _make_state() -> MagicMock:
    state = MagicMock()
    state.set_state = AsyncMock()
    state.clear = AsyncMock()
    return state


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
async def test_login_requests_otp():
    """Successful /login sends OTP code and sets FSM state."""
    message = _make_message()
    state = _make_state()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(return_value="123456")
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value=None)

    await cmd_login(message, state=state, auth_client=auth_client, jwt_redis=jwt_redis)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Ваш код для входа: 123456" in text
    state.set_state.assert_called_once_with(LoginStates.waiting_for_code)


@pytest.mark.asyncio
async def test_login_already_logged_in():
    """Already authenticated user sees 'already logged in' message."""
    message = _make_message()
    state = _make_state()
    auth_client = MagicMock()
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value={"access_token": "tok", "refresh_token": "ref"})

    await cmd_login(message, state=state, auth_client=auth_client, jwt_redis=jwt_redis)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Вы уже вошли" in text
    auth_client.request_otp.assert_not_called() if hasattr(auth_client, 'request_otp') else None


@pytest.mark.asyncio
async def test_process_code_success():
    """Valid OTP code saves JWT and clears FSM state."""
    message = _make_message(text="654321")
    state = _make_state()
    auth_client = MagicMock()
    auth_client.verify_otp = AsyncMock(return_value={
        "accessToken": "access_tok",
        "refreshToken": "refresh_tok",
        "expiresIn": 900,
    })
    jwt_redis = MagicMock()
    jwt_redis.save = AsyncMock()

    await process_otp_code(message, state=state, auth_client=auth_client, jwt_redis=jwt_redis)

    jwt_redis.save.assert_called_once_with(12345, "access_tok", "refresh_tok", 900)
    state.clear.assert_called_once()
    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Вы успешно вошли" in text


@pytest.mark.asyncio
async def test_process_code_invalid():
    """Wrong OTP code (401) shows error and keeps FSM state."""
    message = _make_message(text="000000")
    state = _make_state()
    auth_client = MagicMock()
    auth_client.verify_otp = AsyncMock(side_effect=_make_http_error(401))
    jwt_redis = MagicMock()

    await process_otp_code(message, state=state, auth_client=auth_client, jwt_redis=jwt_redis)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Код неверный" in text
    state.clear.assert_not_called()


@pytest.mark.asyncio
async def test_login_rate_limited():
    """OTP request with rate limit (429) shows friendly message."""
    message = _make_message()
    state = _make_state()
    auth_client = MagicMock()
    auth_client.request_otp = AsyncMock(side_effect=_make_http_error(429))
    jwt_redis = MagicMock()
    jwt_redis.get = AsyncMock(return_value=None)

    await cmd_login(message, state=state, auth_client=auth_client, jwt_redis=jwt_redis)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Слишком много попыток" in text
    state.set_state.assert_not_called()


@pytest.mark.asyncio
async def test_process_code_invalid_format():
    """Non-6-digit input shows format error without calling verify_otp."""
    message = _make_message(text="abc")
    state = _make_state()
    auth_client = MagicMock()
    auth_client.verify_otp = AsyncMock()
    jwt_redis = MagicMock()

    await process_otp_code(message, state=state, auth_client=auth_client, jwt_redis=jwt_redis)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "6 цифр" in text
    auth_client.verify_otp.assert_not_called()
