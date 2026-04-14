"""Tests for /start command handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.start import cmd_start


def _make_message(user_id: int = 12345) -> MagicMock:
    message = MagicMock()
    message.from_user = MagicMock()
    message.from_user.id = user_id
    message.answer = AsyncMock()
    return message


def _make_user_response(
    found: bool = True,
    display_name: str = "Иван Иванов",
    login: str = "student00001",
    initial_password: str = "",
    group_name: str = "ИБ-21",
) -> MagicMock:
    resp = MagicMock()
    resp.found = found
    resp.display_name = display_name
    resp.login = login
    resp.initial_password = initial_password
    resp.group_name = group_name
    return resp


@pytest.mark.asyncio
async def test_start_known_user_first_login():
    """Known user with initial_password sees credentials."""
    message = _make_message()
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(
        return_value=_make_user_response(
            found=True,
            display_name="Иван Иванов",
            login="student00001",
            initial_password="pass123",
        )
    )

    prefs_client = MagicMock()
    prefs_client.is_enabled = AsyncMock(return_value=True)
    await cmd_start(message, academic_client=academic_client, prefs_client=prefs_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Ваш пароль: pass123" in text
    assert "student00001" in text
    assert "Иван Иванов" in text


@pytest.mark.asyncio
async def test_start_known_user_password_changed():
    """Known user with empty initial_password sees login and group name."""
    message = _make_message()
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(
        return_value=_make_user_response(
            found=True,
            display_name="Мария Петрова",
            login="student00002",
            initial_password="",
            group_name="ИБ-21",
        )
    )

    prefs_client = MagicMock()
    prefs_client.is_enabled = AsyncMock(return_value=True)
    await cmd_start(message, academic_client=academic_client, prefs_client=prefs_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Группа: ИБ-21" in text
    assert "student00002" in text
    assert "Мария Петрова" in text


@pytest.mark.asyncio
async def test_start_unknown_user():
    """Unknown telegram_id shows instruction to contact headman."""
    message = _make_message()
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(return_value=_make_user_response(found=False))

    prefs_client = MagicMock()
    prefs_client.is_enabled = AsyncMock(return_value=True)
    await cmd_start(message, academic_client=academic_client, prefs_client=prefs_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Ваш Telegram не привязан к системе" in text
    assert "старосте" in text


@pytest.mark.asyncio
async def test_start_grpc_error():
    """gRPC error shows service unavailable message."""
    message = _make_message()
    academic_client = MagicMock()
    academic_client.get_user_by_telegram_id = AsyncMock(side_effect=Exception("gRPC connection refused"))

    prefs_client = MagicMock()
    prefs_client.is_enabled = AsyncMock(return_value=True)
    await cmd_start(message, academic_client=academic_client, prefs_client=prefs_client)

    message.answer.assert_called_once()
    text = message.answer.call_args[0][0]
    assert "Сервис временно недоступен" in text
