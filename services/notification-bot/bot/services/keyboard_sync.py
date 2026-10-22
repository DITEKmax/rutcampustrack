import asyncio
import logging
import weakref
from contextlib import asynccontextmanager
from typing import Optional

import redis.asyncio as aioredis
from aiogram import Bot

from bot.handlers.prefs import keyboard_signature, main_keyboard
from bot.services.bot_menu import resolve_menu_scope

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
        # Publishers share this client through dispatcher/startup DI. Weak entries
        # live only while a holder or waiter retains the lock, including cancellation.
        self._publication_locks = weakref.WeakValueDictionary()
        if redis_client is not None:
            self._redis = redis_client
        else:
            auth = f":{password}@" if password else ""
            url = f"redis://{auth}{host}:{port}"
            self._redis = aioredis.from_url(url, max_connections=5, decode_responses=True)

    @asynccontextmanager
    async def publication(self, telegram_id: int):
        """Serialize one chat's authority read, keyboard send and Redis receipt."""
        lock = self._publication_locks.get(telegram_id)
        if lock is None:
            lock = asyncio.Lock()
            self._publication_locks[telegram_id] = lock
        async with lock:
            yield

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

    async def invalidate_user_keyboard(self, telegram_id: int) -> bool:
        """Confirm durable pending before a fallback replaces a synced keyboard."""
        try:
            await self._redis.hdel(self._USER_VERSION_KEY, str(telegram_id))
            return True
        except Exception:
            logger.exception("Redis error invalidating keyboard version for telegram_id=%d", telegram_id)
            return False

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
    academic_client,
    *,
    delay_seconds: float = 0.05,
    bot_menu_client=None,
    retry_delay_seconds: float = 1.0,
) -> None:
    """Recover stale keyboards, with at most three attempts per user at startup."""
    user_ids = await keyboard_sync.get_registered_user_ids()
    sent = 0
    skipped = 0
    failed = 0

    for telegram_id in user_ids:
        fallback_sent = False
        for attempt in range(3):
            async with keyboard_sync.publication(telegram_id):
                scope = await resolve_menu_scope(bot_menu_client, telegram_id)
                options = scope.keyboard_options if scope else {}
                keyboard_version = keyboard_signature(**options)
                stored_version = await keyboard_sync.get_user_keyboard_version(telegram_id)
                if scope is not None and stored_version == keyboard_version:
                    skipped += 1
                    break
                try:
                    # An unavailable role read can only deliver common actions. It cannot
                    # acknowledge a role keyboard, even if its old signature was stored.
                    # If Redis cannot confirm pending, retain that previous keyboard.
                    pending = await keyboard_sync.invalidate_user_keyboard(telegram_id)
                    if scope is not None or (pending and not fallback_sent):
                        await bot.send_message(
                            chat_id=telegram_id,
                            text="Клавиатура обновлена.",
                            reply_markup=main_keyboard(**options),
                            disable_notification=True,
                        )
                        sent += 1
                        fallback_sent = scope is None
                    if scope is not None:
                        await keyboard_sync.mark_user_synced(telegram_id, keyboard_version)
                        break
                except Exception:
                    logger.debug("Failed to sync keyboard for telegram_id=%d", telegram_id, exc_info=True)
            if attempt == 2:
                failed += 1
            elif retry_delay_seconds > 0:
                await asyncio.sleep(retry_delay_seconds)

        if delay_seconds > 0:
            await asyncio.sleep(delay_seconds)

    logger.info(
        "Keyboard sync completed: users=%d sent=%d skipped=%d failed=%d",
        len(user_ids),
        sent,
        skipped,
        failed,
    )
