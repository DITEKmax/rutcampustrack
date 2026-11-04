"""One bounded operator-authorized Attendance TLS recovery pass; never auto-run."""

from __future__ import annotations

import asyncio
import json
import logging
import sys
import time
from uuid import UUID

import aio_pika

from bot.consumers.event_consumer import MAX_RETRIES, PARKING_QUEUE_NAME, QUEUE_NAME, RETRY_HEADER
from bot.notifications.audience import audience_request, prepared_audience

REASON_HEADER = "x-bot-recovery-reason"  # Exact aa34 producer header.
REDRIVE_HEADER = "x-bot-redrive-count"
ALLOWED_EVENTS = frozenset({"late_checkin.requested", "excuse.requested"})
OPERATION = "attendance-tls-redrive-once"


def eligible(message, source_queue=PARKING_QUEUE_NAME):
    """Pure validation; never resolve a real request or infer recipients/permissions."""
    try:
        headers = message.headers or {}
        if (
            message.exchange != ""
            or message.routing_key != source_queue
            or headers.get(REASON_HEADER) != "grpc_unavailable"
            or type(headers.get(RETRY_HEADER)) is not int
            or not 0 <= headers[RETRY_HEADER] <= MAX_RETRIES
            or type(headers.get(REDRIVE_HEADER, 0)) is not int
            or headers.get(REDRIVE_HEADER, 0) != 0
        ):
            return False
        event = json.loads(message.body)
        if not isinstance(event, dict) or event.get("event_type") not in ALLOWED_EVENTS:
            return False
        UUID(event["event_id"])
        if message.message_id != event["event_id"]:
            return False
        prepared_audience(event)  # Includes strict delivered_user_ids subset/unique/positive validation.
        audience_request(event)  # Existing factual business identity/time/scope validation, no network.
        request_key = "ticket_id" if event["event_type"] == "excuse.requested" else "request_id"
        identifier = event["payload"].get(request_key)
        return isinstance(identifier, str) and bool(identifier) and identifier == identifier.strip()
    except Exception:
        return False


async def redrive_once(
    url, *, max_messages=10, max_seconds=30, source_queue=PARKING_QUEUE_NAME, destination_queue=QUEUE_NAME
):
    """Same-channel TX publish+ACK. Caller must explicitly authorize real queue effects."""
    if type(max_messages) is not int or not 1 <= max_messages <= 10:
        raise ValueError("Message bound must be between one and ten")
    if type(max_seconds) is not int or not 6 <= max_seconds <= 30:
        raise ValueError("Time bound must be between six and thirty seconds")
    outcome = {"examined": 0, "moved": 0, "nonmatching": 0, "uncertain": 0, "stop_reason": "empty"}
    connection = None
    transaction = None
    # Reserve five seconds for bounded rollback/close, rather than exceeding the operation bound.
    deadline = time.monotonic() + max_seconds - 5

    def remaining():
        return max(0.001, min(8, deadline - time.monotonic()))

    try:
        async with asyncio.timeout(max_seconds - 5):
            connection = await aio_pika.connect(url, timeout=remaining())  # No automatic robust reconnect/retry.
            channel = await connection.channel(publisher_confirms=False)
            await channel.set_qos(prefetch_count=1)
            source = await channel.get_queue(source_queue, ensure=True)
            await channel.get_queue(destination_queue, ensure=True)  # Existing provisioned destination only.
            if source.declaration_result.consumer_count:
                outcome["stop_reason"] = "source_in_use"
                return outcome
            count = min(max_messages, source.declaration_result.message_count)
            for _ in range(count):
                if time.monotonic() >= deadline:
                    outcome["stop_reason"] = "deadline"
                    break
                transaction = channel.transaction()
                await transaction.select(timeout=remaining())
                message = await source.get(fail=False, no_ack=False, timeout=remaining())
                if message is None:
                    await transaction.rollback(timeout=remaining())
                    transaction = None
                    break
                outcome["examined"] += 1
                if not eligible(message, source_queue):
                    await message.nack(requeue=True)
                    await transaction.commit(timeout=remaining())
                    transaction = None
                    outcome["nonmatching"] += 1
                    outcome["stop_reason"] = "nonmatching"
                    break
                headers = dict(message.headers or {})
                headers[RETRY_HEADER] = 0
                headers[REDRIVE_HEADER] = 1
                replacement = aio_pika.Message(
                    message.body,
                    headers=headers,
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                    content_type=message.content_type or "application/json",
                    message_id=message.message_id,
                    correlation_id=message.correlation_id,
                )
                await channel.default_exchange.publish(
                    replacement,
                    routing_key=destination_queue,
                    mandatory=True,
                    timeout=remaining(),
                )
                await message.ack()
                await transaction.commit(timeout=remaining())
                transaction = None
                outcome["moved"] += 1
                outcome["stop_reason"] = "limit" if outcome["moved"] == max_messages else "empty"
    except asyncio.CancelledError:
        outcome["uncertain"] += int(transaction is not None)
        outcome["stop_reason"] = "uncertain" if transaction is not None else "cancelled"
    except Exception:
        outcome["uncertain"] += int(transaction is not None)
        outcome["stop_reason"] = "uncertain" if transaction is not None else "transport_error"
    finally:
        if transaction is not None:
            try:
                async with asyncio.timeout(2):
                    await transaction.rollback()
            except BaseException:
                pass
        if connection is not None:
            try:
                async with asyncio.timeout(3):
                    await connection.close()
            except BaseException:
                pass
    return outcome


def main():
    logging.disable(logging.CRITICAL)
    try:
        configuration = json.load(sys.stdin)
        if configuration.get("operation") != OPERATION:
            raise ValueError("Explicit operation required")
        # Credentials are accepted only through stdin; never argv, logs or persistent Docker env.
        url = configuration["rabbitmq_url"]
        if not isinstance(url, str) or not url:
            raise ValueError("Connection required")
        result = asyncio.run(
            redrive_once(
                url,
                max_messages=configuration.get("max_messages", 10),
                max_seconds=configuration.get("max_seconds", 30),
            )
        )
        print(json.dumps(result))
        return 2 if result["stop_reason"] in {"uncertain", "transport_error", "cancelled"} else 0
    except BaseException as error:
        print(json.dumps({"stop_reason": "configuration_or_runtime_error", "exception_type": type(error).__name__}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
