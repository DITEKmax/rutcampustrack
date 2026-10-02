"""Consumer-side dedup по event_id для notification-bot (M13 G8).

Java-side имеет shared-events `IdempotencyGuard` + `IdempotencyStore`,
тут эквивалент через Redis: ключ `consumed:{consumer_id}:{event_id}`,
TTL 1 час (события старше уже не редоставляются Rabbit'ом).

Использование:
    guard = BotIdempotencyGuard(host="redis", port=6379, password="...")
    if not await guard.try_claim(event_id):
        return  # duplicate, skip
"""

import logging
import secrets
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class BotIdempotencyGuard:
    """Redis-backed dedup для bot consumer'а (M13 G8).

    Атомарный `SET key value NX EX ttl` гарантирует что:
    - первый caller получит claim (return True);
    - все последующие в течение TTL получат False (skip duplicate).

    Если `event_id` отсутствует — пропускаем (return True) с warn'ом.
    Это совместимость с pre-M13 publisher'ами; после M13 G8 все события
    содержат `event_id` (Java `AbstractEventPublisher.fillDefaults`).
    """

    DEFAULT_CONSUMER_ID = "notification-bot"
    # A short lease makes a crashed handler retryable.  Once a handler has
    # completed, ``complete`` upgrades the same key to the DLQ retention TTL.
    DEFAULT_TTL_SECONDS = 300
    DEFAULT_COMPLETION_TTL_SECONDS = 7 * 24 * 60 * 60

    def __init__(
        self,
        consumer_id: str = DEFAULT_CONSUMER_ID,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
        completion_ttl_seconds: int = DEFAULT_COMPLETION_TTL_SECONDS,
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
        self._consumer_id = consumer_id
        self._ttl = ttl_seconds
        self._completion_ttl = max(ttl_seconds, completion_ttl_seconds)

    def _key(self, event_id: str) -> str:
        return f"consumed:{self._consumer_id}:{event_id}"

    async def claim(self, event_id: Optional[str]) -> str | None:
        """Claim an event with a short processing lease.

        Returns an opaque token for the owner, or ``None`` for an event that
        is already processing/completed.  The token is required by
        :meth:`complete` and :meth:`release`, so a late worker cannot mutate a
        lease acquired by a newer delivery.

        - None / blank event_id → ValueError (M13 G24-fix-6 fail-closed).
          После M13 G8 все publisher'ы обязаны заполнять event_id
          (Java AbstractEventPublisher.fillDefaults). Событие без id =
          либо bug в новом publisher'е, либо forged message в обход
          replay-защиты. Caller (event_consumer) делает NACK → DLQ.
        - Redis ошибка → пробрасывает исключение (M13 G24-fix-2: ранее
          fail-open возвращал True, что в паре с handler-exception swallow
          делало replay-защиту бесполезной). Теперь caller реджектит
          message → DLQ → manual triage.
        """
        if not event_id:
            logger.error(
                "Idempotency: rejecting event без event_id (fail-closed) — consumer=%s. "
                "После M13 G8 все publisher'ы обязаны заполнять event_id.",
                self._consumer_id,
            )
            raise ValueError(f"Event без event_id rejected (fail-closed, M13 G24-fix-6): consumer={self._consumer_id}")
        key = self._key(event_id)
        token = secrets.token_urlsafe(24)
        # SET key value NX EX ttl — атомарный insert-or-fail.
        # Redis exception пробрасывается — caller отлавливает и реджектит
        # message в DLQ (см. event_consumer.py).
        ok = await self._redis.set(key, f"processing:{token}", nx=True, ex=self._ttl)
        if ok:
            return token
        logger.info(
            "Idempotency: duplicate event skipped (consumer=%s, event_id=%s)",
            self._consumer_id,
            event_id,
        )
        return None

    async def complete(self, event_id: Optional[str], token: str) -> bool:
        """Mark an owned event complete for the retention period.

        The compare-and-set is performed in Redis, preventing a stale worker
        from overwriting a replacement lease after its original lease expired.
        """
        if not event_id or not token:
            raise ValueError("event_id and claim token are required")
        key = self._key(event_id)
        script = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            redis.call('set', KEYS[1], 'completed', 'EX', ARGV[2])
            return 1
        end
        return 0
        """
        result = await self._redis.eval(
            script, 1, key, f"processing:{token}", str(self._completion_ttl)
        )
        return bool(result)

    async def renew(self, event_id: str, token: str) -> bool:
        """Extend only this owner's processing lease, never a replacement/completed key."""
        if not event_id or not token:
            raise ValueError("event_id and claim token are required")
        result = await self._redis.eval("""
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('expire', KEYS[1], ARGV[2])
        end
        return 0
        """, 1, self._key(event_id), f"processing:{token}", str(self._ttl))
        return bool(result)

    async def is_completed(self, event_id: str) -> bool:
        """A live processing lease is retryable; only a completed marker permits duplicate ACK."""
        if not event_id:
            raise ValueError("event_id is required")
        return await self._redis.get(self._key(event_id)) == "completed"

    async def release(self, event_id: Optional[str], token: str) -> bool:
        """Release an owned processing lease after handler failure."""
        if not event_id or not token:
            raise ValueError("event_id and claim token are required")
        key = self._key(event_id)
        script = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """
        result = await self._redis.eval(script, 1, key, f"processing:{token}")
        return bool(result)

    async def try_claim(self, event_id: Optional[str]) -> bool:
        """Compatibility helper: claim an event and return a boolean.

        New consumers should use ``claim``/``complete``/``release`` so a
        failed handler does not leave a permanent dedup marker.
        """
        return (await self.claim(event_id)) is not None

    async def close(self) -> None:
        await self._redis.aclose()
