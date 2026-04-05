import json
import logging
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class JwtRedisClient:
    """Stores JWT token pairs in Redis keyed by telegram_id.

    Key: bot:jwt:{telegram_id}
    Value: JSON string {"access_token": "...", "refresh_token": "..."}
    TTL: matches refresh token expiry (per D-07)
    """

    def __init__(
        self,
        key_prefix: str = "bot:jwt:",
        ttl: int = 604800,
        host: str = "redis",
        port: int = 6379,
        redis_client: Optional[aioredis.Redis] = None,
    ) -> None:
        if redis_client is not None:
            self._redis = redis_client
        else:
            url = f"redis://{host}:{port}"
            self._redis = aioredis.from_url(url, max_connections=10, decode_responses=True)
        self._key_prefix = key_prefix
        self._ttl = ttl

    def _key(self, telegram_id: int) -> str:
        return f"{self._key_prefix}{telegram_id}"

    async def save(
        self, telegram_id: int, access_token: str, refresh_token: str, expires_in: int
    ) -> None:
        """Save JWT pair. TTL = max(expires_in, self._ttl) to keep refresh token alive."""
        key = self._key(telegram_id)
        data = json.dumps({
            "access_token": access_token,
            "refresh_token": refresh_token,
        })
        # Use the larger of expires_in and default ttl to keep refresh token alive
        ttl = max(expires_in, self._ttl) if expires_in > 0 else self._ttl
        try:
            await self._redis.set(key, data, ex=ttl)
        except Exception:
            logger.exception("Redis error saving JWT for telegram_id=%d", telegram_id)

    async def get(self, telegram_id: int) -> dict | None:
        """Return {"access_token": ..., "refresh_token": ...} or None."""
        key = self._key(telegram_id)
        try:
            raw = await self._redis.get(key)
            return json.loads(raw) if raw else None
        except Exception:
            logger.exception("Redis error reading JWT for telegram_id=%d", telegram_id)
            return None

    async def delete(self, telegram_id: int) -> None:
        """Remove stored JWT (e.g., on token expiry or explicit logout)."""
        key = self._key(telegram_id)
        try:
            await self._redis.delete(key)
        except Exception:
            logger.exception("Redis error deleting JWT for telegram_id=%d", telegram_id)

    async def close(self) -> None:
        await self._redis.aclose()
