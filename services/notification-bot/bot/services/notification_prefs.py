"""Typed private Notification authority client; no Redis preference reads in the bot."""
from __future__ import annotations
import asyncio
import base64
import re
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Optional
import aiohttp

CATEGORIES = ("lessons", "homework", "tickets", "schedule", "group", "reminders")
_EVENT_CATEGORY = {
    "lesson.started": "reminders", "lesson.reminder": "reminders",
    "lesson.cancelled": "lessons", "lesson.closed": "lessons", "lesson.blocked": "lessons",
    "lesson.one_off.created": "schedule", "lesson.one_off.cancelled": "schedule",
    "homework.published": "homework", "homework.updated": "homework",
    "homework.weekly_digest": "homework", "homework.due_reminder": "homework",
    "excuse.requested": "tickets", "excuse.decided": "tickets",
    "late_checkin.requested": "tickets", "late_checkin.decided": "tickets",
    "attendance.marked": "tickets", "group.renamed": "group", "group.archived": "group",
}

def category_for_event(event_type: str) -> Optional[str]:
    return _EVENT_CATEGORY.get(event_type)

class NotificationPreferencesUnavailable(RuntimeError):
    """Retain the original event/task; unknown is neither enabled nor disabled."""

class NotificationBindingMismatch(RuntimeError):
    """Known stale/unbound identity must never deliver to another account."""

@dataclass(frozen=True)
class PreferencesSnapshot:
    user_id: int
    telegram_id: int
    global_enabled: bool
    categories: dict[str, bool]
    muted_until: Optional[datetime]
    canonical_categories: dict[str, bool]
    canonical_muted_until: Optional[datetime]
    eligible: Optional[bool]

def canonical_token(token: str) -> bool:
    if not re.fullmatch(r"[A-Za-z0-9_-]{43}", token):
        return False
    decoded = base64.urlsafe_b64decode(token + "=")
    return len(decoded) == 32 and base64.urlsafe_b64encode(decoded).decode().rstrip("=") == token

class NotificationPrefsClient:
    def __init__(self, base_url: str, token: str, academic_client, session=None) -> None:
        if not canonical_token(token):
            raise ValueError("BOT_TO_NOTIFICATION_SERVICE_TOKEN must be canonical32byte base64url")
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._academic = academic_client
        self._session = session

    async def _identity(self, telegram_id: int, user_id: Optional[int]) -> int:
        if type(telegram_id) is not int or telegram_id <= 0:
            raise NotificationBindingMismatch("Positive Telegram identity is required")
        try:
            bound = await self._academic.get_user_by_telegram_id(telegram_id)
        except Exception as error:
            raise NotificationPreferencesUnavailable("Binding authority is unavailable") from error
        if not bound or not bound.found or bound.user_id <= 0:
            raise NotificationBindingMismatch("Telegram account is not bound")
        if user_id is not None and (type(user_id) is not int or bound.user_id != user_id):
            raise NotificationBindingMismatch("Telegram binding changed")
        return bound.user_id

    async def _request(self, telegram_id: int, user_id: Optional[int], category: Optional[str], body=None) -> PreferencesSnapshot:
        uid = await self._identity(telegram_id, user_id)
        if self._session is None:
            self._session = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=8))
        url = f"{self._base_url}/internal/bot/notification-preferences/{uid}/{telegram_id}"
        for attempt in range(3):
            try:
                async with self._session.request("GET" if body is None else "POST", url,
                        headers={"X-Bot-Preferences-Token": self._token},
                        params={"category": category} if category is not None else None, json=body) as response:
                    if response.status == 409 and body is None:
                        raise NotificationBindingMismatch("Telegram binding does not match")
                    if response.status != 200:
                        raise NotificationPreferencesUnavailable("Notification preferences are unavailable")
                    return _snapshot(await response.json(), uid, telegram_id, category)
            except NotificationBindingMismatch:
                raise
            except (aiohttp.ClientError, asyncio.TimeoutError, NotificationPreferencesUnavailable, ValueError, TypeError) as error:
                if attempt == 2:
                    raise NotificationPreferencesUnavailable("Notification preferences are unavailable") from error
                await asyncio.sleep(0.1 * (attempt + 1))
        raise NotificationPreferencesUnavailable("Notification preferences are unavailable")

    async def get_snapshot(self, telegram_id: int, user_id: Optional[int] = None) -> PreferencesSnapshot:
        return await self._request(telegram_id, user_id, None)

    async def is_enabled(self, telegram_id: Optional[int], category: Optional[str] = None,
                         user_id: Optional[int] = None) -> bool:
        if category not in CATEGORIES:
            raise NotificationPreferencesUnavailable("A classified notification category is required")
        try:
            return (await self._request(telegram_id, user_id, category)).eligible is True
        except NotificationBindingMismatch:
            return False

    async def command(self, telegram_id: int, request_key: str, operation: str, **fields) -> PreferencesSnapshot:
        if not isinstance(request_key, str) or not re.fullmatch(r"[A-Za-z0-9_/:-]{1,128}", request_key):
            raise ValueError("Stable preference request identity is required")
        return await self._request(telegram_id, None, None,
                                   {"requestKey": request_key, "operation": operation, **fields})

    async def toggle_global(self, telegram_id: int, request_key: str) -> PreferencesSnapshot:
        return await self.command(telegram_id, request_key, "TOGGLE_GLOBAL")

    async def toggle_category(self, telegram_id: int, category: str, request_key: str) -> PreferencesSnapshot:
        return await self.command(telegram_id, request_key, "TOGGLE_CATEGORY", category=category)

    async def mute_for(self, telegram_id: int, duration: timedelta, request_key: str) -> PreferencesSnapshot:
        seconds = int(duration.total_seconds())
        if seconds not in (86400, 604800):
            raise ValueError("Unsupported mute duration")
        return await self.command(telegram_id, request_key, "MUTE_FOR", durationSeconds=seconds)

    async def clear_mute(self, telegram_id: int, request_key: str) -> PreferencesSnapshot:
        return await self.command(telegram_id, request_key, "CLEAR_MUTE")

    async def close(self) -> None:
        if self._session is not None:
            await self._session.close()

def _snapshot(raw: object, user_id: int, telegram_id: int, category: Optional[str]) -> PreferencesSnapshot:
    if not isinstance(raw, dict) or type(raw.get("userId")) is not int or raw["userId"] != user_id \
            or type(raw.get("telegramId")) is not int or raw["telegramId"] != telegram_id \
            or type(raw.get("globalEnabled")) is not bool:
        raise NotificationPreferencesUnavailable("Invalid preference response identity")
    def flags(name):
        value = raw.get(name)
        if not isinstance(value, dict) or set(value) != set(CATEGORIES) or any(type(v) is not bool for v in value.values()):
            raise NotificationPreferencesUnavailable("Invalid preference response categories")
        return dict(value)
    def instant(name):
        if name not in raw:
            raise NotificationPreferencesUnavailable("Incomplete preference response")
        value = raw[name]
        if value is None:
            return None
        if not isinstance(value, str):
            raise NotificationPreferencesUnavailable("Invalid preference response mute")
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise NotificationPreferencesUnavailable("Invalid preference response mute")
        return parsed.astimezone(timezone.utc)
    eligible = raw.get("eligible")
    if "eligible" not in raw or (category is None and eligible is not None) or (category is not None and type(eligible) is not bool):
        raise NotificationPreferencesUnavailable("Invalid preference eligibility response")
    return PreferencesSnapshot(user_id, telegram_id, raw["globalEnabled"], flags("categories"), instant("mutedUntil"),
                               flags("canonicalCategories"), instant("canonicalMutedUntil"), eligible)
