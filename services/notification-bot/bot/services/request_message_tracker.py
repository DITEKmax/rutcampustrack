"""Redis-backed map (request_id → [{chat_id, message_id}]).

Используется для двухсторонней синхронизации TG ↔ Web:
- когда бот отправляет карточку запроса старостам (excuse.requested /
  late_checkin.requested), в Redis кладётся пара (chat_id, message_id) для
  каждого адресата;
- когда решение принято через web-панель, attendance-service публикует
  excuse.decided / late_checkin.decided; в event_dispatcher мы поднимаем
  записи и редактируем исходные сообщения в Telegram — убираем кнопки и
  дописываем вердикт. Если решение пришло из TG — такой же edit делается
  в callback-хендлере, а оставшиеся записи (у других старост той же группы)
  поправит consumer.decided.

Ключ: {key_prefix}{kind}:{request_id} — так excuse и late_checkin не
конфликтуют по общему id-пространству.
TTL: достаточно большой, чтобы покрыть жизненный цикл запроса
(день-два). Если редкое решение приходит позже TTL — редактировать
нечего, это не ошибка.
"""

import json
import logging
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class RequestMessageTracker:
    def __init__(
        self,
        key_prefix: str = "bot:request_msgs:",
        ttl: int = 7 * 24 * 3600,
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
        self._key_prefix = key_prefix
        self._ttl = ttl

    def _key(self, kind: str, request_id: str | int) -> str:
        return f"{self._key_prefix}{kind}:{request_id}"

    async def add(self, kind: str, request_id: str | int, chat_id: int, message_id: int) -> None:
        """Добавить одну пару (chat_id, message_id) в список запроса."""
        key = self._key(kind, request_id)
        payload = json.dumps({"chat_id": chat_id, "message_id": message_id})
        try:
            await self._redis.rpush(key, payload)
            await self._redis.expire(key, self._ttl)
        except Exception:
            logger.exception("Redis error storing request message_id (kind=%s, id=%s)", kind, request_id)

    async def get_all(self, kind: str, request_id: str | int) -> list[dict]:
        """Вернуть все сохранённые (chat_id, message_id) для запроса."""
        key = self._key(kind, request_id)
        try:
            raw = await self._redis.lrange(key, 0, -1)
            out = []
            for item in raw:
                try:
                    out.append(json.loads(item))
                except (ValueError, TypeError):
                    continue
            return out
        except Exception:
            logger.exception("Redis error reading request message_ids (kind=%s, id=%s)", kind, request_id)
            return []

    async def delete(self, kind: str, request_id: str | int) -> None:
        """Удалить запись (например, после успешного edit-а у всех старост)."""
        key = self._key(kind, request_id)
        try:
            await self._redis.delete(key)
        except Exception:
            logger.exception("Redis error deleting request key (kind=%s, id=%s)", kind, request_id)

    async def close(self) -> None:
        await self._redis.aclose()
