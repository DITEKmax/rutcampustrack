"""Standalone synthetic Rabbit probe for raw→prepared same-channel transactions.

Run only against an explicitly allocated isolated Rabbit vhost via
SCHEDULE_BOT_AUDIT_RABBIT_URL. No Telegram, Redis, production topology or provider calls.
All topology names carry a per-run UUID. Stdout contains only finite outcomes.
"""

import asyncio
import json
import logging
import os
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from uuid import uuid4

import aio_pika
from aiormq.abc import ChannelFrame
from aiormq.connection import Connection as NativeConnection
from pamqp.commands import Connection as ConnectionFrames

from bot.consumers import event_consumer as consumer
from bot.services.notification_prefs import NotificationPreferencesUnavailable

EVENT = {
    "event_id": "b1e5df89-f73c-4c06-9b3c-231fb56fe41c",
    "event_type": "group.renamed",
    "occurred_at": "2026-10-10T13:00:00+03:00",
    "trace_id": "synthetic-prepare-trace",
    "payload": {"group_id": 7},
}
PREPARED = {
    **EVENT,
    "_bot_prepared": {
        "version": 1,
        "event_id": EVENT["event_id"],
        "event_type": EVENT["event_type"],
        "recipients": [{"user_id": 1, "roles": ["STUDENT", "TEACHER"]}],
    },
}


def close_ok_observer(original, waiters):
    async def observed(native, frame):
        await original(native, frame)
        if type(frame) is ConnectionFrames.CloseOk:
            signal = waiters.get(native)
            if signal is not None:
                # Signal in the reader: EOF/local cleanup may cancel the waiting processor.
                signal.set()

    return observed


async def close_with_barrier(connection, confirmed, waiters):
    native = connection.transport.connection
    assert not confirmed.is_set() and native not in waiters
    waiters[native] = confirmed
    try:
        async with asyncio.timeout(2):
            # aiormq 6.8.1 normally sends this frame after cancelling its reader.
            # Keep the reader alive until Rabbit confirms all worker channels are gone.
            await native.write_queue.put(
                ChannelFrame.marshall(
                    0,
                    [
                        ConnectionFrames.Close(
                            reply_code=200,
                            reply_text="normally closed",
                            class_id=0,
                            method_id=0,
                        )
                    ],
                )
            )
            await confirmed.wait()
    finally:
        waiters.pop(native, None)


async def phase(url, boundary, *, retry=False):
    prefix = "audit.prepared." + uuid4().hex
    names = dict(
        QUEUE_NAME=prefix,
        EXCHANGE_NAME=prefix + ".events",
        DLQ_QUEUE_NAME=prefix + ".dlq",
        DLQ_EXCHANGE_NAME=prefix + ".dead",
        DLQ_ROUTING_KEY=prefix + ".dead-key",
        PARKING_QUEUE_NAME=prefix + ".parked",
    )
    observer = await aio_pika.connect(url)
    channel = await observer.channel()
    source = await channel.declare_exchange(names["EXCHANGE_NAME"], aio_pika.ExchangeType.FANOUT, durable=True)
    dead = await channel.declare_exchange(names["DLQ_EXCHANGE_NAME"], aio_pika.ExchangeType.DIRECT, durable=True)
    queue = await channel.declare_queue(
        prefix,
        durable=True,
        arguments={
            "x-dead-letter-exchange": names["DLQ_EXCHANGE_NAME"],
            "x-dead-letter-routing-key": names["DLQ_ROUTING_KEY"],
        },
    )
    await queue.bind(source)
    await source.publish(aio_pika.Message(json.dumps(EVENT).encode(), delivery_mode=2), routing_key="")
    original_connect = aio_pika.connect_robust
    connection = None
    interrupted = asyncio.Event()
    completed = asyncio.Event()
    providers = []
    close_waiters = {}

    async def connect(*args, **kwargs):
        nonlocal connection
        connection = await original_connect(*args, **kwargs)
        return connection

    async def complete(*args):
        completed.set()
        return True

    guard = SimpleNamespace(
        _ttl=30,
        claim=AsyncMock(return_value="owned"),
        is_completed=AsyncMock(return_value=False),
        release=AsyncMock(return_value=True),
        complete=AsyncMock(side_effect=complete),
    )

    async def dispatch(body):
        providers.append(body["event_id"])

    dispatcher = SimpleNamespace(prepare=AsyncMock(return_value=PREPARED), dispatch=dispatch)
    if retry:
        dispatcher.prepare.side_effect = NotificationPreferencesUnavailable("synthetic authority outage")
    publish_type = type(source)
    transaction_type = aio_pika.transaction.Transaction
    original_publish = publish_type.publish
    original_ack = aio_pika.IncomingMessage.ack
    original_commit = transaction_type.commit

    async def disconnect():
        await close_with_barrier(connection, interrupted, close_waiters)
        raise ConnectionError("synthetic transaction boundary loss")

    async def publish(exchange, message, *args, **kwargs):
        result = await original_publish(exchange, message, *args, **kwargs)
        if boundary == "publish" and kwargs.get("routing_key") == prefix:
            await disconnect()
        return result

    async def ack(message, *args, **kwargs):
        result = await original_ack(message, *args, **kwargs)
        if boundary == "ack" and message.routing_key != prefix:
            await disconnect()
        return result

    async def commit(transaction, *args, **kwargs):
        if boundary == "commit_before":
            await disconnect()
        result = await original_commit(transaction, *args, **kwargs)
        if boundary == "commit_after":
            await disconnect()
        return result

    task = None
    try:
        # Narrow pinned aiormq hook; absent/changed dispatch fails closed, with no fallback.
        original_close_ok = NativeConnection._Connection__handle_close_ok
        observed_close_ok = close_ok_observer(original_close_ok, close_waiters)
        # Cover both worker lifetimes: the native reader captures its handler at startup.
        with (
            patch.multiple(consumer, **names),
            patch.object(consumer.aio_pika, "connect_robust", connect),
            patch.object(NativeConnection, "_Connection__handle_close_ok", observed_close_ok),
            patch.object(consumer, "RETRY_DELAYS", (0,) * consumer.MAX_RETRIES),
        ):
            with (
                patch.object(publish_type, "publish", publish),
                patch.object(aio_pika.IncomingMessage, "ack", ack),
                patch.object(transaction_type, "commit", commit),
            ):
                task = asyncio.create_task(consumer.start_consumer(url, dispatcher, guard))
                await asyncio.wait_for(interrupted.wait(), 8)
                await connection.close()  # Close local channels before iterator cancellation.
                task.cancel()
                await asyncio.gather(task, return_exceptions=True)
            # CloseOk proves broker requeue is settled before independent durable reads.
            original = await queue.get(fail=False, timeout=2)
            assert original is not None, "no message after broker connection close confirmation"
            observed = json.loads(original.body)
            assert await queue.get(fail=False, timeout=2) is None, "broker retained both original and projection"
            is_prepared = "_bot_prepared" in observed
            assert observed == (PREPARED if is_prepared else EVENT)
            assert is_prepared == (not retry and boundary == "commit_after")
            if retry:
                assert (original.headers or {}).get(consumer.RETRY_HEADER, 0) == int(boundary == "commit_after")
            assert not completed.is_set() and not providers
            if is_prepared:
                assert original.exchange == "" and original.routing_key == prefix
            await original.nack(requeue=True)
            if retry:
                dispatcher.prepare.side_effect = None
                dispatcher.prepare.return_value = None
            # Observe replay commit and broker connection close before cancellation/readback.
            replay_committed = asyncio.Event()

            async def replay_commit(transaction, *args, **kwargs):
                result = await original_commit(transaction, *args, **kwargs)
                if completed.is_set():
                    await close_with_barrier(connection, replay_committed, close_waiters)
                return result

            with patch.object(transaction_type, "commit", replay_commit):
                task = asyncio.create_task(consumer.start_consumer(url, dispatcher, guard))
                await asyncio.wait_for(replay_committed.wait(), 8)
                await connection.close()  # Close local channels before iterator cancellation.
                task.cancel()
                await asyncio.gather(task, return_exceptions=True)
            # Include any prefetched processing that raced with the parent's cancellation.
            assert completed.is_set() and providers == [EVENT["event_id"]]
            assert await asyncio.wait_for(queue.get(fail=False, timeout=2), 3) is None
            return {
                "boundary": boundary,
                "replacement": "retry" if retry else "prepared",
                "durable_outcomes": 1,
                "projection_after_loss": is_prepared,
                "completion_before_replay": False,
                "replay_provider_deliveries": len(providers),
            }
    finally:
        if connection is not None:
            await connection.close()
        if task is not None and not task.done():
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        # Only this run's UUID topology is removed.
        await channel.queue_delete(prefix)
        await channel.queue_delete(names["DLQ_QUEUE_NAME"])
        await channel.queue_delete(names["PARKING_QUEUE_NAME"])
        await source.delete()
        await dead.delete()
        await observer.close()


async def main():
    logging.disable(logging.CRITICAL)
    url = os.environ["SCHEDULE_BOT_AUDIT_RABBIT_URL"]
    outcomes = [
        await phase(url, boundary, retry=retry)
        for retry in (False, True)
        for boundary in ("publish", "ack", "commit_before", "commit_after")
    ]
    recovery = await recovery_progress(url)
    print(json.dumps({"prepared_transaction_probe": outcomes, "recovery_progress": recovery}))


async def recovery_progress(url):
    """Real broker FIFO/parking with synthetic authority and completion receipts."""
    from bot.notifications.audience import NotificationDeliveryState

    prefix = "audit.recovery." + uuid4().hex
    names = dict(
        QUEUE_NAME=prefix, EXCHANGE_NAME=prefix + ".events", DLQ_QUEUE_NAME=prefix + ".dlq",
        DLQ_EXCHANGE_NAME=prefix + ".dead", DLQ_ROUTING_KEY=prefix + ".dead-key",
        PARKING_QUEUE_NAME=prefix + ".parked",
    )
    observer = await aio_pika.connect(url)
    channel = await observer.channel()
    source = await channel.declare_exchange(names["EXCHANGE_NAME"], aio_pika.ExchangeType.FANOUT, durable=True)
    dead = await channel.declare_exchange(names["DLQ_EXCHANGE_NAME"], aio_pika.ExchangeType.DIRECT, durable=True)
    queue = await channel.declare_queue(prefix, durable=True, arguments={
        "x-dead-letter-exchange": names["DLQ_EXCHANGE_NAME"],
        "x-dead-letter-routing-key": names["DLQ_ROUTING_KEY"],
    })
    await queue.bind(source)
    parking = await channel.declare_queue(names["PARKING_QUEUE_NAME"], durable=True)
    events = {mode: {**EVENT, "event_id": str(uuid4()), "payload": {"mode": mode}}
              for mode in ("terminal", "healthy", "transient", "exhausted")}
    events["exhausted"]["_bot_prepared"] = {
        "version": 1, "event_id": events["exhausted"]["event_id"], "event_type": EVENT["event_type"],
        "recipients": [{"user_id": uid, "roles": ["STUDENT"]} for uid in (1, 2)],
    }
    for mode in ("terminal", "healthy", "transient", "exhausted", "healthy"):
        exchange = channel.default_exchange if mode == "exhausted" else source
        await exchange.publish(
            aio_pika.Message(json.dumps(events[mode]).encode(), delivery_mode=2),
            routing_key=prefix if mode == "exhausted" else "",
        )
    completed, owned, deliveries, attempts, order = set(), set(), [], {}, []
    original_connect = aio_pika.connect_robust
    connection = task = None
    receipt_available = True

    async def mget(keys):
        return ["sent" if receipt_available and key.endswith(":1") else None for key in keys]

    receipts = NotificationDeliveryState(SimpleNamespace(mget=mget))

    async def snapshot_progress(body):
        nonlocal receipt_available
        result = await receipts.snapshot(body)
        receipt_available = False  # Simulated Redis TTL expiry after first durable retry snapshot.
        return result

    async def connect(*args, **kwargs):
        nonlocal connection
        connection = await original_connect(*args, **kwargs)
        return connection

    async def claim(event_id):
        if event_id in completed or event_id in owned:
            return None
        owned.add(event_id)
        return "synthetic-owner"

    async def release(event_id, token):
        owned.discard(event_id)
        return True

    async def complete(event_id, token):
        completed.add(event_id)
        owned.discard(event_id)
        return True

    async def dispatch(body):
        mode = body["payload"]["mode"]
        order.append(mode)
        attempts[mode] = attempts.get(mode, 0) + 1
        if mode == "terminal":
            try:
                raise ValueError("synthetic malformed authority context")
            except ValueError as error:
                raise NotificationPreferencesUnavailable("synthetic authority failure") from error
        if mode == "exhausted" or (mode == "transient" and attempts[mode] <= 2):
            raise NotificationPreferencesUnavailable("synthetic authority outage")
        deliveries.append(mode)

    guard = SimpleNamespace(
        _ttl=30, claim=claim, release=release, complete=complete,
        is_completed=AsyncMock(side_effect=lambda event_id: event_id in completed),
    )
    try:
        with (
            patch.multiple(consumer, **names), patch.object(consumer.aio_pika, "connect_robust", connect),
            patch.object(consumer, "RETRY_DELAYS", (0,) * consumer.MAX_RETRIES),
        ):
            dispatcher = SimpleNamespace(dispatch=dispatch, snapshot_progress=snapshot_progress)
            task = asyncio.create_task(consumer.start_consumer(url, dispatcher, guard))
            async with asyncio.timeout(10):
                while True:
                    await parking.declare()
                    if parking.declaration_result.message_count == 2 and len(completed) == 2:
                        break
                    await asyncio.sleep(0.05)
            await connection.close()
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        assert deliveries == ["healthy", "transient"]
        assert attempts == {"terminal": 1, "healthy": 1, "transient": 3, "exhausted": 9}
        assert order.index("healthy") < order.index("transient", order.index("transient") + 1)
        assert await queue.get(fail=False, timeout=2) is None
        parked = []
        for _ in range(2):
            message = await parking.get(timeout=2)
            body = json.loads(message.body)
            mode = body["payload"]["mode"]
            expected = events[mode]
            if mode == "exhausted":
                expected = {**expected, "_bot_prepared": {**expected["_bot_prepared"], "delivered_user_ids": [1]}}
            assert body == expected
            assert (message.headers or {}).get(consumer.RETRY_HEADER, 0) == (8 if mode == "exhausted" else 0)
            parked.append(mode)
            await message.ack()
        assert sorted(parked) == ["exhausted", "terminal"]
        return {"parked": sorted(parked), "provider_deliveries": deliveries, "attempts": attempts,
                "confirmed_progress_retained": True}
    finally:
        if connection is not None:
            await connection.close()
        if task is not None:
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        for name in (prefix, names["DLQ_QUEUE_NAME"], names["PARKING_QUEUE_NAME"]):
            await channel.queue_delete(name)
        await source.delete()
        await dead.delete()
        await observer.close()


if __name__ == "__main__":
    asyncio.run(main())
