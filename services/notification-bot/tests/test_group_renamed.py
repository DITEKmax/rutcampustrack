"""Tests for the group.renamed notification handler (58-07 / BUG-006-6)."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.group_renamed import handle_group_renamed


def _make_student(user_id: int, telegram_id: int):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    return s


def _make_group(name: str = "УИТ-311"):
    g = MagicMock()
    g.id = 42
    g.name = name
    g.is_active = True
    return g


def _make_event(group_id: int = 42):
    return {"event_type": "group.renamed", "payload": {"group_id": group_id}}


@pytest.mark.asyncio
async def test_group_renamed_sends_to_students_with_telegram_id():
    """Only students with telegram_id > 0 receive the rename notification."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # skipped
        _make_student(user_id=3, telegram_id=333),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group = AsyncMock(return_value=_make_group("УИТ-311"))
    academic_client.get_group_members = AsyncMock(return_value=students)

    captured = []

    async def capture_put(task):
        captured.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_group_renamed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured) == 2

    for task in captured:
        await task.coroutine_factory()
    assert bot.send_message.call_count == 2


@pytest.mark.asyncio
async def test_group_renamed_message_contains_new_name():
    """Message body includes the new group name resolved via gRPC."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group = AsyncMock(return_value=_make_group("УИТ-211"))
    academic_client.get_group_members = AsyncMock(return_value=students)

    captured = []

    async def capture_put(task):
        captured.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_group_renamed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    await captured[0].coroutine_factory()
    kwargs = bot.send_message.call_args.kwargs
    assert "УИТ-211" in kwargs.get("text", "")
    assert kwargs.get("parse_mode") == "HTML"


@pytest.mark.asyncio
async def test_group_renamed_missing_group_id_returns_early():
    """Handler returns without sending when payload lacks group_id."""
    academic_client = MagicMock()
    academic_client.get_group = AsyncMock()
    academic_client.get_group_members = AsyncMock()

    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    bot = MagicMock()

    await handle_group_renamed(
        {"event_type": "group.renamed", "payload": {}},
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    academic_client.get_group.assert_not_called()
    academic_client.get_group_members.assert_not_called()
    send_queue.put.assert_not_called()


@pytest.mark.asyncio
async def test_group_renamed_grpc_failure_still_notifies_with_fallback():
    """When get_group raises, fallback text is used but notification still sent."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group = AsyncMock(side_effect=Exception("gRPC down"))
    academic_client.get_group_members = AsyncMock(return_value=students)

    captured = []

    async def capture_put(task):
        captured.append(task)

    send_queue = MagicMock()
    send_queue.put = capture_put

    await handle_group_renamed(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured) == 1
    await captured[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "переименована" in text
