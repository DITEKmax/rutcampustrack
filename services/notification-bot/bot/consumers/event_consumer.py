import json
import logging

import aio_pika

from bot.observability import bind_trace_context

logger = logging.getLogger(__name__)

EXCHANGE_NAME = "rut-uit.events"
DLQ_EXCHANGE_NAME = "rut-uit.events.dlq"
QUEUE_NAME = "notification-bot.events"
DLQ_QUEUE_NAME = "notification-bot.events.dlq"
DLQ_ROUTING_KEY = "notification-bot.events.dlq"


async def start_consumer(
    rabbitmq_url: str,
    dispatcher=None,
    idempotency_guard=None,
) -> aio_pika.abc.AbstractRobustConnection:
    """
    Connect to RabbitMQ via connect_robust (auto-reconnect),
    declare fanout exchange + queue with DLQ, consume and log events.

    M13 G8: при наличии `idempotency_guard` проверяем `event_id` через
    Redis SET NX перед dispatch — duplicate skip.

    Returns the connection object so health check can inspect it.
    """
    connection = await aio_pika.connect_robust(rabbitmq_url)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)

    # Declare fanout exchange (idempotent — same exchange used by all consumers)
    exchange = await channel.declare_exchange(
        EXCHANGE_NAME,
        aio_pika.ExchangeType.FANOUT,
        durable=True,
    )

    # Declare DLQ exchange (direct)
    dlq_exchange = await channel.declare_exchange(
        DLQ_EXCHANGE_NAME,
        aio_pika.ExchangeType.DIRECT,
        durable=True,
    )

    # Declare DLQ queue and bind
    dlq_queue = await channel.declare_queue(DLQ_QUEUE_NAME, durable=True)
    await dlq_queue.bind(dlq_exchange, routing_key=DLQ_ROUTING_KEY)

    # Declare main queue with DLQ arguments
    queue = await channel.declare_queue(
        QUEUE_NAME,
        durable=True,
        arguments={
            "x-dead-letter-exchange": DLQ_EXCHANGE_NAME,
            "x-dead-letter-routing-key": DLQ_ROUTING_KEY,
        },
    )
    await queue.bind(exchange)

    logger.info("Consumer bound to queue '%s' on exchange '%s'", QUEUE_NAME, EXCHANGE_NAME)

    async with queue.iterator() as queue_iter:
        async for message in queue_iter:
            async with message.process():
                try:
                    body = json.loads(message.body)
                    event_type = body.get("event_type", "unknown")
                    event_id = body.get("event_id")
                    # M04 Группа 7: trace_id приходит в envelope от Java-сервисов
                    # (shared-events AbstractEventPublisher.fillDefaults).
                    # Биндим его в structlog contextvars чтобы все логи
                    # handler'а несли один trace_id.
                    with bind_trace_context(
                        body.get("trace_id"),
                        event_type=event_type,
                        event_id=event_id,
                    ):
                        # M13 G8 — consumer-side dedup. Redis SET NX guard
                        # отсекает повторную доставку того же event_id.
                        if idempotency_guard is not None:
                            if not await idempotency_guard.try_claim(event_id):
                                continue
                        logger.info("[notification-bot] Received event: %s", event_type)
                        if dispatcher:
                            await dispatcher.dispatch(body)
                except json.JSONDecodeError:
                    logger.error("Failed to decode message body: %s", message.body[:200])
                except Exception:
                    logger.exception("Handler failed for event, acking anyway")

    return connection
