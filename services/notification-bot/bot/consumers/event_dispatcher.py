"""EventDispatcher — routes incoming RabbitMQ events to the correct handler."""
import logging
from typing import Awaitable, Callable

from aiogram import Bot

from bot.config import Settings
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.services.redis_client import ReminderRedisClient
from bot.services.send_queue import TelegramSendQueue

logger = logging.getLogger(__name__)


class EventDispatcher:
    """Routes event dicts (by event_type) to registered async handlers.

    All handler exceptions are caught and logged so that RabbitMQ messages
    are always acknowledged (no infinite requeue loop).
    """

    def __init__(
        self,
        bot: Bot,
        academic_client: AcademicGrpcClient,
        send_queue: TelegramSendQueue,
        redis_client: ReminderRedisClient,
        config: Settings,
    ) -> None:
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._config = config

        # Import handlers here to avoid circular imports at module level
        from bot.notifications.lesson_started import handle_lesson_started
        from bot.notifications.lesson_cancelled import handle_lesson_cancelled

        # Handler registry: event_type -> async callable(event: dict)
        self._handlers: dict[str, Callable[[dict], Awaitable[None]]] = {
            "lesson.started": lambda event: handle_lesson_started(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                redis_client=self._redis_client,
                config=self._config,
            ),
            "lesson.cancelled": lambda event: handle_lesson_cancelled(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            # Placeholders for Plan 02 handlers (registered in next wave)
            # "homework.published": ...,
            # "homework.updated": ...,
            # "excuse.requested": ...,
            # "late_checkin.requested": ...,
        }

    async def dispatch(self, event: dict) -> None:
        """Dispatch an event dict to the appropriate handler.

        Unknown event types are logged at DEBUG and silently ignored.
        Handler exceptions are caught and logged — never re-raised.
        """
        event_type = event.get("event_type")
        handler = self._handlers.get(event_type)

        if handler is None:
            logger.debug("Unhandled event type: %s", event_type)
            return

        try:
            await handler(event)
        except Exception:
            logger.exception("Handler failed for event_type=%s", event_type)
