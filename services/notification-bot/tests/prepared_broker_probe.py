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


async def phase(url, boundary):
    prefix = "audit.prepared." + uuid4().hex
    names = dict(
        QUEUE_NAME=prefix,
        EXCHANGE_NAME=prefix + ".events",
        DLQ_QUEUE_NAME=prefix + ".dlq",
        DLQ_EXCHANGE_NAME=prefix + ".dead",
        DLQ_ROUTING_KEY=prefix + ".dead-key",
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
            assert is_prepared == (boundary == "commit_after")
            assert not completed.is_set() and not providers
            if is_prepared:
                assert original.exchange == "" and original.routing_key == prefix
            await original.nack(requeue=True)
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
        await source.delete()
        await dead.delete()
        await observer.close()


async def main():
    logging.disable(logging.CRITICAL)
    url = os.environ["SCHEDULE_BOT_AUDIT_RABBIT_URL"]
    outcomes = [await phase(url, boundary) for boundary in ("publish", "ack", "commit_before", "commit_after")]
    print(json.dumps({"prepared_transaction_probe": outcomes}))


if __name__ == "__main__":
    asyncio.run(main())
