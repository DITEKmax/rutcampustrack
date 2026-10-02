import asyncio
import json
import logging

import aio_pika
import grpc

from bot.observability import bind_trace_context
from bot.services.notification_prefs import NotificationPreferencesUnavailable

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

    # Declare DLQ queue and bind.
    # M16 G2 — retention args. Без них DLQ растёт бесконечно (фиксировалось
    # в future-ideas.md § N6 для notification-web; то же актуально и здесь).
    # x-message-ttl=7d синхронно с временем reasonable для manual triage.
    # x-max-length=10000 + drop-head = circuit breaker против flood'а handler bug'ов.
    # Alert DLQBacklog (infra/prometheus/rules/rabbitmq.yml) ловит >10 сообщений за 5 мин.
    dlq_queue = await channel.declare_queue(
        DLQ_QUEUE_NAME,
        durable=True,
        arguments={
            "x-message-ttl": 7 * 24 * 60 * 60 * 1000,  # 7 дней (мс)
            "x-max-length": 10000,
            "x-overflow": "drop-head",  # старые сначала, чтобы новые landed
        },
    )
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

    processors = set()
    slots = asyncio.Semaphore(2)
    configured_budget = getattr(idempotency_guard, "_ttl", 300)
    budget = configured_budget if type(configured_budget) is int and configured_budget > 0 else 300

    async def requeue(message, *, delay=True):
        if delay:
            await asyncio.sleep(1)
        if not message.processed:
            try:
                await message.nack(requeue=True)
            except aio_pika.exceptions.ChannelInvalidStateError:
                # Closed transport returns the original UNACKED message to Rabbit.
                logger.info("Channel closed while deferring original event")

    async def process(message):
        event_id = claim_token = batch = heartbeat = None
        lease_lost = False
        owner = asyncio.current_task()

        async def release():
            if claim_token is not None:
                try:
                    # Existing HTTP authority timeout also bounds best-effort cleanup;
                    # on failure the existing processing lease expiry enables replay.
                    async with asyncio.timeout(min(8, budget)):
                        await idempotency_guard.release(event_id, claim_token)
                except Exception:
                    logger.warning("Processing lease retained until expiry event_id=%s", event_id)

        async def renew():
            nonlocal lease_lost
            try:
                while True:
                    await asyncio.sleep(budget / 3)
                    if not await idempotency_guard.renew(event_id, claim_token):
                        raise RuntimeError("Lost processing lease")
            except asyncio.CancelledError:
                raise
            except Exception:
                lease_lost = True
                logger.warning("Lease loss defers original event_id=%s", event_id)
                owner.cancel()

        async with message.process(requeue=False, ignore_processed=True):
            try:
                # Deadline includes authority, queue/rate/backoff/429 and provider waits.
                # A permanently unavailable recipient cannot occupy an event slot forever.
                async with asyncio.timeout(budget):
                    body = json.loads(message.body)
                    event_id = body.get("event_id")
                    event_type = body.get("event_type", "unknown")
                    with bind_trace_context(body.get("trace_id"), event_type=event_type, event_id=event_id):
                        if idempotency_guard is not None:
                            claim = getattr(idempotency_guard, "claim", None)
                            if claim is None:
                                if not await idempotency_guard.try_claim(event_id):
                                    return
                            else:
                                claim_token = await claim(event_id)
                                if claim_token is None:
                                    if not await idempotency_guard.is_completed(event_id):
                                        await requeue(message)
                                    return
                                if getattr(idempotency_guard, "renew", None) is not None:
                                    heartbeat = asyncio.create_task(renew())
                        logger.info("[notification-bot] Received event: %s", event_type)
                        if dispatcher:
                            batch = await dispatcher.dispatch(body)
                            if batch is not None:
                                await batch.wait()
                        if lease_lost:
                            raise NotificationPreferencesUnavailable("Event lease lost")
                        if claim_token is not None:
                            if not await idempotency_guard.complete(event_id, claim_token):
                                raise NotificationPreferencesUnavailable("Event lease lost at completion")
            except (NotificationPreferencesUnavailable, TimeoutError):
                if batch is not None:
                    batch.cancel()
                await release()
                await requeue(message)
            except asyncio.CancelledError:
                if batch is not None:
                    batch.cancel()
                # Graceful shutdown/channel replacement behaves like a process crash.
                # Do not wait for Redis while stopping the owner; its TTL is the fallback.
                await requeue(message, delay=False)
                if not lease_lost:
                    raise
            except grpc.aio.AioRpcError as error:
                if batch is not None:
                    batch.cancel()
                await release()
                if error.code() in {grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.DEADLINE_EXCEEDED,
                                    grpc.StatusCode.UNKNOWN, grpc.StatusCode.INTERNAL,
                                    grpc.StatusCode.RESOURCE_EXHAUSTED, grpc.StatusCode.ABORTED}:
                    await requeue(message)
                else:
                    raise
            except BaseException:
                if batch is not None:
                    batch.cancel()
                await release()
                # Provider exhaustion / malformed handler uses the existing durable DLQ.
                raise
            finally:
                if heartbeat is not None:
                    heartbeat.cancel()
                    await asyncio.gather(heartbeat, return_exceptions=True)

    def finished(task):
        processors.discard(task)
        slots.release()
        if not task.cancelled() and task.exception() is not None:
            logger.error("Event processing failed; original retained by DLQ", exc_info=task.exception())

    try:
        async with queue.iterator() as queue_iter:
            async for message in queue_iter:
                await slots.acquire()
                task = asyncio.create_task(process(message))
                processors.add(task)
                task.add_done_callback(finished)
    finally:
        for task in list(processors):
            task.cancel()
        # Close broker transport before awaiting owner cleanup: pending messages
        # return to the durable queue even if a processor was inside provider I/O.
        await connection.close()
        await asyncio.gather(*list(processors), return_exceptions=True)

    return connection
