"""M04 Группа 9 — тесты для alert_fired handler."""

from __future__ import annotations

import os
from unittest.mock import AsyncMock, MagicMock

import pytest

os.environ["OTEL_SDK_DISABLED"] = "true"

from bot.notifications.alert_fired import (  # noqa: E402
    _format_message,
    _parse_admin_ids,
    handle_alert_fired,
)


def test_parse_admin_ids_mixed():
    assert _parse_admin_ids("123, 456") == [123, 456]
    assert _parse_admin_ids("0") == []
    assert _parse_admin_ids("") == []
    assert _parse_admin_ids(None) == []
    assert _parse_admin_ids("abc,123,-5,0,99") == [123, 99]


def test_format_message_critical():
    msg = _format_message(
        {
            "name": "ServiceDown",
            "severity": "critical",
            "status": "firing",
            "summary": "auth-service down",
            "description": "scrape failed 1m",
        }
    )
    assert "ServiceDown" in msg
    assert "CRITICAL" in msg
    assert "🔔" in msg
    assert "🔴" in msg
    assert "auth-service down" in msg
    assert "scrape failed 1m" in msg


def test_format_message_resolved():
    msg = _format_message(
        {
            "name": "OutboxLagHigh",
            "severity": "warning",
            "status": "resolved",
        }
    )
    assert "✅" in msg
    assert "🟡" in msg
    assert "WARNING" in msg


def test_format_message_truncates_long_description():
    msg = _format_message(
        {
            "name": "Foo",
            "severity": "warning",
            "status": "firing",
            "description": "x" * 5000,
        }
    )
    # 3500 char cap + suffix должен сработать
    assert len(msg) < 5000
    assert "[truncated]" in msg


def test_format_message_escapes_html():
    msg = _format_message(
        {
            "name": "Test<script>",
            "severity": "warning",
            "status": "firing",
            "summary": "<img src=x>",
            "description": "alert & more",
        }
    )
    # bold tags мы пишем сами, но юзерские значения должны быть escape'нуты
    assert "<script>" not in msg
    assert "&lt;script&gt;" in msg
    assert "&lt;img" in msg
    assert "alert &amp; more" in msg


@pytest.mark.asyncio
async def test_handle_alert_fired_enqueues_one_per_admin():
    bot = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()
    event = {
        "event_type": "alert.fired",
        "payload": {
            "name": "ServiceDown",
            "severity": "critical",
            "status": "firing",
            "summary": "x",
            "description": "y",
        },
    }
    await handle_alert_fired(event, bot=bot, send_queue=send_queue, admin_ids=[111, 222])
    assert send_queue.put.await_count == 2
    # chat_id'ы в двух отдельных SendTask'ах
    tasks = [call.args[0] for call in send_queue.put.await_args_list]
    assert {t.chat_id for t in tasks} == {111, 222}


@pytest.mark.asyncio
async def test_handle_alert_fired_no_admins_dropped():
    bot = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()
    event = {"payload": {"name": "Foo", "severity": "warning", "status": "firing"}}
    await handle_alert_fired(event, bot=bot, send_queue=send_queue, admin_ids=[])
    send_queue.put.assert_not_called()


@pytest.mark.asyncio
async def test_handle_alert_fired_missing_payload_graceful():
    bot = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()
    # payload отсутствует — handler не должен падать, просто молча доставит
    # сообщение со значениями по умолчанию.
    await handle_alert_fired({}, bot=bot, send_queue=send_queue, admin_ids=[111])
    assert send_queue.put.await_count == 1
