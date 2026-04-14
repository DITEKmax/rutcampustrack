"""Tests for the student alert notification handler (excuse.decided).

Covers AC-8 (Phase 59): student gets Telegram notification after headman
decides the excuse ticket, with distinct text for approved vs rejected and
graceful handling of malformed payloads (T-59-06-03).
"""

import json
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.student_alerts import handle_student_alert

FIXTURE_PATH = Path(__file__).parent / "fixtures" / "excuse_decided.json"


def _make_user(telegram_id: int) -> MagicMock:
    u = MagicMock()
    u.telegram_id = telegram_id
    return u


def _capturing_send_queue():
    captured: list = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured.append(task)

    send_queue.put = capture_put
    return send_queue, captured


def _load_fixture() -> dict:
    with FIXTURE_PATH.open(encoding="utf-8") as fh:
        return json.load(fh)


@pytest.mark.asyncio
async def test_approved_event_sends_ru_message_to_student():
    """AC-8: approved event → send_queue.put called with ru text '✅ ... одобрен'."""
    event = _load_fixture()  # fixture from 59-05 is status=approved
    assert event["payload"]["status"] == "approved"

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_user(telegram_id=55555))
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(
        event, bot=bot, academic_client=academic_client, send_queue=send_queue
    )

    assert len(captured) == 1
    task = captured[0]
    assert task.user_id == event["payload"]["user_id"]
    assert task.chat_id == 55555

    # Execute the coroutine factory to verify the actual bot call
    await task.coroutine_factory()
    kwargs = bot.send_message.call_args.kwargs
    assert kwargs["chat_id"] == 55555
    text = kwargs["text"]
    assert "✅" in text
    assert "одобрен" in text
    # decision_comment from fixture should be present
    assert event["payload"]["decision_comment"] in text


@pytest.mark.asyncio
async def test_rejected_event_sends_ru_message_with_comment():
    """rejected event → text contains '❌' + 'отклонён' + decision_comment."""
    event = {
        "event_type": "excuse.decided",
        "payload": {
            "ticket_id": "abc",
            "user_id": 42,
            "student_id": 42,
            "status": "rejected",
            "decision_comment": "документы не прикреплены",
        },
    }

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_user(telegram_id=9999))
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(
        event, bot=bot, academic_client=academic_client, send_queue=send_queue
    )

    assert len(captured) == 1
    await captured[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs["text"]
    assert "❌" in text
    assert "отклонён" in text
    assert "документы не прикреплены" in text
    academic_client.get_user_by_id.assert_awaited_once_with(42)


@pytest.mark.asyncio
async def test_rejected_event_without_comment_falls_back_to_default():
    """rejected without decision_comment → text says 'без комментария'."""
    event = {
        "event_type": "excuse.decided",
        "payload": {
            "ticket_id": "abc",
            "user_id": 42,
            "status": "rejected",
            # decision_comment omitted
        },
    }

    bot = MagicMock()
    bot.send_message = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_user(telegram_id=123))
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(
        event, bot=bot, academic_client=academic_client, send_queue=send_queue
    )

    assert len(captured) == 1
    await captured[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs["text"]
    assert "отклонён" in text
    assert "без комментария" in text


@pytest.mark.asyncio
async def test_missing_user_id_skips_send_gracefully():
    """T-59-06-03: payload without user_id → no crash, no send_queue.put call."""
    event = {
        "event_type": "excuse.decided",
        "payload": {"status": "approved", "ticket_id": "abc"},
    }

    bot = MagicMock()
    bot.send_message = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock()
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(
        event, bot=bot, academic_client=academic_client, send_queue=send_queue
    )

    assert captured == []
    bot.send_message.assert_not_called()
    academic_client.get_user_by_id.assert_not_called()


@pytest.mark.asyncio
async def test_unknown_status_skips_send():
    """Unexpected status string → logged and ignored (no send_queue.put)."""
    event = {
        "event_type": "excuse.decided",
        "payload": {"user_id": 42, "status": "pending", "ticket_id": "abc"},
    }

    bot = MagicMock()
    bot.send_message = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock()
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(
        event, bot=bot, academic_client=academic_client, send_queue=send_queue
    )

    assert captured == []
    academic_client.get_user_by_id.assert_not_called()


@pytest.mark.asyncio
async def test_student_without_telegram_id_skips_send():
    """Student has no linked Telegram (telegram_id=0) → graceful skip."""
    event = {
        "event_type": "excuse.decided",
        "payload": {"user_id": 42, "status": "approved", "ticket_id": "abc"},
    }

    bot = MagicMock()
    bot.send_message = AsyncMock()
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_user(telegram_id=0))
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(
        event, bot=bot, academic_client=academic_client, send_queue=send_queue
    )

    assert captured == []
    bot.send_message.assert_not_called()
