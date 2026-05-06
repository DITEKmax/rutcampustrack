import logging
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class ReminderRedisClient:
    def __init__(
        self,
        key_template: str,
        ttl: int,
        host: str = "redis",
        port: int = 6379,
        password: str = "",
        redis_client: Optional[aioredis.Redis] = None,
        marked_key_template: str = "reminder:marked:{lesson_id}:{user_id}",
    ) -> None:
        if redis_client is not None:
            self._redis = redis_client
        else:
            auth = f":{password}@" if password else ""
            url = f"redis://{auth}{host}:{port}"
            self._redis = aioredis.from_url(url, max_connections=10, decode_responses=True)
        self._key_template = key_template
        self._marked_key_template = marked_key_template
        self._ttl = ttl

    def _key(self, lesson_id: int, user_id: int) -> str:
        return self._key_template.format(lesson_id=lesson_id, user_id=user_id)

    def _marked_key(self, lesson_id: int, user_id: int) -> str:
        return self._marked_key_template.format(lesson_id=lesson_id, user_id=user_id)

    async def add_message_id(self, lesson_id: int, user_id: int, message_id: int) -> None:
        key = self._key(lesson_id, user_id)
        try:
            await self._redis.rpush(key, message_id)
            await self._redis.expire(key, self._ttl)
        except Exception:
            logger.exception(
                "Redis error storing reminder message_id for lesson=%d user=%d",
                lesson_id,
                user_id,
            )

    async def get_message_ids(self, lesson_id: int, user_id: int) -> list[int]:
        key = self._key(lesson_id, user_id)
        try:
            raw = await self._redis.lrange(key, 0, -1)
            return [int(v) for v in raw]
        except Exception:
            logger.exception(
                "Redis error reading reminder message_ids for lesson=%d user=%d",
                lesson_id,
                user_id,
            )
            return []

    async def delete_key(self, lesson_id: int, user_id: int) -> None:
        key = self._key(lesson_id, user_id)
        try:
            await self._redis.delete(key)
        except Exception:
            logger.exception(
                "Redis error deleting reminder key for lesson=%d user=%d",
                lesson_id,
                user_id,
            )

    async def mark_student_marked(self, lesson_id: int, user_id: int, status: str | None = None) -> None:
        key = self._marked_key(lesson_id, user_id)
        try:
            await self._redis.set(key, status or "marked", ex=self._ttl)
        except Exception:
            logger.exception(
                "Redis error storing reminder marked state for lesson=%d user=%d",
                lesson_id,
                user_id,
            )

    async def is_student_marked(self, lesson_id: int, user_id: int) -> bool:
        key = self._marked_key(lesson_id, user_id)
        try:
            return await self._redis.get(key) is not None
        except Exception:
            logger.exception(
                "Redis error reading reminder marked state for lesson=%d user=%d",
                lesson_id,
                user_id,
            )
            return False

    async def delete_marked_key(self, lesson_id: int, user_id: int) -> None:
        key = self._marked_key(lesson_id, user_id)
        try:
            await self._redis.delete(key)
        except Exception:
            logger.exception(
                "Redis error deleting reminder marked state for lesson=%d user=%d",
                lesson_id,
                user_id,
            )

    async def close(self) -> None:
        await self._redis.aclose()
