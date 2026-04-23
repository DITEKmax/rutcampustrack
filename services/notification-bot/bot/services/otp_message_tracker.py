"""OTP message tracker — persistent store for Telegram messages tied to an OTP code.

Stores (chat_id, user_message_id, bot_message_id) per telegram_id with a TTL
equal to the OTP lifetime. When the OTP is consumed (otp.verified event) or
expires naturally, both messages are removed from the chat.

Redis keeps the binding across bot restarts so that no messages are orphaned
if the process is restarted during the 5-minute OTP window.
"""

from __future__ import annotations

import json
import logging
from typing import Optional

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class OtpMessageTracker:
    KEY_PREFIX = "otp_msgs:"
    PENDING_PREFIX = "otp_pending_user_msg:"

    def __init__(
        self,
        ttl_seconds: int,
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
            self._redis = aioredis.from_url(url, max_connections=5, decode_responses=True)
        self._ttl = ttl_seconds

    def _key(self, telegram_id: int) -> str:
        return f"{self.KEY_PREFIX}{telegram_id}"

    def _pending_key(self, telegram_id: int) -> str:
        return f"{self.PENDING_PREFIX}{telegram_id}"

    async def store_pending_user_msg(
        self,
        telegram_id: int,
        chat_id: int,
        user_message_id: int,
    ) -> None:
        """M09 G2: сохранить pending /login message user'а ДО прихода otp.requested.

        Consumer события добавит bot_message_id и переложит всё в финальный
        ключ через {@link finalize_with_bot_msg}.
        """
        payload = json.dumps({"chat_id": chat_id, "user_message_id": user_message_id})
        try:
            await self._redis.set(self._pending_key(telegram_id), payload, ex=self._ttl)
        except Exception:
            logger.exception("Redis error storing pending user msg for telegram_id=%d", telegram_id)

    async def finalize_with_bot_msg(
        self,
        telegram_id: int,
        bot_message_id: int,
    ) -> None:
        """M09 G2: дополняет pending-запись id'ом бот-сообщения и перемещает в
        финальный ключ. Если pending нет (пользователь не делал /login, а auth
        дёрнули откуда-то ещё) — сохраняем только chat_id=telegram_id + bot_msg.
        """
        pending_raw = None
        try:
            pending_raw = await self._redis.get(self._pending_key(telegram_id))
            if pending_raw is not None:
                await self._redis.delete(self._pending_key(telegram_id))
        except Exception:
            logger.exception("Redis error reading pending user msg for telegram_id=%d", telegram_id)

        if pending_raw is not None:
            pending = json.loads(pending_raw)
            await self.store(
                telegram_id=telegram_id,
                chat_id=pending["chat_id"],
                user_message_id=pending["user_message_id"],
                bot_message_id=bot_message_id,
            )
        else:
            await self.store(
                telegram_id=telegram_id,
                chat_id=telegram_id,
                user_message_id=None,
                bot_message_id=bot_message_id,
            )

    async def store(
        self,
        telegram_id: int,
        chat_id: int,
        user_message_id: Optional[int],
        bot_message_id: int,
    ) -> None:
        """Persist the pair of message ids for this OTP session.

        If a previous session exists (user pressed /login twice), it is
        overwritten — the old messages become orphaned but harmless.

        M09 G2: user_message_id может быть None, если OTP запрошен не из
        /login команды (напр. auth/otp/request дёрнут из веб-панели для
        заведения аккаунта — пользовательского сообщения /login нет).
        """
        payload = json.dumps(
            {
                "chat_id": chat_id,
                "user_message_id": user_message_id,
                "bot_message_id": bot_message_id,
            }
        )
        try:
            await self._redis.set(self._key(telegram_id), payload, ex=self._ttl)
        except Exception:
            logger.exception("Redis error storing OTP messages for telegram_id=%d", telegram_id)

    async def pop(self, telegram_id: int) -> Optional[dict]:
        """Atomically read and delete the stored tuple.

        Returns a dict with chat_id/user_message_id/bot_message_id or None
        if nothing is stored (already consumed or never existed).
        """
        key = self._key(telegram_id)
        try:
            async with self._redis.pipeline(transaction=True) as pipe:
                pipe.get(key)
                pipe.delete(key)
                raw, _ = await pipe.execute()
            if raw is None:
                return None
            return json.loads(raw)
        except Exception:
            logger.exception("Redis error reading OTP messages for telegram_id=%d", telegram_id)
            return None

    async def close(self) -> None:
        await self._redis.aclose()
