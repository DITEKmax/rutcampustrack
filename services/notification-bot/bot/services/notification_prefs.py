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
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

# Категории уведомлений должны быть синхронизированы с PWA
# (frontends/pwa/src/features/notifications/notificationPrefs.ts) —
# оба клиента должны показывать один набор и использовать одинаковые ключи.
CATEGORIES: tuple[str, ...] = (
    "lessons",      # lesson.started, lesson.cancelled
    "homework",     # homework.published, homework.updated
    "tickets",      # excuse.*, late_checkin.*
    "schedule",     # lesson.one_off.*
    "group",        # group.renamed, group.archived
    "reminders",    # NOTIF-02/NOTIF-03 — напоминания в середине/конце пары
)

# Ивент → категория. None означает «категория неприменима», отправляем
# без фильтрации по категории (но глобальный toggle всё равно работает).
_EVENT_CATEGORY: dict[str, str] = {
    "lesson.started": "lessons",
    "lesson.cancelled": "lessons",
    "lesson.one_off.created": "schedule",
    "lesson.one_off.cancelled": "schedule",
    "homework.published": "homework",
    "homework.updated": "homework",
    "excuse.requested": "tickets",
    "excuse.decided": "tickets",
    "late_checkin.requested": "tickets",
    "late_checkin.decided": "tickets",
    "group.renamed": "group",
    "group.archived": "group",
    "attendance.reminder": "reminders",
}


def category_for_event(event_type: str) -> Optional[str]:
    """Возвращает категорию для event_type или None, если неизвестен."""
    return _EVENT_CATEGORY.get(event_type)


class NotificationPrefsClient:
    _GLOBAL_PREFIX = "bot:notif:"
    _CATEGORY_PREFIX = "bot:notif:cat:"

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

    async def is_enabled(self, telegram_id: Optional[int], category: Optional[str] = None) -> bool:
        """True, если сообщение должно быть отправлено пользователю.

        telegram_id=None — системное сообщение (без пользователя), всегда True.
        category=None — событие без категории, проверяем только глобальный toggle.
        """
        if telegram_id is None:
            return True
        try:
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

    async def close(self) -> None:
        await self._redis.aclose()
