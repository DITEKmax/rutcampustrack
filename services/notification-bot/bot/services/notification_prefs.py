"""Per-user notification on/off flag stored in Redis.

Key format: bot:notif:{telegram_id} = "off" (presence == disabled).
Default state (key absent) = notifications ON.
"""

import logging
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class NotificationPrefsClient:
    _KEY_PREFIX = "bot:notif:"

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

    def _key(self, telegram_id: int) -> str:
        return f"{self._KEY_PREFIX}{telegram_id}"

    async def is_enabled(self, telegram_id: Optional[int]) -> bool:
        if telegram_id is None:
            return True
        try:
            value = await self._redis.get(self._key(telegram_id))
            return value != "off"
        except Exception:
            logger.exception("Redis error reading notif pref for telegram_id=%s", telegram_id)
            return True  # fail-open — лучше прислать, чем потерять

    async def disable(self, telegram_id: int) -> None:
        await self._redis.set(self._key(telegram_id), "off")

    async def enable(self, telegram_id: int) -> None:
        await self._redis.delete(self._key(telegram_id))

    async def close(self) -> None:
        await self._redis.aclose()
