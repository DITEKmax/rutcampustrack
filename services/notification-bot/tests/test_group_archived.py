"""Tests for the group.archived notification handler (58-07 / BUG-006-6)."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.group_archived import handle_group_archived


def _make_student(user_id: int, telegram_id: int):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    return s


def _make_group(name: str = "УИТ-411 (выпуск 2026)"):
    g = MagicMock()
    g.id = 42
    g.name = name
    g.is_active = False
    return g


def _make_event(group_id: int = 42):
    return {"event_type": "group.archived", "payload": {"group_id": group_id}}


@pytest.mark.asyncio
async def test_group_archived_sends_to_students_with_telegram_id():
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # skipped
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group = AsyncMock(return_value=_make_group())
    academic_client.get_group_members = AsyncMock(return_value=students)

    captured = []

    async def capture_put(task):
        captured.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_group_archived(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured) == 1
    await captured[0].coroutine_factory()
    assert bot.send_message.call_count == 1


@pytest.mark.asyncio
async def test_group_archived_message_contains_graduation_text_and_name():
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group = AsyncMock(return_value=_make_group("УИТ-411 (выпуск 2026)"))
    academic_client.get_group_members = AsyncMock(return_value=students)

    captured = []

    async def capture_put(task):
        captured.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_group_archived(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "архивирована" in text
    assert "выпуск" in text.lower()
    assert "УИТ-411" in text


@pytest.mark.asyncio
async def test_group_archived_missing_group_id_returns_early():
    academic_client = MagicMock()
    academic_client.get_group = AsyncMock()
    academic_client.get_group_members = AsyncMock()

    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    bot = MagicMock()

    await handle_group_archived(
        {"event_type": "group.archived", "payload": {}},
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    academic_client.get_group.assert_not_called()
    send_queue.put.assert_not_called()
