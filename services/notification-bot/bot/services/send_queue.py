import asyncio
import logging
import time
from dataclasses import dataclass
from contextlib import asynccontextmanager
from contextvars import ContextVar
from typing import Any, Awaitable, Callable, Optional

from bot.services.notification_prefs import CATEGORIES, NotificationPreferencesUnavailable

logger = logging.getLogger(__name__)


def _exception_summary(exc: Exception) -> str:
    message = str(exc)
    if message:
        return f"{type(exc).__name__}: {message}"
    return type(exc).__name__


@dataclass
class SendTask:
    coroutine_factory: Callable[[], Awaitable[Any]]
    user_id: Optional[int] = None
    chat_id: Optional[int] = None
    # Опциональный post-send хук: получает значение, вернувшееся из
    # coroutine_factory (обычно это aiogram Message с полем message_id).
    # Используется, чтобы сохранить пару (chat_id, message_id) в Redis для
    # последующего редактирования при получении *.decided.
    on_sent: Optional[Callable[[Any], Awaitable[None]]] = None
    # Категория уведомления (см. bot.services.notification_prefs.CATEGORIES).
    # Generic tasks always require a classified category and exact recipient.
    category: Optional[str] = None
    # Set only by the trusted alert.fired producer, never inferred from missing IDs/category.
    system_alert: bool = False


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
        self._staged: ContextVar[Optional[list[SendTask]]] = ContextVar("telegram_staged_tasks", default=None)

    def start(self) -> None:
        self._worker_task = asyncio.create_task(self._worker())

    async def put(self, task: SendTask) -> None:
        staged = self._staged.get()
        if staged is not None:
            staged.append(task)
        else:
            await self._queue.put(task)

    @asynccontextmanager
    async def staged(self):
        """Resolve every actual task before admitting any of an event's tasks to the worker."""
        batch: list[SendTask] = []
        token = self._staged.set(batch)
        try:
            yield
            admitted = []
            for task in batch:
                if await self._eligible(task):
                    admitted.append(task)
            # Unbounded put_nowait has no await/cancellation boundary part-way through the batch.
            for task in admitted:
                self._queue.put_nowait(task)
        finally:
            self._staged.reset(token)

    async def _eligible(self, task: SendTask) -> bool:
        if task.system_alert:
            return True
        if self._prefs_client is None or task.category not in CATEGORIES \
                or type(task.user_id) is not int or task.user_id <= 0 \
                or type(task.chat_id) is not int or task.chat_id <= 0:
            raise NotificationPreferencesUnavailable("Classified recipient preferences are required")
        try:
            return await self._prefs_client.is_enabled(task.chat_id, task.category, user_id=task.user_id)
        except NotificationPreferencesUnavailable:
            raise
        except Exception as error:
            raise NotificationPreferencesUnavailable("Recipient preferences are unavailable") from error

    async def _worker(self) -> None:
        while True:
            task = await self._queue.get()
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
            while True:
                try:
                    enabled = await self._eligible(task)
                    break
                except NotificationPreferencesUnavailable:
                    # Retain this pending task, including across provider retry attempts.
                    await asyncio.sleep(1)
            if not enabled:
                return
            await self._consume_token()
            try:
                result = await task.coroutine_factory()
                self._total_sent += 1
                if task.on_sent is not None:
                    try:
                        await task.on_sent(result)
                    except Exception:
                        logger.exception("on_sent hook raised — ignoring, send already succeeded")
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
                        ("Send failed after %d attempts user_id=%s chat_id=%s category=%s error=%s — skipping"),
                        len(self._RETRY_DELAYS) + 1,
                        task.user_id,
                        task.chat_id,
                        task.category,
                        _exception_summary(e),
                        exc_info=True,
                    )
                    self._total_failed += 1
                    return
                logger.warning(
                    ("Send attempt %d failed user_id=%s chat_id=%s category=%s error=%s — retrying in %ds"),
                    attempt,
                    task.user_id,
                    task.chat_id,
                    task.category,
                    _exception_summary(e),
                    delay,
                )
                await asyncio.sleep(delay)

    async def shutdown(self) -> None:
        if self._worker_task:
            self._worker_task.cancel()
            try:
                await self._worker_task
            except asyncio.CancelledError:
                pass
        logger.info("SendQueue shutdown: sent=%d failed=%d", self._total_sent, self._total_failed)
