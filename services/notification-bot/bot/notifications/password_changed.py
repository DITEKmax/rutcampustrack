"""Notify an account's Telegram after a committed password change."""

from __future__ import annotations

import logging

from aiogram import Bot
from bot.services.notification_prefs import NotificationPreferencesUnavailable

logger = logging.getLogger(__name__)


async def handle_password_changed(event: dict, *, bot: Bot, credential_cards=None) -> None:
    payload = event.get("payload")
    if not isinstance(payload, dict):
        logger.warning("password.changed event malformed: payload_type=%s", type(payload).__name__)
        raise ValueError("password event payload malformed")

    version = event.get("event_version", 1)
    if type(version) is not int:
        raise ValueError("password event version malformed")
    if version == 1:
        # Telegram-only legacy metadata cannot identify an original account after rebind.
        # Never fabricate UID/generation or redirect an old notice to the new binding.
        logger.warning("Legacy password event lacks original cleanup identity")
        return
    if version != 2:
        raise ValueError("password event version unsupported")
    identity = (payload.get("user_id"), payload.get("credentials_version"), payload.get("telegram_binding_version"))
    if any(type(value) is not int or value <= 0 for value in identity):
        raise ValueError("password event identity malformed")
    telegram_id = payload.get("telegram_id")
    if telegram_id is not None and (type(telegram_id) is not int or telegram_id <= 0):
        raise ValueError("password event recipient malformed")
    import uuid

    event_id = event.get("event_id")
    try:
        if not isinstance(event_id, str) or str(uuid.UUID(event_id)) != event_id:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise ValueError("password event identifier malformed") from None
    if credential_cards is None:
        raise NotificationPreferencesUnavailable("Password credential cleanup capability unavailable")
    user_id, credentials_version, binding_version = identity
    await credential_cards.cleanup(user_id=user_id, credentials_version=credentials_version, event_id=event_id)
    await credential_cards.notify_change(
        user_id=user_id, credentials_version=credentials_version,
        binding_version=binding_version, telegram_id=telegram_id,
    )
