import asyncio
import logging
from typing import Optional

import redis.asyncio as aioredis
from aiogram import Bot
from aiogram.types import ReplyKeyboardMarkup

logger = logging.getLogger(__name__)


class KeyboardSyncClient:
    """Redis-backed registry of Telegram users that should receive keyboard updates."""

    _ACTIVE_USERS_KEY = "bot:keyboard:active_users"
    _USER_VERSION_KEY = "bot:keyboard:user_versions"

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
            self._redis = aioredis.from_url(url, max_connections=5, decode_responses=True)

    async def register_user(self, telegram_id: int) -> None:
        try:
            await self._redis.sadd(self._ACTIVE_USERS_KEY, str(telegram_id))
        except Exception:
            logger.exception("Redis error registering keyboard user telegram_id=%d", telegram_id)

    async def mark_user_synced(self, telegram_id: int, keyboard_version: str) -> None:
        try:
            await self._redis.hset(self._USER_VERSION_KEY, str(telegram_id), keyboard_version)
        except Exception:
            logger.exception("Redis error marking keyboard version for telegram_id=%d", telegram_id)

    async def get_registered_user_ids(self) -> list[int]:
        try:
            raw_ids = await self._redis.smembers(self._ACTIVE_USERS_KEY)
        except Exception:
            logger.exception("Redis error loading keyboard user registry")
            return []

        user_ids: list[int] = []
        for raw_id in raw_ids:
            try:
                user_ids.append(int(raw_id))
            except (TypeError, ValueError):
                logger.warning("Ignoring invalid keyboard registry entry: %r", raw_id)
        return sorted(user_ids)

    async def get_user_keyboard_version(self, telegram_id: int) -> str | None:
        try:
            value = await self._redis.hget(self._USER_VERSION_KEY, str(telegram_id))
            return value if isinstance(value, str) else None
        except Exception:
            logger.exception("Redis error reading keyboard version for telegram_id=%d", telegram_id)
            return None

    async def close(self) -> None:
        await self._redis.aclose()


async def mark_keyboard_synced(
    keyboard_sync: KeyboardSyncClient | None,
    telegram_id: int,
    keyboard_version: str,
) -> None:
    if keyboard_sync is None:
        return
    await keyboard_sync.register_user(telegram_id)
    await keyboard_sync.mark_user_synced(telegram_id, keyboard_version)


async def sync_registered_keyboards(
    bot: Bot,
    keyboard_sync: KeyboardSyncClient,
    keyboard: ReplyKeyboardMarkup,
    keyboard_version: str,
    *,
    delay_seconds: float = 0.05,
) -> None:
    """Push the current reply keyboard to registered users whose version is stale."""
    user_ids = await keyboard_sync.get_registered_user_ids()
    sent = 0
    skipped = 0
    failed = 0

    for telegram_id in user_ids:
        stored_version = await keyboard_sync.get_user_keyboard_version(telegram_id)
        if stored_version == keyboard_version:
            skipped += 1
            continue

        try:
            await bot.send_message(
                chat_id=telegram_id,
                text="Клавиатура обновлена.",
                reply_markup=keyboard,
                disable_notification=True,
            )
            await keyboard_sync.mark_user_synced(telegram_id, keyboard_version)
            sent += 1
        except Exception:
            failed += 1
            logger.debug("Failed to sync keyboard for telegram_id=%d", telegram_id, exc_info=True)

        if delay_seconds > 0:
            await asyncio.sleep(delay_seconds)

    logger.info(
        "Keyboard sync completed: version=%s users=%d sent=%d skipped=%d failed=%d",
        keyboard_version,
        len(user_ids),
        sent,
        skipped,
        failed,
    )
