"""Per-user notification preferences for the Telegram bot.

Две модели подписки уживаются вместе по обратной совместимости:

1. **Глобальный toggle** (legacy): ключ ``bot:notif:{telegram_id}``.
   ``"off"`` в значении → все уведомления от бота выключены.

2. **Per-category toggles** (new): hash ``bot:notif:cat:{telegram_id}``,
   поле на категорию, значение ``"off"``. Отсутствие поля (или любое
   значение кроме ``"off"``) означает «категория включена». Категории
   перечислены в :data:`CATEGORIES`.

Решение о том, уходит ли сообщение в TG, принимается в два этапа:

* если глобально выключено — не шлём вне зависимости от категорий;
* если категория явно выключена — не шлём;
* иначе шлём.

Такой подход позволяет: (а) пользователям со старыми настройками
ничего не терять, (б) быстро выключить всё разом одной кнопкой.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

# Категории уведомлений должны быть синхронизированы с PWA
# (frontends/pwa/src/features/notifications/notificationPrefs.ts) —
# оба клиента должны показывать один набор и использовать одинаковые ключи.
CATEGORIES: tuple[str, ...] = (
    "lessons",  # lesson.started, lesson.cancelled
    "homework",  # homework.published, homework.updated
    "tickets",  # excuse.*, late_checkin.*
    "schedule",  # lesson.one_off.*
    "group",  # group.renamed, group.archived
    "reminders",  # NOTIF-02/NOTIF-03 — напоминания в середине/конце пары
)

# Ивент → категория. None означает «категория неприменима», отправляем
# без фильтрации по категории (но глобальный toggle всё равно работает).
_EVENT_CATEGORY: dict[str, str] = {
    "lesson.started": "reminders",
    "lesson.reminder": "reminders",
    "lesson.cancelled": "lessons",
    "lesson.one_off.created": "schedule",
    "lesson.one_off.cancelled": "schedule",
    "homework.published": "homework",
    "homework.updated": "homework",
    "excuse.requested": "tickets",
    "excuse.decided": "tickets",
    "late_checkin.requested": "tickets",
    "late_checkin.decided": "tickets",
    "attendance.marked": "tickets",
    "group.renamed": "group",
    "group.archived": "group",
}


def category_for_event(event_type: str) -> Optional[str]:
    """Возвращает категорию для event_type или None, если неизвестен."""
    return _EVENT_CATEGORY.get(event_type)


class NotificationPrefsClient:
    _GLOBAL_PREFIX = "bot:notif:"
    _CATEGORY_PREFIX = "bot:notif:cat:"
    _TELEGRAM_MUTE_PREFIX = "bot:notif:mute:"
    _USER_PREF_PREFIX = "notif:prefs:user:"
    _MUTE_UNTIL_FIELD = "mute_until"

    def __init__(
        self,
        host: str = "redis",
        port: int = 6379,
        password: str = "",
        redis_client: Optional[aioredis.Redis] = None,
    ) -> None:
        if redis_client is not None:
            self._redis = redis_client
        else:
            auth = f":{password}@" if password else ""
            url = f"redis://{auth}{host}:{port}"
            self._redis = aioredis.from_url(url, max_connections=10, decode_responses=True)

    def _global_key(self, telegram_id: int) -> str:
        return f"{self._GLOBAL_PREFIX}{telegram_id}"

    def _category_key(self, telegram_id: int) -> str:
        return f"{self._CATEGORY_PREFIX}{telegram_id}"

    def _telegram_mute_key(self, telegram_id: int) -> str:
        return f"{self._TELEGRAM_MUTE_PREFIX}{telegram_id}"

    def _user_pref_key(self, user_id: int) -> str:
        return f"{self._USER_PREF_PREFIX}{user_id}"

    async def is_enabled(
        self,
        telegram_id: Optional[int],
        category: Optional[str] = None,
        user_id: Optional[int] = None,
    ) -> bool:
        """True, если сообщение должно быть отправлено пользователю.

        telegram_id=None — системное сообщение (без пользователя), всегда True.
        category=None — событие без категории, проверяем только глобальный toggle.
        """
        if telegram_id is None:
            return True
        try:
            if user_id is not None and not await self._user_preferences_enabled(user_id, category):
                return False
            muted_until = await self.get_muted_until(telegram_id)
            if muted_until is not None and muted_until > _now_utc():
                return False
            global_value = await self._redis.get(self._global_key(telegram_id))
            if global_value == "off":
                return False
            if category is None:
                return True
            cat_value = await self._redis.hget(self._category_key(telegram_id), category)
            return cat_value != "off"
        except Exception:
            logger.exception("Redis error reading notif pref for telegram_id=%s", telegram_id)
            return True  # fail-open — лучше прислать, чем потерять

    async def _user_preferences_enabled(self, user_id: int, category: Optional[str]) -> bool:
        key = self._user_pref_key(user_id)
        muted_raw = await self._redis.hget(key, self._MUTE_UNTIL_FIELD)
        muted_until = _parse_instant(muted_raw)
        if muted_until is not None and muted_until > _now_utc():
            return False
        if category is None:
            return True
        cat_value = await self._redis.hget(key, category)
        return cat_value != "off"

    async def disable(self, telegram_id: int) -> None:
        """Глобально отключить уведомления (legacy API)."""
        await self._redis.set(self._global_key(telegram_id), "off")

    async def enable(self, telegram_id: int) -> None:
        """Глобально включить уведомления (legacy API)."""
        await self._redis.delete(self._global_key(telegram_id))

    async def set_category(self, telegram_id: int, category: str, enabled: bool) -> None:
        """Включить/выключить одну категорию."""
        if category not in CATEGORIES:
            raise ValueError(f"Unknown category: {category}")
        if enabled:
            await self._redis.hdel(self._category_key(telegram_id), category)
        else:
            await self._redis.hset(self._category_key(telegram_id), category, "off")

    async def get_categories(self, telegram_id: int) -> dict[str, bool]:
        """Снимок состояния всех категорий. Отсутствующие = True."""
        try:
            raw = await self._redis.hgetall(self._category_key(telegram_id))
        except Exception:
            logger.exception("Redis error loading categories for telegram_id=%s", telegram_id)
            raw = {}
        return {cat: raw.get(cat) != "off" for cat in CATEGORIES}

    async def is_global_enabled(self, telegram_id: int) -> bool:
        try:
            return await self._redis.get(self._global_key(telegram_id)) != "off"
        except Exception:
            logger.exception("Redis error reading global pref for telegram_id=%s", telegram_id)
            return True

    async def mute_for(self, telegram_id: int, duration: timedelta) -> datetime:
        muted_until = _now_utc() + duration
        await self._redis.set(self._telegram_mute_key(telegram_id), _format_instant(muted_until))
        return muted_until

    async def clear_mute(self, telegram_id: int) -> None:
        await self._redis.delete(self._telegram_mute_key(telegram_id))

    async def get_muted_until(self, telegram_id: int) -> Optional[datetime]:
        try:
            value = await self._redis.get(self._telegram_mute_key(telegram_id))
        except Exception:
            logger.exception("Redis error reading mute pref for telegram_id=%s", telegram_id)
            return None
        muted_until = _parse_instant(value)
        if muted_until is not None and muted_until <= _now_utc():
            await self.clear_mute(telegram_id)
            return None
        return muted_until

    async def close(self) -> None:
        await self._redis.aclose()


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)


def _format_instant(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _parse_instant(value: object) -> Optional[datetime]:
    if not isinstance(value, str) or not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        try:
            return datetime.fromtimestamp(float(value), timezone.utc)
        except (TypeError, ValueError):
            return None
