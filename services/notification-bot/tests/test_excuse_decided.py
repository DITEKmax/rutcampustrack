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
    """The private decision is delivered to its student with the factual outcome."""
    event = _load_fixture()  # fixture from 59-05 is status=approved
    assert event["payload"]["status"] == "approved"

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))
    academic_client = MagicMock()
    academic_client.get_user_by_id = AsyncMock(return_value=_make_user(telegram_id=55555))
    send_queue, captured = _capturing_send_queue()

    await handle_student_alert(event, bot=bot, academic_client=academic_client, send_queue=send_queue)

    assert len(captured) == 1
    task = captured[0]
    assert task.user_id == event["payload"]["user_id"]
    assert task.chat_id == 55555

    # Execute the coroutine factory to verify the actual bot call
    await task.coroutine_factory()
    kwargs = bot.send_message.call_args.kwargs
    assert kwargs["chat_id"] == 55555
    text = kwargs["text"]
    assert "одобрен" in text
    assert kwargs["parse_mode"] == "HTML"
    # decision_comment from fixture should be present
    assert event["payload"]["decision_comment"] in text


@pytest.mark.asyncio
async def test_rejected_event_sends_ru_message_with_comment():
    """The student's rejection retains the actual decision comment."""
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

    await handle_student_alert(event, bot=bot, academic_client=academic_client, send_queue=send_queue)

    assert len(captured) == 1
    await captured[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs["text"]
    assert "отклонена" in text
    assert "документы не прикреплены" in text
    academic_client.get_user_by_id.assert_awaited_once_with(42)


@pytest.mark.asyncio
async def test_rejected_event_without_comment_does_not_invent_a_reason():
    """A missing decision comment never becomes an invented reason."""
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

    await handle_student_alert(event, bot=bot, academic_client=academic_client, send_queue=send_queue)

    assert len(captured) == 1
    await captured[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs["text"]
    assert "отклонена" in text
    assert "Причина:" not in text


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

    await handle_student_alert(event, bot=bot, academic_client=academic_client, send_queue=send_queue)

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

    await handle_student_alert(event, bot=bot, academic_client=academic_client, send_queue=send_queue)

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

    await handle_student_alert(event, bot=bot, academic_client=academic_client, send_queue=send_queue)

    assert captured == []
    bot.send_message.assert_not_called()


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("extra", "expected", "absent"),
    [
        (
            {
                "excluded_lessons": [
                    {"lesson_id": 12, "date": "2026-10-07", "lesson_number": 3, "subject_name": "<b>Математика</b>"}
                ]
            },
            ["частично", "07.10.2026", "Пара №3", "&lt;b&gt;Математика&lt;/b&gt;"],
            ["<b>Математика</b>"],
        ),
        ({"excluded_lesson_ids": [12]}, ["частично", "Исключено занятий: 1"], ["Пара ID 12"]),
        ({"excluded_occurrence_ids": ["102"]}, ["частично", "Исключено занятий: 1"], ["102"]),
        (
            {
                "excluded_lessons": [
                    {"lesson_id": 12, "date": "bad", "lesson_number": "bad"},
                    None,
                    True,
                    {"lesson_id": 15, "subject_name": "Hidden fourth"},
                    16,
                ]
            },
            ["частично", "Исключено занятий: 5", "Ещё исключено: 2"],
            ["Пара ID 12", "Hidden fourth", "bad"],
        ),
        (
            {"excluded_lessons": "invalid", "excluded_lesson_ids": [12]},
            ["частично", "Исключено занятий: 1"],
            ["invalid", "Пара ID 12"],
        ),
        ({"excluded_lessons": []}, ["Уважительная причина одобрена"], ["частично", "Исключённые пары"]),
    ],
)
async def test_partial_approval_escapes_facts_hides_identifiers_and_reports_display_limit(extra, expected, absent):
    event = {
        "event_type": "excuse.decided",
        "payload": {
            "user_id": 42,
            "status": "approved",
            "decision_comment": "<b>Комментарий</b>",
            **extra,
        },
    }
    bot = MagicMock(send_message=AsyncMock())
    academic = MagicMock(get_user_by_id=AsyncMock(return_value=_make_user(123)))
    queue, captured = _capturing_send_queue()

    await handle_student_alert(event, bot=bot, academic_client=academic, send_queue=queue)

    assert len(captured) == 1
    assert captured[0].user_id == 42
    await captured[0].coroutine_factory()
    kwargs = bot.send_message.call_args.kwargs
    assert kwargs["parse_mode"] == "HTML"
    assert "&lt;b&gt;Комментарий&lt;/b&gt;" in kwargs["text"]
    assert "<b>Комментарий</b>" not in kwargs["text"]
    assert all(value in kwargs["text"] for value in expected)
    assert all(value not in kwargs["text"] for value in absent)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "reason", "expected"),
    [
        ("approved", "present_priority", "уже стоит отметка присутствия"),
        ("rejected", "", "отклонена"),
        ("cancelled", "geo_confirmed", "Присутствие подтверждено по геолокации"),
    ],
)
async def test_late_decision_does_not_treat_excluded_metadata_as_partial(status, reason, expected):
    event = {
        "event_type": "late_checkin.decided",
        "payload": {
            "user_id": 42,
            "status": status,
            "resolution_reason": reason,
            "excluded_lessons": [12],
        },
    }
    bot = MagicMock(send_message=AsyncMock())
    academic = MagicMock(get_user_by_id=AsyncMock(return_value=_make_user(123)))
    queue, captured = _capturing_send_queue()

    await handle_student_alert(event, bot=bot, academic_client=academic, send_queue=queue)

    await captured[0].coroutine_factory()
    kwargs = bot.send_message.call_args.kwargs
    assert expected in kwargs["text"]
    assert "частично" not in kwargs["text"]
    assert kwargs["parse_mode"] == "HTML"
