"""Explicit v2 original identity; legacy never follows a rebound Telegram ID."""

import uuid
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.notifications.password_changed import handle_password_changed
from bot.services.notification_prefs import NotificationPreferencesUnavailable


def _event(telegram=42):
    event = {
        "event_type": "password.changed", "event_version": 2, "event_id": str(uuid.uuid4()),
        "payload": {"user_id": 17, "credentials_version": 3, "telegram_binding_version": 2},
    }
    if telegram is not None:
        event["payload"]["telegram_id"] = telegram
    return event


@pytest.mark.asyncio
@pytest.mark.parametrize("telegram", [42, None])
async def test_v2_cleans_original_uid_before_fresh_notice_even_when_unlinked(telegram):
    operations = []

    async def clean(**kwargs):
        operations.append(("cleanup", kwargs["user_id"], kwargs["credentials_version"]))

    async def notice(**kwargs):
        operations.append(("notice", kwargs["user_id"], kwargs["binding_version"]))

    cards = SimpleNamespace(cleanup=clean, notify_change=notice)
    await handle_password_changed(_event(telegram), bot=SimpleNamespace(), credential_cards=cards)
    assert operations == [("cleanup", 17, 3), ("notice", 17, 2)]


@pytest.mark.asyncio
async def test_cleanup_failure_never_completes_or_notifies_event():
    cards = SimpleNamespace(
        cleanup=AsyncMock(side_effect=NotificationPreferencesUnavailable("synthetic provider outage")),
        notify_change=AsyncMock(),
    )
    with pytest.raises(NotificationPreferencesUnavailable):
        await handle_password_changed(_event(), bot=SimpleNamespace(), credential_cards=cards)
    cards.notify_change.assert_not_awaited()


@pytest.mark.asyncio
async def test_legacy_telegram_only_event_never_fabricates_uid_or_redirects_notice():
    bot = SimpleNamespace(send_message=AsyncMock())
    cards = SimpleNamespace(cleanup=AsyncMock(), notify_change=AsyncMock())
    await handle_password_changed(
        {"event_version": 1, "payload": {"telegram_id": 42}}, bot=bot, credential_cards=cards,
    )
    bot.send_message.assert_not_awaited()
    cards.cleanup.assert_not_awaited()
    cards.notify_change.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("field", ["user_id", "credentials_version", "telegram_binding_version", "telegram_id"])
async def test_boolean_identity_rejected_without_provider_call(field):
    event = _event()
    event["payload"][field] = True
    bot = SimpleNamespace(send_message=AsyncMock())
    with pytest.raises(ValueError):
        await handle_password_changed(event, bot=bot)
    bot.send_message.assert_not_awaited()


@pytest.mark.asyncio
async def test_v2_without_cleanup_dependency_retried_instead_of_acknowledged():
    with pytest.raises(NotificationPreferencesUnavailable):
        await handle_password_changed(_event(), bot=SimpleNamespace())
