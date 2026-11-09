import asyncio
import json
import logging
import random
import time
import weakref
from contextlib import asynccontextmanager
from typing import Optional

import redis.asyncio as aioredis
from aiogram import Bot
from aiogram.exceptions import TelegramBadRequest, TelegramForbiddenError, TelegramRetryAfter

from bot.handlers.prefs import keyboard_signature, main_keyboard
from bot.observability import (
    KEYBOARD_SYNC_CATALOG_AVAILABLE,
    KEYBOARD_SYNC_FRESHNESS,
    KEYBOARD_SYNC_OLDEST_PENDING,
    KEYBOARD_SYNC_PENDING,
    KEYBOARD_SYNC_REGISTRY_AVAILABLE,
    KEYBOARD_SYNC_RETRYABLE,
    KEYBOARD_SYNC_TERMINAL,
    KEYBOARD_SYNC_USERS_FAILED,
    STARTUP_COMPLETED,
    STARTUP_FAILED,
)
from bot.services.bot_menu import MenuScope, resolve_menu_scope

logger = logging.getLogger(__name__)


def authority_tuple(scope):
    return [scope.user_id, scope.telegram_binding_version, scope.roles_version]


def receipt_for(scope):
    return json.dumps([keyboard_signature(**scope.keyboard_options), authority_tuple(scope)], separators=(",", ":"))


class KeyboardSyncClient:
    """Durable discovery, pending work and ACK receipts; one publisher per chat/process.

    Redis must retain/persist these keys across application restarts. Telegram has no
    idempotency key: ACK followed by a failed Redis commit may cause a duplicate on
    retry. A receipt is never inferred from a completed task or attempted write.
    Auth mutations cannot be atomic with an external send: a concurrent mutation
    may briefly expose the old buttons; the post-send read refuses its receipt and
    a later pass replaces them. Button handlers still require current authority.
    """

    _ACTIVE_USERS_KEY = "bot:keyboard:active_users"
    _USER_VERSION_KEY = "bot:keyboard:user_versions"
    _RECEIPTS_KEY = "bot:keyboard:receipts:v2"
    _STATE_KEY = "bot:keyboard:pending:v2"
    _UNSAFE_UI_KEY = "bot:keyboard:unsafe_ui:v2"
    _BINDINGS_KEY = "bot:keyboard:bindings:v2"
    _PROVIDER_BACKOFF_KEY = "bot:keyboard:provider_backoff:v2"

    def __init__(self, host="redis", port=6379, password="", redis_client: Optional[aioredis.Redis] = None):
        self._publication_locks = weakref.WeakValueDictionary()
        self._catalog_available = False
        self._registry_available = False
        if redis_client is not None:
            self._redis = redis_client
        else:
            auth = f":{password}@" if password else ""
            self._redis = aioredis.from_url(
                f"redis://{auth}{host}:{port}",
                max_connections=5,
                decode_responses=True,
                socket_connect_timeout=3,
                socket_timeout=3,
                retry_on_timeout=False,
            )

    @asynccontextmanager
    async def publication(self, telegram_id: int):
        lock = self._publication_locks.get(telegram_id)
        if lock is None:
            lock = asyncio.Lock()
            self._publication_locks[telegram_id] = lock
        async with lock:
            yield

    async def register_user(self, telegram_id: int, *, reactivate=False):
        await self._redis.sadd(self._ACTIVE_USERS_KEY, str(telegram_id))
        if reactivate:
            await self.note_possible_publication(telegram_id, keyboard_signature())
            await self.set_state(telegram_id, self._pending())
        else:
            await self._redis.hsetnx(self._STATE_KEY, str(telegram_id), json.dumps(self._pending()))

    @staticmethod
    def _pending():
        return dict(status="pending", attempts=0, next_at=0, since=time.time())

    async def state(self, telegram_id):
        raw = await self._redis.hget(self._STATE_KEY, str(telegram_id))
        return json.loads(raw) if raw is not None else self._pending()

    async def set_state(self, telegram_id, state):
        await self._redis.hset(self._STATE_KEY, str(telegram_id), json.dumps(state))

    async def ui_needs_cleanup(self, telegram_id):
        """Receipt invalidation is not evidence that a previously visible UI vanished."""
        if await self._redis.hget(self._UNSAFE_UI_KEY, str(telegram_id)) is not None:
            return True
        common = keyboard_signature()
        receipt = await self.get_receipt(telegram_id)
        if receipt is not None and json.loads(receipt)[0] != common:
            return True
        version = await self.get_user_keyboard_version(telegram_id)
        if version is not None and version != common:
            return True
        # Preserve pending work written by the earlier fallback implementation.
        state = await self.state(telegram_id)
        return bool(state.get("fallback_needed") and not state.get("fallback_sent"))

    async def note_possible_publication(self, telegram_id, keyboard_version):
        """Commit UI risk before deleting receipts or attempting an external role send.

        This ledger is separate from retry/catalogue state, which may be reset on
        binding changes or /start. Only authoritative success/common ACK clears it.
        """
        if keyboard_version != keyboard_signature() or await self.ui_needs_cleanup(telegram_id):
            await self._redis.hset(self._UNSAFE_UI_KEY, str(telegram_id), "1")

    async def mark_common_keyboard(self, telegram_id):
        """Record a common keyboard ACK without acknowledging current Auth scope."""
        state = await self.state(telegram_id)
        state.pop("fallback_needed", None)
        state.pop("fallback_sent", None)
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.hdel(self._UNSAFE_UI_KEY, str(telegram_id))
            pipe.hdel(self._USER_VERSION_KEY, str(telegram_id))
            pipe.hdel(self._RECEIPTS_KEY, str(telegram_id))
            pipe.hset(self._STATE_KEY, str(telegram_id), json.dumps(state))
            await pipe.execute()
        return state

    async def mark_user_synced(self, telegram_id, keyboard_version, scope: MenuScope | None = None):
        # Legacy content-only writes cannot acknowledge an authoritative receipt.
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.hset(self._USER_VERSION_KEY, str(telegram_id), keyboard_version)
            if scope is not None:
                pipe.hset(self._RECEIPTS_KEY, str(telegram_id), receipt_for(scope))
                pipe.hset(self._STATE_KEY, str(telegram_id), json.dumps(dict(status="synced")))
                pipe.hdel(self._UNSAFE_UI_KEY, str(telegram_id))
            await pipe.execute()

    async def get_registered_user_ids(self):
        try:
            raw_ids = await self._redis.smembers(self._ACTIVE_USERS_KEY)
        except Exception:
            self._registry_available = False
            KEYBOARD_SYNC_REGISTRY_AVAILABLE.set(0)
            raise RuntimeError("Keyboard registry unavailable") from None
        KEYBOARD_SYNC_REGISTRY_AVAILABLE.set(1)
        self._registry_available = True
        return sorted(int(value) for value in raw_ids if str(value).isdigit() and int(value) > 0)

    async def get_user_keyboard_version(self, telegram_id):
        return await self._redis.hget(self._USER_VERSION_KEY, str(telegram_id))

    async def get_receipt(self, telegram_id):
        return await self._redis.hget(self._RECEIPTS_KEY, str(telegram_id))

    async def invalidate_user_keyboard(self, telegram_id, *, reactivate=True):
        """Explicit /start reactivates terminal work; commit pending before replacing UI."""
        try:
            await self.note_possible_publication(telegram_id, keyboard_signature())
            async with self._redis.pipeline(transaction=True) as pipe:
                pipe.hdel(self._USER_VERSION_KEY, str(telegram_id))
                pipe.hdel(self._RECEIPTS_KEY, str(telegram_id))
                if reactivate:
                    pipe.hset(self._STATE_KEY, str(telegram_id), json.dumps(self._pending()))
                await pipe.execute()
            return True
        except Exception:
            logger.warning("Keyboard pending storage unavailable")
            return False

    async def refresh_bindings(self, menu_client):
        """Only a completed bounded authoritative sweep establishes absent bindings.

        Each discovered tuple is reconciled under the same lock as /start/send/receipt.
        A failed page does not mark any omitted recipient unbound.
        """
        if menu_client is None or not callable(getattr(menu_client, "list_keyboard_bindings", None)):
            self._catalog_available = False
            KEYBOARD_SYNC_CATALOG_AVAILABLE.set(0)
            return False
        before = {i: await self.state(i) for i in await self.get_registered_user_ids()}
        seen = set()
        after = upper = 0
        try:
            while True:
                page = await menu_client.list_keyboard_bindings(after, upper, 200)
                if len(page.bindings) > 200 or page.through_user_id < upper:
                    raise RuntimeError("Invalid binding catalogue page")
                if after and page.through_user_id != upper:
                    raise RuntimeError("Changed binding catalogue boundary")
                upper = page.through_user_id
                previous = after
                for binding in page.bindings:
                    if (
                        binding.user_id <= previous
                        or binding.user_id > upper
                        or binding.telegram_id <= 0
                        or binding.telegram_binding_version <= 0
                        or binding.roles_version <= 0
                    ):
                        raise RuntimeError("Invalid binding catalogue entry")
                    previous = binding.user_id
                    telegram_id = binding.telegram_id
                    seen.add(telegram_id)
                    identity = json.dumps([binding.user_id, binding.telegram_binding_version, binding.roles_version])
                    async with self.publication(telegram_id):
                        await self.register_user(telegram_id)
                        old = await self._redis.hget(self._BINDINGS_KEY, str(telegram_id))
                        state = await self.state(telegram_id)
                        # Terminal provider failures stay terminal for the same binding;
                        # a new binding or a reappearing unbound identity reactivates it.
                        if old != identity or state.get("reason") == "unbound":
                            # The UI ledger and receipts survive this retry reset.
                            # Promote only the former pending flag; do not make
                            # discovery depend on a recipient's receipt read.
                            if state.get("fallback_needed") and not state.get("fallback_sent"):
                                await self._redis.hset(self._UNSAFE_UI_KEY, str(telegram_id), "1")
                            await self.set_state(telegram_id, self._pending())
                            await self._redis.hset(self._BINDINGS_KEY, str(telegram_id), identity)
                next_after = page.next_after_user_id
                if not next_after:
                    break
                if not page.bindings or next_after != previous or next_after <= after or next_after > upper:
                    raise RuntimeError("Non-progressing binding catalogue")
                after = next_after
            for telegram_id in before.keys() - seen:
                async with self.publication(telegram_id):
                    if await self.state(telegram_id) == before[telegram_id]:
                        state = before[telegram_id]
                        if state.get("reason") == "provider_unreachable":
                            continue
                        if await self.ui_needs_cleanup(telegram_id):
                            await self.note_possible_publication(telegram_id, keyboard_signature())
                            if state.get("reason") != "unbound" or state["status"] == "terminal":
                                await self.set_state(
                                    telegram_id,
                                    dict(
                                        state,
                                        status="pending",
                                        reason="unbound",
                                        next_at=0,
                                        since=state.get("since", time.time()),
                                    ),
                                )
                        else:
                            await self.set_state(telegram_id, dict(status="terminal", reason="unbound"))
            KEYBOARD_SYNC_CATALOG_AVAILABLE.set(1)
            self._catalog_available = True
            return True
        except Exception:
            self._catalog_available = False
            KEYBOARD_SYNC_CATALOG_AVAILABLE.set(0)
            logger.warning("Keyboard binding catalogue unavailable")
            return False

    async def refresh_health(self):
        rows = await self._redis.hgetall(self._STATE_KEY)
        states = [json.loads(raw) for raw in rows.values()]
        pending = [s for s in states if s["status"] in {"pending", "retryable"}]
        KEYBOARD_SYNC_PENDING.set(len(pending))
        KEYBOARD_SYNC_RETRYABLE.set(sum(s["status"] == "retryable" for s in states))
        KEYBOARD_SYNC_TERMINAL.set(sum(s["status"] == "terminal" for s in states))
        KEYBOARD_SYNC_OLDEST_PENDING.set(max((time.time() - s["since"] for s in pending), default=0))
        KEYBOARD_SYNC_FRESHNESS.set(time.time())
        healthy = not pending and self._catalog_available and self._registry_available
        STARTUP_COMPLETED.labels("keyboard_sync").set(int(healthy))
        STARTUP_FAILED.labels("keyboard_sync").set(int(not healthy))

    async def close(self):
        await self._redis.aclose()


async def mark_keyboard_synced(keyboard_sync, telegram_id, keyboard_version, *, scope=None, bot_menu_client=None):
    if keyboard_sync is None:
        return
    await keyboard_sync.register_user(telegram_id)
    if keyboard_version == keyboard_signature():
        await keyboard_sync.mark_common_keyboard(telegram_id)
    # /start also checks after a potentially delayed Telegram ACK/card publication.
    current = await resolve_menu_scope(bot_menu_client, telegram_id) if scope is not None else None
    if current != scope or scope is None:
        return
    await keyboard_sync.mark_user_synced(telegram_id, keyboard_version, scope)


def _terminal(error):
    return isinstance(error, TelegramForbiddenError) or (
        isinstance(error, TelegramBadRequest) and "chat not found" in error.message.lower()
    )


async def _reconcile_user(bot, sync, menu, telegram_id, *, now, retry_delay_seconds):
    async with sync.publication(telegram_id):
        state = await sync.state(telegram_id)
        if state["status"] == "terminal" or state.get("next_at", 0) > now:
            return
        provider_backoff = await sync._redis.get(sync._PROVIDER_BACKOFF_KEY)
        if provider_backoff is not None and float(provider_backoff) > now:
            return
        # A durable pending marker precedes authority, send and receipt. Preserve age.
        state = dict(state, status="pending", since=state.get("since", now))
        await sync.set_state(telegram_id, state)
        try:
            scope = await resolve_menu_scope(menu, telegram_id)
            if scope is None:
                # Revoke a previously published role UI safely, while keeping an
                # uncertain/fenced/no-grant menu retryable. A common fallback is
                # never an authoritative success receipt.
                if await sync.ui_needs_cleanup(telegram_id):
                    await sync.note_possible_publication(telegram_id, keyboard_signature())
                    if not await sync.invalidate_user_keyboard(telegram_id, reactivate=False):
                        raise RuntimeError("Keyboard fallback pending unavailable")
                    if await resolve_menu_scope(menu, telegram_id) is not None:
                        raise RuntimeError("Keyboard authority recovered before fallback")
                    await bot.send_message(
                        chat_id=telegram_id,
                        text="Клавиатура обновлена.",
                        reply_markup=main_keyboard(),
                        disable_notification=True,
                    )
                    state = await sync.mark_common_keyboard(telegram_id)
                if state.get("reason") == "unbound":
                    await sync.set_state(telegram_id, dict(status="terminal", reason="unbound"))
                    return
                raise RuntimeError("Current keyboard authority unavailable")
            receipt = receipt_for(scope)
            if await sync.get_receipt(telegram_id) != receipt:
                await sync.note_possible_publication(telegram_id, keyboard_signature(**scope.keyboard_options))
                if not await sync.invalidate_user_keyboard(telegram_id, reactivate=False):
                    raise RuntimeError("Keyboard receipt invalidation unavailable")
                # Reread after storage waits, immediately before publication.
                fresh = await resolve_menu_scope(menu, telegram_id)
                if fresh != scope:
                    raise RuntimeError("Keyboard authority changed before publication")
                await bot.send_message(
                    chat_id=telegram_id,
                    text="Клавиатура обновлена.",
                    reply_markup=main_keyboard(**scope.keyboard_options),
                    disable_notification=True,
                )
                if keyboard_signature(**scope.keyboard_options) == keyboard_signature():
                    state = await sync.mark_common_keyboard(telegram_id)
                if await resolve_menu_scope(menu, telegram_id) != scope:
                    raise RuntimeError("Keyboard authority changed during publication")
            else:
                # A catalogue tuple never authorizes skipping a fresh own read.
                if await resolve_menu_scope(menu, telegram_id) != scope:
                    raise RuntimeError("Keyboard authority changed before receipt reuse")
            await sync.mark_user_synced(telegram_id, keyboard_signature(**scope.keyboard_options), scope)
        except Exception as error:
            KEYBOARD_SYNC_USERS_FAILED.inc()
            if _terminal(error):
                await sync.set_state(telegram_id, dict(status="terminal", reason="provider_unreachable"))
            else:
                attempts = state.get("attempts", 0) + 1
                delay = min(300.0, retry_delay_seconds * (2 ** min(attempts - 1, 10)))
                delay = min(300.0, delay * random.uniform(0.8, 1.2))
                if isinstance(error, TelegramRetryAfter):
                    delay = max(delay, float(error.retry_after))
                    await sync._redis.set(sync._PROVIDER_BACKOFF_KEY, str(time.time() + delay))
                await sync.set_state(
                    telegram_id, dict(state, status="retryable", attempts=attempts, next_at=time.time() + delay)
                )
            logger.warning("Keyboard recipient reconciliation deferred (%s)", type(error).__name__)


async def sync_registered_keyboards(
    bot: Bot,
    keyboard_sync: KeyboardSyncClient,
    academic_client=None,
    *,
    delay_seconds=0.05,
    bot_menu_client=None,
    retry_delay_seconds=5.0,
):
    """One bounded pass; the supervised periodic worker retries pending work forever."""
    # Registry and catalogue failures are separate from per-recipient failures.
    await keyboard_sync.refresh_bindings(bot_menu_client)
    for telegram_id in await keyboard_sync.get_registered_user_ids():
        try:
            await _reconcile_user(
                bot,
                keyboard_sync,
                bot_menu_client,
                telegram_id,
                now=time.time(),
                retry_delay_seconds=retry_delay_seconds,
            )
        except Exception:
            # Redis failure must not prevent progress for another recipient.
            keyboard_sync._registry_available = False
            KEYBOARD_SYNC_REGISTRY_AVAILABLE.set(0)
            logger.warning("Keyboard recipient durable state unavailable")
        if delay_seconds > 0:
            await asyncio.sleep(delay_seconds)
    await keyboard_sync.refresh_health()


async def run_keyboard_sync(
    bot,
    keyboard_sync,
    academic_client=None,
    *,
    bot_menu_client=None,
    interval_seconds=5.0,
    delay_seconds=0.05,
    retry_delay_seconds=5.0,
):
    """Cancellable, rate-limited recovery; a process restart resumes Redis state."""
    while True:
        try:
            await sync_registered_keyboards(
                bot,
                keyboard_sync,
                academic_client,
                bot_menu_client=bot_menu_client,
                delay_seconds=delay_seconds,
                retry_delay_seconds=retry_delay_seconds,
            )
        except Exception:
            KEYBOARD_SYNC_REGISTRY_AVAILABLE.set(0)
            STARTUP_COMPLETED.labels("keyboard_sync").set(0)
            STARTUP_FAILED.labels("keyboard_sync").set(1)
            logger.warning("Keyboard recovery pass unavailable")
        await asyncio.sleep(interval_seconds * random.uniform(0.8, 1.2))
