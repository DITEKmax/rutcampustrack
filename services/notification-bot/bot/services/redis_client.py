import logging
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class ReminderLifecycleUnavailable(RuntimeError):
    """Reminder state or retirement could not be confirmed and needs broker retry."""


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

    async def add_message_id(
        self, lesson_id: int, user_id: int, message_id: int, *, chat_id: int | None = None, strict=False
    ) -> None:
        key = self._key(lesson_id, user_id)
        value = f"{chat_id}:{message_id}" if chat_id is not None else message_id
        try:
            if strict:
                if await self._redis.lpos(key, value) is not None:
                    return
                async with self._redis.pipeline(transaction=True) as pipeline:
                    await pipeline.rpush(key, value).expire(key, self._ttl).execute()
            else:
                await self._redis.rpush(key, value)
                await self._redis.expire(key, self._ttl)
        except Exception as error:
            if strict:
                raise ReminderLifecycleUnavailable("Unable to track reminder") from error
            logger.exception(
                "Redis error storing reminder message_id for lesson=%d user=%d",
                lesson_id,
                user_id,
            )

    async def get_message_ids(self, lesson_id: int, user_id: int) -> list[int]:
        return [message_id for _, message_id in await self.get_messages(lesson_id, user_id)]

    async def get_messages(self, lesson_id: int, user_id: int, *, strict=False) -> list[tuple[int | None, int]]:
        key = self._key(lesson_id, user_id)
        try:
            raw = await self._redis.lrange(key, 0, -1)
            messages = []
            for stored in raw:
                value = stored.decode() if isinstance(stored, bytes) else stored
                chat_id, separator, message_id = value.partition(":")
                messages.append((int(chat_id), int(message_id)) if separator else (None, int(value)))
            return messages
        except Exception as error:
            if strict:
                raise ReminderLifecycleUnavailable("Unable to read tracked reminders") from error
            logger.exception(
                "Redis error reading reminder message_ids for lesson=%d user=%d",
                lesson_id,
                user_id,
            )
            return []

    async def remove_message_id(self, lesson_id: int, user_id: int, message_id: int, *, chat_id: int | None) -> None:
        value = f"{chat_id}:{message_id}" if chat_id is not None else message_id
        try:
            await self._redis.lrem(self._key(lesson_id, user_id), 0, value)
        except Exception as error:
            raise ReminderLifecycleUnavailable("Unable to retire tracked reminder") from error

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

    async def mark_student_marked(
        self, lesson_id: int, user_id: int, status: str | None = None, *, strict=False
    ) -> None:
        key = self._marked_key(lesson_id, user_id)
        try:
            await self._redis.set(key, status or "marked", ex=self._ttl)
        except Exception as error:
            if strict:
                raise ReminderLifecycleUnavailable("Unable to store marked student") from error
            logger.exception(
                "Redis error storing reminder marked state for lesson=%d user=%d",
                lesson_id,
                user_id,
            )

    async def is_student_marked(self, lesson_id: int, user_id: int, *, strict=False) -> bool:
        key = self._marked_key(lesson_id, user_id)
        try:
            return await self._redis.get(key) is not None
        except Exception as error:
            if strict:
                raise ReminderLifecycleUnavailable("Unable to read marked student") from error
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
