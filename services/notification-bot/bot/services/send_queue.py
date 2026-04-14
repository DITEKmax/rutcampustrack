import asyncio
import logging
import time
from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Optional

logger = logging.getLogger(__name__)


@dataclass
class SendTask:
    coroutine_factory: Callable[[], Awaitable[Any]]
    user_id: Optional[int] = None
    chat_id: Optional[int] = None


class TelegramSendQueue:
    _RATE = 30  # tokens per second
    _MAX_TOKENS = 30  # burst ceiling
    _RETRY_DELAYS = [1, 2, 4]  # backoff delays in seconds

    def __init__(self, prefs_client=None) -> None:
        self._queue: asyncio.Queue[SendTask] = asyncio.Queue()
        self._tokens: float = self._MAX_TOKENS
        self._last_refill: float = time.monotonic()
        self._worker_task: Optional[asyncio.Task] = None
        self._total_sent: int = 0
        self._total_failed: int = 0
        self._prefs_client = prefs_client  # NotificationPrefsClient | None

    def start(self) -> None:
        self._worker_task = asyncio.create_task(self._worker())

    async def put(self, task: SendTask) -> None:
        await self._queue.put(task)

    async def _worker(self) -> None:
        while True:
            task = await self._queue.get()
            if self._prefs_client is not None and task.chat_id is not None:
                if not await self._prefs_client.is_enabled(task.chat_id):
                    self._queue.task_done()
                    continue
            await self._consume_token()
            await self._send_with_retry(task)
            self._queue.task_done()

    async def _consume_token(self) -> None:
        while True:
            now = time.monotonic()
            elapsed = now - self._last_refill
            self._tokens = min(self._MAX_TOKENS, self._tokens + elapsed * self._RATE)
            self._last_refill = now
            if self._tokens >= 1.0:
                self._tokens -= 1.0
                return
            sleep_time = (1.0 - self._tokens) / self._RATE
            await asyncio.sleep(sleep_time)

    async def _send_with_retry(self, task: SendTask) -> None:
        try:
            from aiogram.exceptions import TelegramRetryAfter as RetryAfterExc
        except ImportError:
            RetryAfterExc = None

        for attempt, delay in enumerate(self._RETRY_DELAYS + [None], start=1):
            try:
                await task.coroutine_factory()
                self._total_sent += 1
                return
            except Exception as e:
                if RetryAfterExc is not None and isinstance(e, RetryAfterExc):
                    logger.warning("Telegram 429 — retry after %ds", e.retry_after)
                    await asyncio.sleep(e.retry_after)
                    continue
                if hasattr(e, "retry_after"):
                    logger.warning("Telegram 429 — retry after %ds", e.retry_after)
                    await asyncio.sleep(e.retry_after)
                    continue
                if delay is None:
                    logger.error(
                        "Send failed after %d attempts user_id=%s chat_id=%s — skipping",
                        len(self._RETRY_DELAYS),
                        task.user_id,
                        task.chat_id,
                    )
                    self._total_failed += 1
                    return
                logger.warning("Send attempt %d failed — retrying in %ds", attempt, delay)
                await asyncio.sleep(delay)

    async def shutdown(self) -> None:
        if self._worker_task:
            self._worker_task.cancel()
            try:
                await self._worker_task
            except asyncio.CancelledError:
                pass
        logger.info("SendQueue shutdown: sent=%d failed=%d", self._total_sent, self._total_failed)
