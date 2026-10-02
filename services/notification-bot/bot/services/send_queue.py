import asyncio
import logging
import time
import grpc
from dataclasses import dataclass
from contextlib import asynccontextmanager
from contextvars import ContextVar
from typing import Any, Awaitable, Callable, Optional

from bot.services.notification_prefs import CATEGORIES, NotificationPreferencesUnavailable

logger = logging.getLogger(__name__)


class TelegramDeliveryFailed(RuntimeError):
    """Provider attempts exhausted; the original event belongs in the existing DLQ."""


class RecipientSuppressed(Exception):
    """A multi-action producer rechecked authority between provider calls."""


class DeliveryBatch:
    """In-memory execution handles only; Rabbit owns the durable pending event."""

    def __init__(self):
        self.tasks = []
        self.futures = []
        self.cancelled = asyncio.Event()
        self.failure = None

    def cancel(self):
        self.cancelled.set()
        for future in self.futures:
            if not future.done():
                future.cancel()

    async def wait(self):
        try:
            await asyncio.gather(*self.futures, return_exceptions=True)
            if self.failure is not None:
                raise self.failure
            if self.cancelled.is_set():
                raise asyncio.CancelledError
        except BaseException:
            self.cancel()
            raise


def group_audience(academic_client, group_id, user_id, chat_id, *, headman=False):
    """Fresh grant roster, never the ordinary five-minute member cache."""
    async def authorized():
        try:
            members = await academic_client.get_current_group_members(group_id)
        except grpc.aio.AioRpcError as error:
            if error.code() == grpc.StatusCode.NOT_FOUND:
                return False
            raise NotificationPreferencesUnavailable("Current audience is unavailable") from error
        except Exception as error:
            raise NotificationPreferencesUnavailable("Current audience is unavailable") from error
        if members is None:
            raise NotificationPreferencesUnavailable("Current audience is incomplete")
        return any(member.user_id == user_id and member.telegram_id == chat_id
                   and (not headman or member.is_headman) for member in members)
    return authorized


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
    audience_check: Optional[Callable[[], Awaitable[bool]]] = None


class TelegramSendQueue:
    _RATE = 30  # tokens per second
    _MAX_TOKENS = 30  # burst ceiling
    _RETRY_DELAYS = [1, 2, 4]  # backoff delays in seconds

    def __init__(self, prefs_client=None) -> None:
        self._queue: asyncio.Queue = asyncio.Queue()
        self._tokens: float = self._MAX_TOKENS
        self._last_refill: float = time.monotonic()
        self._worker_task: Optional[asyncio.Task] = None
        self._workers: list[asyncio.Task] = []
        self._total_sent: int = 0
        self._total_failed: int = 0
        self._prefs_client = prefs_client  # NotificationPrefsClient | None
        self._staged: ContextVar[Optional[DeliveryBatch]] = ContextVar("telegram_staged_tasks", default=None)

    def start(self) -> None:
        self._workers = [asyncio.create_task(self._worker()) for _ in range(2)]
        self._worker_task = self._workers[0]

    async def put(self, task: SendTask) -> None:
        staged = self._staged.get()
        if staged is not None:
            staged.tasks.append(task)
        else:
            self._admit(task, DeliveryBatch())

    def _admit(self, task, batch):
        future = asyncio.get_running_loop().create_future()
        # Standalone callers may not await a batch; retrieve diagnostics without
        # changing what a broker-owned batch's waiter observes.
        future.add_done_callback(lambda done: done.exception() if not done.cancelled() else None)
        batch.futures.append(future)
        self._queue.put_nowait((task, batch, future))

    @asynccontextmanager
    async def staged(self):
        """Resolve every actual task before admitting any of an event's tasks to the worker."""
        batch = DeliveryBatch()
        token = self._staged.set(batch)
        try:
            yield batch
            admitted = []
            for task in batch.tasks:
                if await self._eligible(task):
                    admitted.append(task)
            # Unbounded put_nowait has no await/cancellation boundary part-way through the batch.
            for task in admitted:
                self._admit(task, batch)
        except BaseException:
            batch.cancel()
            raise
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
            enabled = await self._prefs_client.is_enabled(task.chat_id, task.category, user_id=task.user_id)
            if not enabled:
                return False
            return await task.audience_check() if task.audience_check is not None else True
        except NotificationPreferencesUnavailable:
            raise
        except Exception as error:
            raise NotificationPreferencesUnavailable("Recipient preferences are unavailable") from error

    async def _worker(self) -> None:
        while True:
            task, batch, future = await self._queue.get()
            sending = cancelled = None
            try:
                if batch.cancelled.is_set():
                    future.cancel()
                    continue
                sending = asyncio.create_task(self._send_with_retry(task, batch))
                cancelled = asyncio.create_task(batch.cancelled.wait())
                done, _ = await asyncio.wait((sending, cancelled), return_when=asyncio.FIRST_COMPLETED)
                if cancelled in done:
                    future.cancel()
                else:
                    outcome = sending.result()
                    if not future.done():
                        future.set_result(outcome)
            except asyncio.CancelledError:
                batch.cancel()
                if asyncio.current_task().cancelling():
                    raise
            except Exception as error:
                if not future.done():
                    future.set_exception(error)
                batch.failure = batch.failure or error
                batch.cancel()
            finally:
                for child in (sending, cancelled):
                    if child is not None and not child.done():
                        child.cancel()
                await asyncio.gather(*(child for child in (sending, cancelled) if child is not None),
                                     return_exceptions=True)
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

    async def _send_with_retry(self, task: SendTask, batch: DeliveryBatch | None = None) -> str:
        try:
            from aiogram.exceptions import TelegramRetryAfter as RetryAfterExc
        except ImportError:
            RetryAfterExc = None

        for attempt, delay in enumerate(self._RETRY_DELAYS + [None], start=1):
            await self._consume_token()
            if batch is not None and batch.cancelled.is_set():
                raise asyncio.CancelledError
            # All rate/backoff/429 waits precede the fresh authorization boundary.
            if not await self._eligible(task):
                return "SUPPRESSED"
            if batch is not None and batch.cancelled.is_set():
                raise asyncio.CancelledError
            try:
                result = await task.coroutine_factory()
                self._total_sent += 1
                if task.on_sent is not None:
                    try:
                        await task.on_sent(result)
                    except Exception:
                        logger.exception("on_sent hook raised — ignoring, send already succeeded")
                return "SENT"
            except RecipientSuppressed:
                return "SUPPRESSED"
            except NotificationPreferencesUnavailable:
                raise
            except Exception as e:
                if delay is not None and ((RetryAfterExc is not None and isinstance(e, RetryAfterExc))
                                           or hasattr(e, "retry_after")):
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
                    raise TelegramDeliveryFailed("Provider attempts exhausted") from e
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
        for worker in self._workers:
            worker.cancel()
        await asyncio.gather(*self._workers, return_exceptions=True)
        while not self._queue.empty():
            _, batch, _ = self._queue.get_nowait()
            batch.cancel()
            self._queue.task_done()
        logger.info("SendQueue shutdown: sent=%d failed=%d", self._total_sent, self._total_failed)
