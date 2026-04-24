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
    DEFAULT_TTL_SECONDS = 3600  # 1h — больше чем Rabbit redelivery window

    def __init__(
        self,
        consumer_id: str = DEFAULT_CONSUMER_ID,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
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
            self._redis = aioredis.from_url(
                url, max_connections=10, decode_responses=True
            )
        self._consumer_id = consumer_id
        self._ttl = ttl_seconds

    def _key(self, event_id: str) -> str:
        return f"consumed:{self._consumer_id}:{event_id}"

    async def try_claim(self, event_id: Optional[str]) -> bool:
        """Пытается claim'ить event_id. True если новый, False если дубль.

        - None / blank event_id → True (no-dedup, warn).
        - Redis ошибка → True + log.error (fail-open: лучше дубль обработать,
          чем потерять событие при transient Redis блипе).
        """
        if not event_id:
            logger.warning(
                "Idempotency: event without event_id, processing without dedup (consumer=%s)",
                self._consumer_id,
            )
            return True
        key = self._key(event_id)
        try:
            # SET key value NX EX ttl — атомарный insert-or-fail
            ok = await self._redis.set(key, "1", nx=True, ex=self._ttl)
            if ok:
                return True
            logger.info(
                "Idempotency: duplicate event skipped (consumer=%s, event_id=%s)",
                self._consumer_id,
                event_id,
            )
            return False
        except Exception:
            logger.exception(
                "Idempotency: Redis error claiming event_id=%s, fail-open (will process)",
                event_id,
            )
            return True

    async def close(self) -> None:
        await self._redis.aclose()
