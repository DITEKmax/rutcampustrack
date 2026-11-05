"""Isolated UUID Rabbit topology only; no real request, Redis or Telegram calls."""

import asyncio
import json
import logging
import os
from types import SimpleNamespace
from unittest.mock import patch
from uuid import uuid4

import aio_pika
from aiormq.abc import ChannelFrame
from aiormq.connection import Connection as NativeConnection
from pamqp.commands import Connection as ConnectionFrames

from tools import redrive_parked_attendance as helper


def fixture():
    event_id = str(uuid4())
    body = json.dumps(
        {
            "event_id": event_id,
            "event_type": "late_checkin.requested",
            "occurred_at": "2026-11-04T13:00:00+00:00",
            "traceparent": "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
            "payload": {"group_id": 7, "user_id": 2, "request_id": "0123456789abcdef01234567"},
            "_bot_prepared": {
                "version": 1,
                "event_id": event_id,
                "event_type": "late_checkin.requested",
                "recipients": [{"user_id": 1, "roles": ["HEADMAN"]}, {"user_id": 2, "roles": ["STUDENT"]}],
                "delivered_user_ids": [1],
            },
        },
        separators=(",", ":"),
    ).encode()
    headers = {
        helper.REASON_HEADER: "grpc_unavailable",
        helper.RETRY_HEADER: 8,
        "traceparent": "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
    }
    return body, headers, event_id


def pure_validation():
    body, headers, event_id = fixture()
    source = "audit.redrive.validation.parked"
    original = SimpleNamespace(body=body, headers=headers, message_id=event_id, exchange="", routing_key=source)
    assert helper.eligible(original, source)
    rejected = 0
    for changes in (
        {"exchange": "rut-uit.events"},
        {"routing_key": "other"},
        {"headers": {**headers, helper.REASON_HEADER: "grpc_not_found"}},
        {"headers": {**headers, helper.REDRIVE_HEADER: 1}},
        {"headers": {**headers, helper.RETRY_HEADER: "8"}},
        {"headers": {**headers, helper.RETRY_HEADER: True}},
        {"message_id": str(uuid4())},
    ):
        assert not helper.eligible(SimpleNamespace(**{**vars(original), **changes}), source)
        rejected += 1
    for progress in ([1, 1], [99], [True], None):
        event = json.loads(body)
        event["_bot_prepared"]["delivered_user_ids"] = progress
        assert not helper.eligible(SimpleNamespace(**{**vars(original), "body": json.dumps(event).encode()}), source)
        rejected += 1
    return {"eligible": 1, "rejected": rejected}


def close_ok_observer(original, waiters):
    async def observed(native, frame):
        await original(native, frame)
        if type(frame) is ConnectionFrames.CloseOk and native in waiters:
            waiters[native].set()

    return observed


async def close_with_barrier(connection, closed, waiters):
    native = connection.transport.connection
    waiters[native] = closed
    try:
        async with asyncio.timeout(2):
            await native.write_queue.put(
                ChannelFrame.marshall(
                    0,
                    [
                        ConnectionFrames.Close(
                            reply_code=200,
                            reply_text="synthetic redrive boundary loss",
                            class_id=0,
                            method_id=0,
                        )
                    ],
                )
            )
            await closed.wait()
    finally:
        waiters.pop(native, None)


async def topology(url):
    observer = await aio_pika.connect(url)
    channel = await observer.channel()
    prefix = "audit.redrive." + uuid4().hex
    source = await channel.declare_queue(prefix + ".parked", durable=True)
    destination = await channel.declare_queue(prefix + ".active", durable=True)
    return observer, channel, source, destination


async def seed(channel, queue, body, headers, event_id):
    await channel.default_exchange.publish(
        aio_pika.Message(
            body,
            headers=headers,
            message_id=event_id,
            content_type="application/json",
            delivery_mode=2,
        ),
        routing_key=queue.name,
    )


async def clean(observer, channel, source, destination):
    await channel.queue_delete(source.name)
    await channel.queue_delete(destination.name)
    await observer.close()


async def boundary_phase(url, boundary):
    observer, channel, source, destination = await topology(url)
    body, headers, event_id = fixture()
    await seed(channel, source, body, headers, event_id)
    connection = None
    closed = asyncio.Event()
    waiters = {}
    original_connect = aio_pika.connect
    publish_type = type(channel.default_exchange)
    original_publish = publish_type.publish
    original_ack = aio_pika.IncomingMessage.ack
    original_commit = aio_pika.transaction.Transaction.commit

    async def connect(*args, **kwargs):
        nonlocal connection
        connection = await original_connect(*args, **kwargs)
        return connection

    async def disconnect():
        await close_with_barrier(connection, closed, waiters)
        raise ConnectionError("synthetic transaction boundary loss")

    async def publish(exchange, message, *args, **kwargs):
        result = await original_publish(exchange, message, *args, **kwargs)
        if boundary == "publish" and kwargs.get("routing_key") == destination.name:
            await disconnect()
        return result

    async def ack(message, *args, **kwargs):
        result = await original_ack(message, *args, **kwargs)
        if boundary == "ack" and message.routing_key == source.name:
            await disconnect()
        return result

    async def commit(transaction, *args, **kwargs):
        if boundary == "commit_before":
            await disconnect()
        result = await original_commit(transaction, *args, **kwargs)
        if boundary == "commit_after":
            await disconnect()
        return result

    try:
        with (
            patch.object(helper.aio_pika, "connect", connect),
            patch.object(
                NativeConnection,
                "_Connection__handle_close_ok",
                close_ok_observer(NativeConnection._Connection__handle_close_ok, waiters),
            ),
            patch.object(publish_type, "publish", publish),
            patch.object(aio_pika.IncomingMessage, "ack", ack),
            patch.object(aio_pika.transaction.Transaction, "commit", commit),
        ):
            result = await helper.redrive_once(url, source_queue=source.name, destination_queue=destination.name)
        assert closed.is_set() and result["stop_reason"] == "uncertain" and result["moved"] == 0
        parked = await source.get(fail=False, timeout=2)
        active = await destination.get(fail=False, timeout=2)
        assert int(parked is not None) + int(active is not None) == 1
        committed = boundary == "commit_after"
        assert (active is not None) == committed
        message = active if committed else parked
        assert message.body == body and message.message_id == event_id
        assert message.headers[helper.RETRY_HEADER] == (0 if committed else 8)
        assert message.headers.get(helper.REDRIVE_HEADER, 0) == int(committed)
        assert message.delivery_mode.value == 2
        await message.ack()
        return {
            "boundary": boundary,
            "durable_outcomes": 1,
            "active_after_loss": committed,
            "progress_bytes_preserved": True,
            "helper_stopped_uncertain": True,
        }
    finally:
        if connection is not None:
            await connection.close()
        await clean(observer, channel, source, destination)


async def success_once(url):
    observer, channel, source, destination = await topology(url)
    body, headers, event_id = fixture()
    try:
        await seed(channel, source, body, headers, event_id)
        result = await helper.redrive_once(url, source_queue=source.name, destination_queue=destination.name)
        assert result["moved"] == 1 and result["uncertain"] == 0
        message = await destination.get(timeout=2)
        assert message.body == body and message.headers[helper.RETRY_HEADER] == 0
        assert message.headers[helper.REDRIVE_HEADER] == 1
        assert message.headers["traceparent"] == headers["traceparent"]
        assert message.exchange == "" and message.routing_key == destination.name
        assert await source.get(fail=False, timeout=2) is None
        # Synthetic consumer retry exhaustion preserves redrive-count. Operator cannot loop it again.
        await seed(channel, source, message.body, {**message.headers, helper.RETRY_HEADER: 8}, event_id)
        await message.ack()
        repeated = await helper.redrive_once(url, source_queue=source.name, destination_queue=destination.name)
        assert repeated["nonmatching"] == 1 and repeated["moved"] == 0
        retained = await source.get(timeout=2)
        assert retained.body == body
        await retained.ack()
        return {"moved": 1, "progress_bytes_preserved": True, "second_redrive_blocked": True}
    finally:
        await clean(observer, channel, source, destination)


async def nonmatch_and_bound(url, *, nonmatch):
    observer, channel, source, destination = await topology(url)
    body, headers, event_id = fixture()
    total = 2 if nonmatch else 11
    try:
        for index in range(total):
            selected = {**headers, helper.REASON_HEADER: "grpc_not_found"} if nonmatch and index == 0 else headers
            await seed(channel, source, body, selected, event_id)
        result = await helper.redrive_once(url, source_queue=source.name, destination_queue=destination.name)
        assert result["examined"] == (1 if nonmatch else 10)
        assert result["moved"] == (0 if nonmatch else 10)
        assert result["stop_reason"] == ("nonmatching" if nonmatch else "limit")
        await source.declare()
        await destination.declare()
        assert source.declaration_result.message_count == (2 if nonmatch else 1)
        assert destination.declaration_result.message_count == (0 if nonmatch else 10)
        return {
            "case": "nonmatch_stop" if nonmatch else "ten_message_bound",
            "messages_preserved": total,
            "examined": result["examined"],
            "moved": result["moved"],
        }
    finally:
        await clean(observer, channel, source, destination)


async def main():
    logging.disable(logging.CRITICAL)
    url = os.environ["SCHEDULE_BOT_AUDIT_RABBIT_URL"]
    result = {
        "validation": pure_validation(),
        "boundaries": [
            await boundary_phase(url, boundary) for boundary in ("publish", "ack", "commit_before", "commit_after")
        ],
        "success_once": await success_once(url),
        "nonmatch": await nonmatch_and_bound(url, nonmatch=True),
        "bound": await nonmatch_and_bound(url, nonmatch=False),
    }
    print(json.dumps(result))


if __name__ == "__main__":
    asyncio.run(main())
