"""Settings UI uses atomic authority commands and never reports an unavailable mutation as saved."""

from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.handlers.prefs import (
    cb_mute_clear,
    cb_mute_day,
    cb_toggle_category,
    cb_toggle_global,
    cmd_open_settings,
)
from bot.services.notification_prefs import CATEGORIES, NotificationPreferencesUnavailable


def snapshot(global_enabled=True, homework=False):
    return SimpleNamespace(
        global_enabled=global_enabled, categories={c: c != "homework" or homework for c in CATEGORIES}, muted_until=None
    )


def callback(data):
    return SimpleNamespace(
        id="callback-100",
        data=data,
        from_user=SimpleNamespace(id=100),
        answer=AsyncMock(),
        message=SimpleNamespace(edit_text=AsyncMock()),
    )


@pytest.mark.parametrize(
    "handler,data,operation,fields",
    [
        (cb_toggle_global, "prefs:global:toggle", "TOGGLE_GLOBAL", {}),
        (cb_toggle_category, "prefs:cat:homework", "TOGGLE_CATEGORY", {"category": "homework"}),
        (cb_mute_day, "prefs:mute:day", "MUTE_FOR", {"durationSeconds": 86400}),
        (cb_mute_clear, "prefs:mute:clear", "CLEAR_MUTE", {}),
    ],
)
async def test_callback_passes_stable_identity_and_uses_server_snapshot(handler, data, operation, fields):
    from datetime import datetime, timezone

    returned = snapshot(global_enabled=operation != "TOGGLE_GLOBAL", homework=False)
    if operation == "MUTE_FOR":
        returned.muted_until = datetime(2030, 1, 2, tzinfo=timezone.utc)
    client = SimpleNamespace(command=AsyncMock(return_value=returned), get_snapshot=AsyncMock())
    cb = callback(data)
    await handler(cb, client)
    client.command.assert_awaited_once_with(100, "callback-100", operation, **fields)
    client.get_snapshot.assert_not_awaited()
    cb.message.edit_text.assert_awaited_once()
    text = cb.message.edit_text.await_args.args[0]
    buttons = {
        b.callback_data: b.text
        for row in cb.message.edit_text.await_args.kwargs["reply_markup"].inline_keyboard
        for b in row
    }
    assert ("глобально выключены" in text) == (not returned.global_enabled)
    assert ("prefs:mute:clear" in buttons) == (returned.muted_until is not None)
    assert ("Пауза: нет" in text) == (returned.muted_until is None)
    if returned.global_enabled:
        assert buttons["prefs:cat:homework"].startswith("⬜")
        assert buttons["prefs:cat:reminders"].startswith("✅")
    else:
        assert not any(key.startswith("prefs:cat:") for key in buttons)
    cb.answer.assert_awaited_once()


async def test_callback_outage_does_not_show_enabled_default_or_success():
    client = SimpleNamespace(
        command=AsyncMock(side_effect=NotificationPreferencesUnavailable()), get_snapshot=AsyncMock()
    )
    cb = callback("prefs:global:toggle")
    await cb_toggle_global(cb, client)
    cb.message.edit_text.assert_not_awaited()
    assert cb.answer.await_args.kwargs["show_alert"] is True
    assert "недоступны" in cb.answer.await_args.args[0]


async def test_open_settings_outage_has_no_default_menu():
    client = SimpleNamespace(get_snapshot=AsyncMock(side_effect=NotificationPreferencesUnavailable()))
    message = SimpleNamespace(from_user=SimpleNamespace(id=100), answer=AsyncMock())
    await cmd_open_settings(message, client)
    assert "недоступны" in message.answer.await_args.args[0]
    assert "reply_markup" not in message.answer.await_args.kwargs


async def test_unknown_category_does_not_mutate():
    client = SimpleNamespace(command=AsyncMock())
    cb = callback("prefs:cat:unknown")
    await cb_toggle_category(cb, client)
    client.command.assert_not_awaited()
    cb.answer.assert_awaited_once_with("Неизвестная категория")
