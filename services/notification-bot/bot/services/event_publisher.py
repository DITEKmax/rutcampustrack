"""RabbitMQ event publisher for the notification-bot.

Currently used to publish headman decisions on late-checkin requests back to
the attendance-service. Keeps a durable connection open; reconnects on failure
on the next publish attempt.
"""

import asyncio
import json
import logging
import time
import uuid
from typing import Optional

import aio_pika
import structlog

logger = logging.getLogger(__name__)

EXCHANGE_NAME = "rut-uit.events"
EVENT_SOURCE = "notification-bot"


class EventPublisher:
    def __init__(self, rabbitmq_url: str) -> None:
        self._url = rabbitmq_url
        self._connection: Optional[aio_pika.abc.AbstractRobustConnection] = None
        self._channel: Optional[aio_pika.abc.AbstractRobustChannel] = None
        self._exchange: Optional[aio_pika.abc.AbstractRobustExchange] = None
        self._lock = asyncio.Lock()

    async def _ensure_connected(self) -> None:
        if self._exchange is not None and not self._connection.is_closed:
            return
        async with self._lock:
            if self._exchange is not None and not self._connection.is_closed:
                return
            self._connection = await aio_pika.connect_robust(self._url)
            self._channel = await self._connection.channel()
            self._exchange = await self._channel.declare_exchange(
                EXCHANGE_NAME, aio_pika.ExchangeType.FANOUT, durable=True
            )

    async def publish(self, event_type: str, payload: dict) -> None:
        # M04 Группа 7 — поддерживаем unified envelope (shared-events
        # DomainEvent): trace_id из structlog contextvars с UUID-fallback,
        # event_version=1, source="notification-bot".
        ctx = structlog.contextvars.get_contextvars()
        trace_id = ctx.get("trace_id") or uuid.uuid4().hex
        envelope = {
            "event_type": event_type,
            "event_id": str(uuid.uuid4()),
            "event_version": 1,
            "trace_id": trace_id,
            "source": EVENT_SOURCE,
            "occurred_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "payload": payload,
        }
        try:
            await self._ensure_connected()
            body = json.dumps(envelope).encode("utf-8")
            await self._exchange.publish(
                aio_pika.Message(body=body, content_type="application/json"),
                routing_key="",
            )
            logger.info("Published %s event_id=%s", event_type, envelope["event_id"])
        except Exception:
            logger.exception("Failed to publish %s — resetting connection", event_type)
            self._exchange = None
            raise

    async def close(self) -> None:
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
